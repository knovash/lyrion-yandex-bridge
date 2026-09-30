package Plugins::LyrionYandexBridge::Settings;

# Страница настроек плагина в веб-интерфейсе LMS (Настройки -> Плагины -> Lyrion Yandex Bridge).

use strict;

use base qw(Slim::Web::Settings);

use JSON::XS;
use LWP::UserAgent;
use Slim::Utils::Log;
use Slim::Utils::Network;
use Slim::Utils::Prefs;

my $prefs = preferences('plugin.lyrionyandexbridge');
my $log   = logger('plugin.lyrionyandexbridge');

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
	$st{'lms'} = ($d->{'lms'}->{'ip'} || '') . ':' . ($d->{'lms'}->{'port'} || '') . ' '
		. ($d->{'lms'}->{'online'} ? $on : $off);

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

	$st{'rooms'} = join(', ', @{$d->{'rooms'} || []}) || '<span style="color:red">none</span>';

	$st{'music'} = 'local: ' . (join(', ', @{$d->{'musicLocal'} || []}) || '-')
		. '<br>&nbsp;&nbsp;&nbsp;in Yandex: ' . (join(', ', @{$d->{'musicYandex'} || []}) || '-');

	$st{'spotify'} = $d->{'spotify'}->{'loggedIn'}
		? $on . ', ~' . ($d->{'spotify'}->{'minutesLeft'} || 0) . ' min'
		: '<span style="color:red">not authorized</span>';

	$st{'cloud'} = ($d->{'cloud'}->{'url'} || '') . ' '
		. ($d->{'cloud'}->{'connected'} ? $on : '<span style="color:red">no connection</span>');
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

	if ($paramRef->{'saveSettings'}) {
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
		# кнопка авторизации в облаке ведёт на /auth клиента
		$paramRef->{'status'}->{'authurl'} = $paramRef->{'weburl'} . 'auth';
	}

	return $class->SUPER::handler($client, $paramRef);
}

1;
