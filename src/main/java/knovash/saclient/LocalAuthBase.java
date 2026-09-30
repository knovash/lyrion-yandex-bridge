package knovash.saclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Базовая страница локальной авторизации (по образцу LocalAuthBase из squeeze-alice):
 * показывает ссылку на облачный /authorize?state=<UUID> и в фоне опрашивает
 * /token?state=... до получения токенов. Транспорт доставки токенов — HTTP (без MQTT).
 */
public abstract class LocalAuthBase implements HttpHandler {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected final Config config;

    protected LocalAuthBase(Config config) {
        this.config = config;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String sessionId = UUID.randomUUID().toString();
        String authUrl = getAuthUrl(sessionId);
        log("AUTH PAGE state=" + sessionId + " url=" + authUrl);

        String html = "<html><head><meta charset='utf-8'></head><body>" +
                (getDisplayName().isEmpty() ? "" : "Вы вошли как: " + getDisplayName() + "<br><br>") +
                "<a href='" + authUrl + "'>" + getLinkText() + "</a>" +
                "</body></html>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }

        // ждём пакет токенов в фоне (как pollTokenOverHttp в squeeze-alice)
        new Thread(() -> pollToken(sessionId), "token-poll").start();
    }

    /** Опрос облачного /token?state=... каждые 3 сек, до ~3 минут. */
    private void pollToken(String sessionId) {
        for (int i = 0; i < 60; i++) {
            try {
                Thread.sleep(3000);
                HttpRequest request = HttpRequest.newBuilder(
                                URI.create(config.serverHttpUrl + getTokenPath() + "?state=" + sessionId))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body() != null) {
                    JsonNode root = MAPPER.readTree(response.body());
                    if (hasToken(root)) {
                        applyToken(root);
                        return;
                    }
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                log("token poll error: " + e.getMessage());
            }
        }
        log("TOKEN NOT RECEIVED (timeout 3min)");
    }

    protected abstract String getDisplayName();

    protected abstract String getAuthUrl(String sessionId);

    protected abstract String getTokenPath();

    protected abstract String getLinkText();

    /** В ответе уже есть токен? (пустой ответ — авторизация ещё не завершена) */
    protected abstract boolean hasToken(JsonNode root);

    /** Сохранить токены из ответа. */
    protected abstract void applyToken(JsonNode root);

    protected static void log(String message) {
        System.out.println("[" + LocalTime.now().format(TIME) + " " + LocalAuthBase.class.getSimpleName() + "] " + message);
    }
}
