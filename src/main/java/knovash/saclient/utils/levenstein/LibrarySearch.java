package knovash.saclient.utils.levenstein;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import knovash.saclient.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
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
        return findBest(target, candidates, false);
    }

    /**
     * albumsFirst: приоритет album > artist > track (для «найди файл»),
     * иначе artist > album > track (для «найди»).
     * Если полного совпадения нет — fallback: отбрасываем самые короткие слова
     * запроса (запрос может быть «артист + альбом»), пока не найдётся совпадение.
     */
    public static Candidate findBest(String target, List<Candidate> candidates, boolean albumsFirst) {
        String t = Utils.normalizeForSearch(target);
        Pick pick = pickBest(t, candidates, albumsFirst);
        if (pick.candidate != null) return pick.candidate;
        List<String> words = new ArrayList<>(Arrays.asList(t.split(" ")));
        while (words.size() > 1) {
            words.remove(shortestWordIndex(words));
            String reduced = String.join(" ", words);
            Pick reducedPick = pickBest(reduced, candidates, albumsFirst);
            // редуцированный набор принимаем только при почти точном совпадении
            // и достаточной длине (короткий обрывок — источник ложных срабатываний)
            if (reducedPick.candidate != null && reducedPick.score <= 1 && reduced.length() >= 5) return reducedPick.candidate;
        }
        return null;
    }

    private static Pick pickBest(String t, List<Candidate> candidates, boolean albumsFirst) {
        Pick best = new Pick(null, Integer.MAX_VALUE);
        int bestPriority = Integer.MAX_VALUE;
        // короткий запрос (<=5 симв.) — допуск не хуже 1, иначе ложные срабатывания («зюзя»~SZA)
        int shortLimit = t.length() <= 5 ? 1 : Integer.MAX_VALUE;
        for (Candidate c : candidates) {
            if (c == null || c.matchName == null || c.matchName.isEmpty()) continue;
            int score = FavoritesSearch.score(t, Utils.normalizeForSearch(c.matchName));
            if (score < 0 || score > shortLimit) continue;
            int priority = typePriority(c.type, albumsFirst);
            if (score < best.score || (score == best.score && priority < bestPriority)) {
                best = new Pick(c, score);
                bestPriority = priority;
            }
        }
        log.info("LIBRARY SEARCH: '{}' -> {} (score {})",
                t, best.candidate == null ? null : best.candidate.matchName,
                best.score == Integer.MAX_VALUE ? -1 : best.score);
        return best;
    }

    private static int shortestWordIndex(List<String> words) {
        int idx = 0;
        for (int i = 1; i < words.size(); i++) {
            if (words.get(i).length() < words.get(idx).length()) idx = i;
        }
        return idx;
    }

    private static class Pick {
        final Candidate candidate;
        final int score;

        Pick(Candidate candidate, int score) {
            this.candidate = candidate;
            this.score = score;
        }
    }

    private static int typePriority(String type, boolean albumsFirst) {
        if (albumsFirst) {
            if ("album".equals(type)) return 0;
            if ("artist".equals(type)) return 1;
            return 2; // track
        }
        if ("artist".equals(type)) return 0;
        if ("album".equals(type)) return 1;
        return 2; // track
    }
}
