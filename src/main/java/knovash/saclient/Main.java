package knovash.saclient;

import lombok.extern.log4j.Log4j2;
import knovash.saclient.lms.LmsPlayers;
import knovash.saclient.utils.Utils;
import knovash.saclient.yandex.SmartHome;
import knovash.saclient.yandex.Yandex;
import knovash.saclient.yandex.YandexUtils;

import java.io.File;
import java.time.LocalTime;
import java.util.concurrent.CompletableFuture;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.MINUTES;

@Log4j2
public class Main {

    // LMS плееры и устройства УДЯ (как в squeeze-alice)
    public static LmsPlayers lmsPlayers = new LmsPlayers();
    public static SmartHome smartHome = new SmartHome();
    public static Map<String, String> roomsAndAliceIds = new HashMap<>();
    public static Map<String, String> roomsAndPlayers = new HashMap<>();
    public static ZoneId zoneId = ZoneId.of("Europe/Minsk");
    public static Config config = new Config();
    public static Boolean lmsServerOnline;
    public static CloudClient cloudClient;
    public static List<YandexUtils.DeviceFromYandex> yandexInfoDevices;
    public static String start = "- - - - STARTED - - - - -";
    public static String finish = "- - - - FINISHED - - - - -";
    public static String line = "-----------------------------------------------------------------------";
    public static String music = "музыка";
    public static String myIp = "";

    public static void main(String[] args) throws Exception {
        log.info("LYRION YANDEX BRIDGE START");
        log.info("TIME ZONE: " + zoneId + " TIME: " + LocalTime.now(zoneId).truncatedTo(MINUTES));
        log.info("OS: " + System.getProperty("os.name") + ", user.home: " + System.getProperty("user.home"));

        config = Config.load();
        config.applyArgs(args); // аргументы запуска (например, от плагина LMS) переопределяют config.json
        config.save(); // при первом запуске создаёт config.json с дефолтами
        zoneId = ZoneId.of(config.zoneId);
        new File("data").mkdirs(); // папка для данных и настроек (как в squeeze-alice)

        // настройки из data/*.properties
        knovash.saclient.web.Settings.settings.loadFromFile();
        knovash.saclient.web.SettingsButtons.settingsButtons.loadFromFile();
        knovash.saclient.web.SettingsLms.settingsLms.loadFromFile();
        knovash.saclient.web.SettingsTasker.settingsTasker.loadFromFile();
        knovash.saclient.web.SettingsVoice.settingsVoice.loadFromFile();

        // общие ключи/ID (skillId, токен навыка, TTS) - выдаёт облако через GET /info
        if (config.instanceToken != null && !config.instanceToken.isEmpty()) {
            knovash.saclient.utils.InfoClient.setInfo(config.serverHttpUrl);
        }

        // LMS: поиск сервера, чтение плееров, обновление
        Utils.getMyIpAddress();
        Utils.readRoomsAndAliceIds(); // соответствие комнат и id колонок Алиса
        Utils.readRoomsAndPlayers(); // соответствие комнат и плееров
        try {
            lmsPlayers.searchForLmsIp();
            lmsPlayers.read(); // прочитать ранее сохраненные плееры LMS и их настройки
            lmsPlayers.updatePlayers(); // получить список плееров из LMS и создать плееры в сервисе
            lmsPlayers.logPlayersNames();
        } catch (Exception e) {
            // при запуске плагином LMS клиент может стартовать раньше готовности LMS -
            // не умираем, повторим попытку через минуту
            log.error("LMS INIT ERROR (LMS not ready yet?) - will retry in 60s: " + e);
        }

        // УДЯ: восстановить локальные устройства из файла, затем устройства "Музыка"
        // и кнопки-устройства из Яндекса. В ФОНОВОМ потоке: user/info у Яндекса может
        // занимать секунды — не задерживаем готовность веб-сервера (:8888) клиента
        smartHome.read();
        CompletableFuture.runAsync(Main::yandexInit);

        // повтор инициализации через 60 с, если плееры не удалось получить при старте
        java.util.concurrent.ScheduledExecutorService retryExecutor =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        retryExecutor.schedule(() -> {
            try {
                if (lmsPlayers.players == null || lmsPlayers.players.isEmpty()) {
                    log.info("- - - RETRY LMS INIT AFTER 60s - - -");
                    lmsPlayers.searchForLmsIp();
                    lmsPlayers.updatePlayers();
                    lmsPlayers.logPlayersNames();
                }
            } catch (Exception e) {
                log.error("LMS RETRY ERROR: " + e);
            }
        }, 60, java.util.concurrent.TimeUnit.SECONDS);

        // локальный веб-сервер (страницы настроек + авторизация) и облако
        LocalServer.start(config);
        cloudClient = new CloudClient(config);
        cloudClient.start();

        if (!config.yandexLoggedIn()) {
            System.out.println("ЯНДЕКС НЕ АВТОРИЗОВАН: откройте http://localhost:" + config.localPort + "/auth");
        }
        if (!config.spotifyLoggedIn()) {
            System.out.println("SPOTIFY НЕ АВТОРИЗОВАН: откройте http://localhost:" + config.localPort + "/auth_spotify");
        }

        // шедулер: периодическая отправка состояний устройств в Яндекс
        knovash.saclient.yandex.SchedulerPlayersUpdate.startPeriodicUpdate2(
                knovash.saclient.web.Settings.KeyValue.VALUE_PERIOD_UPDATE_DEVICES_TO_YANDEX.value);

        // шедулер: периодическое обновление токена Spotify
        knovash.saclient.utils.SchedulerSpotifyRefreshToken.startPeriodicRefresh(
                10, 5);

        // Держим процесс живым
        Thread.currentThread().join();
    }

    /**
     * Инициализация УДЯ: комнаты и устройства из аккаунта Яндекса (user/info).
     * Вызывается при старте, после авторизации в Яндекс и повторно при необходимости.
     * При неудаче (Яндекс вернул ошибку/нет сети) текущие комнаты не затираются.
     */
    public static void yandexInit() {
        try {
            yandexInfoDevices = Yandex.devicesGetFromYandexInfo();
            log.info("YANDEX get yandexInfoDevices: " + yandexInfoDevices);
            if (yandexInfoDevices == null) {
                // Яндекс недоступен (разовая 500/таймаут) — НЕ затирать локальные устройства
                // (комнаты, назначенные через плагин/страницу плееров), они восстановятся
                // из devices.json при следующем старте, а Яндекс отдаст их при опросе провайдера
                log.info("YANDEX INFO NOT AVAILABLE - KEEP LOCAL DEVICES: "
                        + smartHome.devices.stream().filter(Objects::nonNull).map(device -> device.room).collect(Collectors.toList()));
                return;
            }
            Yandex.createMusicDevicesFromYandex(yandexInfoDevices);
            // зачистить возможные дубликаты устройств "музыка" по комнатам
            smartHome.dedupeMusicDevicesByRoom(yandexInfoDevices.stream()
                    .filter(Objects::nonNull)
                    .map(d -> d.id)
                    .collect(Collectors.toSet()));
            // инвариант: устройство "музыка" есть только в комнатах с назначенным плеером
            smartHome.removeMusicDevicesWithoutPlayers(lmsPlayers.players);
            Yandex.createOtherDevicesFromYandexDevices(Yandex.otherDevices);
            log.info("YANDEX DEVICES saved local: " + smartHome.devices.stream().filter(Objects::nonNull).map(device -> device.room).collect(Collectors.toList()));
            smartHome.write();
        } catch (Exception e) {
            log.error("YANDEX INIT ERROR - continue: " + e);
        }
    }
}
