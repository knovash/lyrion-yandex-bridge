package Plugins::AirPlay2Bridge::Settings;

# Страница настроек AirPlay2Bridge: список плееров, пути к python/bridge.py/squeezelite.

use strict;
use base qw(Slim::Web::Settings);

use Slim::Utils::Log;
use Slim::Utils::Prefs;

my $prefs = preferences('plugin.airplay2bridge');
my $log   = logger('plugin.airplay2bridge');

my $plugin;  # weak reference to plugin class for restart

sub name {
	return Slim::Web::HTTP::CSRF->tokenName;
}

sub page {
	return 'plugins/AirPlay2Bridge/settings/basic.html';
}

sub handler {
	my ($class, $client, $params, $callback, @args) = @_;

	if ($params->{'saveSettings'}) {
		$prefs->set('enabled', $params->{'pref_enabled'} ? 1 : 0);
		$prefs->set('players', $params->{'pref_players'} || '');
		$prefs->set('pythonpath', $params->{'pref_pythonpath'} || '');
		$prefs->set('bridgebin', $params->{'pref_bridgebin'} || '');
		$prefs->set('squeezelite', $params->{'pref_squeezelite'} || '');
		$prefs->set('lmsip', $params->{'pref_lmsip'} || '127.0.0.1');

		require Plugins::AirPlay2Bridge::BridgeProcess;
		Plugins::AirPlay2Bridge::BridgeProcess->restartAll;
		$params->{'validated'} = 1;
	}

	for my $key (qw(enabled players pythonpath bridgebin squeezelite lmsip)) {
		$params->{"pref_$key"} = $prefs->get($key);
	}

	return $class->SUPER::handler($client, $params, $callback, @args);
}

1;