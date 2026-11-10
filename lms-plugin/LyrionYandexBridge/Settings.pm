package Plugins::LyrionYandexBridge::Settings;

# Страница настроек плагина в веб-интерфейсе LMS (Настройки -> Плагины -> Lyrion Yandex Bridge).

use strict;

use base qw(Slim::Web::Settings);

use JSON::XS;
use LWP::UserAgent;
use File::Spec::Functions qw(catfile catdir);
use Encode qw(encode);
use Slim::Utils::Log;
use Slim::Utils::Network;
use Slim::Utils::Prefs;

my $prefs = preferences('plugin.lyrionyandexbridge');
my $log   = logger('plugin.lyrionyandexbridge');

# анти-шторм refresh: вкладка настроек LMS может отправлять форму repeatedly
my $lastRefreshRequest = 0;

sub name { 'PLUGIN_LYRION_YANDEX_BRIDGE' }

sub page { 'plugins/LyrionYandexBridge/settings/basic.html' }

# имена pref_* автоматически сохраняются базовым классом при saveSettings
sub prefs {
	return ($prefs, qw(autorun javapath port bind opts));
}

# запросить у клиента JSON-сводку (GET /status.json) и собрать строки для шаблона.
# надписи - ТОЛЬКО английские литералы (латиница не зависит от перекодировок LMS);
# русские данные (имена, комнаты) приходят из JSON клиента и отображаются корректно.
sub _clientStatus {
	my $port = $prefs->get('port') || 8888;

	my $ua = LWP::UserAgent->new(timeout => 3);
	my $resp = eval { $ua->get("http://127.0.0.1:$port/status.json") };
	return unless $resp && $resp->is_success;

	my $d = eval { JSON::XS::decode_json($resp->decoded_content) };
	return unless $d && ref $d eq 'HASH';

	my $on  = '<span style="color:green">connected</span>';
	my $off = '<span style="color:red">disconnected</span>';

	my %st;

	# селекты выбора комнаты для каждого плеера (только комната, без остальных настроек).
	# inyandex: устройство этой комнаты зарегистрировано в Яндексе (тот же список,
	# что и «Music devices in Yandex»). alice: Яндекс-колонки в комнате (модель + имя).
	my %alicebyroom = %{ $d->{'aliceByRoom'} || {} };
	my @yandexmusic = @{$d->{'musicYandex'} || []};
	my @roomsel;
	for my $p (@{$d->{'players'} || []}) {
		my $room = defined $p->{'room'} ? $p->{'room'} : '';
		my @rooms = @{$d->{'rooms'} || []};
		unshift @rooms, $room if $room ne '' && !grep { $_ eq $room } @rooms;
		push @roomsel, {
			'name'      => $p->{'name'} || '',
			'room'      => $room,
			'options'   => \@rooms,
			'inyandex'  => ($room ne '' && grep { $_ eq $room } @yandexmusic) ? 1 : 0,
			'buttons'   => ($p->{'createButtons'} ? 1 : 0),
			'alice'     => ($room ne '' && $alicebyroom{$room})
				? join(', ', @{$alicebyroom{$room}}) : '',
		};
	}
	$st{'roomselects'} = \@roomsel;

	my $offshort = ' <span style="color:red">off</span>';
	my @pl = map {
		($_->{'name'} || '')
		. (defined $_->{'room'} && $_->{'room'} ne '' ? ' (' . $_->{'room'} . ')' : '')
		. ($_->{'connected'} ? '' : $offshort)
	} @{$d->{'players'} || []};
	$st{'players'} = scalar @pl ? join(', ', @pl) : '<span style="color:red">none</span>';

	$st{'yandex'} = $d->{'yandex'}->{'loggedIn'}
		? $on . ' ' . ($d->{'yandex'}->{'name'} || '')
		: '<span style="color:red">not authorized</span>';

# (комнаты Яндекса на странице плагина не показываем — список длинный, был убран по просьбе владельца)

	$st{'music_plugin'} = (join(', ', @{$d->{'musicLocal'} || []}) || '-');
	$st{'music_yandex'} = (join(', ', @{$d->{'musicYandex'} || []}) || '-');

	my $spotifyUser = $d->{'spotify'}->{'user'} || '';
	my $spotifyError = $d->{'spotify'}->{'error'} || '';
	$st{'spotify'} = $d->{'spotify'}->{'loggedIn'}
		? $on . ($spotifyUser ne '' ? ' ' . $spotifyUser : '')
		: '<span style="color:red">not authorized' . ($spotifyError ne '' ? ' (' . $spotifyError . ')' : '') . '</span>';
	$st{'spotifyloggedin'} = $d->{'spotify'}->{'loggedIn'} ? 1 : 0;

	$st{'cloud'} = $d->{'cloud'}->{'connected'}
		? $on
		: '<span style="color:red">not authorized</span>';
	$st{'cloudconnected'} = $d->{'cloud'}->{'connected'} ? 1 : 0;

	# лог клиента: размер и период обнуления (приходят из /status.json клиента)
	my $logsize = $d->{'log'}->{'size'} || 0;
	my @units = ('B', 'KB', 'MB', 'GB');
	my $ui = 0;
	while ($logsize >= 1024 && $ui < 3) { $logsize /= 1024; $ui++; }
	$st{'logsize'} = sprintf('%.1f %s', $logsize, $units[$ui]);
	$st{'logdays'} = defined $d->{'log'}->{'rotateDays'} ? int($d->{'log'}->{'rotateDays'}) : 5;

	return \%st;
}

# --- серверные зависимости и состояние голоса (Piper/Алиса) -----------------

# найти установленный Piper: сначала /opt/piper (ручная установка), затем
# <datadir>/piper (ставится кнопкой). Возвращает хеш с путями и размерами.
sub _piperFind {
	my $dir = Plugins::LyrionYandexBridge::ClientProcess::dataDir();
	for my $base ('/opt/piper', catfile($dir, 'piper')) {
		my $bin = catfile($base, 'piper', 'piper');
		next unless -x $bin;
		my ($voice) = glob("$base/*.onnx");
		return {
			'base'  => $base,
			'bin'   => $bin,
			'voice' => defined $voice ? $voice : '',
			'ok'    => defined $voice && -s $voice ? 1 : 0,
		};
	}
	return;
}

# прочитать значение ключа из data/settings_voice.properties
sub _propGet {
	my ($key) = @_;
	my $f = catfile(Plugins::LyrionYandexBridge::ClientProcess::dataDir(), 'data', 'settings_voice.properties');
	open my $fh, '<', $f or return;
	my $val;
	while (my $l = <$fh>) {
		if ($l =~ /^\Q$key\E=(.*)$/) { $val = $1; chomp $val; last; }
	}
	close $fh;
	return $val;
}

# установить ключ в data/settings_voice.properties (перезапись строки)
sub _propSet {
	my ($key, $val) = @_;
	my $dir = Plugins::LyrionYandexBridge::ClientProcess::dataDir();
	my $f = catfile($dir, 'data', 'settings_voice.properties');
	mkdir catdir($dir, 'data') unless -d catdir($dir, 'data');
	my @lines;
	if (open my $fh, '<', $f) { @lines = <$fh>; close $fh; }
	@lines = grep { !/^\Q$key\E=/ } @lines;
	push @lines, "$key=$val\n";
	if (open my $out, '>', $f) { print $out @lines; close $out; }
	else { $log->error("cannot write $f: $!"); }
}

sub _humanSize {
	my ($b) = @_;
	return '' unless defined $b;
	my @u = ('B', 'KB', 'MB', 'GB');
	my $i = 0;
	while ($b >= 1024 && $i < 3) { $b /= 1024; $i++; }
	return sprintf('%.0f %s', $b, $u[$i]);
}

# сводка «Сервер»: java/piper/ffmpeg/cookies + режим голоса
sub _serverStatus {
	my %srv;

	# java
	my $java = Plugins::LyrionYandexBridge::ClientProcess::javaPath();
	if ($java) {
		my $v = `$java -version 2>&1 | head -1`;
		$v =~ s/\s+$//;
		$srv{'java'} = qq{<span style="color:green">$java</span> - $v};
	}
	else {
		$srv{'java'} = '<span style="color:red">not found</span> - sudo apt install default-jre-headless';
	}

	# ffmpeg (опционален: усиление громкости уведомлений)
	$srv{'ffmpeg'} = Slim::Utils::Misc::findbin('ffmpeg')
		? qq{<span style="color:green">ok</span>}
		: '<span style="color:orange">not found (optional) - sudo apt install ffmpeg';

	# piper: установлен / идёт установка / нет
	my $p = _piperFind();
	if ($p && $p->{'ok'}) {
		$srv{'piperok'}   = 1;
		$srv{'piperbin'}  = $p->{'bin'};
		$srv{'pipervoice'} = $p->{'voice'};
		$srv{'piperinfo'} = _humanSize(-s $p->{'bin'}) . ' + voice ' . _humanSize(-s $p->{'voice'});
	}
	else {
		my $dir = Plugins::LyrionYandexBridge::ClientProcess::dataDir();
		my $logf = catfile($dir, 'piper-install.log');
		if (-e catfile($dir, 'piper', 'INSTALLED')) {
			$srv{'piperinfo'} = 'installed (see log)';
		}
		elsif (-e $logf && -M $logf < 5.0 / 1440) {    # лог свежее 5 минут
			$srv{'piperinstalling'} = 1;
			$srv{'piperinfo'} = 'installing...';
		}
		else {
			$srv{'piperinfo'} = '<span style="color:red">not installed</span>';
		}
	}

	# режим голоса и cookies Алисы
	$srv{'voicemode'} = (_propGet('VOICE_TOGGLE_SAY_ALICE') // 'true') eq 'false' ? 'piper' : 'alice';
	my $ck = _propGet('VOICE_STRING_YA_COOKIES') // '';
	$srv{'cookies'} = ($ck ne '' && $ck ne '---')
		? '<span style="color:green">set</span>'
		: '<span style="color:orange">not set (Alice voice disabled) - paste on client settings page</span>';

	return \%srv;
}

sub handler {
	my ($class, $client, $paramRef, $callback, $httpClient, $response) = @_;

	require Plugins::LyrionYandexBridge::ClientProcess;

	if ($paramRef->{'restart'}) {
		$log->info('restart requested from settings page');
		Plugins::LyrionYandexBridge::ClientProcess->stop;
		Plugins::LyrionYandexBridge::ClientProcess->start;
	}

	# Reset: полный сброс состояния клиента. Все файлы состояния в data/ (config.json с токенами,
	# lms_players.json с комнатами/настройками плееров, привязки навыка, devices, settings*.properties)
	# уводятся в бэкап *.bak-reset-<ts>; логи, pid и старые бэкапы не трогаем. Клиент стартует с нуля.
	if ($paramRef->{'resetclient'}) {
		$log->warn('client reset (full wipe) requested from settings page');
		Plugins::LyrionYandexBridge::ClientProcess->stop;
		my $dir = Plugins::LyrionYandexBridge::ClientProcess::dataDir();
		my $suffix = '.bak-reset-' . time();
		# состояние клиента лежит в двух местах: config.json в корне dataDir и
		# файлы *.json/*.properties в подкаталоге data/ (комнаты плееров, привязки, devices)
		for my $f (glob(catfile($dir, '*')), glob(catfile($dir, 'data', '*'))) {
			my ($name) = $f =~ m{([^/]+)$};
			next if !-f $f;
			next if $name eq 'log.txt' || $name eq 'client-stdout.log' || $name eq 'client.pid';
		# НЕ трогаем настройки голоса (settings_voice.properties): cookies Яндекса для
		# TTS Алисы и пути Piper не относятся к привязке УДЯ — сброс связи их убивать
		# не должен (добыча cookies заново — ручная процедура через браузер).
		next if $name eq 'settings_voice.properties';
			next if $name =~ /\.bak/;
			next if $name !~ /\.(json|properties)$/;
			if (rename($f, $f . $suffix)) {
				$log->warn("reset: $name -> $name$suffix");
			}
			else {
				$log->error("reset: cannot backup $name: $!");
			}
		}
		Plugins::LyrionYandexBridge::ClientProcess->start;
	}

	# Установка Piper кнопкой: фоновый запуск setup-piper.sh (качает ~68МБ,
	# пишет прогресс в <datadir>/piper-install.log; статус — на странице).
	if ($paramRef->{'piperinstall'}) {
		my $dir  = Plugins::LyrionYandexBridge::ClientProcess::dataDir();
		my $bin  = catfile(Plugins::LyrionYandexBridge::ClientProcess::baseDir(), 'Bin', 'setup-piper.sh');
		if (-f $bin) {
			unlink catfile($dir, 'piper', 'INSTALLED');
			$log->info("piper install requested: sh $bin $dir (background)");
			system('/bin/sh', '-c', qq{nohup /bin/sh "$bin" "$dir" >/dev/null 2>&1 &});
		}
		else {
			$log->error("setup script not found: $bin");
		}
	}

	# после успешной установки Piper (INSTALLED) — раз перезапустить клиента,
	# чтобы он подхватил пути из settings_voice.properties
	{
		my $dir = Plugins::LyrionYandexBridge::ClientProcess::dataDir();
		my $mk  = catfile($dir, 'piper', 'INSTALLED');
		if (-e $mk) {
			my $t = (stat $mk)[9] || 0;
			if ($t > ($prefs->get('piperRestarted') || 0)) {
				$prefs->set('piperRestarted' => $t);
				$log->info('piper installed — restarting client to pick up settings');
				Plugins::LyrionYandexBridge::ClientProcess->stop;
				Plugins::LyrionYandexBridge::ClientProcess->start;
			}
		}
	}

	# Режим голоса уведомлений (alice|piper): пишем VOICE_TOGGLE_SAY_ALICE
	# в settings_voice.properties и перезапускаем клиента (читает файл при старте)
	if ($paramRef->{'saveSettings'} && defined $paramRef->{'lybvoice'}) {
		my $new = $paramRef->{'lybvoice'} eq 'piper' ? 'false' : 'true';
		my $cur = _propGet('VOICE_TOGGLE_SAY_ALICE') // 'true';
		if ($new ne $cur) {
			_propSet('VOICE_TOGGLE_SAY_ALICE' => $new);
			$log->info("voice engine switched to $paramRef->{'lybvoice'} — restarting client");
			Plugins::LyrionYandexBridge::ClientProcess->stop;
			Plugins::LyrionYandexBridge::ClientProcess->start;
		}
	}

	if ($paramRef->{'saveSettings'}) {
		# после Restart/Reset клиент грузится — запросы к нему всё равно таймаутятся
		# и лишь блокируют рендер страницы; комнаты и refresh применяем только при обычном Apply
		unless ($paramRef->{'restart'} || $paramRef->{'resetclient'}) {
		# удаление плеера кнопкой Remove в блоке Player rooms: submit-кнопки lybdel_<player>
		# передают имя плеера в ИМЕНИ параметра. Шлём клиенту action=player_remove — то же,
		# что кнопка «Удалить» на странице /players клиента (плеер вернётся сам, только когда
		# реально появится в LMS: offline-записи скипаются фиксом от 19.10).
		my %deleted;
		{
			my $port = $prefs->get('port') || 8888;
			my $ua   = LWP::UserAgent->new(timeout => 8);
			for my $k (sort keys %$paramRef) {
				next unless $k =~ /^lybdel_(.+)$/;
				my $player = $1;
				next if $player eq '';
				# имя из HTML-формы приходит байтами UTF-8 (LMS), из JSON — wide-строкой:
				# LWP для кодирования формы нужны байты
				my $playerBytes = Encode::is_utf8($player) ? encode('UTF-8', $player) : $player;
				my $r = eval { $ua->post("http://127.0.0.1:$port/form", {
					'action'            => 'player_remove',
					'player_name_value' => $playerBytes,
				}) };
				# клиент на успех отвечает 302-редиректом — это норма, не «fail»
				my $ok = $r && ($r->is_success || $r->is_redirect);
				$log->info("player remove from settings page: $player (" . ($ok ? 'ok' : 'fail') . ")");
				$deleted{$player} = 1;
			}
		}

		# комнаты плееров: селекты lybroom_<player> отправляем клиенту (только изменившиеся;
		# пустое значение = не назначать). Применяем ДО возможного рестарта клиента из-за смены port/bind.
		{
			my $port = $prefs->get('port') || 8888;
			my $ua = LWP::UserAgent->new(timeout => 8);
			my $cur = eval { JSON::XS::decode_json($ua->get("http://127.0.0.1:$port/status.json")->decoded_content) } || {};
			my %curroom = map { ($_->{'name'} || '') => ($_->{'room'} || '') } @{ $cur->{'players'} || [] };
			for my $k (sort keys %$paramRef) {
				# матчить надо ВНУТРИ цикла: $1 из блока grep снаружи не живёт (динамический скоуп)
				next unless $k =~ /^lybroom_(.+)$/;
				my ($player, $room) = ($1, $paramRef->{$k} // '');
				next if $deleted{$player}; # плеер удалён кнопкой Remove в этом же запросе
				# пустое значение = снять комнату с плеера (пункт «—» в селекте) — отправляем и его;
				# пропускаем только если значение не изменилось
				next if !defined $room || ($curroom{$player} // "\x00") eq $room;
				my $r = eval { $ua->post("http://127.0.0.1:$port/form", {
					'action'                => 'player_room_set',
					# JSON::XS отдаёт Unicode-строки: LWP теряет wide-char значения при
					# кодировании формы — обязательно превращаем в байты UTF-8
					'player_name_value'     => encode('UTF-8', $player),
					'player_room_value'     => encode('UTF-8', $room),
				}) };
				# клиент на успех отвечает 302-редиректом — это норма, не «fail»
				my $ok = $r && ($r->is_success || $r->is_redirect);
				$log->info("player room from settings page: $player -> $room ("
					. ($ok ? 'ok' : 'fail') . ")");
			}

			# чекбоксы «Create action devices» lybbtn_<player>: в форме приходят только
			# включённые. Включённые и изменившиеся шлём как 1; плееры из status.json,
			# у которых было createButtons=1, а чекбокса в запросе нет — выключаем (0).
			for my $p (@{ $cur->{'players'} || [] }) {
				my $player = $p->{'name'} || '';
				next if $player eq '' || $deleted{$player};
				my $curbtn = $p->{'createButtons'} ? 1 : 0;
				my $newbtn = $paramRef->{"lybbtn_$player"} ? 1 : 0;
				next if $curbtn == $newbtn; # не изменился
				my $playerBytes = Encode::is_utf8($player) ? encode('UTF-8', $player) : $player;
				my $r = eval { $ua->post("http://127.0.0.1:$port/form", {
					'action'                => 'player_buttons_set',
					'player_name_value'     => $playerBytes,
					'player_buttons_value'  => $newbtn,
				}) };
				my $ok = $r && ($r->is_success || $r->is_redirect);
				$log->info("player buttons from settings page: $player -> $newbtn ("
					. ($ok ? 'ok' : 'fail') . ")");
			}

			# период обнуления лога клиента (поле clientlogdays в блоке Client status)
			if (defined $paramRef->{'clientlogdays'} && $paramRef->{'clientlogdays'} =~ /^\d+$/) {
				my $v = int($paramRef->{'clientlogdays'});
				$v = 0 if $v < 0;
				$v = 365 if $v > 365;
				my $r = eval { $ua->post("http://127.0.0.1:$port/form", {
					'action'         => 'log_days_set',
					'log_days_value' => $v,
				}) };
				my $ok = $r && ($r->is_success || $r->is_redirect);
				$log->info("client log reset days from settings page: $v (" . ($ok ? 'ok' : 'fail') . ")");
			}

			# попросить клиент перечитать плееров и user/info — тогда блок Client status
			# (Music devices in Yandex и пр.) на ЭТОЙ же отрисовке будет свежим:
			# _clientStatus вызывается ниже по ходу обработчика и читает обновлённый /status.json.
			# Не чаще раза в 10с: HTTP LMS однопоточный, частые refresh-штормы тормозят его.
			if (time() - $lastRefreshRequest > 10) {
				$lastRefreshRequest = time();
				eval {
					$ua->post("http://127.0.0.1:$port/form", { 'action' => 'statusbar_refresh' });
					$log->info('client refresh requested after settings save');
				};
			}
		}
		} # unless restart/reset

		# radio/checkbox отсылаются только в состоянии "вкл"
		$paramRef->{'pref_autorun'} ||= 0;

		if ($paramRef->{'pref_port'} !~ /^\d+$/) {
			$paramRef->{'pref_port'} = 8888;
		}
		if ($paramRef->{'pref_bind'} ne '0.0.0.0') {
			$paramRef->{'pref_bind'} = 'localhost';
		}

		# если порт/bind изменились - процесс нужно перезапустить с новыми параметрами
		my $needsRestart = ($paramRef->{'pref_port'} ne ($prefs->get('port') // ''))
			|| ($paramRef->{'pref_bind'} ne ($prefs->get('bind') // ''));

		# применяем новое состояние сразу
		if ($paramRef->{'pref_autorun'}) {
			if ($needsRestart || !Plugins::LyrionYandexBridge::ClientProcess->alive) {
				Plugins::LyrionYandexBridge::ClientProcess->stop;
				Plugins::LyrionYandexBridge::ClientProcess->start;
			}
		}
		else {
			Plugins::LyrionYandexBridge::ClientProcess->stop;
		}
	}

	$paramRef->{'running'} = Plugins::LyrionYandexBridge::ClientProcess->alive ? 1 : 0;
	$paramRef->{'pid'}     = Plugins::LyrionYandexBridge::ClientProcess->pid || '';
	$paramRef->{'weburl'}  = 'http://' . (Slim::Utils::Network::serverAddr() || 'localhost')
		. ':' . ($prefs->get('port') || 8888) . '/';
	$paramRef->{'requestsurl'} = 'http://' . (Slim::Utils::Network::serverAddr() || 'localhost')
		. ':' . ($prefs->get('port') || 8888) . '/requests';
	$paramRef->{'lasterror'} = $prefs->get('lasterror') || '';

	# серверные зависимости (java/piper/ffmpeg/cookies + режим голоса)
	$paramRef->{'srv'} = _serverStatus();

	# сводка состояния из самого клиента (обновляется при открытии страницы)
	$paramRef->{'status'} = $paramRef->{'running'} ? _clientStatus() : undef;
	if ($paramRef->{'status'}) {
		# кнопки авторизации в облаке/Spotify ведут на страницы клиента
		$paramRef->{'status'}->{'authurl'} = $paramRef->{'weburl'} . 'auth';
		$paramRef->{'status'}->{'authurlspotify'} = $paramRef->{'weburl'} . 'auth_spotify';
		$paramRef->{'status'}->{'logurl'} = $paramRef->{'weburl'} . 'log';
	}

	return $class->SUPER::handler($client, $paramRef);
}

1;
