package Plugins::LyrionYandexBridge::ClientProcess;

# Управление java-процессом Lyrion Yandex Bridge.
# Запуск: sh -c "cd <datadir> && exec java -jar ... >> log 2>&1 & echo $! > pid"
# (без Proc::Background - меньше зависимостей от окружения LMS).
# Контроль: pid-файл + kill 0, автоперезапуск таймером beat() каждые 30 секунд.

use strict;

use File::Spec::Functions qw(catdir catfile);
use Slim::Utils::Log;
use Slim::Utils::Misc;
use Slim::Utils::OSDetect;
use Slim::Utils::PluginManager;
use Slim::Utils::Prefs;
use Slim::Utils::Timers;
use Time::HiRes;

my $prefs      = preferences('plugin.lyrionyandexbridge');
my $server     = preferences('server');
my $log        = logger('plugin.lyrionyandexbridge');

my $jarName      = 'lyrion-yandex-bridge-1.8.1.jar';
my $pluginModule = 'Plugins::LyrionYandexBridge::Plugin';
my $pidFile;

# каталог плагина (где лежит Bin/<jar>)
sub baseDir {
	my $plugins = Slim::Utils::PluginManager->allPlugins;
	return $plugins->{'LyrionYandexBridge'}->{'basedir'} if $plugins->{'LyrionYandexBridge'} && $plugins->{'LyrionYandexBridge'}->{'basedir'};
	while (my ($name, $data) = each %$plugins) {
		next unless ref $data;
		if (($data->{'module'} || '') eq $pluginModule && $data->{'basedir'}) {
			return $data->{'basedir'};
		}
	}
	return;
}

# рабочий каталог клиента: там создаются config.json и data/
# при переходе со старого имени (SAClient) - переносим данные с токенами
sub dataDir {
	my $dir = catdir(Slim::Utils::OSDetect::dirsFor('cache'), 'LyrionYandexBridge');
	if (!-d $dir) {
		my $old = catdir(Slim::Utils::OSDetect::dirsFor('cache'), 'SAClient');
		if (-d $old) {
			$log->warn("migrating data dir $old -> $dir");
			rename $old, $dir or mkdir $dir;
		}
		else {
			mkdir $dir or $log->error("cannot create data dir $dir: $!");
		}
	}
	return $dir;
}

sub logFile {
	return catfile(dataDir(), 'client-stdout.log');
}

sub pidFile {
	return catfile(dataDir(), 'client.pid');
}

sub jarFile {
	my $base = baseDir() || return;
	return catfile($base, 'Bin', $jarName);
}

sub javaPath {
	my $path = $prefs->get('javapath') || '';
	$path =~ s/^\s+|\s+$//g;
	return $path if $path && -x $path;

	$path = Slim::Utils::Misc::findbin('java');
	return $path if $path && -x $path;

	for my $j (sort glob('/usr/lib/jvm/*/bin/java')) {
		return $j if -x $j;
	}
	return;
}

sub lmsPort {
	return $server->get('httpport') || 9000;
}

# ---- состояние процесса ----

sub _readPid {
	my $file = pidFile();
	return unless -e $file;
	open my $fh, '<', $file or return;
	my $pid = <$fh>;
	close $fh;
	return unless defined $pid;
	$pid =~ s/\D//g;
	return $pid || undef;
}

sub _pidIsOurs {
	my ($pid) = @_;
	return 0 unless $pid && $pid > 1;
	# процесс жив?
	return 0 unless kill(0, $pid);
	# и это действительно наш клиент (защита от переиспользования pid)
	my $cmd = '';
	if (open my $fh, '<', "/proc/$pid/cmdline") {
		local $/;
		$cmd = <$fh>;
		close $fh;
	}
	return 1 unless defined $cmd && length $cmd; # не linux - верим kill 0
	return index($cmd, $jarName) >= 0;
}

sub pid {
	my $pid = _readPid();
	return ($pid && _pidIsOurs($pid)) ? $pid : undef;
}

sub alive {
	return pid() ? 1 : 0;
}

sub _lastError {
	my ($msg) = @_;
	$prefs->set('lasterror', $msg);
	$log->error($msg);
}

# ---- запуск/остановка ----

sub start {
	my $class = shift;

	return 1 if alive();

	my $java = javaPath();
	if (!$java) {
		_lastError('Lyrion Yandex Bridge: java not found. Install Java 11+ or set the path in plugin settings');
		return;
	}

	my $jar = jarFile();
	if (!$jar || !-e $jar) {
		_lastError('Lyrion Yandex Bridge: jar not found: ' . ($jar || '<basedir>/Bin/' . $jarName));
		return;
	}

	my $dir = dataDir();
	if (!-d $dir) {
		_lastError("Lyrion Yandex Bridge: data dir does not exist: $dir");
		return;
	}

	my @args = (
		'-jar', $jar,
		'--lms.ip=127.0.0.1',
		'--lms.port=' . lmsPort(),
		'--port=' . $prefs->get('port'),
		'--bind=' . $prefs->get('bind'),
	);
	my $opts = $prefs->get('opts') || '';
	push @args, split(/\s+/, $opts) if $opts ne '';

	my $logfile = logFile();
	my $pidfile = pidFile();
	my $cmdline = join(' ', $java, map { my $a = $_; $a =~ s/(')/'\\''/g; "'" . $a . "'" } @args);
	my $shell = "cd '" . $dir . "' && exec " . $cmdline . " >> '" . $logfile . "' 2>&1 & echo \$! > '" . $pidfile . "'";

	$log->info("starting Lyrion Yandex Bridge: $cmdline (cwd $dir)");

	my $rc = system('/bin/sh', '-c', $shell);
	if ($rc == -1) {
		_lastError("Lyrion Yandex Bridge: cannot run /bin/sh: $!");
		return;
	}
	elsif ($rc & 127) {
		_lastError("Lyrion Yandex Bridge: /bin/sh died with signal " . ($rc & 127));
		return;
	}

	my $pid = _readPid();
	if (!$pid) {
		_lastError("Lyrion Yandex Bridge: no pid after start (shell rc=$rc), see $logfile");
		return;
	}

	$log->info("Lyrion Yandex Bridge started (pid $pid)");
	$prefs->set('lasterror', '');

	# проверим через 5 секунд, что процесс жив, и залогируем хвост лога при падении
	Slim::Utils::Timers::setTimer($class, Time::HiRes::time() + 5, \&_checkStarted, $pid);

	Slim::Utils::Timers::setTimer($class, Time::HiRes::time() + 30, \&beat);
	return 1;
}

sub _checkStarted {
	my ($class, $pid) = @_;

	if (_pidIsOurs($pid)) {
		$log->info("Lyrion Yandex Bridge is running (pid $pid)");
		return;
	}

	my $tail = '';
	if (open my $fh, '<', logFile()) {
		my @lines = <$fh>;
		close $fh;
		$tail = join('', @lines[-12 .. -1]);
	}
	_lastError("Lyrion Yandex Bridge (pid $pid) died just after start. Log tail:\n$tail");
}

# автоперезапуск упавшего процесса (пока включён autorun)
sub beat {
	my $class = shift;

	if ($prefs->get('autorun') && !alive()) {
		$log->error('Lyrion Yandex Bridge crashed - restarting');
		$class->start;
	}

	Slim::Utils::Timers::setTimer($class, Time::HiRes::time() + 30, \&beat);
}

sub stop {
	my $class = shift;

	my $pid = _readPid();
	if ($pid && kill(0, $pid)) {
		$log->info("stopping Lyrion Yandex Bridge (pid $pid)");
		kill('TERM', $pid);
		for (1..20) {
			last unless kill(0, $pid);
			select(undef, undef, undef, 0.2);
		}
		kill('KILL', $pid) if kill(0, $pid);
	}
	unlink pidFile() if -e pidFile();
}

1;
