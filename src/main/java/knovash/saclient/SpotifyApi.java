package knovash.saclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Запросы к Spotify Web API (как SpotifyRequests в squeeze-alice):
 * Authorization: Bearer <accessToken>; при истечении срока или 401 токен
 * обновляется через облако (POST /refresh_spotify — client_secret живёт на сервере).
 */
public class SpotifyApi {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** GET к Spotify Web API с авто-обновлением токена. Возвращает тело или null. */
    public static String get(Config config, String uri) {
        if (!ensureToken(config)) return null;
        HttpResult result = doGet(config, uri);
        if (result.status == 401) { // токен отозван/протух раньше срока — обновляем и повторяем
            refresh(config);
            if (!ensureToken(config)) return null;
            result = doGet(config, uri);
        }
        return result.status == 200 ? result.body : null;
    }

    private static HttpResult doGet(Config config, String uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + config.spotifyAccessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            log("GET " + uri + " -> " + response.statusCode());
            return new HttpResult(response.statusCode(), response.body());
        } catch (Exception e) {
            log("ERROR: " + e);
            return new HttpResult(-1, null);
        }
    }

    /** Проверка срока действия и обновление при необходимости. */
    private static boolean ensureToken(Config config) {
        if (config.spotifyAccessToken == null || config.spotifyAccessToken.isEmpty()) {
            log("SPOTIFY NOT LOGGED IN (http://localhost:" + config.localPort + "/auth_spotify)");
            return false;
        }
        if (config.spotifyExpiresAt > 0 && System.currentTimeMillis() > config.spotifyExpiresAt - 60_000) {
            refresh(config);
        }
        return config.spotifyAccessToken != null && !config.spotifyAccessToken.isEmpty();
    }

    /** POST <server>/refresh_spotify {"refresh_token": ...} — секрет остаётся на сервере. */
    public static synchronized void refresh(Config config) {
        if (config.spotifyRefreshToken == null || config.spotifyRefreshToken.isEmpty()) {
            log("NO REFRESH TOKEN");
            return;
        }
        try {
            String body = "{\"refresh_token\":\"" + config.spotifyRefreshToken + "\"}";
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.serverHttpUrl + "/refresh_spotify"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = MAPPER.readTree(response.body());
                String accessToken = root.path("access_token").asText("");
                long expiresIn = root.path("expires_in").asLong(3600);
                if (!accessToken.isEmpty()) {
                    config.spotifyAccessToken = accessToken;
                    config.spotifyExpiresAt = System.currentTimeMillis() + expiresIn * 1000L;
                    config.save();
                    log("TOKEN REFRESHED, expires in " + expiresIn + " sec");
                    return;
                }
            }
            log("REFRESH FAILED: " + response.statusCode() + " " + response.body());
        } catch (Exception e) {
            log("REFRESH ERROR: " + e);
        }
    }

    private static class HttpResult {
        final int status;
        final String body;

        HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static void log(String message) {
        System.out.println("[SpotifyApi] " + message);
    }
}
