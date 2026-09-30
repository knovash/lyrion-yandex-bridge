package knovash.saclient.spotify;

import lombok.extern.log4j.Log4j2;
import knovash.saclient.http.HttpClientWrapper;
import knovash.saclient.http.HttpResponseResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static knovash.saclient.Main.*;

@Log4j2
public class SpotifyRequests {

    private static final HttpClientWrapper httpClient = new HttpClientWrapper();

    private static synchronized void refreshTokenIfExpired() {
        log.info(start);
        if (config.spotifyAccessToken == null || config.spotifyAccessToken.isEmpty()) {
            log.debug("No access token, cannot refresh");
            return;
        }
        if (config.spotifyRefreshToken == null || config.spotifyRefreshToken.isEmpty()) {
            log.debug("No refresh token, cannot refresh");
            return;
        }
        long now = System.currentTimeMillis();
        if (now > config.spotifyExpiresAt) {
            log.info("Spotify token expired. Requesting refresh...");
            // обновление через облако (POST <server>/refresh_spotify), секрет остаётся на сервере
            knovash.saclient.SpotifyApi.refresh(config);
            log.info("Token refreshed. New expiry: {}", config.spotifyExpiresAt);
        }
        log.info(finish);
    }

    private static HttpResponseResult executeRequest(String method, String uri, String body) {
        refreshTokenIfExpired();
        if (config.spotifyAccessToken == null || config.spotifyAccessToken.isEmpty()) {
            log.error("No access token, aborting request to {}", uri);
            return HttpResponseResult.error("No access token");
        }
        else log.info("TOKEN OK");

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + config.spotifyAccessToken);
        headers.put("Content-Type", "application/json");

        HttpResponseResult result;

        switch (method.toUpperCase()) {
            case "GET":
                result = httpClient.doGet(uri, headers);
                break;
            case "POST":
                result = httpClient.doPost(uri, body, headers);
                break;
            case "PUT":
                result = httpClient.doPut(uri, body, headers);
                break;
            default:
                return HttpResponseResult.error("Unsupported method");
        }

        if (result.getStatusCode() == 401) {
            log.warn("Received 401, forcing token refresh and retry...");
            refreshTokenIfExpired();
            if (config.spotifyAccessToken != null && !config.spotifyAccessToken.isEmpty()) {
                headers.put("Authorization", "Bearer " + config.spotifyAccessToken);
                switch (method.toUpperCase()) {
                    case "GET":
                        result = httpClient.doGet(uri, headers);
                        break;
                    case "POST":
                        result = httpClient.doPost(uri, body, headers);
                        break;
                    case "PUT":
                        result = httpClient.doPut(uri, body, headers);
                        break;
                }
            }
        }
        if (result.getStatusCode() == 204) {
            log.info("204");
        }
        log.info("RESULT: " + result.getStatusCode());
        return result;
    }

    public static String requestGet(String uri) {
        HttpResponseResult result = executeRequest("GET", uri, null);
        log.debug("RESULT: " + result);
        if (result.isSuccess()) {
            log.info("OK");
            return result.getBody();
        } else {
            log.error("GET request failed: {}", result.getStatusCode());
            return null;
        }
    }

    public static String requestPut(String uri) {
        HttpResponseResult result = executeRequest("PUT", uri, null);
        if (result.isSuccess()) {
            return result.getBody();
        } else {
            log.error("PUT request failed: {}", result.getStatusCode());
            return null;
        }
    }

    public static String requestPost(String uri) {
        HttpResponseResult result = executeRequest("POST", uri, null);
        if (result.isSuccess()) {
            return result.getBody();
        } else {
            log.error("POST request failed: {}", result.getStatusCode());
            return null;
        }
    }
}