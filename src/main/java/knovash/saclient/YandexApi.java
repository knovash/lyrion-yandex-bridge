package knovash.saclient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Запросы к Яндекс IoT API (как Yandex.java в squeeze-alice):
 * Authorization: OAuth <yandexToken>. Токен появляется после http://localhost:8888/auth
 */
public class YandexApi {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** Информация о пользователе и устройствах: GET https://api.iot.yandex.net/v1.0/user/info */
    public static String getUserInfo(Config config) {
        if (config.yandexToken == null || config.yandexToken.isEmpty()) {
            log("YANDEX NOT LOGGED IN");
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.iot.yandex.net/v1.0/user/info"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "OAuth " + config.yandexToken)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            log("GET user/info -> " + response.statusCode());
            if (response.statusCode() == 200) return response.body();
            log("BODY: " + response.body());
        } catch (Exception e) {
            log("ERROR: " + e);
        }
        return null;
    }

    private static void log(String message) {
        System.out.println("[YandexApi] " + message);
    }
}
