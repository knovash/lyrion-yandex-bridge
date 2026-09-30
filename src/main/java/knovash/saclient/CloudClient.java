package knovash.saclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import knovash.saclient.server.HandlerAll;
import knovash.saclient.utils.Parser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class CloudClient {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final Config config;
    private volatile WebSocket ws;
    private volatile boolean running = true;
    private volatile Thread thread;
    private final Object lock = new Object();

    public CloudClient(Config config) {
        this.config = config;
    }

    public void start() {
        running = true;
        Thread t = new Thread(this::runLoop, "cloud-client");
        t.setDaemon(true);
        thread = t;
        t.start();
    }

    /** Переподключение с актуальным instanceToken (после авторизации в Яндексе). */
    public void restart() {
        stop();
        Thread t = thread;
        if (t != null) {
            try { t.join(3000); } catch (InterruptedException ignored) {}
        }
        start();
    }

    public void stop() {
        running = false;
        WebSocket w = ws;
        if (w != null) w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
    }

    private void runLoop() {
        while (running) {
            try {
                connectAndServe();
            } catch (Exception e) {
                System.err.println("Connection error: " + e.getMessage());
            }
            if (running) sleepQuiet(5000);
        }
    }

    private void connectAndServe() throws Exception {
        if (config.instanceToken == null || config.instanceToken.isEmpty()) {
            log("NO INSTANCE TOKEN: откройте http://localhost:" + config.localPort + "/auth");
            return;
        }
        CompletableFuture<WebSocket> future = http.newWebSocketBuilder()
                .header("Authorization", "Bearer " + config.instanceToken)
                .buildAsync(URI.create(config.serverWsUrl), new Listener());

        ws = future.get();
        log("CONNECTED TO " + config.serverWsUrl);

        // Ждём, пока сессия не закроется
        synchronized (lock) {
            while (running && !ws.isOutputClosed()) {
                lock.wait(1000);
            }
        }
    }

    private void handleMessage(String json) {
        // Пришёл СЫРОЙ запрос Яндекса: сервер его не разбирал.
        // Весь парсинг (заголовки, тело) делаем здесь, на стороне клиента.
        log("RX: " + json);
        try {
            JsonNode envelope = mapper.readTree(json);
            String correlationId = envelope.path("correlationId").asText("");
            JsonNode request = envelope.path("request");

            String method = request.path("method").asText("");
            String url = request.path("url").asText("");
            String path = request.path("path").asText("");
            String query = request.hasNonNull("query") ? request.path("query").asText() : null;
            String headers = request.path("headers").asText("");
            String body = request.path("body").asText("");

            // Сырой запрос — в лог/консоль как есть
            log("REQUEST: " + method + " " + url);
            log("PATH: " + path);
            log("HEADERS: " + headers);
            log("BODY: " + body);
            log("QUERY: " + query);

            // ПАРСИНГ НА СТОРОНЕ КЛИЕНТА: заголовки -> Map, тело -> JsonNode
            Map<String, List<String>> headerMap = parseHeaders(headers);
            JsonNode bodyJson = parseJson(body);
            log("PARSED: " + headerMap.size() + " headers"
                    + (bodyJson == null ? "; body is not JSON" : "; body parsed as " + bodyJson.getNodeType()));

            // Запросы с известными роутами (УДЯ /v1.0/..., навык /alice/..., /cmd, /spotify)
            // выполняем локально через switchPath и отвечаем телом для Яндекса.
            // Поле correlationId нужно облаку (sa_server) для матчинга ответа с запросом;
            // Яндекс неизвестное поле игнорирует.
            if (path.startsWith("/v1.0") || path.startsWith("/alice")
                    || path.equals("/cmd") || path.startsWith("/spotify")) {
                Context context = buildContext(path, query, body, headerMap);
                context = HandlerAll.switchPath(context);
                String providerBody = context.bodyResponse;

                ObjectNode response = mapper.createObjectNode();
                response.put("correlationId", correlationId);
                if (path.startsWith("/alice")) {
                    // Навык «Раз Два» — вебхук Диалогов: Яндекс требует формат
                    // {"response":{"text":...,"end_session":true},"version":"1.0"}.
                    // Раньше текст уходил в "payload" — без поля "response" Яндекс
                    // отвечал «навык не отвечает». correlationId остаётся сверху:
                    // облако матчит по нему ответ с запросом, Яндекс лишнее поле
                    // игнорирует (как и в ответах УДЯ /v1.0/...).
                    String text = providerBody == null || providerBody.isEmpty()
                            ? "произошла ошибка, попробуйте позже" : providerBody;
                    ObjectNode aliceResponse = response.putObject("response");
                    aliceResponse.put("text", text);
                    aliceResponse.put("end_session", true);
                    response.put("version", "1.0");
                } else {
                    JsonNode providerJson = parseJson(providerBody);
                    if (providerJson != null && providerJson.isObject()) {
                        response.setAll((ObjectNode) providerJson);
                    } else if (providerBody != null) {
                        response.put("payload", providerBody);
                    }
                }
                String responseJson = mapper.writeValueAsString(response);
                WebSocket w = ws;
                if (w != null) {
                    w.sendText(responseJson, true);
                    log("TX (provider): " + responseJson);
                }
                return;
            }

            // Остальные запросы: тот же сырой запрос + пометка, что запрос выполнен
            ObjectNode responseRequest = request.isObject()
                    ? (ObjectNode) request.deepCopy()
                    : mapper.createObjectNode();
            responseRequest.put("executed", true);

            ObjectNode response = mapper.createObjectNode();
            response.put("correlationId", correlationId);
            response.set("request", responseRequest);
            String responseJson = mapper.writeValueAsString(response);

            WebSocket w = ws;
            if (w != null) {
                w.sendText(responseJson, true);
                log("TX: " + responseJson);
            }
        } catch (Exception e) {
            System.err.println("handleMessage error: " + e);
        }
    }

    /** Собрать Context из конверта облака (для обработки запросов УДЯ локально). */
    private Context buildContext(String path, String query, String body, Map<String, List<String>> headerMap) {
        Context context = new Context();
        context.path = path;
        context.body = body == null ? "" : body;
        context.query = query;
        context.queryMap = new LinkedHashMap<>(Parser.bodyToMap(query));
        Headers h = new Headers();
        headerMap.forEach((name, values) -> values.forEach(value -> h.add(name, value)));
        context.requestHeaders = h;
        context.responseHeaders = new Headers();
        return context;
    }

    /** Установлено ли сейчас соединение WebSocket с облаком. */
    public boolean isConnected() {
        WebSocket w = ws;
        return w != null && !w.isOutputClosed();
    }

    /** "name=[v1, v2]; name2=[v]" -> Map<name, values>. Формат строки задаёт сервер. */
    private Map<String, List<String>> parseHeaders(String headers) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (headers == null || headers.isEmpty()) return map;
        for (String entry : headers.split("; ")) {
            int eq = entry.indexOf("=[");
            if (eq < 0) continue;
            int close = entry.lastIndexOf(']');
            if (close < eq + 2) continue;
            String name = entry.substring(0, eq);
            String[] values = entry.substring(eq + 2, close).split(", ");
            map.put(name, Arrays.asList(values));
        }
        return map;
    }

    /** Сырое тело -> JsonNode (null, если это не JSON). */
    private JsonNode parseJson(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private static void log(String message) {
        System.out.println("[" + LocalTime.now().format(TIME) + " " + CloudClient.class.getName() + "] - " + message);
    }

    private void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String msg = buffer.toString();
                buffer.setLength(0);
                try {
                    handleMessage(msg);
                } catch (Exception e) {
                    System.err.println("handleMessage error: " + e);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("WS closed: " + statusCode + " " + reason);
            synchronized (lock) { lock.notifyAll(); }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("WS error: " + error);
            synchronized (lock) { lock.notifyAll(); }
        }
    }
}