package knovash.saclient.web;

import lombok.extern.log4j.Log4j2;
import knovash.saclient.Context;
import knovash.saclient.Main;
import knovash.saclient.yandex.Yandex;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static knovash.saclient.lms.LmsSearchForIp.isLmsServer;
import static knovash.saclient.Main.*;

@Log4j2
public class PageIndex {

    public static Context action(Context context) {
        context.bodyResponse = page();
        context.code = 200;
        return context;
    }

    public static void refresh(HashMap<String, String> parameters) {
        log.info("REFRESH");

        if (!isLmsServer(config.lmsIp, Integer.parseInt(config.lmsPort))) {
            log.info("SEARCH FOR LMS IP...");
            lmsPlayers.searchForLmsIp();
        } else
            log.info("LMS IP " + config.lmsIp);


        if (lmsServerOnline) {
            log.info("LMS ONLINE - UPDATE PLAYERS");
            lmsPlayers.updatePlayers();
        } else
            log.info("LMS OFFLINE - SKIP UPDATE PLAYERS");


        if (config.yandexName != null) {
            yandexInfoDevices = Yandex.devicesGetFromYandexInfo(); // получить устройства "Музыка" которые уже есть в Яндексе
            log.info("YANDEX get yandexInfoDevices: " + yandexInfoDevices);
        } else
            log.info("YANDEX NOT LOGGED");
    }

    public static String page() {
        String pageInner = statusBar() +
                "<p><a href=/auth target=\"_blank\" rel=\"noopener noreferrer\">Авторизация в Яндекс</a></p>" +
                "<p><a href=/auth_spotify target=\"_blank\" rel=\"noopener noreferrer\">Авторизация в Spotify</a></p>" +
                "<p><a href=/settings_lms>Настройки LMS</a></p>" +
                "<p><a href=/players>Настройки плееров и комнат</a></p>" +
                "<p><a href=/settings>Настройки</a></p>" +
                "<p><a href=/settings_buttons>Настройки устройств-действий в УДЯ</a></p>" +
                "<p><a href=/settings_tasker>Настройки Tasker</a></p>" +
                "<p><a href=/settings_voice>Настройки голосовых уведомлений</a></p>" +
                "<p><a href=/requests target=\"_blank\" rel=\"noopener noreferrer\">Запросы</a></p>" +
                "";
        String page = pageOuter(pageInner, "Lyrion Yandex Bridge", "Lyrion Yandex Bridge");
        return page;
    }

    public static String statusBar() {
        log.info("PAGE STATUSBAR START");
        String bar = "<fieldset>" +
                "<legend><b>" + "Информация" + "</b></legend>" +
                "LMS: " + infoLmsIpStatus() + "<br>" +
                "LMS плееры: " + infoLmsPlayers() + "<br>" +
                "Yandex: " + infoUserYandex() + "<br>" +
                "Yandex комнаты: " + indexYandexRooms() + "<br>" +
                "Устройства Музыка тут: " + indexMusicDevicesLocal() + "<br>" +
                "Yandex устройства Музыка: " + indexMusicDevicesYanex() + "<br>" +
                "Spotify: " + infoUserSpoty() + "<br>" +
                "Облако: " + infoCloudStatus() + "<br>" +
                "<form method='POST' action='/form'>" +
                "<input name='action' type='hidden'  value='statusbar_refresh'>" +
                "<button type='submit'>обновить</button>" +
                "</form>" +
                "</fieldset>";
        return bar;
    }

    private static String infoUserSpoty() {
        if (config.spotifyAccessToken == null || config.spotifyAccessToken.isEmpty())
            return "<a href=/auth_spotify target=\"_blank\" rel=\"noopener noreferrer\">Авторизация в Spotify</a>";

        long now = System.currentTimeMillis();
        long timeLeft = config.spotifyExpiresAt - now;
        Duration d = Duration.ofMillis(timeLeft);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();   // Java 9+
        return "<span style='color: green;'>" + " подключен" + "</span>" + (" " + hours + "ч " + minutes + "м ");
    }

    private static String infoUserYandex() {
        if (config.yandexName != null && !config.yandexName.isEmpty())
            return "<span style='color: green;'> подключен</span>" + " " + config.yandexName;
        else return
                "<a href=/auth " + "target=\"_blank\" rel=\"noopener noreferrer\"" + ">Авторизация в Яндекс</a>";
    }

    private static String infoCloudStatus() {
        if (Main.cloudClient == null)
            return "<span style='color: red;'> отключен</span>";
        return "<a href=" + config.serverHttpUrl + " target=\"_blank\" rel=\"noopener noreferrer\">" + config.serverHttpUrl + "</a>";
    }

    private static String infoLmsIpStatus() {
        if (isLmsServer(config.lmsIp, Integer.parseInt(config.lmsPort)))
            return config.lmsIp + "<span style='color: green;'>" + " подключен" + "</span>";
        else
            return "<span style='color: red;'>" + " отключен" + "</span>";
    }

    private static String indexYandexRooms() {
        if (Yandex.rooms == null || Yandex.rooms.isEmpty()) return "<span style='color: red;'>нет</span>";
        else return Yandex.rooms.toString();
    }

    private static String indexMusicDevicesYanex() {
        // 1. Проверка локальных устройств
        if (smartHome.devices == null || smartHome.devices.isEmpty()) {
            return "<a href=/players>Настройка плееров и комнат</a>";
        }

        // 2. Собираем комнаты локальных устройств с именем "музыка"
        Set<String> localMusicRooms = smartHome.devices.stream()
                .filter(d -> "музыка".equals(d.name.toLowerCase()))
                .map(d -> d.room)
                .collect(Collectors.toSet());

        // Если локальных устройств "музыка" нет – предлагаем настроить плееры
        if (localMusicRooms.isEmpty()) {
            return "<a href=/players>Настройка плееров и комнат</a>";
        }

        // 3. Проверка наличия устройств из Яндекс.УДЯ
        if (yandexInfoDevices == null || yandexInfoDevices.isEmpty()) {
            // Если есть комнаты в Яндексе, но нет устройств – просим добавить их в УДЯ
            if (Yandex.rooms != null && !Yandex.rooms.isEmpty()) {
                return "<span style='color: red;'>добавьте устройства в приложении УДЯ</span> " +
                        "<a href=/settings_buttons>Выберите устройства-действий в УДЯ</a>";
            } else {
                return "Нет устройств в Яндексе";
            }
        }

        // 4. Собираем комнаты из яндекс-устройств с именем "музыка"
        Set<String> yandexMusicRooms = yandexInfoDevices.stream()
                .filter(d -> "музыка".equals(d.name.toLowerCase()))
                .map(d -> d.roomName)
                .collect(Collectors.toSet());

        // Если в Яндексе нет устройств "музыка" – просим добавить их
        if (yandexMusicRooms.isEmpty()) {
            return "<span style='color: red;'>добавьте устройства в приложении УДЯ</span> " +
                    "<a href=/settings_buttons>Выберите устройства-действий в УДЯ</a>";
        }

        // 5. Сравниваем наборы комнат
        String result = yandexMusicRooms.toString();
        if (!yandexMusicRooms.equals(localMusicRooms)) {
            result += " <span style='color: red;'>Обновите устройства в УДЯ</span>";
        }
        return result;
    }

    private static String indexMusicDevicesLocal() {
        if (smartHome.devices == null || smartHome.devices.isEmpty()) {
            return "<a href=/players>Настройка плееров и комнат</a>";
        }
        List<String> listMusic = smartHome.devices.stream()
                //.filter(d -> d.room != null)
                .filter(d -> "музыка".equals(d.name.toLowerCase()))
                .map(d -> d.room)
                .collect(Collectors.toList());
        return listMusic.toString();
    }

    private static String infoLmsPlayers() {
        if (!lmsServerOnline) return "<span style='color: red;'>нет</span>";
        return lmsPlayers.players.stream().map(player -> player.name).collect(Collectors.toList()).toString();
    }


    public static String pageOuter(String pageInner, String title, String header) {
        String page = "<!DOCTYPE html><html lang=\"ru\">" +
                "<head>" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">" +
                "<meta charset=\"UTF-8\">" +
                "<title>" + title + " local</title>" +
                "</head>" +

                "<body>" +
                "<p><a href=\"/\">Home</a></p>" +
                "  <h2>" + header + "</h2>" +
                pageInner +
                "<p><a href=\"/\">Home</a></p>" +
                "</body></html>";
        return page;
    }
}
