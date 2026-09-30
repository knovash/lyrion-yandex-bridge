package knovash.saclient.utils;

import lombok.extern.log4j.Log4j2;
import knovash.saclient.Main;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static knovash.saclient.web.Settings.KeyToggle.*;

@Log4j2
public class SchedulerSpotifyRefreshToken {

    private static final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(1);
    private static volatile ScheduledFuture<?> scheduledTask;
    private static final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    private SchedulerSpotifyRefreshToken() {
    }

    /**
     * Запускает периодическую проверку и упреждающее обновление токена Spotify.
     *
     * @param checkIntervalMinutes    интервал проверки в минутах
     * @param refreshThresholdMinutes за сколько минут до истечения начинать обновление
     */
    public static void startPeriodicRefresh(int checkIntervalMinutes, int refreshThresholdMinutes) {
        log.info("Starting Spotify token refresh scheduler. Check interval: {} min, threshold: {} min",
                checkIntervalMinutes, refreshThresholdMinutes);

        cancelExistingTask();

        Runnable refreshTask = () -> {
            if (!TOGGLE_SCHEDULER_SPOTIFY_REFRESH_TOKEN.value) return;

            // Проверяем, не выполняется ли уже обновление
            if (refreshInProgress.get()) {
                log.debug("Token refresh already in progress, skipping check");
                return;
            }

            try {
                long now = System.currentTimeMillis();
                long expiresAt = Main.config.spotifyExpiresAt;
                long timeLeft = expiresAt - now;

                // Если токена нет или время жизни неизвестно, пропускаем
                if (Main.config.spotifyAccessToken == null || Main.config.spotifyAccessToken.isEmpty() ||
                        expiresAt == 0) {
                    log.debug("No valid token present, skipping refresh check");
                    return;
                }

                // Если токен истёк или скоро истечёт
                if (timeLeft <= 0 || timeLeft <= TimeUnit.MINUTES.toMillis(refreshThresholdMinutes)) {
                    log.info("Token expires in {} minutes, refreshing...", TimeUnit.MILLISECONDS.toMinutes(timeLeft));
                    refreshToken();
                } else {
                    log.debug("Token valid for {} minutes, no refresh needed",
                            TimeUnit.MILLISECONDS.toMinutes(timeLeft));
                }
            } catch (Exception e) {
                log.error("Error during token refresh check", e);
            } finally {
                refreshInProgress.set(false);
            }
        };

        scheduledTask = executor.scheduleAtFixedRate(
                refreshTask,
                1,      // начальная задержка
                checkIntervalMinutes,
                TimeUnit.MINUTES
        );
    }

    private static void refreshToken() {
        if (!refreshInProgress.compareAndSet(false, true)) {
            log.debug("Refresh already in progress, skipping");
            return;
        }

        try {
            // обновление через облако (POST <server>/refresh_spotify), секрет остаётся на сервере
            knovash.saclient.SpotifyApi.refresh(Main.config);
            log.info("Spotify token refreshed successfully by scheduler");
        } catch (Exception e) {
            log.error("Failed to refresh Spotify token in scheduler", e);
        } finally {
            refreshInProgress.set(false);
        }
    }

    public static void stopPeriodicRefresh() {
        log.info("Stopping Spotify token refresh scheduler");
        cancelExistingTask();
    }

    public static void shutdown() {
        log.info("Shutting down Spotify token scheduler");
        cancelExistingTask();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void cancelExistingTask() {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            log.debug("Cancelling existing Spotify token refresh task");
            scheduledTask.cancel(false);
        }
    }
}