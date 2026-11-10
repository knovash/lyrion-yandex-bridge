package knovash.saclient.yandex;

import knovash.saclient.http.HttpClientWrapper;
import knovash.saclient.http.HttpResponseResult;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static knovash.saclient.web.SettingsVoice.KeyString.VOICE_STRING_YA_COOKIES;
import static knovash.saclient.web.SettingsVoice.KeyToggle.VOICE_TOGGLE_SAY_ALICE;

/*
 * Озвучка произвольного текста НАСТОЯЩИМ голосом Алисы на колонке.
 * Механизм как в AlexxIT/YandexStation (способ 1, облачный):
 *   1) в УДЯ есть сценарий "SA voice <device_id>" с TTS-шагом
 *      (devices.capabilities.quasar, instance=tts, value.text)
 *   2) перед каждым вызовом текст обновляется (PUT m/v3)
 *   3) сценарий запускается (POST m/user/scenarios/{id}/actions) — колонка
 *      синтезирует фразу своим движком. Без подписки, лимит 100 символов.
 * Авторизация m/-API — cookies аккаунта (Session_id) + x-csrf-token c yandex.ru/iot.
 * Официальный api.iot.yandex.net сценарии редактировать не умеет (проверено).
 */
@Log4j2
public class YandexAliceSay {

    private static final HttpClientWrapper httpClient = new HttpClientWrapper();
    private static volatile String csrf; // кэш csrf-токена веб-сессии

    private static final String MASK_EN = "0123456789abcdef-";
    private static final String MASK_RU = "оеаинтсрвлкмдпуяы";

    // точка входа /cmd?action=voice_say&alice=<device_id>&value=<текст>
    public static String voiceSay(String aliceId, String text) {
        if (text == null || text.isBlank()) return "ERROR пустой текст";
        if (!VOICE_TOGGLE_SAY_ALICE.value) return piper(text);
        if (aliceId == null || aliceId.isBlank()) return "ERROR нет параметра alice";
        try {
            say(aliceId.toLowerCase().trim(), fix(text));
            log.info("ALICE SAY ok: " + text);
            return "ALICE ok";
        } catch (Exception e) {
            log.error("ALICE SAY ошибка: " + e.getMessage());
            String p = piper(text);
            if (p != null) {
                log.info("фолбэк на Piper (причина: " + e.getMessage() + ")");
                return p; // чистый "PIPER <url>" — скрипт скачает и сыграет
            }
            return "ERROR AliceSay: " + e.getMessage();
        }
    }

    private static String piper(String text) {
        String url = PiperTTS.textToVoiceFile(text);
        return url == null ? null : "PIPER " + url;
    }

    // лимит облака — 100 символов; сжимаем пробелы
    static String fix(String text) {
        String t = text.replaceAll("  +", " ").trim();
        return t.length() > 100 ? t.substring(0, 100) : t;
    }

    // кодируем uid в рус. буквы (Яндекс привередлив к голосовому триггеру)
    static String encode(String uid) {
        StringBuilder sb = new StringBuilder();
        for (char ch : uid.toCharArray()) {
            int i = MASK_EN.indexOf(ch);
            if (i < 0) throw new IllegalStateException("недопустимый символ id: " + ch);
            sb.append(MASK_RU.charAt(i));
        }
        return sb.toString();
    }

    private static Map<String, String> headers() {
        String cookies = VOICE_STRING_YA_COOKIES.value;
        if (cookies == null || cookies.isBlank() || "---".equals(cookies))
            throw new IllegalStateException("cookies не заданы (Настройки голоса)");
        Map<String, String> h = new HashMap<>();
        h.put("Cookie", cookies.trim());
        return h;
    }

    private static String csrf() {
        if (csrf != null) return csrf;
        Map<String, String> h = headers();
        HttpResponseResult r = httpClient.doGet("https://yandex.ru/iot", h);
        if (!r.isSuccess()) throw new IllegalStateException("yandex.ru/iot HTTP " + r.getStatusCode());
        Matcher m = Pattern.compile("\"csrfToken2\":\"(.+?)\"").matcher(r.getBody());
        if (!m.find()) throw new IllegalStateException("csrfToken2 не найден (cookies живы?)");
        csrf = m.group(1);
        return csrf;
    }

    private static HttpResponseResult request(String method, String url, String body) {
        HttpResponseResult r = exec(method, url, body);
        if (r.getStatusCode() == 403) { // протух csrf — сброс и повтор
            csrf = null;
            r = exec(method, url, body);
        }
        return r;
    }

    private static HttpResponseResult exec(String method, String url, String body) {
        Map<String, String> h = headers();
        h.put("x-csrf-token", csrf());
        if ("PUT".equals(method)) return httpClient.doPut(url, body, h);
        if ("POST".equals(method)) return httpClient.doPost(url, body == null ? "{}" : body, h);
        return httpClient.doGet(url, h);
    }

    static String jsonEsc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // найти -> (создать) -> обновить текст -> запустить.
    // «ХА <id>» — сценарии от старой интеграции Home Assistant (та же tts-структура
    // и тот же триггер encode(id)) — переиспользуем их, не создавая дублей.
    static void say(String deviceId, String text) {
        String payload = ttsScenario("SA voice " + deviceId, encode(deviceId) + "с", deviceId, text);
        String sid = findScenarioId("SA voice " + deviceId);
        if (sid == null) sid = findScenarioId("ХА " + deviceId);
        if (sid == null) {
            HttpResponseResult r = request("POST",
                    "https://iot.quasar.yandex.ru/m/v4/user/scenarios", payload);
            check(r, "создание сценария");
            Matcher m = Pattern.compile("\"scenario_id\":\"([^\"]+)\"").matcher(r.getBody());
            if (!m.find()) throw new IllegalStateException("нет scenario_id в ответе: "
                    + (r.getBody() == null ? "пусто" : r.getBody().substring(0, Math.min(300, r.getBody().length()))));
            sid = m.group(1);
        } else {
            HttpResponseResult r = request("PUT",
                    "https://iot.quasar.yandex.ru/m/v3/user/scenarios/" + sid, payload);
            check(r, "обновление сценария");
        }
        HttpResponseResult r = request("POST",
                "https://iot.quasar.yandex.ru/m/user/scenarios/" + sid + "/actions", "{}");
        check(r, "запуск сценария");
    }

    private static void check(HttpResponseResult r, String what) {
        if (!r.isSuccess()) {
            String body = r.getBody() == null ? "" : r.getBody();
            if (body.length() > 160) body = body.substring(0, 160);
            throw new IllegalStateException(what + " HTTP " + r.getStatusCode() + " " + body);
        }
    }

    private static String findScenarioId(String name) {
        HttpResponseResult r = request("GET",
                "https://iot.quasar.yandex.ru/m/user/scenarios", null);
        check(r, "список сценариев");
        String quoted = Pattern.quote(name);
        Matcher m = Pattern.compile("\\{\"id\":\"([^\"]+)\"[^{}]*?\"name\":\"" + quoted + "\"")
                .matcher(r.getBody());
        if (m.find()) return m.group(1);
        m = Pattern.compile("\"name\":\"" + quoted + "\"[^{}]*?\"id\":\"([^\"]+)\"")
                .matcher(r.getBody());
        return m.find() ? m.group(1) : null;
    }

    // payload TTS-сценария (шаблон scenario_speaker_tts из AlexxIT/YandexStation)
    static String ttsScenario(String name, String trigger, String deviceId, String text) {
        return "{\"name\":\"" + jsonEsc(name) + "\",\"icon\":\"home\","
                + "\"triggers\":[{\"trigger\":{\"type\":\"scenario.trigger.voice\",\"value\":\"" + trigger + "\"}}],"
                + "\"steps\":[{\"type\":\"scenarios.steps.actions.v2\",\"parameters\":{\"items\":[{"
                + "\"id\":\"" + deviceId + "\",\"type\":\"step.action.item.device\",\"value\":{"
                + "\"id\":\"" + deviceId + "\",\"item_type\":\"device\",\"capabilities\":[{"
                + "\"type\":\"devices.capabilities.quasar\","
                + "\"state\":{\"instance\":\"tts\",\"value\":{\"text\":\"" + jsonEsc(text) + "\"}}}]}}]}}]}";
    }
}

