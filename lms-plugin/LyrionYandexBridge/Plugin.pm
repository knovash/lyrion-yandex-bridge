package Plugins::LyrionYandexBridge::Plugin;

# Lyrion Yandex Bridge (Yandex Smart Home) — LMS plugin wrapper.
# Запускает Java-клиент sa_client (fat-jar) как фоновый процесс при старте LMS,
# следит за ним (автоперезапуск) и останавливает при выключении LMS.

use strict;

use base qw(Slim::Plugin::Base);

use Slim::Utils::Prefs;
use Slim::Utils::Log;

my $prefs = preferences('plugin.lyrionyandexbridge');

$prefs->init({
	autorun  => 1,
	javapath => '',
	port     => 8888,
	bind     => '0.0.0.0',
	opts     => '',
});

my $log = Slim::Utils::Log->addLogCategory({
	'category'     => 'plugin.lyrionyandexbridge',
	'defaultLevel' => 'INFO',
	'description'  => 'PLUGIN_LYRION_YANDEX_BRIDGE',
});

sub initPlugin {
	my $class = shift;

	$class->SUPER::initPlugin(@_);

	require Plugins::LyrionYandexBridge::ClientProcess;

	if ( main::INFOLOG && $log->is_info ) {
		$log->info('Lyrion Yandex Bridge plugin initialized');
	}

	if ($prefs->get('autorun')) {
		Plugins::LyrionYandexBridge::ClientProcess->start;
	}

	if (!$::noweb) {
		require Plugins::LyrionYandexBridge::Settings;
		Plugins::LyrionYandexBridge::Settings->new;
	}
}

sub shutdownPlugin {
	Plugins::LyrionYandexBridge::ClientProcess->stop;
}

1;
