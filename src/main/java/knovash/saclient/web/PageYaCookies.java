package knovash.saclient.web;

import lombok.extern.log4j.Log4j2;
import knovash.saclient.Context;

import java.util.LinkedHashMap;
import java.util.Map;

import static knovash.saclient.web.SettingsVoice.settingsVoice;
import static knovash.saclient.web.SettingsVoice.KeyString.VOICE_STRING_YA_COOKIES;

/**
 * Страница /ya_cookies — «Настройка cookies»: форма ввода ключа из cookie
 * аккаунта Яндекса (Session_id=...; yandexuid=...) + инструкция как найти,
 * для чего нужен и план QR-логина. Сохранение — в data/settings_voice.properties
 * (ключ VOICE_STRING_YA_COOKIES), применяется сразу: YandexAliceSay читает
 * значение при каждом вызове (volatile), рестарт клиента не нужен.
 */
@Log4j2
public class PageYaCookies {

    public static Context action(Context context) {
        String current = VOICE_STRING_YA_COOKIES.value;
        boolean set = current != null && !current.isBlank() && !"---".equals(current.trim());
        String status = set
                ? "<b>Cookies заданы</b> (" + escape(cookieNames(current)) + ") — озвучка будет голосом настоящей Алисы"
                : "<b>Cookies не заданы</b> — озвучка идёт локальным синтезатором Piper";

        String inner =
                "<fieldset><legend><b>Ключ из cookie Яндекса</b></legend>" +
                        "<p>" + status + "</p>" +
                        "<form method='POST' action='/form'>" +
                        "<input type='hidden' name='action' value='ya_cookies_save'>" +
                        "<textarea name='ya_cookies' rows='8' cols='90' " +
                        "placeholder='Вставьте как угодно: всю таблицу из F12 → Application → Cookies, заголовок Cookie из Network или просто пары имя=значение — нужные куки извлекутся автоматически'>" +
                        (set ? escape(current.trim()) : "") + "</textarea><br>" +
                        "<button type='submit'>Сохранить</button>" +
                        " <small>(применяется сразу, рестарт не нужен; чтобы сбросить — очистите поле)</small>" +
                        "</form>" +
                        "</fieldset>" +

                        "<fieldset><legend><b>Для чего это нужно</b></legend>" +
                        "<p>Cookies аккаунта Яндекса позволяют клиенту говорить <b>голосом настоящей Алисы</b>: " +
                        "текст отправляется в облачный сценарий УДЯ (m/-API, yandex.ru/iot), и колонка " +
                        "синтезирует его своим движком. Используется локальными фразами рутованных станций " +
                        "(подтверждения «спотифай …», ответ «что играет») и голосовыми уведомлениями. " +
                        "Без cookies — автоматический фолбэк на локальный синтезатор Piper.</p>" +
                        "</fieldset>" +

                        "<fieldset><legend><b>Как найти ключ</b></legend>" +
                        "<p>1. Откройте <b>yandex.ru</b> в браузере (Chrome/Firefox) и войдите в свой аккаунт.</p>" +
                        "<p>2. <b>F12</b> → вкладка <b>Application</b> (Хранение) → <b>Cookies</b> → " +
                        "<b>https://yandex.ru</b> → кликните по списку → <b>Ctrl+A</b> → <b>Ctrl+C</b> " +
                        "(скопируется вся таблица) — и просто вставьте её в поле выше <b>как есть</b>.</p>" +
                        "<p><small>Подойдёт и заголовок <b>Cookie</b> целиком из F12 → Network → первый запрос → " +
                        "Request Headers, и просто пары <code>имя=значение</code>. Сервис сам найдёт нужные: " +
                        "Session_id, sessionid2, yandex_login, yandexuid, i, yp, sessar, L, yuidss, ymex.</small></p>" +
                        "<p><b>Важно:</b> только Session_id+yandexuid недостаточно — Яндекс возвращает 403 " +
                        "на изменение сценариев. Поэтому вставляйте всё — лишнее отфильтруется.</p>" +
                        "<p><small>Сессия живёт долго; после смены пароля или «Выйти на всех устройствах» — " +
                        "повторите шаги.</small></p>" +
                        "</fieldset>" +

                        "<fieldset><legend><b>Безопасность</b></legend>" +
                        "<p>Строка cookies — это <b>полная сессия аккаунта</b> Яндекс. Храните её только на своём " +
                        "сервере и никому не передавайте. Отзыв доступа: сменить пароль или «Выйти на всех " +
                        "устройствах» в аккаунте — после этого вставьте новые cookies.</p>" +
                        "</fieldset>" +

                        "<fieldset><legend><b>Планы</b></legend>" +
                        "<p>В будущем здесь появится вход по <b>QR-коду</b> (passport magic-code): кнопка на этой " +
                        "странице — сканируете камерой телефона, cookies получаются автоматически, без ручного " +
                        "копирования из браузера.</p>" +
                        "</fieldset>";

        context.bodyResponse = PageIndex.pageOuter(inner, "Настройка cookies", "Настройка cookies");
        context.code = 200;
        return context;
    }

    // сохранение из ProcessForm (action=ya_cookies_save)
    public static void save(Map<String, String> body) {
        String cookies = body.get("ya_cookies");
        if (cookies == null) return;
        cookies = cookies.trim();
        if (cookies.isEmpty()) cookies = "---";
        else {
            // v1.13.1: умный парсер — принимаем вставку любого вида (таблица
            // devtools / заголовок Cookie / пары имя=значение), извлекаем нужное
            String parsed = extractCookies(cookies);
            if (parsed != null) {
                cookies = parsed;
                log.info("YA COOKIES parsed ok: " + cookieNames(cookies));
            } else {
                log.warn("YA COOKIES: не распознано ни одной известной куки — сохраняю как есть");
            }
        }
        VOICE_STRING_YA_COOKIES.value = cookies;
        settingsVoice.saveToFile();
        log.info("YA COOKIES " + ("---".equals(cookies) ? "CLEARED" : "SAVED"));
    }

    // v1.13.1: нужные куки аккаунта Яндекса в порядке отправки (остальное
    // (метрика _ym_*, gdpr, bh, yabs-sid и пр.) в авторизации не участвует)
    private static final String[] NEEDED = {
            "Session_id", "sessionid2", "yandex_login", "yandexuid",
            "i", "yp", "sessar", "L", "yuidss", "ymex"};

    /**
     * Извлекает нужные куки из вставки любого вида: таблица devtools
     * (строки «имя TAB значение TAB домен…»), заголовок Cookie
     * («имя=значение; имя2=значение2») или пары имя=значение по строкам —
     * в любой последовательности. Возвращает «имя=значение; …» только из
     * известных, или null, если ни одной не найдено.
     */
    static String extractCookies(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Map<String, String> found = new LinkedHashMap<>();
        for (String line : raw.split("\\r?\\n")) {
            String[] pairs;
            if (line.contains("\t")) { // devtools-таблица: имя TAB значение TAB…
                String[] f = line.split("\\t");
                pairs = f.length >= 2 ? new String[]{f[0].trim() + "=" + f[1].trim()} : new String[]{line};
            } else {
                pairs = line.split(";");
            }
            for (String p : pairs) {
                int eq = p.indexOf('=');
                if (eq <= 0) continue;
                String name = p.substring(0, eq).trim();
                String value = p.substring(eq + 1).trim();
                if (value.isEmpty()) continue;
                for (String n : NEEDED) {
                    if (n.equalsIgnoreCase(name) && !found.containsKey(n)) found.put(n, value);
                }
            }
        }
        if (found.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (String n : NEEDED) {
            String v = found.get(n);
            if (v != null) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(n).append('=').append(v);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    // имена кук из сохранённой строки (для статуса страницы)
    private static String cookieNames(String cookieHeader) {
        StringBuilder sb = new StringBuilder();
        for (String p : cookieHeader.split(";")) {
            int eq = p.indexOf('=');
            String n = eq > 0 ? p.substring(0, eq).trim() : p.trim();
            if (n.isEmpty() || n.equals("---")) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(n);
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
