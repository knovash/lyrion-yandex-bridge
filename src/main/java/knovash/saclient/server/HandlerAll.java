package knovash.saclient.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.extern.log4j.Log4j2;
import knovash.saclient.Context;
import knovash.saclient.cmd.HandlePathCmd;
import knovash.saclient.spotify.SpotifyPlayerCommands;
import knovash.saclient.voice.HandleVoiceAlice;
import knovash.saclient.yandex.provider.ProviderAction;
import knovash.saclient.yandex.provider.ProviderCheck;
import knovash.saclient.yandex.provider.ProviderQuery;
import knovash.saclient.yandex.provider.ProviderUserDevices;
import knovash.saclient.yandex.provider.ProviderUserUnlink;
import knovash.saclient.web.PageIndex;
import knovash.saclient.web.PageLms;
import knovash.saclient.web.PagePlayers;
import knovash.saclient.web.PageRequests;
import knovash.saclient.web.StatusJson;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static knovash.saclient.web.Settings.settings;
import static knovash.saclient.web.SettingsButtons.settingsButtons;
import static knovash.saclient.web.SettingsLms.settingsLms;
import static knovash.saclient.web.SettingsTasker.settingsTasker;
import static knovash.saclient.web.SettingsVoice.settingsVoice;

@Log4j2
public class HandlerAll implements HttpHandler {

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String path = httpExchange.getRequestURI().getPath();

        if (path.startsWith("/music/")) {
            serveSoundFile(httpExchange, path); // отдать звуковой файл для уведомлений
            return;
        }

        Context context = Context.contextCreate(httpExchange);
        if (context == null) { // /favicon.ico и пр.
            httpExchange.sendResponseHeaders(404, -1);
            httpExchange.close();
            return;
        }
        log.debug("CLIENT ID IN QUERY: " + context.queryMap.get("client_id"));
        context = HandlerAll.switchPath(context);
        String response = context.bodyResponse;

        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        httpExchange.getResponseHeaders().putAll(context.responseHeaders);
        // ответы провайдера УДЯ и /status.json — JSON, страницы — HTML
        boolean isJson = context.path != null
                && (context.path.startsWith("/v1.0") || context.path.equals("/status.json"));
        httpExchange.getResponseHeaders().set("Content-Type",
                isJson ? "application/json; charset=UTF-8" : "text/html; charset=UTF-8");
        httpExchange.sendResponseHeaders(context.code, responseBytes.length);
        try (OutputStream outputStream = httpExchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }

    /**
     * Роутинг запросов. Используется и локальным веб-сервером, и CloudClient
     * (запросы Яндекса, приходящие через облако по WebSocket).
     */
    public static Context switchPath(Context context) {
        String path = context.path;
        switch (path) {
            //    YANDEX PROVIDER управление устройствами умного дома
            case "/v1.0":
                return ProviderCheck.providerCheckRun(context);
            case "/v1.0/user/unlink":
                return ProviderUserUnlink.providerUserUnlinkRun(context);
            case "/v1.0/user/devices":
                return ProviderUserDevices.providerUserDevicesRun(context);
            case "/v1.0/user/devices/query":
                return ProviderQuery.providerQueryRun(context);
            case "/v1.0/user/devices/action":
                return ProviderAction.providerActionRun(context);
            //    YANDEX VOICE команды дополнительного голосового навыка "Раз Два"
            case "/alice/":
                return HandleVoiceAlice.processContext(context);
            //    COMMANDS команды из Tasker или Пульта
            case "/cmd":
                return HandlePathCmd.action(context);
            //    SPOTIFY управление плеером
            case "/spotify":
                return SpotifyPlayerCommands.action(context);
            // WEB
            case "/":
                return PageIndex.action(context);
            case "/players":
                return PagePlayers.action(context);
            case "/settings":
                return settings.action(context);
            case "/settings_buttons":
                return settingsButtons.action(context);
            case "/settings_lms":
                return settingsLms.action(context);
            case "/settings_tasker":
                return settingsTasker.action(context);
            case "/settings_voice":
                return settingsVoice.action(context);
            case "/lms":
                return PageLms.action(context);
            // история поисковых запросов навыка (data/search_requests.txt)
            case "/requests":
                return PageRequests.action(context);
            // JSON-сводка состояния (используется страницей плагина LMS)
            case "/status.json":
                context.bodyResponse = StatusJson.get();
                context.code = 200;
                return context;
            default:
                log.info("WARNING! NOT FOUND PATH: " + path);
                return PageIndex.action(context);
        }
    }

    /** Раздать звук уведомления из classpath /sounds (для Player.signal). */
    private static void serveSoundFile(HttpExchange exchange, String path) throws IOException {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        if (fileName.isEmpty()) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        try (java.io.InputStream is = HandlerAll.class.getResourceAsStream("/sounds/" + fileName)) {
            if (is == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] data = is.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        } catch (IOException e) {
            exchange.close();
        }
    }
}
