package knovash.saclient.web;

import lombok.extern.log4j.Log4j2;
import knovash.saclient.Context;

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
                ? "<b>Cookies заданы</b> — озвучка будет голосом настоящей Алисы"
                : "<b>Cookies не заданы</b> — озвучка идёт локальным синтезатором Piper";

        String inner =
                "<fieldset><legend><b>Ключ из cookie Яндекса</b></legend>" +
                        "<p>" + status + "</p>" +
                        "<form method='POST' action='/form'>" +
                        "<input type='hidden' name='action' value='ya_cookies_save'>" +
                        "<textarea name='ya_cookies' rows='3' cols='90' " +
                        "placeholder='Скопируйте заголовок Cookie целиком из F12 → Network'>" +
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
                        "<p>2. Нажмите <b>F12</b> → вкладка <b>Application</b> (Хранение) → <b>Cookies</b> → " +
                        "<b>https://yandex.ru</b>.</p>" +
                        "<p>3. Перейдите на вкладку <b>Network</b> (Сеть), обновите страницу (F5) и кликните " +
                        "первый запрос к <b>yandex.ru</b>.</p>" +
                        "<p>4. В <b>Request Headers</b> найдите заголовок <b>Cookie:</b> и скопируйте его " +
                        "<b>целиком</b> (все поля: Session_id, yandexuid, yandex_login и остальные).</p>" +
                        "<p>5. Вставьте скопированное в поле выше — как есть, целиком.</p>" +
                        "<p><b>Важно:</b> нужен именно полный заголовок Cookie. Только Session_id+yandexuid " +
                        "недостаточно: Яндекс возвращает 403 на изменение сценариев.</p>" +
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
        VOICE_STRING_YA_COOKIES.value = cookies;
        settingsVoice.saveToFile();
        log.info("YA COOKIES " + ("---".equals(cookies) ? "CLEARED" : "SAVED"));
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
