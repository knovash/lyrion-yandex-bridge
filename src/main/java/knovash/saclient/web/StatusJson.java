package knovash.saclient.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import knovash.saclient.Main;
import knovash.saclient.SpotifyApi;
import knovash.saclient.lms.Player;
import knovash.saclient.yandex.Yandex;
import knovash.saclient.yandex.YandexInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * JSON-сводка состояния клиента для страницы плагина LMS (GET /status.json).
 */
public class StatusJson {

    // человекочитаемая модель по типу УДЯ-устройства Яндекс-колонки
    private static String modelByType(String type) {
        if (type == null) return "";
        String prefix = "devices.types.smart_speaker.yandex.station";
        if (!type.startsWith(prefix)) return "";
        String suffix = type.substring(prefix.length());
        switch (suffix) {
            case "": return "Станция";
            case ".lite": return "Лайт";
            case ".lite_2": case ".lite2": return "Лайт 2";
            case ".mini": return "Мини";
            case ".mini_2": case ".mini2": return "Мини 2";
            case ".mini_3": case ".mini3": return "Мини 3";
            case ".midi": return "Миди";
            case ".max": return "Макс";
            case ".2": case "_2": return "Станция 2";
            // кодовые имена платформ из вики сообщества (hackalice.me)
            case ".micro": return "Лайт";
            case ".plum": return "Лайт 2";
            case ".cucumber": return "Миди";
            case ".bergamot": return "Мини 3";
            case ".pickle": return "Мини 3 Про";
            case ".mango": return "Стрит";
            case ".orion": return "Станция 3";
            default: return suffix.replace(".", "").replace("_", " ");
        }
    }

    public static String get() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        // LMS
        ObjectNode lms = root.putObject("lms");
        lms.put("ip", Main.config.lmsIp == null ? "" : Main.config.lmsIp);
        lms.put("port", Main.config.lmsPort == null ? "" : Main.config.lmsPort);
        // только кэшированный флаг: live-проверка здесь делала status.json медленным и
        // усиливала шторм запросов к однопоточному HTTP LMS; флаг самолечится в updatePlayers
        lms.put("online", Boolean.TRUE.equals(Main.lmsServerOnline));

        // плееры LMS
        ArrayNode players = root.putArray("players");
        if (Main.lmsPlayers != null && Main.lmsPlayers.players != null) {
            for (Player p : Main.lmsPlayers.players) {
                if (p == null) continue;
                ObjectNode pn = players.addObject();
                pn.put("name", p.name == null ? "" : p.name);
                pn.put("room", p.room == null ? "" : p.room);
                pn.put("connected", p.connected);
                pn.put("createButtons", p.createButtons);
            }
        }

        // Яндекс-колонки по комнатам: "aliceByRoom": { "Склад": ["Лайт (имя)", ...] }
        ObjectNode aliceByRoom = root.putObject("aliceByRoom");
        if (Yandex.yandexInfo != null && Yandex.yandexInfo.devices != null && Yandex.yandexInfo.rooms != null) {
            Map<String, String> roomNameById = new HashMap<>();
            for (YandexInfo.Room r : Yandex.yandexInfo.rooms) {
                if (r != null && r.id != null) roomNameById.put(r.id, r.name == null ? "" : r.name);
            }
            Map<String, YandexInfo.Device> deviceById = new HashMap<>();
            for (YandexInfo.Device dv : Yandex.yandexInfo.devices) {
                if (dv != null && dv.id != null) deviceById.put(dv.id, dv);
            }
            for (YandexInfo.Room r : Yandex.yandexInfo.rooms) {
                if (r == null || r.devices == null) continue;
                ArrayNode arr = null;
                for (String deviceId : r.devices) {
                    YandexInfo.Device dv = deviceById.get(deviceId);
                    if (dv == null || dv.type == null) continue;
                    if (!dv.type.startsWith("devices.types.smart_speaker.yandex.station")) continue;
                    String model = modelByType(dv.type);
                    String entry = (dv.name == null || dv.name.isEmpty())
                            ? (model.isEmpty() ? "Станция" : model) : dv.name;
                    if (arr == null) arr = aliceByRoom.putArray(roomNameById.getOrDefault(r.id, ""));
                    arr.add(entry);
                }
            }
        }

        // Яндекс
        ObjectNode yandex = root.putObject("yandex");
        yandex.put("loggedIn", Main.config.yandexLoggedIn());
        yandex.put("name", Main.config.yandexName == null ? "" : Main.config.yandexName);
        ArrayNode rooms = root.putArray("rooms");
        if (Yandex.rooms != null) Yandex.rooms.forEach(rooms::add);

        ArrayNode musicLocal = root.putArray("musicLocal");
        if (Main.smartHome != null && Main.smartHome.devices != null) {
            Main.smartHome.devices.stream()
                    .filter(Objects::nonNull)
                    .filter(d -> "музыка".equalsIgnoreCase(d.name))
                    .map(d -> d.room)
                    .filter(Objects::nonNull)
                    .forEach(musicLocal::add);
        }
        ArrayNode musicYandex = root.putArray("musicYandex");
        if (Main.yandexInfoDevices != null) {
            Main.yandexInfoDevices.stream()
                    .filter(Objects::nonNull)
                    .filter(d -> "музыка".equalsIgnoreCase(d.name))
                    .map(d -> d.roomName)
                    .filter(Objects::nonNull)
                    .forEach(musicYandex::add);
        }

        // Spotify (если refresh-токен сломан — loggedIn=false + причина, а не вечное «залогинен»)
        ObjectNode spotify = root.putObject("spotify");
        boolean spotifyBroken = Main.config.spotifyRefreshError != null
                && !Main.config.spotifyRefreshError.isEmpty();
        spotify.put("loggedIn", Main.config.spotifyLoggedIn() && !spotifyBroken);
        spotify.put("user", spotifyUserCached());
        spotify.put("minutesLeft", Math.max(0,
                (int) ((Main.config.spotifyExpiresAt - System.currentTimeMillis()) / 60000L)));
        if (spotifyBroken) spotify.put("error", Main.config.spotifyRefreshError);

        // Облако
        ObjectNode cloud = root.putObject("cloud");
        cloud.put("url", Main.config.serverHttpUrl);
        cloud.put("connected", Main.cloudClient != null && Main.cloudClient.isConnected());

        // Лог клиента: размер и период обнуления (для страницы плагина LMS)
        ObjectNode logNode = root.putObject("log");
        java.io.File logFile = new java.io.File("data/log.txt");
        logNode.put("size", logFile.exists() ? logFile.length() : 0L);
        logNode.put("rotateDays", Settings.KeyValue.VALUE_LOG_RESET_DAYS.value);

        return root.toString();
    }

    // имя пользователя Spotify: api /v1/me. Первый запрос идёт В ФОНЕ (Spotify может
    // отвечать секунды — не блокируем /status.json), дальше кэш 10 минут
    private static volatile String spotifyUser = null;
    private static volatile long spotifyUserFetchedAt = 0L;
    private static final java.util.concurrent.atomic.AtomicBoolean spotifyUserFetching =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static String spotifyUserCached() {
        long now = System.currentTimeMillis();
        if ((spotifyUser == null || now - spotifyUserFetchedAt > 600_000L)
                && spotifyUserFetching.compareAndSet(false, true)) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    String json = SpotifyApi.get(Main.config, "https://api.spotify.com/v1/me");
                    if (json != null) {
                        JsonNode me = new ObjectMapper().readTree(json);
                        String name = me.path("display_name").asText("");
                        if (name.isEmpty()) name = me.path("id").asText("");
                        if (!name.isEmpty()) {
                            spotifyUser = name;
                            spotifyUserFetchedAt = System.currentTimeMillis();
                        }
                    }
                } catch (Exception ignored) {
                    // останется прежнее кэшированное значение или пустая строка
                } finally {
                    spotifyUserFetching.set(false);
                }
            });
        }
        return spotifyUser == null ? "" : spotifyUser;
    }
}
