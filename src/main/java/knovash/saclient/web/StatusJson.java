package knovash.saclient.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import knovash.saclient.Main;
import knovash.saclient.SpotifyApi;
import knovash.saclient.lms.LmsSearchForIp;
import knovash.saclient.lms.Player;
import knovash.saclient.yandex.Yandex;

import java.util.Objects;

/**
 * JSON-сводка состояния клиента для страницы плагина LMS (GET /status.json).
 */
public class StatusJson {

    public static String get() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        // LMS
        ObjectNode lms = root.putObject("lms");
        lms.put("ip", Main.config.lmsIp == null ? "" : Main.config.lmsIp);
        lms.put("port", Main.config.lmsPort == null ? "" : Main.config.lmsPort);
        // кэшированный флаг может ложно быть false (разовый медленный ответ): при false/null
        // делаем живую проверку (она же обновляет флаг для updatePlayers)
        boolean lmsOnline = Boolean.TRUE.equals(Main.lmsServerOnline)
                || LmsSearchForIp.isLmsServer(Main.config.lmsIp, Integer.parseInt(Main.config.lmsPort));
        lms.put("online", lmsOnline);

        // плееры LMS
        ArrayNode players = root.putArray("players");
        if (Main.lmsPlayers != null && Main.lmsPlayers.players != null) {
            for (Player p : Main.lmsPlayers.players) {
                if (p == null) continue;
                ObjectNode pn = players.addObject();
                pn.put("name", p.name == null ? "" : p.name);
                pn.put("room", p.room == null ? "" : p.room);
                pn.put("connected", p.connected);
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

        // Spotify
        ObjectNode spotify = root.putObject("spotify");
        spotify.put("loggedIn", Main.config.spotifyLoggedIn());
        spotify.put("user", spotifyUserCached());
        spotify.put("minutesLeft", Math.max(0,
                (int) ((Main.config.spotifyExpiresAt - System.currentTimeMillis()) / 60000L)));

        // Облако
        ObjectNode cloud = root.putObject("cloud");
        cloud.put("url", Main.config.serverHttpUrl);
        cloud.put("connected", Main.cloudClient != null && Main.cloudClient.isConnected());

        return root.toString();
    }

    // имя пользователя Spotify: api /v1/me, кэш 10 минут (страницу плагина открывают редко,
    // но дёргать Spotify при каждом /status.json не хотим)
    private static volatile String spotifyUser = null;
    private static volatile long spotifyUserFetchedAt = 0L;

    private static String spotifyUserCached() {
        long now = System.currentTimeMillis();
        if (spotifyUser == null || now - spotifyUserFetchedAt > 600_000L) {
            try {
                String json = SpotifyApi.get(Main.config, "https://api.spotify.com/v1/me");
                if (json != null) {
                    JsonNode me = new ObjectMapper().readTree(json);
                    String name = me.path("display_name").asText("");
                    if (name.isEmpty()) name = me.path("id").asText("");
                    if (!name.isEmpty()) {
                        spotifyUser = name;
                        spotifyUserFetchedAt = now;
                    }
                }
            } catch (Exception ignored) {
                // останется прежнее кэшированное значение или пустая строка
            }
        }
        return spotifyUser == null ? "" : spotifyUser;
    }
}
