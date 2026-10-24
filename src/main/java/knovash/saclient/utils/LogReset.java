package knovash.saclient.utils;

import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static knovash.saclient.web.Settings.KeyValue.VALUE_LOG_RESET_DAYS;

/**
 * Обнуление лога клиента data/log.txt раз в N дней (по умолчанию 5, настройка
 * VALUE_LOG_RESET_DAYS; 0 = не обнулять). Дата последнего обнуления хранится в
 * data/log_reset.properties (lastResetAt). При первом запуске без маркера маркер
 * создаётся от текущего момента, существующий лог НЕ трогается.
 * Обнуление: stop appender "File" (log4j2.xml) -> truncate файла -> start appender.
 */
@Log4j2
public class LogReset {

    private static final String LOG_FILE = "data/log.txt";
    private static final String MARKER_FILE = "data/log_reset.properties";
    private static final String KEY_LAST_RESET = "lastResetAt";
    private static final String APPENDER_NAME = "File"; // имя File-appender в log4j2.xml

    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "log-reset");
        t.setDaemon(true);
        return t;
    });
    private static volatile ScheduledFuture<?> scheduledTask;

    private LogReset() {
    }

    /** Разовая проверка: вызывается при старте клиента. */
    public static synchronized void check() {
        try {
            int days = VALUE_LOG_RESET_DAYS.value;
            if (days <= 0) {
                return; // 0 = обнуление отключено
            }
            long last = readLastReset();
            if (last <= 0) {
                // маркера нет (первый запуск новой версии) — отсчёт от сейчас, лог не трогаем
                writeLastReset(System.currentTimeMillis());
                log.info("LOG RESET: маркер создан, лог не тронут (период {} дн)", days);
                return;
            }
            long elapsed = System.currentTimeMillis() - last;
            if (elapsed >= TimeUnit.DAYS.toMillis(days)) {
                reset(days, elapsed);
            }
        } catch (Exception e) {
            log.error("LOG RESET CHECK ERROR", e);
        }
    }

    /** Периодическая проверка (по умолчанию раз в час). */
    public static void startPeriodicCheck(int initialDelayMinutes, int periodMinutes) {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
        }
        scheduledTask = executor.scheduleAtFixedRate(
                LogReset::check, initialDelayMinutes, periodMinutes, TimeUnit.MINUTES);
        log.info("LOG RESET: проверка каждые {} мин, период обнуления {} дн",
                periodMinutes, VALUE_LOG_RESET_DAYS.value);
    }

    private static void reset(int days, long elapsed) {
        try {
            Appender appender = null;
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            if (ctx != null && ctx.getConfiguration() != null) {
                appender = ctx.getConfiguration().getAppender(APPENDER_NAME);
            }
            if (appender != null) {
                appender.stop(); // закрывает файл лога
            }
            try (RandomAccessFile raf = new RandomAccessFile(LOG_FILE, "rw")) {
                raf.setLength(0); // обнуление
            }
            writeLastReset(System.currentTimeMillis());
            if (appender != null) {
                appender.start(); // переоткрывает файл лога
            }
            log.info("LOG RESET: data/log.txt обнулён (прошло {} дн, период {} дн)",
                    elapsed / TimeUnit.DAYS.toMillis(1), days);
        } catch (Exception e) {
            log.error("LOG RESET ERROR", e);
        }
    }

    private static long readLastReset() {
        File f = new File(MARKER_FILE);
        if (!f.exists()) {
            return -1;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            Properties p = new Properties();
            p.load(in);
            return Long.parseLong(p.getProperty(KEY_LAST_RESET, "-1"));
        } catch (Exception e) {
            return -1;
        }
    }

    private static void writeLastReset(long ts) {
        try (FileOutputStream out = new FileOutputStream(MARKER_FILE)) {
            Properties p = new Properties();
            p.setProperty(KEY_LAST_RESET, String.valueOf(ts));
            p.store(out, "log rotation marker");
        } catch (Exception e) {
            log.error("LOG RESET MARKER WRITE ERROR", e);
        }
    }
}