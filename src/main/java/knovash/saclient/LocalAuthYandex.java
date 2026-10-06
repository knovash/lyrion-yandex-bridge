package knovash.saclient;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.concurrent.CompletableFuture;

/**
 * Страница авторизации в Яндексе: ссылка на облачный /authorize?state=...,
 * опрос /token?state=... (как LocalAuthServerYandex в squeeze-alice).
 */
public class LocalAuthYandex extends LocalAuthBase {

    public LocalAuthYandex(Config config) {
        super(config);
    }

    @Override
    protected String getDisplayName() {
        return config.yandexName == null ? "" : config.yandexName;
    }

    @Override
    protected String getAuthUrl(String sessionId) {
        return config.serverHttpUrl + "/authorize?state=" + sessionId;
    }

    @Override
    protected String getTokenPath() {
        return "/token";
    }

    @Override
    protected String getLinkText() {
        return "Авторизоваться через Яндекс";
    }

    @Override
    protected boolean hasToken(JsonNode root) {
        return !root.path("token").asText("").isEmpty();
    }

    @Override
    protected void applyToken(JsonNode root) {
        config.yandexToken = root.path("token").asText("");
        config.instanceToken = root.path("instanceToken").asText("");
        config.yandexUid = root.path("uid").asText("");
        config.yandexName = root.path("name").asText("");
        config.save();
        log("YANDEX TOKEN RECEIVED uid=" + config.yandexUid + " name=" + config.yandexName);
        // переподключаем WebSocket с новым instanceToken
        if (Main.cloudClient != null) Main.cloudClient.restart();
        // комнаты/устройства должны подтягиваться из аккаунта СРАЗУ после авторизации,
        // а не ждать рестарта клиента. Асинхронно: ответ страницы авторизации не ждёт Яндекс
        CompletableFuture.runAsync(Main::yandexInit);
    }
}
