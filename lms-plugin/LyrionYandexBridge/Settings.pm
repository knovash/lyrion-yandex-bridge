package Plugins::LyrionYandexBridge::Settings;

# Страница настроек плагина в веб-интерфейсе LMS (Настройки -> Плагины -> Lyrion Yandex Bridge).

use strict;

use base qw(Slim::Web::Settings);

use JSON::XS;
use LWP::UserAgent;
use File::Spec::Functions qw(catfile);
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
	# что и «Music devices in Yandex»)
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
	$st{'spotify'} = $d->{'spotify'}->{'loggedIn'}
		? $on . ($spotifyUser ne '' ? ' ' . $spotifyUser : '')
		: '<span style="color:red">not authorized</span>';
	$st{'spotifyloggedin'} = $d->{'spotify'}->{'loggedIn'} ? 1 : 0;

	$st{'cloud'} = $d->{'cloud'}->{'connected'}
		? $on
		: '<span style="color:red">not authorized</span>';
	$st{'cloudconnected'} = $d->{'cloud'}->{'connected'} ? 1 : 0;

	return \%st;
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

	if ($paramRef->{'saveSettings'}) {
		# после Restart/Reset клиент грузится — запросы к нему всё равно таймаутятся
		# и лишь блокируют рендер страницы; комнаты и refresh применяем только при обычном Apply
		unless ($paramRef->{'restart'} || $paramRef->{'resetclient'}) {
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
	$paramRef->{'lasterror'} = $prefs->get('lasterror') || '';

	# сводка состояния из самого клиента (обновляется при открытии страницы)
	$paramRef->{'status'} = $paramRef->{'running'} ? _clientStatus() : undef;
	if ($paramRef->{'status'}) {
		# кнопки авторизации в облаке/Spotify ведут на страницы клиента
		$paramRef->{'status'}->{'authurl'} = $paramRef->{'weburl'} . 'auth';
		$paramRef->{'status'}->{'authurlspotify'} = $paramRef->{'weburl'} . 'auth_spotify';
	}

	return $class->SUPER::handler($client, $paramRef);
}

1;
