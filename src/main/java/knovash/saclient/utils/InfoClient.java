package knovash.saclient.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static knovash.saclient.Main.config;

@Log4j2
public class InfoClient {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Info fetchInfo(String baseUrl) {
        try {
            String url = baseUrl + "/info";
            HttpRequest request = buildInfoRequest(url, preferredInfoToken());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            // instanceToken отвергнут (облако перезапускалось и потеряло реестр) —
            // повторяем с Яндекс access_token
            if (response.statusCode() == 401
                    && preferredInfoToken() != null && preferredInfoToken().equals(config.instanceToken)
                    && config.yandexToken != null && !config.yandexToken.isEmpty()) {
                log.warn("/info: instanceToken отвергнут (рестарт облака?), повтор Яндекс-токеном");
                request = buildInfoRequest(url, config.yandexToken);
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            }

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), Info.class);
            } else {
                log.error("Ошибка при запросе /info, код: {}", response.statusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("Не удалось получить Info: ", e);
            return null;
        }
    }

    private static String preferredInfoToken() {
        if (config.instanceToken != null && !config.instanceToken.isEmpty()) return config.instanceToken;
        if (config.yandexToken != null && !config.yandexToken.isEmpty()) return config.yandexToken;
        return null;
    }

    private static HttpRequest buildInfoRequest(String url, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET();
        // endpoint защищён instanceToken (выдаётся облаком при авторизации)
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    /**
     * Обновить общие настройки с сервера. При недоступности сервера или
     * пустых значениях локальные значения НЕ затираются (раньше могли
     * затереться null-ами и падал NPE).
     */
    public static void setInfo(String baseUrl) {
        Info info = fetchInfo(baseUrl);
        if (info == null) {
            log.warn("INFO NOT RECEIVED FROM {} — работаю на локальных значениях", baseUrl);
            return;
        }
        boolean changed = false;
        if (notEmpty(info.skillId)) {
            config.skillId = info.skillId;
            changed = true;
        }
        if (notEmpty(info.yandextSkillTokenDeveloper)) {
            config.yandexSkillTokenDeveloper = info.yandextSkillTokenDeveloper;
            changed = true;
        }
        if (notEmpty(info.yandexSstTttsApiKey)) {
            config.yandexSstTttsApiKey = info.yandexSstTttsApiKey;
            changed = true;
        }
        if (changed) config.save();
        log.info("INFO UPDATED FROM SERVER");
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    public static class Info {

        public String yandextSkillTokenDeveloper;
        public String yandexSstTttsApiKey;
        public String skillId;
    }
}
