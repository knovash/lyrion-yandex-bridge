package knovash.saclient;

import com.sun.net.httpserver.HttpServer;
import knovash.saclient.server.HandlerAll;
import knovash.saclient.server.HandlerFavicon;
import knovash.saclient.server.HandlerForm;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Локальный HTTP-сервер: веб-интерфейс настроек (из squeeze-alice)
 * и страницы авторизации.
 * http://localhost:8888/            — главная (статус + ссылки)
 * http://localhost:8888/players     — настройка плееров и комнат
 * http://localhost:8888/settings    — настройки
 * http://localhost:8888/settings_lms— настройки LMS
  * http://localhost:8888/settings_buttons — устройства-действия УДЯ
 * http://localhost:8888/auth        — авторизация в Яндексе
 * http://localhost:8888/auth_spotify— авторизация в Spotify
 * /v1.0/...                         — запросы провайдера УДЯ (прямой вызов, помимо облака)
 */
public class LocalServer {

    public static void start(Config config) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(config.bind, config.localPort), 0);

            server.createContext("/favicon.ico", new HandlerFavicon());
            server.createContext("/auth", new LocalAuthYandex(config));
            server.createContext("/auth_spotify", new LocalAuthSpotify(config));
            server.createContext("/form", new HandlerForm());
            server.createContext("/", new HandlerAll());

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            log("STARTED http://" + (config.bind.equals("0.0.0.0") ? "<любой интерфейс>" : config.bind) + ":" + config.localPort
                    + "/  (веб-настройки), /auth  и  /auth_spotify");
        } catch (Exception e) {
            System.err.println("LOCAL SERVER ERROR: " + e);
        }
    }

    private static void log(String message) {
        System.out.println("[" + LocalServer.class.getSimpleName() + "] " + message);
    }
}
