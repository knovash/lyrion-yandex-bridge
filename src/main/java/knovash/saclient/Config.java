package knovash.saclient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;

/**
 * Конфиг клиента: адрес сервера, токены и параметры LMS/УДЯ.
 * Хранится в config.json рядом с рабочей директорией.
 * instanceToken/yandexToken выдаются сервером после авторизации (http://localhost:8888/auth),
 * spotify-токены — после http://localhost:8888/auth_spotify.
 */
public class Config {
    /** Адрес сервера задаётся ТОЛЬКО здесь: код — источник истины, config.json хранит только токены. */
    public static final String SERVER_HTTP_URL = "https://alice-lms.zeabur.app";
    public static final String SERVER_WS_URL = "wss://alice-lms.zeabur.app/ws";
    //public static final String SERVER_HTTP_URL = "https://sa-ws-server.zeabur.app";
    //public static final String SERVER_WS_URL = "wss://sa-ws-server.zeabur.app/ws";

    public String serverHttpUrl = SERVER_HTTP_URL;
    public String serverWsUrl = SERVER_WS_URL;
    public int localPort = 8888;

    /** Адрес, на котором слушать локальный веб-сервер (localhost или 0.0.0.0).
     *  Не сохраняется в config.json — задаётся только аргументом --bind (например, плагином LMS). */
    @JsonIgnore
    public String bind = "localhost";

    public String instanceToken = "";  // выдан сервером при авторизации в Яндексе (для WebSocket /ws)
    public String yandexToken = "";    // OAuth-токен Яндекса (api.iot.yandex.net)
    public String yandexUid = "";
    public String yandexName = "";

    public String spotifyAccessToken = "";
    public String spotifyRefreshToken = "";
    public long spotifyExpiresAt = 0;

    // ---------- LMS (перенесено из squeeze-alice) ----------
    public String lmsIp = "";
    public String lmsPort = "9000";
    public String silence = "";
    public Integer delay = 3;
    public String zoneId = "Europe/Minsk";

    // ---------- УДЯ (перенесено из squeeze-alice) ----------
    public String skillId = "";                       // id навыка УДЯ (для callback/state)
    public String yandexSkillTokenDeveloper = "";     // OAuth-токен разработчика навыка
    public String yandexSstTttsApiKey = "";           // API-ключ Yandex SpeechKit (TTS)
    public String scenarioId = "";                    // id сценария Яндекса (голосовые уведомления)
    public String domain = "";                        // домен для InfoClient (загрузка ключей)

    // ---------- файлы данных ----------
    public String fileRoomsAndAliceIds = "data/rooms_and_alice_ids.json";
    public String fileRoomsAndPlayers = "data/rooms_and_players.json";
    public String fileDevices = "data/devices.json";
    public String fileLmsPlayers = "data/lms_players.json";

    private static final String FILE_NAME = "config.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static Config load() {
        Config config;
        try {
            File file = new File(FILE_NAME);
            if (file.exists()) {
                config = MAPPER.readValue(file, Config.class);
                System.out.println("[Config] CONFIG LOADED FROM " + FILE_NAME);
            } else {
                System.out.println("[Config] NO " + FILE_NAME + ", USING DEFAULTS");
                config = new Config();
            }
        } catch (Exception e) {
            System.err.println("[Config] CONFIG LOAD ERROR: " + e);
            config = new Config();
        }
        // адрес сервера всегда из кода — устаревший config.json не может его перекрыть
        config.serverHttpUrl = SERVER_HTTP_URL;
        config.serverWsUrl = SERVER_WS_URL;
        // дефолты для новых полей, отсутствующих в старом config.json
        if (config.lmsIp == null) config.lmsIp = "";
        if (config.lmsPort == null || config.lmsPort.isEmpty()) config.lmsPort = "9000";
        if (config.silence == null) config.silence = "";
        if (config.zoneId == null || config.zoneId.isEmpty()) config.zoneId = "Europe/Minsk";
        System.out.println("[Config] SERVER: " + config.serverHttpUrl + " LMS: " + config.lmsIp + ":" + config.lmsPort);
        return config;
    }

    public synchronized void save() {
        try {
            MAPPER.writeValue(new File(FILE_NAME), this);
            System.out.println("[Config] CONFIG SAVED TO " + FILE_NAME);
        } catch (Exception e) {
            System.err.println("[Config] CONFIG SAVE ERROR: " + e);
        }
    }

    public boolean yandexLoggedIn() {
        return yandexToken != null && !yandexToken.isEmpty();
    }

    public boolean spotifyLoggedIn() {
        return spotifyAccessToken != null && !spotifyAccessToken.isEmpty();
    }

    /**
     * Аргументы командной строки (используются, в частности, плагином LMS):
     * --lms.ip=127.0.0.1 --lms.port=9000 --port=8888 --bind=localhost|0.0.0.0
     * Переопределяют значения из config.json (кроме bind, которого нет в файле).
     */
    public void applyArgs(String[] args) {
        if (args == null) return;
        for (String arg : args) {
            try {
                if (arg.startsWith("--lms.ip=")) {
                    lmsIp = arg.substring("--lms.ip=".length());
                } else if (arg.startsWith("--lms.port=")) {
                    lmsPort = arg.substring("--lms.port=".length());
                } else if (arg.startsWith("--port=")) {
                    localPort = Integer.parseInt(arg.substring("--port=".length()));
                } else if (arg.startsWith("--bind=")) {
                    bind = arg.substring("--bind=".length());
                } else {
                    System.err.println("[Config] UNKNOWN ARG: " + arg);
                }
            } catch (Exception e) {
                System.err.println("[Config] ARG ERROR: " + arg + " -> " + e);
            }
        }
    }
}
