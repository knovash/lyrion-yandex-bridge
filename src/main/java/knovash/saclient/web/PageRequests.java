package knovash.saclient.web;

import lombok.extern.log4j.Log4j2;
import knovash.saclient.Context;
import knovash.saclient.utils.SearchRequests;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Страница /requests — история поисковых запросов навыка «Раз Два, включи …»
 * (содержимое data/search_requests.txt: пары строк ЗАПРОС/РЕЗУЛЬТАТ с временем).
 * Открывается ссылкой «Запросы» с главной страницы в новом окне.
 */
@Log4j2
public class PageRequests {

    public static Context action(Context context) {
        StringBuilder lines = new StringBuilder();
        try {
            java.nio.file.Path path = Paths.get(SearchRequests.FILE);
            if (Files.exists(path)) {
                String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                for (String line : content.split("\n", -1)) {
                    if (line.trim().isEmpty()) continue;
                    lines.append("<p style=\"margin:0\">").append(escape(line)).append("</p>");
                }
            }
        } catch (Exception e) {
            log.error("READ SEARCH REQUESTS ERROR", e);
        }
        String inner = "<fieldset><legend><b>История запросов «включи …»</b></legend>" +
                "<div style=\"font-family:monospace\">" +
                (lines.length() == 0 ? "— пока пусто —" : lines) +
                "</div></fieldset>";
        context.bodyResponse = PageIndex.pageOuter(inner, "Запросы", "Запросы");
        context.code = 200;
        return context;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
