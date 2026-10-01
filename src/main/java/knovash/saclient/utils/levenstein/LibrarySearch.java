package knovash.saclient.utils.levenstein;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import knovash.saclient.utils.Utils;

import java.util.List;

/**
 * Выбор лучшего кандидата из медиатеки LMS (артист/альбом/трек) по нечёткому
 * совпадению с голосовым запросом. Скоринг — общий с Избранным (FavoritesSearch.score).
 */
@Log4j2
public class LibrarySearch {

    @Data
    public static class Candidate {
        public final String type;      // artist | album | track
        public final String id;        // id в LMS
        public final String name;      // имя для ответа пользователю
        public final String info;      // артист (для альбома/трека), может быть null
        public final String matchName; // имя для сопоставления (обычно "артист название")
    }

    public static Candidate findBest(String target, List<Candidate> candidates) {
        String t = Utils.normalizeForSearch(target);
        Candidate best = null;
        int bestScore = Integer.MAX_VALUE;
        int bestPriority = Integer.MAX_VALUE;
        for (Candidate c : candidates) {
            if (c == null || c.matchName == null || c.matchName.isEmpty()) continue;
            int score = FavoritesSearch.score(t, Utils.normalizeForSearch(c.matchName));
            if (score < 0) continue;
            int priority = typePriority(c.type);
            if (score < bestScore || (score == bestScore && priority < bestPriority)) {
                best = c;
                bestScore = score;
                bestPriority = priority;
            }
        }
        log.info("LIBRARY SEARCH: '{}' -> {} (score {})",
                t, best == null ? null : best.matchName, bestScore == Integer.MAX_VALUE ? -1 : bestScore);
        return best;
    }

    /** При равном счёте предпочитаем артиста (включение целиком), потом альбом, потом трек. */
    private static int typePriority(String type) {
        if ("artist".equals(type)) return 0;
        if ("album".equals(type)) return 1;
        return 2; // track
    }
}
