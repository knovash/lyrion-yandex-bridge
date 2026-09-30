package knovash.saclient;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Страница авторизации в Spotify: ссылка на облачный /authorize_spotify?state=...,
 * опрос /token_spotify?state=... (как LocalAuthServerSpotify в squeeze-alice).
 */
public class LocalAuthSpotify extends LocalAuthBase {

    public LocalAuthSpotify(Config config) {
        super(config);
    }

    @Override
    protected String getDisplayName() {
        return "";
    }

    @Override
    protected String getAuthUrl(String sessionId) {
        return config.serverHttpUrl + "/authorize_spotify?state=" + sessionId;
    }

    @Override
    protected String getTokenPath() {
        return "/token_spotify";
    }

    @Override
    protected String getLinkText() {
        return "Авторизоваться в Spotify";
    }

    @Override
    protected boolean hasToken(JsonNode root) {
        return !root.path("access_token").asText("").isEmpty();
    }

    @Override
    protected void applyToken(JsonNode root) {
        config.spotifyAccessToken = root.path("access_token").asText("");
        String refresh = root.path("refresh_token").asText("");
        if (!refresh.isEmpty()) config.spotifyRefreshToken = refresh; // Spotify может не вернуть новый refresh
        config.spotifyExpiresAt = root.path("expires_at").asLong(0);
        config.save();
        log("SPOTIFY TOKEN RECEIVED expires_at=" + config.spotifyExpiresAt);
    }
}
