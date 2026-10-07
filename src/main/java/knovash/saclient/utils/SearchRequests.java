package knovash.saclient.utils;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * История поисковых запросов навыка «Раз Два, включи …» — файл data/search_requests.txt.
 * На каждый запрос пишется пара строк:
 *   [дата время] ЗАПРОС: <полученный текст команды>
 *   [дата время] РЕЗУЛЬТАТ: <что нашлось / не нашла>
 * Содержимое файла показывает страница /requests (ссылка «Запросы» на главной).
 */
@Log4j2
public class SearchRequests {

    public static final String FILE = "data/search_requests.txt";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    /** Текст полученного запроса («включи …»). */
    public static synchronized void logRequest(String request) {
        append("ЗАПРОС: " + request);
    }

    /** Результат поиска по этому запросу — следующей строкой после ЗАПРОС. */
    public static synchronized void logResult(String result) {
        append("РЕЗУЛЬТАТ: " + result);
    }

    private static void append(String line) {
        try {
            Files.write(Paths.get(FILE),
                    (LocalDateTime.now().format(TIME) + " " + line + System.lineSeparator())
                            .getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // логирование истории не должно ломать саму команду
            log.error("WRITE SEARCH REQUESTS ERROR: " + e.getMessage());
        }
    }
}
