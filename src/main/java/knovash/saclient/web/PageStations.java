package knovash.saclient.web;

import knovash.saclient.Context;
import knovash.saclient.Main;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Страница /stations — «Рутованные станции»: список станций (ssh root@ip -p порт),
 * проверка их текущей привязки (какой сервер прописан в whatplays/squeezelite)
 * и кнопка «Перенацелить сюда» — смена привязки станций при переезде сервера
 * LMS/sa_client на другой IP. Перенастройка выполняется ТОЛЬКО по нажатию
 * (решение владельца 12.11 — ничего автоматического).
 *
 * Рецепт обкатан вручную 12.11 (.120/.126): whatplays = /data/opt/bin/whatplays.sh
 * (writable), адрес squeezelite-сервера в /etc/init.d/S98squeeze (rootfs станций
 * RO — remount rw на время правки), sshd станций (S99zdropbear) на порту 2222.
 * Новый адрес = Main.myIp (этот сервер). Список и пароль — data/stations.json
 * (gitignored, как и все данные).
 */
@Log4j2
public class PageStations {

    private static final Path FILE = Paths.get("data", "stations.json");
    private static volatile String lastResult = "";

    public static Context action(Context context) {
        render(context);
        return context;
    }

    private static void render(Context context) {
        JSONObject root = read();
        JSONArray stations = root.optJSONArray("stations");
        String pass = root.optString("sshPassword", "");

        StringBuilder list = new StringBuilder();
        StringBuilder rows = new StringBuilder();
        for (int i = 0; stations != null && i < stations.length(); i++) {
            JSONObject st = stations.optJSONObject(i);
            if (st == null) continue;
            String name = st.optString("name", "?");
            String ip = st.optString("ip", "");
            int port = st.optInt("port", 2222);
            list.append(name).append(';').append(ip).append(';').append(port).append('\n');
            rows.append("<tr><td>").append(escape(name))
                    .append("</td><td><code>").append(escape(ip)).append(':').append(port).append("</code></td>")
                    .append("<td><form method='POST' action='/form' style='display:inline'>")
                    .append("<input type='hidden' name='action' value='station_check'>")
                    .append("<input type='hidden' name='station_name' value=\"").append(escape(name)).append("\">")
                    .append("<button type='submit'>Проверить</button></form></td>")
                    .append("<td><form method='POST' action='/form' style='display:inline'>")
                    .append("<input type='hidden' name='action' value='station_repoint'>")
                    .append("<input type='hidden' name='station_name' value=\"").append(escape(name)).append("\">")
                    .append("<button type='submit'>Перенацелить сюда</button></form></td></tr>");
        }
        if (rows.length() == 0) rows.append("<tr><td colspan='4'>станции не заданы — добавьте ниже</td></tr>");

        String inner =
                "<fieldset><legend><b>Рутованные станции</b></legend>" +
                        "<p>Привязка станций к серверу: whatplays шлёт локальные фразы на веб этого клиента, " +
                        "squeezelite станции подключается к LMS. При смене IP сервера нажмите " +
                        "<b>«Перенацелить сюда»</b> — станция перепропишет адреса на этот сервер и перезапустит " +
                        "сервисы (если IP уже этот — просто рестарт). Ничего автоматического не происходит.</p>" +
                        "<table border='1' cellpadding='4' cellspacing='0'>" +
                        "<tr><th>Станция</th><th>Адрес</th><th></th><th></th></tr>" + rows + "</table>" +
                        "<form method='POST' action='/form'>" +
                        "<input type='hidden' name='action' value='station_repoint'>" +
                        "<input type='hidden' name='station_name' value='ALL'>" +
                        "<button type='submit'>Перенацелить ВСЕ станции сюда</button></form>" +
                        (lastResult.isEmpty() ? "" :
                                "<h3>Результат последней операции</h3><pre>" + escape(lastResult) + "</pre>") +
                        "</fieldset>" +
                        "<fieldset><legend><b>Список станций и доступ</b></legend>" +
                        "<form method='POST' action='/form'>" +
                        "<input type='hidden' name='action' value='stations_save'>" +
                        "<p>Станции — по одной в строке: <code>имя;ip;порт</code> (порт ssh, обычно 2222)</p>" +
                        "<textarea name='stations_list' rows='4' cols='60'>" + escape(list.toString().trim()) + "</textarea><br>" +
                        "<p>SSH-пароль root станций: <input type='text' name='ssh_password' value=\"" + escape(pass) + "\" size='20'> " +
                        "<small>(хранится в data/stations.json на этом сервере)</small></p>" +
                        "<button type='submit'>Сохранить</button></form></fieldset>";

        context.bodyResponse = PageIndex.pageOuter(inner, "Рутованные станции", "Рутованные станции");
        context.code = 200;
    }

    // ==================== операции (из ProcessForm) ====================
    public static void save(Map<String, String> body) {
        String list = body.getOrDefault("stations_list", "");
        String pass = body.getOrDefault("ssh_password", "");
        JSONObject root = new JSONObject();
        JSONArray arr = new JSONArray();
        for (String line : list.split("\\r?\\n")) {
            String[] f = line.split(";");
            if (f.length < 2) continue;
            String name = f[0].trim();
            String ip = f[1].trim();
            int port = 2222;
            try { if (f.length > 2 && !f[2].trim().isEmpty()) port = Integer.parseInt(f[2].trim()); }
            catch (NumberFormatException ignored) { }
            if (name.isEmpty() || ip.isEmpty()) continue;
            arr.put(new JSONObject().put("name", name).put("ip", ip).put("port", port));
        }
        root.put("stations", arr);
        root.put("sshPassword", pass);
        write(root);
        lastResult = "Список станций сохранён (" + arr.length() + " шт.)";
        log.info("STATIONS saved: " + arr.length());
    }

    public static Context op(Context context, String kind, String stationName) {
        JSONObject root = read();
        String pass = root.optString("sshPassword", "");
        JSONArray stations = root.optJSONArray("stations");
        boolean all = stationName == null || stationName.isBlank() || "ALL".equalsIgnoreCase(stationName);
        StringBuilder out = new StringBuilder();
        int done = 0;
        for (int i = 0; stations != null && i < stations.length(); i++) {
            JSONObject st = stations.optJSONObject(i);
            if (st == null) continue;
            String name = st.optString("name", "?");
            if (!all && !name.equals(stationName)) continue;
            out.append("=== ").append(name).append(" (").append(st.optString("ip", "?")).append(") ===\n");
            if ("check".equals(kind)) {
                out.append(sshExec(st.optString("ip"), st.optInt("port", 2222), pass, scriptCheck()));
            } else {
                out.append(sshExec(st.optString("ip"), st.optInt("port", 2222), pass, scriptRepoint(Main.myIp)));
            }
            out.append('\n');
            done++;
        }
        if (done == 0) out.append("станция не найдена: ").append(stationName);
        lastResult = out.toString();
        log.info("STATIONS " + kind + " " + stationName + ":\n" + lastResult);
        render(context);
        return context;
    }

    // ==================== SSH (системный ssh+sshpass; скрипт подаётся на stdin) ====================
    // ssh-клиент сервера совместим с любыми алгоритмами станций (JSch не осилил
    // ed25519-only dropbear Лайтов). Скрипт уходит через "sh -s" — без экранирования.
    private static String sshExec(String ip, int port, String pass, String cmd) {
        if (ip == null || ip.isBlank()) return "ошибка: не задан ip";
        if (pass == null || pass.isBlank()) return "ошибка: не задан ssh-пароль (см. форму ниже)";
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "sshpass", "-p", pass, "ssh", "-p", String.valueOf(port),
                    "-o", "StrictHostKeyChecking=no",
                    "-o", "UserKnownHostsFile=/dev/null",
                    "-o", "ConnectTimeout=10",
                    "root@" + ip, "sh -s");
            pb.redirectErrorStream(true);
            p = pb.start();
            p.getOutputStream().write(cmd.getBytes(StandardCharsets.UTF_8));
            p.getOutputStream().close();
            if (!p.waitFor(35, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "таймаут ssh (35с)";
            }
            return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            String m = e.getMessage() == null ? e.toString() : e.getMessage();
            if (m.contains("Cannot run program"))
                return "ошибка: на сервере нет sshpass/ssh — apt install sshpass openssh-client";
            return "SSH ERROR: " + m;
        } finally {
            if (p != null && p.isAlive()) p.destroyForcibly();
        }
    }

    /** Проверка (read-only): текущие адреса whatplays/squeezelite + живость процессов. */
    static String scriptCheck() {
        return "echo '--- whatplays (LMS/SAC):'; grep -E '^LMS=|^SAC=' /data/opt/bin/whatplays.sh 2>/dev/null || echo 'нет whatplays'; "
                + "echo '--- squeezelite сервер:'; grep -oE '[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+' /etc/init.d/S98squeeze 2>/dev/null | sort -u || echo 'нет S98squeeze'; "
                + "echo '--- процессов (whatplays+squeezelite):'; ps | grep -cE 'whatpl[a]ys|squeezelite'";
    }

    /**
     * Перенастройка на новый IP (обкатано вручную 12.11): старый IP берём из SAC
     * whatplays, заменяем в whatplays и S98squeeze (remount rw/ro), рестартуем
     * S98squeeze и S97whatplays. При совпадении IP — только рестарт.
     */
    static String scriptRepoint(String newIp) {
        return "WP=/data/opt/bin/whatplays.sh\n"
                + "NEW=" + newIp + "\n"
                + "OLD=$(grep -oE 'https?://[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+' $WP 2>/dev/null | head -1 | grep -oE '[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+')\n"
                + "echo \"было: ${OLD:-не найдено} -> стало: $NEW\"\n"
                + "if [ -n \"$OLD\" ] && [ \"$OLD\" != \"$NEW\" ]; then\n"
                + "  sed -i \"s/$OLD/$NEW/g\" $WP\n"
                + "  mount -o remount,rw / 2>/dev/null\n"
                + "  sed -i \"s/$OLD/$NEW/g\" /etc/init.d/S98squeeze 2>/dev/null\n"
                + "  mount -o remount,ro / 2>/dev/null\n"
                + "  echo 'адреса заменены'\n"
                + "else\n"
                + "  echo 'IP уже этот — только рестарт сервисов'\n"
                + "fi\n"
                + "/etc/init.d/S98squeeze stop 2>/dev/null; sleep 1; /etc/init.d/S98squeeze start 2>/dev/null\n"
                + "ps | grep 'whatpl[a]ys' | awk '{print $1}' | xargs kill 2>/dev/null; sleep 1\n"
                + "/etc/init.d/S97whatplays start 2>/dev/null; sleep 2\n"
                + "echo '--- теперь:'; grep -E '^LMS=|^SAC=' $WP\n"
                + "echo '--- squeeze:'; grep -oE '[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+' /etc/init.d/S98squeeze | sort -u\n"
                + "echo '--- процессов:'; ps | grep -cE 'whatpl[a]ys|squeezelite'";
    }

    // ==================== data/stations.json ====================
    private static JSONObject read() {
        try {
            if (Files.exists(FILE)) return new JSONObject(Files.readString(FILE, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("stations.json read error: " + e.getMessage());
        }
        return new JSONObject().put("stations", new JSONArray()).put("sshPassword", "");
    }

    private static synchronized void write(JSONObject root) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.write(FILE, root.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("stations.json write error: " + e.getMessage());
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
