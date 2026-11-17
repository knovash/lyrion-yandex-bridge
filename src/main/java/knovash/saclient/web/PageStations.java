package knovash.saclient.web;

import knovash.saclient.Context;
import knovash.saclient.Main;
import knovash.saclient.utils.Utils;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    /** Последний скан сети: строки таблицы, найденные рутованные ip, подпись когда. */
    private static volatile String lastScanRows = "";
    private static volatile List<String> lastScanRooted = new ArrayList<>();
    private static volatile String lastScanAt = "";

    private static final Pattern IP_RE = Pattern.compile("\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b");
    private static final Pattern NEIGH_RE = Pattern.compile("^(\\d+\\.\\d+\\.\\d+\\.\\d+).*lladdr\\s+([0-9a-fA-F:]{17})");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
            String ip = st.optString("ip", "");
            String name = st.optString("name", "");
            if (name.isEmpty() || "?".equals(name)) name = "станция " + ip;
            int port = st.optInt("port", 2222);
            list.append(ip).append('\n');
            rows.append("<tr><td>").append(escape(name))
                    .append("</td><td><code>").append(escape(ip)).append(':').append(port).append("</code></td>")
                    .append(svcCell(st.optJSONObject("services"), "sq", ip, "squeezelite"))
                    .append(svcCell(st.optJSONObject("services"), "ap", ip, "AirPlay"))
                    .append(svcCell(st.optJSONObject("services"), "wp", ip, "лок.команды"))
                    .append(actionCell("station_check", ip, "Проверить"))
                    .append(actionCell("station_repoint", ip, "Перенацелить сюда"))
                    .append("</tr>");
        }
        if (rows.length() == 0) rows.append("<tr><td colspan='7'>станции не заданы — введите ip ниже или нажмите «Найти»</td></tr>");

        String scanBlock = lastScanRows.isEmpty() ? "" :
                "<h3>Скан сети (" + escape(lastScanAt) + ")</h3>" +
                "<table border='1' cellpadding='4' cellspacing='0'>" +
                "<tr><th>IP</th><th>MAC</th><th>Устройство</th><th>:2222 ssh</th><th>:5555 adb</th><th></th></tr>" +
                lastScanRows + "</table>" +
                (lastScanRooted.isEmpty() ? "" :
                        "<form method='POST' action='/form'><input type='hidden' name='action' value='station_scan_add'>" +
                        "<input type='hidden' name='station_ip' value='*'>" +
                        "<button type='submit'>Добавить все станции с рутом (" + lastScanRooted.size() + ")</button></form>");

        String inner =
                "<fieldset><legend><b>Рутованные станции</b></legend>" +
                        "<p>Привязка станций к серверу: whatplays шлёт локальные фразы на веб этого клиента, " +
                        "squeezelite станции подключается к LMS. При смене IP сервера нажмите " +
                        "<b>«Перенацелить сюда»</b> — станция перепропишет адреса на этот сервер и перезапустит " +
                        "сервисы (если IP уже этот — просто рестарт). Ничего автоматического не происходит.</p>" +
                        "<table border='1' cellpadding='4' cellspacing='0'>" +
                        "<tr><th>Станция</th><th>Адрес</th><th>squeezelite</th><th>AirPlay</th><th>лок.команды</th><th></th><th></th></tr>" + rows + "</table>" +
                        "<form method='POST' action='/form' style='display:inline'><input type='hidden' name='action' value='station_check'>" +
                        "<input type='hidden' name='station_ip' value='ALL'><button type='submit'>Проверить ВСЕ</button></form> " +
                        "<form method='POST' action='/form' style='display:inline'><input type='hidden' name='action' value='station_repoint'>" +
                        "<input type='hidden' name='station_ip' value='ALL'><button type='submit'>Перенацелить ВСЕ сюда</button></form>" +
                        (lastResult.isEmpty() ? "" :
                                "<h3>Результат последней операции</h3><pre>" + escape(lastResult) + "</pre>") +
                        "</fieldset>" +
                        "<fieldset><legend><b>Список станций</b></legend>" +
                        "<form method='POST' action='/form'>" +
                        "<input type='hidden' name='action' value='stations_save'>" +
                        "<p>Сюда введите ip ваших станций, имеющих рут, для их поиска и подключения:</p>" +
                        "<textarea name='stations_list' rows='5' cols='40'>" + escape(list.toString().trim()) + "</textarea><br>" +
                        "<p>SSH-пароль root станций: <input type='text' name='ssh_password' value=\"" + escape(pass) + "\" size='20'> " +
                        "<small>(хранится в data/stations.json на этом сервере)</small></p>" +
                        "<button type='submit'>Сохранить</button></form>" +
                        "<form method='POST' action='/form' style='display:inline'><input type='hidden' name='action' value='station_scan'>" +
                        "<button type='submit'>Найти станции с рутом в сети</button></form> " +
                        "<small>Скан подсети: :2222 открыт = рут (ssh), :5555 = adb; остальные живые хосты — по MAC.</small>" +
                        scanBlock +
                        "</fieldset>";

        context.bodyResponse = PageIndex.pageOuter(inner, "Рутованные станции", "Рутованные станции");
        context.code = 200;
    }

    // ==================== операции (из ProcessForm) ====================

    /** Сохранение списка: только IP по одному в строке (старый формат «имя;ip;порт» тоже принимается). */
    public static void save(Map<String, String> body) {
        String list = body.getOrDefault("stations_list", "");
        String pass = body.getOrDefault("ssh_password", "");
        JSONObject root = read();
        JSONArray old = root.optJSONArray("stations");
        JSONArray arr = new JSONArray();
        for (String line : list.split("\\r?\\n")) {
            String ip = extractIp(line.trim());
            if (ip == null) continue;
            JSONObject st = findByIp(old, ip);
            if (st == null) {
                st = new JSONObject().put("name", "станция " + ip).put("port", 2222);
            }
            st.put("ip", ip);
            st.put("port", st.optInt("port", 2222));
            String name = st.optString("name", "");
            if (name.isEmpty() || "?".equals(name)) st.put("name", "станция " + ip);
            arr.put(st);
        }
        root.put("stations", arr);
        root.put("sshPassword", pass);
        write(root);
        lastResult = "Список станций сохранён (" + arr.length() + " шт.). Имена и статусы подтянутся после «Проверить».";
        log.info("STATIONS saved: " + arr.length());
    }

    /** Проверка (check — теперь полный статус сервисов) или перенастройка (repoint). Ключ — ip. */
    public static Context op(Context context, String kind, String target) {
        JSONObject root = read();
        String pass = root.optString("sshPassword", "");
        JSONArray stations = root.optJSONArray("stations");
        boolean all = target == null || target.isBlank() || "ALL".equalsIgnoreCase(target);
        StringBuilder out = new StringBuilder();
        int done = 0;
        boolean changed = false;
        for (int i = 0; stations != null && i < stations.length(); i++) {
            JSONObject st = stations.optJSONObject(i);
            if (st == null) continue;
            String ip = st.optString("ip", "");
            if (!all && !ip.equals(target) && !st.optString("name", "").equals(target)) continue;
            out.append("=== ").append(st.optString("name", ip)).append(" (").append(ip).append(") ===\n");
            if ("check".equals(kind)) {
                String res = sshExec(ip, st.optInt("port", 2222), pass, scriptStatus());
                out.append(res).append('\n');
                applyStatus(root, st, res);
                changed = true;
            } else {
                out.append(sshExec(ip, st.optInt("port", 2222), pass, scriptRepoint(Main.myIp))).append('\n');
            }
            done++;
        }
        if (done == 0) out.append("станция не найдена: ").append(target);
        if (changed) write(root);
        lastResult = out.toString();
        log.info("STATIONS " + kind + " " + target + ":\n" + lastResult);
        render(context);
        return context;
    }

    /** Включить/выключить сервис на станции (только старт/стоп установленного). service: sq|ap|wp. */
    public static Context service(Context context, String ip, String service, String state) {
        JSONObject root = read();
        String pass = root.optString("sshPassword", "");
        JSONObject st = findByIp(root.optJSONArray("stations"), ip);
        if (ip == null || ip.isBlank() || st == null) {
            lastResult = "станция не найдена: " + ip;
        } else {
            boolean on = "on".equalsIgnoreCase(state);
            String title = "sq".equals(service) ? "squeezelite" : "ap".equals(service) ? "AirPlay" : "лок.команды";
            String res = sshExec(st.optString("ip"), st.optInt("port", 2222), pass,
                    scriptService(service, on) + "\n" + scriptStatus());
            applyStatus(root, st, res);
            write(root);
            lastResult = "=== " + st.optString("name", ip) + " (" + ip + "): " + title
                    + " → " + (on ? "ВКЛ" : "ВЫКЛ") + " ===\n" + res;
            log.info("STATIONS service " + ip + " " + service + " " + state);
        }
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

    /**
     * Полный статус станции одним вызовом: MAC/серийник/ROOM из whatplays, адреса LMS/SAC,
     * установленность и живость squeezelite / лок.команд / AirPlay. Ключи парсятся applyStatus().
     */
    static String scriptStatus() {
        return "MAC=$(cat /sys/class/net/wlan0/address 2>/dev/null)\n"
                + "SERIAL=$(tr ' ' '\\n' </proc/cmdline | grep androidboot.serialno | cut -d= -f2)\n"
                + "ROOM=$(sed -n \"s/^ROOM=//p\" /data/opt/bin/whatplays.sh 2>/dev/null | head -1 | sed \"s/^'//; s/'$//\")\n"
                + "echo \"MAC=$MAC\"\n"
                + "echo \"SERIAL=$SERIAL\"\n"
                + "echo \"ROOM=$ROOM\"\n"
                + "echo \"LMS=$(grep '^LMS=' /data/opt/bin/whatplays.sh 2>/dev/null | head -1 | cut -d= -f2-)\"\n"
                + "echo \"SAC=$(grep '^SAC=' /data/opt/bin/whatplays.sh 2>/dev/null | head -1 | cut -d= -f2-)\"\n"
                + "echo \"SQS=$(grep -oE '[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+' /etc/init.d/S98squeeze 2>/dev/null | sort -u | tr '\\n' ' ')\"\n"
                + "echo \"SQ_INST=$([ -x /data/opt/bin/squeezelite ] && echo 1 || echo 0)\"\n"
                + "echo \"SQ_RUN=$(pidof squeezelite >/dev/null 2>&1 && echo 1 || echo 0)\"\n"
                + "echo \"WP_INST=$([ -f /data/opt/bin/whatplays.sh ] && echo 1 || echo 0)\"\n"
                + "echo \"WP_RUN=$(ps | grep -q 'whatpl[a]ys' && echo 1 || echo 0)\"\n"
                + "echo \"AP_INST=$([ -f /etc/init.d/S99zairplay-lite1 ] || [ -d /data/opt/airplay ] && echo 1 || echo 0)\"\n"
                + "echo \"AP_RUN=$(ps | grep -qE 'shairpo[r]t|avah[i]|nqpt[p]' && echo 1 || echo 0)\"\n"
                + "echo \"PROCS=$(ps | grep -cE 'whatpl[a]ys|squeezelite')\"\n";
    }

    /**
     * Вкл/выкл сервиса на станции (только старт/стоп установленного):
     * off = stop + kill + перенос init-скрипта в /data/opt/disabled (автостарт снят, rootfs RO);
     * on = возврат init-скрипта в /etc/init.d + start. service: sq | ap | wp.
     */
    static String scriptService(String service, boolean on) {
        String init;
        String extraKill;
        switch (service == null ? "wp" : service) {
            case "sq":
                init = "S98squeeze";
                extraKill = "killall squeezelite 2>/dev/null";
                break;
            case "ap":
                init = "S99zairplay-lite1";
                extraKill = "killall shairport-sync 2>/dev/null; killall nqptp 2>/dev/null; "
                        + "killall avahi-daemon 2>/dev/null; killall avahi 2>/dev/null";
                break;
            default:
                init = "S97whatplays";
                extraKill = "ps | grep 'whatpl[a]ys' | awk '{print $1}' | xargs -r kill 2>/dev/null; "
                        + "killall alice_ipc_mon tcpcat 2>/dev/null; "
                        + "ps | grep 'main[d] --cli' | awk '{print $1}' | xargs -r kill 2>/dev/null";
        }
        if (on) {
            return "D=/data/opt/disabled\n"
                    + "if [ -f $D/" + init + " ]; then\n"
                    + "  mount -o remount,rw /\n"
                    + "  cp $D/" + init + " /etc/init.d/" + init + "\n"
                    + "  chmod 755 /etc/init.d/" + init + "\n"
                    + "  mount -o remount,ro /\n"
                    + "  echo 'автостарт возвращён'\n"
                    + "fi\n"
                    + "if [ -f /etc/init.d/" + init + " ]; then /etc/init.d/" + init + " start && echo 'start ок'; else echo 'init-скрипта нет — сервис не установлен'; fi\n"
                    + "sleep 2\n";
        }
        return "I=/etc/init.d/" + init + "\n"
                + "D=/data/opt/disabled\n"
                + "mkdir -p $D\n"
                + "[ -f $I ] && $I stop 2>/dev/null\n"
                + extraKill + "\n"
                + "sleep 1\n"
                + "if [ -f $I ]; then\n"
                + "  mount -o remount,rw /\n"
                + "  mv $I $D/" + init + "\n"
                + "  mount -o remount,ro /\n"
                + "  echo 'автостарт снят'\n"
                + "else\n"
                + "  echo 'init-скрипта в /etc/init.d нет'\n"
                + "fi\n";
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

    /** Автопоиск в своей /24: :2222 (рут ssh) и :5555 (adb), затем ARP → MAC/вендор. */
    public static Context scan(Context context) {
        String base = (Main.myIp != null && !Main.myIp.isBlank()) ? Main.myIp : Utils.getMyIpAddress();
        String prefix = base.replaceAll("\\.\\d+$", "");
        long t0 = System.currentTimeMillis();
        ExecutorService ex = Executors.newFixedThreadPool(64);
        List<Future<String[]>> futures = new ArrayList<>();
        for (int i = 1; i <= 254; i++) {
            final String ip = prefix + "." + i;
            futures.add(ex.submit(() -> new String[]{ip, portOpen(ip, 2222) ? "1" : "0", portOpen(ip, 5555) ? "1" : "0"}));
        }
        ex.shutdown();
        try {
            ex.awaitTermination(90, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Map<String, String> arp = readArp(); // ПОСЛЕ скана: попытки connect наполняют ARP-таблицу
        JSONArray oui = read().optJSONArray("yandexOui");

        StringBuilder rows = new StringBuilder();
        StringBuilder out = new StringBuilder();
        List<String> rooted = new ArrayList<>();
        try {
            for (Future<String[]> f : futures) {
                String[] h = f.get();
                String ip = h[0];
                if (ip.equals(base)) continue;
                boolean p2 = "1".equals(h[1]), p5 = "1".equals(h[2]);
                String mac = arp.getOrDefault(ip, "");
                if (!p2 && !p5 && mac.isEmpty()) continue; // хост не отвечает — не показываем
                boolean yandex = isYandexMac(mac, oui);
                String dev = p2 ? "<b>рут (ssh :2222)</b>"
                        : p5 ? "adb (:5555) без ssh"
                        : yandex ? "похоже, станция (MAC)"
                        : "прочее устройство";
                rows.append("<tr><td><code>").append(ip).append("</code></td>")
                        .append("<td><small>").append(mac.isEmpty() ? "—" : mac).append("</small></td>")
                        .append("<td>").append(dev).append("</td>")
                        .append("<td>").append(p2 ? "✔" : "—").append("</td>")
                        .append("<td>").append(p5 ? "✔" : "—").append("</td>")
                        .append("<td>").append(p2 ? actionCell("station_scan_add", ip, "добавить") : "").append("</td></tr>");
                out.append(ip).append("  mac=").append(mac.isEmpty() ? "-" : mac)
                        .append(p2 ? "  ROOT" : "").append(p5 ? "  ADB" : "").append(yandex ? "  YANDEX?" : "").append('\n');
                if (p2) rooted.add(ip);
            }
        } catch (Exception e) {
            log.warn("scan join error: " + e.getMessage());
        }
        lastScanRows = rows.toString();
        lastScanRooted = rooted;
        long sec = (System.currentTimeMillis() - t0) / 1000;
        lastScanAt = prefix + ".0/24, " + sec + " с";
        lastResult = "Скан " + prefix + ".0/24 завершён за " + sec + " с\n"
                + "с рутом (:2222): " + rooted.size() + ", устройств в ARP: " + arp.size() + "\n\n" + out;
        log.info("STATIONS scan: rooted=" + rooted + " arp=" + arp.size());
        render(context);
        return context;
    }

    /** Добавить ip из результатов скана ("*" = все найденные с рутом). */
    public static Context scanAdd(Context context, String ipOrAll) {
        JSONObject root = read();
        JSONArray stations = root.optJSONArray("stations");
        if (stations == null) {
            stations = new JSONArray();
            root.put("stations", stations);
        }
        List<String> add = ("*".equals(ipOrAll) || "ALL".equalsIgnoreCase(ipOrAll))
                ? new ArrayList<>(lastScanRooted) : List.of(ipOrAll == null ? "" : ipOrAll);
        StringBuilder out = new StringBuilder();
        int added = 0;
        for (String ip : add) {
            if (ip.isBlank() || findByIp(stations, ip) != null) continue;
            stations.put(new JSONObject().put("name", "станция " + ip).put("ip", ip).put("port", 2222));
            out.append("добавлена ").append(ip).append(" (имя подтянется после «Проверить»)\n");
            added++;
        }
        write(root);
        lastResult = "Добавлено станций: " + added + "\n" + out;
        log.info("STATIONS scanAdd: +" + added);
        render(context);
        return context;
    }

    /** Разбор вывода scriptStatus и обновление записи станции (имя/ROOM, mac, serial, сервисы, OUI). */
    private static void applyStatus(JSONObject root, JSONObject st, String res) {
        Map<String, String> kv = parseKv(res);
        String mac = kv.getOrDefault("MAC", "");
        String serial = kv.getOrDefault("SERIAL", "");
        String room = kv.getOrDefault("ROOM", "");
        if (!mac.isEmpty()) {
            st.put("mac", mac);
            learnOui(root, mac); // рутованная станция = источник OUI-префикса Яндекса
        }
        if (!serial.isEmpty()) st.put("serial", serial);
        if (!room.isEmpty()) st.put("room", room);
        String name = st.optString("name", "");
        if (!room.isEmpty() && (name.isEmpty() || "?".equals(name) || name.startsWith("станция "))) st.put("name", room);
        JSONObject svc = new JSONObject()
                .put("sq", new JSONObject().put("inst", "1".equals(kv.get("SQ_INST"))).put("run", "1".equals(kv.get("SQ_RUN"))))
                .put("ap", new JSONObject().put("inst", "1".equals(kv.get("AP_INST"))).put("run", "1".equals(kv.get("AP_RUN"))))
                .put("wp", new JSONObject().put("inst", "1".equals(kv.get("WP_INST"))).put("run", "1".equals(kv.get("WP_RUN"))));
        st.put("services", svc);
        st.put("statusAt", LocalDateTime.now().format(TS));
    }

    // ==================== UI-помощники ====================

    /** Ячейка таблицы сервисов: ▶ работает [Выкл] / ● установлен [Вкл] / — / ? (статус не запрашивался). */
    private static String svcCell(JSONObject services, String key, String ip, String title) {
        if (services == null || !services.has(key))
            return "<td title='" + title + "'><span style='color:#888'>?</span></td>";
        JSONObject s = services.optJSONObject(key);
        boolean inst = s != null && s.optBoolean("inst", false);
        boolean run = s != null && s.optBoolean("run", false);
        if (run)
            return "<td title='" + title + "'><span style='color:green'>▶ работает</span><br>"
                    + svcForm(ip, key, "off", "Выкл") + "</td>";
        if (inst)
            return "<td title='" + title + "'><span style='color:#b8860b'>● установлен</span><br>"
                    + svcForm(ip, key, "on", "Вкл") + "</td>";
        return "<td title='" + title + "'><span style='color:#888'>—</span></td>";
    }

    private static String svcForm(String ip, String service, String state, String label) {
        return "<form method='POST' action='/form' style='display:inline'>" +
                "<input type='hidden' name='action' value='station_service'>" +
                "<input type='hidden' name='station_ip' value='" + escape(ip) + "'>" +
                "<input type='hidden' name='station_service' value='" + service + "'>" +
                "<input type='hidden' name='station_state' value='" + state + "'>" +
                "<button type='submit' style='font-size:x-small'>" + label + "</button></form>";
    }

    private static String actionCell(String action, String ip, String label) {
        return "<td><form method='POST' action='/form' style='display:inline'>" +
                "<input type='hidden' name='action' value='" + action + "'>" +
                "<input type='hidden' name='station_ip' value='" + escape(ip) + "'>" +
                "<button type='submit'>" + label + "</button></form></td>";
    }

    // ==================== разное ====================

    private static JSONObject findByIp(JSONArray stations, String ip) {
        if (stations == null || ip == null) return null;
        for (int i = 0; i < stations.length(); i++) {
            JSONObject st = stations.optJSONObject(i);
            if (st != null && ip.equals(st.optString("ip"))) return st;
        }
        return null;
    }

    /** Первый IP-подобный токен строки (принимает и старый формат «имя;ip;порт»). */
    private static String extractIp(String line) {
        Matcher m = IP_RE.matcher(line);
        if (!m.find()) return null;
        String ip = m.group(1);
        for (String part : ip.split("\\.")) {
            try {
                if (Integer.parseInt(part) > 255) return null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return ip;
    }

    /** Строки вида КЛЮЧ=значение с коротким ключом из [A-Z_]. */
    private static Map<String, String> parseKv(String res) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : res.split("\\n")) {
            int i = line.indexOf('=');
            if (i > 0 && i <= 16) {
                String k = line.substring(0, i).trim();
                if (k.matches("[A-Z_]+")) map.put(k, line.substring(i + 1).trim());
            }
        }
        return map;
    }

    private static void learnOui(JSONObject root, String mac) {
        if (mac == null || mac.length() < 8) return;
        String oui = mac.substring(0, 8).toUpperCase();
        JSONArray arr = root.optJSONArray("yandexOui");
        if (arr == null) {
            arr = new JSONArray();
            root.put("yandexOui", arr);
        }
        for (int i = 0; i < arr.length(); i++) {
            if (oui.equals(arr.optString(i))) return;
        }
        arr.put(oui);
    }

    private static boolean isYandexMac(String mac, JSONArray oui) {
        if (mac == null || mac.length() < 8 || oui == null) return false;
        String p = mac.substring(0, 8).toUpperCase();
        for (int i = 0; i < oui.length(); i++) {
            if (p.equals(oui.optString(i))) return true;
        }
        return false;
    }

    /** ARP-таблица хоста: ip -> mac (после скана там живые хосты подсети). */
    private static Map<String, String> readArp() {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            Process p = new ProcessBuilder("ip", "neigh").start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(5, TimeUnit.SECONDS);
            for (String line : out.split("\n")) {
                Matcher m = NEIGH_RE.matcher(line.trim());
                if (m.find()) map.put(m.group(1), m.group(2).toLowerCase());
            }
        } catch (Exception e) {
            log.warn("ip neigh error: " + e.getMessage());
        }
        return map;
    }

    private static boolean portOpen(String ip, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 700);
            return true;
        } catch (Exception e) {
            return false;
        }
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
