package knovash.saclient.web;

import lombok.extern.log4j.Log4j2;
import knovash.saclient.Context;

/**
 * Страница /local_commands — справочник локальных фраз рутованных станций
 * (whatplays.sh на Яндекс Станции Лайт). Внизу — краткое описание что такое
 * рут станции и его возможности. Открывается ссылкой «Локальные команды root
 * станции» с главной страницы.
 */
@Log4j2
public class PageLocalCommands {

    public static Context action(Context context) {
        String table = "<table border=1 cellpadding=4 cellspacing=0 style=\"border-collapse:collapse\">" +
                "<tr><th>Фраза (сказать Алисе)</th><th>action /cmd</th><th>Что делает</th></tr>" +
                row("«дальше»", "next", "следующий трек плейлиста (если треков ≥ 2) или следующий канал") +
                row("«назад»", "prev", "предыдущий трек/канал") +
                row("«повтор»", "repeat_on / repeat_off", "тумблер повтора плейлиста") +
                row("«рандом»", "shuffle_on / shuffle_off", "тумблер перемешивания") +
                row("«отдельно» / «вместе»", "separate_on / separate_off", "вывести плеер комнаты из группы синхронизации / вернуть") +
                row("«переключи сюда»", "switch_here", "перенести играющую музыку в комнату, где стоит колонка") +
                row("«переключи музыку в/на X»", "switch_room", "перенести музыку в комнату X (X — комната УДЯ или имя плеера, нечётко; «в спальню», «на jbl white»…)") +
                row("«что играет»", "whats_playing_text", "колонка отвечает голосом, что играет в её комнате — логика та же, что у навыка «Раз Два», озвучка как у фраз «спотифай»") +
                row("«спотифай X»", "spotify_artist", "включить исполнителя X (Spotify) на плеере комнаты") +
                row("«спотифай X альбом/трек/плейлист Y»", "spotify_album / spotify_track / spotify_playlist", "поиск альбома/трека/плейлиста") +
                row("«избранное X»", "favorite_name", "закладка Избранного LMS по названию (нечётко)") +
                "</table>";

        String inner =
                "<fieldset><legend><b>Локальные фразы рутованных станций</b></legend>" +
                        "<p>Фразы распознаются на самой колонке (перехват локального IPC распознавания) — " +
                        "срабатывают быстрее ответа облака, работают без подписок. Действуют на плеер комнаты, " +
                        "в которой стоит колонка (как в УДЯ). Исходники: whatplays.sh (cline-репозиторий, docs/lite-scripts).</p>" +
                        table +
                        "</fieldset>" +
                        "<fieldset><legend><b>Что такое рут станции</b></legend>" +
                        "<p><b>Рут станции</b> — полный (root) доступ к Linux-системе Яндекс Станции, полученный " +
                        "без снятия облачной привязки. Облако, УДЯ, сценарии и музыка продолжают работать как раньше.</p>" +
                        "<p><b>Возможности:</b> запуск своих скриптов и сервисов с автозагрузкой (init-скрипты); " +
                        "чтение локального IPC распознавания речи — перехват фраз раньше ответа облака; " +
                        "управление диалогами Алисы (мгновенная отмена «родного» ответа); локальный вывод звука " +
                        "(aplay через ALSA dmix, squeezelite как LMS-плеер колонки); SSH-доступ (dropbear) для " +
                        "обслуживания без ADB.</p>" +
                        "</fieldset>";
        context.bodyResponse = PageIndex.pageOuter(inner, "Локальные команды", "Локальные команды root станции");
        context.code = 200;
        return context;
    }

    private static String row(String phrase, String action, String what) {
        return "<tr><td>" + phrase + "</td><td><code>" + action + "</code></td><td>" + what + "</td></tr>";
    }
}
