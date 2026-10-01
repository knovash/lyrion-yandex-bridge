package knovash.saclient.voice;

import lombok.extern.log4j.Log4j2;
import knovash.saclient.Context;
import knovash.saclient.Main;
import knovash.saclient.lms.Player;
import knovash.saclient.yandex.SmartHome;
import knovash.saclient.player.ActionsAsync;
import knovash.saclient.yandex.provider.response.Device;
import knovash.saclient.utils.JsonUtils;

import static knovash.saclient.Main.*;

@Log4j2
public class HandleVoiceAlice {

    // Метод должен вернуть ТЕКСТ для ответа Алисы и выполнить действие
    public static Context processContext(Context context) {
        log.info(start);
        String body = context.body;
        context.code = 200;
        String command = JsonUtils.jsonGetValue(body, "command");

        if (command == null) {
            context.bodyResponse = "я не поняла команду";
            return context;
        }

        String aliceId = JsonUtils.jsonGetValue(body, "application_id");
        String room = Main.roomsAndAliceIds.get(aliceId);
        log.info("ROOM BY aliceId: " + room);

        String answer = processCommand(aliceId, room, command);

        log.info("ANSWER: {}", answer);
        context.bodyResponse = answer;
        log.info(finish);
        return context;
    }

    public static String processCommand(String aliceId, String room, String command) {
        log.info(start);

        command = command.trim().toLowerCase();
        log.info("COMMAND: " + command + " ROOM: " + room + " AliceID: " + aliceId);

        // Базовые команды без зависимостей
        if (command.isEmpty())
            return "Я умею управлять плеерами подключенными в Lyrion Music Server";
        if (command.contains("помощь") || command.contains("помоги") || command.contains("подскажи"))
            return "У вас локально должен быть установлен Lyrion Music Server и приложение навыка";
        if (command.contains("что ты умеешь") || command.contains("что ты можешь"))
            return "Я умею управлять плеерами подключенными в Lyrion Music Server";

        // Команды привязки комнаты (не требуют наличия комнаты в idRooms)
        if (command.startsWith("это комната")) {
            if (command.contains("с колонкой")) {
                return ActionsAsync.selectRoomWithSpeaker(command, aliceId);
            } else {
                return ActionsAsync.selectRoomByCommand(command, aliceId);
            }
        }

        // Получаем комнату по идентификатору сессии
        lmsPlayers.updatePlayers();
        if (room == null) return "скажите навыку, это комната и название комнаты";

        // Команды выбора колонки (требуют комнату, но не плеер)
        if (command.matches("(выбери||включи) колонку.*"))
            return ActionsAsync.selectNewPlayerInRoomByCommand(command, room, true);

        // Ищем устройство в комнате и соответствующий плеер
        Device device = SmartHome.deviceByRoom(room);
        if (device == null)
            return "скажите навыку, выбери колонку, и название колонки";

        Player player = lmsPlayers.playerByRoom(device.room);
        log.info("PLAYER: " + player.name);
        if (player == null)
            return "колонка в комнате не выбрана, скажите навыку, выбери колонку, и название колонки";

        // Обработка всех команд, требующих плеер
        try {
            if (command.contains("что играет"))
                return ActionsAsync.whatsPlayingAnswerForAlice(player, true);
            if (command.contains("лимит"))
                return ActionsAsync.volumeLimitSet(player, command);
            if (command.contains("какая громкость"))
                return ActionsAsync.whatsVolume(player);
            if (command.contains("где пульт"))
                return "пульт подключен к " + lmsPlayers.btPlayerName;
            if (command.startsWith("включи избранное") || command.startsWith("включи канал"))
                return ActionsAsync.channelPlayByName(player, command);
            if (command.startsWith("включи альбом"))
                return ActionsAsync.spotifyPlayCommand(player, command, "album");
            if (command.startsWith("включи трек"))
                return ActionsAsync.spotifyPlayCommand(player, command, "track");
            if (command.startsWith("включи плейлист"))
                return ActionsAsync.spotifyPlayCommand(player, command, "playlist");
            if (command.startsWith("включи"))
                return ActionsAsync.spotifyPlayCommand(player, command, "artist");
        } catch (Exception e) {
            log.error("Ошибка при обработке команды '{}': {}", command, e.getMessage(), e);
            return "Произошла внутренняя ошибка, попробуйте позже";
        }

        return "Я не поняла команду";
    }
}