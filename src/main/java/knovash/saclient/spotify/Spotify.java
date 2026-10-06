package knovash.saclient.spotify;

import lombok.extern.log4j.Log4j2;
import knovash.saclient.lms.Player;
import knovash.saclient.lms.PlayerStatus.PlaylistLoop;
import knovash.saclient.spotify.spotify_pojo.*;
import knovash.saclient.spotify.spotify_pojo.spotify_artists.SpotifyArtists;
import knovash.saclient.utils.JsonUtils;
import knovash.saclient.utils.Utils;
import knovash.saclient.utils.levenstein.Levenstein;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static knovash.saclient.Main.*;

@Log4j2
public class Spotify {

    // Статические поля оставлены для совместимости (но их использование внутри класса сведено к минимуму)
    public static PlayerState playerState = new PlayerState();
    public static CurrentlyPlaying currentlyPlaying = new CurrentlyPlaying();
    public static Boolean active = false;
    //public static String currentGetName;

    // ------- МЕТОД, КОТОРЫЙ НЕ ИЗМЕНЯТЬ (me) -------

    public static String me() {
        log.info("SPOTIFY INFO START");
        if (config.spotifyAccessToken == null || config.spotifyAccessToken.isEmpty()) {
            log.info("SPOTIFY NOT LOGGED IN");
            return null;
        }
        String uri = "https://api.spotify.com/v1/me";
        log.info("URI: " + uri);
        String body = SpotifyRequests.requestGet(uri);
        log.info("SPOTY ME BODY: " + body);
        return body;
    }

    // ------- МЕТОДЫ ПОИСКА -------
    public static class GetLinkResult {

        public final String uri;
        public final String name;

        public GetLinkResult(String uri, String name) {
            this.uri = uri;
            this.name = name;
        }
    }

    /**
     * Умный поиск Spotify. Проблема старого подхода (просто первый результат):
     * «виктория бекхэм» → Spotify «угадывал» Viktor Tsoi, «backham victoria» → Victoria Dayneko.
     * Теперь: транслит+нормализация запроса, вариант с обратным порядком слов,
     * и выбор ЛУЧШЕГО результата по скорингу имени (порядок слов не важен, слова fuzzy).
     */
    private static GetLinkResult searchBest(String type, String target, Function<String, List<String[]>> search) {
        log.info("SPOTIFY SEARCH " + type + ": " + target);
        GetLinkResult best = null;
        int bestScore = -1;
        for (String q : searchQueries(target)) {
            try {
                List<String[]> items = search.apply(q);
                if (items != null) {
                    for (String[] item : items) {
                        if (item == null || item.length < 2 || item[0] == null || item[1] == null) continue;
                        int sc = matchScore(q, item[1]);
                        if (sc > bestScore) {
                            bestScore = sc;
                            best = new GetLinkResult(item[0], item[1]);
                        }
                    }
                }
            } catch (Exception e) {
                log.info("SPOTIFY SEARCH ERROR for '" + q + "': " + e);
            }
            if (bestScore >= 90) break; // точное/вложенное совпадение — дальше не ищем
        }
        log.info("SPOTIFY BEST " + type + ": " + (best == null ? "none" : best.name) + " (score " + bestScore + ")");
        return best != null ? best : new GetLinkResult(null, null);
    }

    /** Варианты запроса: нормализованный (транслит) + те же слова в обратном порядке. */
    static List<String> searchQueries(String target) {
        String t = normalizeWords(target);
        if (t.isEmpty()) return List.of(target == null ? "" : target.trim());
        List<String> queries = new ArrayList<>(List.of(t));
        List<String> words = new ArrayList<>(List.of(t.split(" ")));
        Collections.reverse(words);
        String reversed = String.join(" ", words);
        if (!reversed.equals(t)) queries.add(reversed);
        return queries;
    }

    private static String normalizeWords(String s) {
        if (s == null) return "";
        return Utils.normalizeForSearch(s).toLowerCase()
                .replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    /** Скоринг совпадения имени кандидата с запросом (порядок слов не важен, слова fuzzy). */
    static int matchScore(String query, String candidateName) {
        String c = normalizeWords(candidateName);
        if (c.isEmpty() || query == null || query.isEmpty()) return 0;
        if (c.equals(query)) return 100;
        boolean contains = c.contains(query) || query.contains(c);
        // «вхождение» засчитывается как сильное только при сопоставимой длине:
        // иначе артист «Viktoria» получал 90 за одно слово из двух («viktoria bekhem»)
        if (contains && Math.min(c.length(), query.length()) >= 0.6 * Math.max(c.length(), query.length())) {
            return 90;
        }
        Set<String> qWords = new HashSet<>(List.of(query.split(" ")));
        Set<String> cWords = new HashSet<>(List.of(c.split(" ")));
        int matched = 0;
        for (String qw : qWords) {
            for (String cw : cWords) {
                if (cw.equals(qw) || cw.contains(qw) || qw.contains(cw)
                        || Levenstein.dist(qw.toCharArray(), cw.toCharArray())
                           <= Math.max(1, Math.min(qw.length(), cw.length()) / 3)) {
                    matched++;
                    break;
                }
            }
        }
        if (matched == qWords.size()) return contains ? 85 : 80; // все слова запроса нашлись
        if (matched * 2 >= qWords.size()) return 60;             // хотя бы половина
        return contains ? 40 : 0;
    }

    private static String spotifySearchJson(String query, String type, int limit) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://api.spotify.com/v1/search?q=" + encodedQuery + "&type=" + type + "&limit=" + limit + "&market=ES";
        String json = SpotifyRequests.requestGet(url);
        if (json == null) return null;
        return json.replace("\\\"", ""); // Осторожно: может повредить JSON
    }


    public static GetLinkResult getLinkArtist(String target) {
        return searchBest("artist", target, q -> {
            SpotifyArtists r = JsonUtils.jsonToPojo(spotifySearchJson(q, "artist", 5), SpotifyArtists.class);
            if (r == null || r.artists == null || r.artists.items == null) return List.of();
            return r.artists.items.stream()
                    .filter(Objects::nonNull)
                    .map(a -> new String[]{a.uri, a.name})
                    .collect(Collectors.toList());
        });
    }

    public static GetLinkResult getLinkTrack(String target) {
        return searchBest("track", target, q -> {
            SpotifySearchTrack r = JsonUtils.jsonToPojo(spotifySearchJson(q, "track", 5), SpotifySearchTrack.class);
            if (r == null || r.tracks == null || r.tracks.items == null) return List.of();
            return r.tracks.items.stream()
                    .filter(Objects::nonNull)
                    .filter(t -> t.artists != null && !t.artists.isEmpty())
                    .map(t -> new String[]{t.uri, t.artists.get(0).name + ", " + t.name})
                    .collect(Collectors.toList());
        });
    }

    public static GetLinkResult getLinkAlbum(String target) {
        return searchBest("album", target, q -> {
            SpotifySearchAlbum r = JsonUtils.jsonToPojo(spotifySearchJson(q, "album", 5), SpotifySearchAlbum.class);
            if (r == null || r.albums == null || r.albums.items == null) return List.of();
            return r.albums.items.stream()
                    .filter(Objects::nonNull)
                    .filter(a -> a.artists != null && !a.artists.isEmpty())
                    .map(a -> new String[]{a.uri, a.artists.get(0).name + ", " + a.name})
                    .collect(Collectors.toList());
        });
    }

    public static GetLinkResult getLinkPlaylist(String target) {
        return searchBest("playlist", target, q -> {
            SpotifySearchPlaylist r = JsonUtils.jsonToPojo(spotifySearchJson(q, "playlist", 5), SpotifySearchPlaylist.class);
            if (r == null || r.playlists == null || r.playlists.items == null) return List.of();
            return r.playlists.items.stream()
                    .filter(Objects::nonNull)
                    .map(p -> new String[]{p.uri, p.name})
                    .collect(Collectors.toList());
        });
    }

    public static CurrentlyPlaying getCurrentlyPlaying() {
        log.info(start);
        String json = SpotifyRequests.requestGet("https://api.spotify.com/v1/me/player/currently-playing");
        log.debug("SPOTIFY RESPONSE PLAYING: " + json);
        if (json == null) return null;
        json = json.replace("\\\"", "");
        CurrentlyPlaying cp = JsonUtils.jsonToPojo(json, CurrentlyPlaying.class);
        currentlyPlaying = cp; // для совместимости
        log.info(finish);
        return cp;
    }

    // Для обратной совместимости
    public static String checkSpotifyIsPlaying() {
        log.info(start);
        log.info("CHECK SPOTIFY IS PLAYING...");
        Spotify.currentlyPlaying = null;
        CurrentlyPlaying cp = getCurrentlyPlaying();
        if (cp == null) return "ERROR";
        log.info("Spotify is playing: {}", cp.is_playing);
        Spotify.currentlyPlaying = cp;
        log.info(finish);
        return JsonUtils.pojoToJson(cp);
    }

    public static String currentlyPlayingDetails() {
        log.info(start);
        CurrentlyPlaying cp = getCurrentlyPlaying();
        if (cp == null || cp.item == null) return "Nothing playing";
        String albumName = cp.item.album.name;
        String artistName = cp.item.artists.get(0).name;
        String artistType = cp.item.artists.get(0).type;
        String playingType = cp.currently_playing_type;
        log.info(finish);
        return albumName + " - " + artistName + " - " + artistType + " - " + playingType;
    }

    // Управление воспроизведением
    public static void pause() {
        log.info("Pausing Spotify");
        SpotifyRequests.requestPut("https://api.spotify.com/v1/me/player/pause");
    }

    public static void play() {
        log.info("Resuming Spotify");
        SpotifyRequests.requestPut("https://api.spotify.com/v1/me/player/play");
    }

    public static void next() {
        log.info("Next track");
        SpotifyRequests.requestPost("https://api.spotify.com/v1/me/player/next");
    }

    public static void prev() {
        log.info("Previous track");
        SpotifyRequests.requestPost("https://api.spotify.com/v1/me/player/previous");
    }

    // Громкость
    public static void volumeUp() {
        volumeSet("+10");
    }

    public static void volumeDn() {
        volumeSet("-10");
    }

    public static void volume(String value) {
        try {
            int volume = Integer.parseInt(value);
            if (volume < 0 || volume > 100) {
                log.warn("Volume must be between 0 and 100");
                return;
            }
            String uri = "https://api.spotify.com/v1/me/player/volume?volume_percent=" + volume;
            SpotifyRequests.requestPut(uri);
        } catch (NumberFormatException e) {
            log.error("Invalid volume value: {}", value);
        }
    }

    public static void volumeSet(String value) {
        if (value == null) return;
        if (value.startsWith("+") || value.startsWith("-")) {
            PlayerState state = requestPlayerState();
            if (state == null || state.device == null) {
                log.warn("Cannot get current volume");
                return;
            }
            int current = state.device.volume_percent;
            try {
                int delta = Integer.parseInt(value);
                int newVolume = current + delta;
                if (newVolume < 1) newVolume = 1;
                if (newVolume > 100) newVolume = 100;
                volume(String.valueOf(newVolume));
            } catch (NumberFormatException e) {
                log.error("Invalid delta: {}", value);
            }
        } else {
            volume(value);
        }
    }

    // Состояние плеера
    public static PlayerState requestPlayerState() {
        String json = SpotifyRequests.requestGet("https://api.spotify.com/v1/me/player/");
        if (json == null) return null;
        json = json.replace("\\\"", "");
        PlayerState state = JsonUtils.jsonToPojo(json, PlayerState.class);
        playerState = state;
        return state;
    }

    public static Boolean requestPlayerStateBool() {
        PlayerState state = requestPlayerState();
        return state != null && state.is_playing;
    }

    // Получение имени по ID (используется в transfer)
    public static String getNameById(String id) {
        if (id == null) return "";
        String name = "";
        if (id.contains("album")) {
            String albumId = id.replace("spotify:album:", "");
            String uri = "https://api.spotify.com/v1/albums/" + albumId + "?fields=name,artists.name";
            String json = SpotifyRequests.requestGet(uri);
            if (json != null) {
                AlbumsArtistTitle album = JsonUtils.jsonToPojo(json, AlbumsArtistTitle.class);
                if (album.artists != null && !album.artists.isEmpty()) {
                    name = album.artists.get(0).name + " - " + album.name;
                }
            }
        } else if (id.contains("artist")) {
            String artistId = id.replace("spotify:artist:", "");
            String uri = "https://api.spotify.com/v1/artists/" + artistId + "?fields=name";
            String json = SpotifyRequests.requestGet(uri);
            if (json != null) {
                name = JsonUtils.jsonGetValue(json, "name");
            }
        } else if (id.contains("playlist")) {
            String playlistId = id.replace("spotify:playlist:", "");
            String uri = "https://api.spotify.com/v1/playlists/" + playlistId + "?fields=name";
            String json = SpotifyRequests.requestGet(uri);
            if (json != null) {
                name = JsonUtils.jsonGetValue(json, "name");
            }
        }
        log.info("Resolved name for {}: {}", id, name);
        return name;
    }


    // Трансфер на Squeezebox
    public static boolean transferSpotifyToLms(Player player) {
        log.info("Starting transfer to player {}", player.name);

        CurrentlyPlaying playing = getCurrentlyPlaying();
        if (playing == null) {
            log.warn("Spotify currently playing is null – nothing is playing or API error. Transfer aborted.");
            return false;
        }
        if (!playing.is_playing) {
            log.info("Spotify is paused/stopped (is_playing=false). Transfer aborted.");
            return false;
        }

        String playingUri;
        int trackIndex;

        if (playing.context == null) {
            playingUri = playing.item.uri;
            trackIndex = 0;
            log.info("Single track: {}", playing.item.name);
        } else {
            playingUri = playing.context.uri;
            trackIndex = playing.item.track_number - 1;
            log.info("Context: {} ({})", playing.context.type, playing.context.uri);
        }

        player
                .ifExpiredAndNotPlayingUnsyncWakeSetVolume(null, false)
                .playPath(playingUri)
                .waitFor(500)
                .pause();

        if (playing.context != null && "playlist".equals(playing.context.type)) {
            player.waitFor(500);
            if (player.playerStatus != null && player.playerStatus.result != null
                    && player.playerStatus.result.playlist_loop != null) {
                String targetTitle = playing.item.name;
                Optional<PlaylistLoop> match = player.playerStatus.result.playlist_loop.stream()
                        .filter(pl -> targetTitle.equals(pl.title))
                        .findFirst();
                if (match.isPresent()) {
                    trackIndex = match.get().playlist_index;
                    log.info("Found track in playlist at index {}", trackIndex);
                }
            }
        }

        player.playTrackNumber(String.valueOf(trackIndex));
        player.syncOtherPlayingNotInGroupToThis();

        Utils.sleep(5);
        pause();

        log.info("Transfer completed");
        return true;
    }

}