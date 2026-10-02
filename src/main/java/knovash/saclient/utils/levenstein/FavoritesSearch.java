package knovash.saclient.utils.levenstein;

import lombok.extern.log4j.Log4j2;
import knovash.saclient.utils.Utils;

import java.util.List;

/**
 * Поиск закладки в Избранном LMS по названию из голосовой команды.
 * Запрос может быть на русском («включи избранное саммер джаз»), названия закладок —
 * обычно английские ("Summer Jazz"). Обе стороны прогоняются через одну нормализацию
 * (Utils.normalizeForSearch), дальше — точное/вложенное/нечёткое сравнение.
 */
@Log4j2
public class FavoritesSearch {

    /** Вернуть найденную закладку (исходное имя из списка) или null. */
    public static String find(String target, List<String> favorites) {
        String t = Utils.normalizeForSearch(target);
        if (t.isEmpty() || favorites == null || favorites.isEmpty()) return null;
        String best = null;
        int bestScore = Integer.MAX_VALUE;
        for (String favorite : favorites) {
            if (favorite == null || favorite.trim().isEmpty()) continue;
            String f = Utils.normalizeForSearch(favorite);
            if (f.isEmpty()) continue;
            int score = score(t, f);
            if (score >= 0 && score < bestScore) {
                bestScore = score;
                best = favorite;
            }
        }
        log.info("FAVORITES SEARCH: '{}' -> {} (score {})", t, best, bestScore == Integer.MAX_VALUE ? -1 : bestScore);
        return best;
    }

    /** Оценка совпадения: -1 = не подходит, меньше = лучше. */
    static int score(String t, String f) {
        if (t.equals(f)) return 0;
        if (t.length() >= 3 && f.contains(t)) return 0;
        if (f.length() >= 3 && t.contains(f)) return 0;
        Integer byWords = matchWordsInOrder(t, f);
        int noSpaces = Levenstein.dist(t.replace(" ", "").toCharArray(), f.replace(" ", "").toCharArray());
        if (byWords != null) return Math.min(byWords, noSpaces);
        return noSpaces <= Math.max(2, t.length() / 3) ? noSpaces : -1;
    }

    /**
     * Каждое слово запроса нечётко совпадает со словами закладки, идущими ПО ПОРЯДКУ
     * («драм энд бейс» -> Drum and Bass). null — если выстроить не удалось.
     */
    private static Integer matchWordsInOrder(String t, String f) {
        String[] targetWords = t.split(" ");
        String[] favWords = f.split(" ");
        int from = 0;
        int total = 0;
        for (String w : targetWords) {
            if (w.isEmpty()) continue;
            int bestAt = -1;
            int bestDist = 0;
            for (int j = from; j < favWords.length; j++) {
                int d = distWord(w, favWords[j]);
                if (d <= wordThreshold(w) && (bestAt == -1 || d < bestDist)) {
                    bestAt = j;
                    bestDist = d;
                }
            }
            if (bestAt == -1) return null;
            from = bestAt + 1;
            total += bestDist;
        }
        return total;
    }

    /** Сравнение слова: полное + с префиксом слова закладки («смуз» ~ «smoo|th»). */
    private static int distWord(String w, String favWord) {
        int dFull = Levenstein.dist(w.toCharArray(), favWord.toCharArray());
        if (dFull <= wordThreshold(w)) return dFull;
        String prefix = favWord.substring(0, Math.min(favWord.length(), w.length()));
        return Math.min(dFull, Levenstein.dist(w.toCharArray(), prefix.toCharArray()));
    }

    private static int wordThreshold(String word) {
        // 2-символьные слова — допуск 1 («ап»~up, «май»~my); в составе последовательности
        // слов это безопасно (матчатся ВСЕ слова по порядку);
        // 3+ буквы — допуск max(2, len/2): «дип»≈deep, «бейс»≈bass
        return word.length() <= 2 ? 1 : Math.max(2, word.length() / 2);
    }
}
