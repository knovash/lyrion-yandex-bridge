package knovash.saclient.yandex;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import knovash.saclient.yandex.provider.response.*;
import knovash.saclient.lms.Player;
import knovash.saclient.utils.JsonUtils;
import knovash.saclient.web.SettingsButtons;
import knovash.saclient.yandex.Yandex;
import knovash.saclient.yandex.YandexUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static knovash.saclient.Main.config;
import static knovash.saclient.Main.smartHome;

@Log4j2
@Data
public class SmartHome {

    public List<Device> devices = new ArrayList<>();
    public static String saveToFileJson = "data/devices.json";
    public List<String> devicesButtons = List.of(
            "Переключи сюда",
            "Дальше",
            "Назад",
            "Отдельно",
            "Вместе",
            "Что играет",
            "Где пульт",
            "Подключи пульт"
    );

    public Device deviceById(String deviceId) { // приходит от яндекса EXT ID
//        log.info(">>> DEVICE ID " + deviceId);
        return devices.stream()
                .filter(d -> d.id != null && d.id.equals(deviceId))
                .findFirst()
                .orElse(null);
    }

    public Device deviceByName(String name, String room) { // приходит от яндекса EXT ID
//        log.info(">>> DEVICE ID " + deviceId);
        return devices.stream()
                .filter(d -> room.equals(d.room) && name.equals(d.name))
                .findFirst()
                .orElse(null);
    }

    public static Device deviceByRoom(String room) {
        if (room == null) return null;
        return smartHome.devices.stream()
                .filter(d -> room.equalsIgnoreCase(d.room))
                .findFirst()
                .orElse(null);
    }

    public void create(String deviceByRoomName, YandexUtils.DeviceFromYandex deviceFromYandex) {
        if (devices == null) devices = new ArrayList<>();

        if (deviceFromYandex != null)
        // Случай 1: синхронизация с Яндексом
        {
            // ищем и по id, И по комнате: устройство могло быть создано локально с UUID-id
            // до того, как Яндекс присвоил ему свой external_id — иначе плодились дубли
            Device deviceExists = smartHome.devices.stream()
                    .filter(device -> deviceFromYandex.id.equals(device.id)
                            || ("музыка".equalsIgnoreCase(device.name)
                                && deviceFromYandex.roomName != null
                                && deviceFromYandex.roomName.equalsIgnoreCase(device.room)))
                    .findFirst().orElse(null);
            if (deviceExists != null) {
                // ВАЖНО !!! external_id от Яндекс сохранять в id устройства Музыка !!!
                if (!deviceFromYandex.id.equals(deviceExists.id)) {
                    log.info("UPDATE LOCAL DEVICE ID BY YANDEX: room=" + deviceExists.room
                            + " " + deviceExists.id + " -> " + deviceFromYandex.id);
                    deviceExists.id = deviceFromYandex.id;
                }
                return;
            }
            log.info("CREATE DEVICE FROM YANDEX " + deviceFromYandex.roomName + " id: " + deviceFromYandex.id);
            createNewDeviceMusic(deviceFromYandex.roomName, deviceFromYandex.externalId, deviceFromYandex.name);
        } else
//
        // создание 1. глосом 2. веб. используя имя комнаты. если девай еще небыл создан и его нет в локальных девайсах по имени комнаты тогда создавать ненадо
        {
            if (devices.stream().noneMatch(d -> (deviceByRoomName != null && deviceByRoomName.equalsIgnoreCase(d.room)))) {
                log.info("DEVICE NOT EXISTS. CREATE NEW DEVICE FROM PLAYER ROOM NAME: " + deviceByRoomName);
                createNewDeviceMusic(deviceByRoomName, null, "музыка");

            } else {
                log.info("DEVICE EXISTS. SKIP CREATE BY VOICE OR WEB");
            }
        }
    }

    private Device createNewDeviceMusic(String roomName, String deviceId, String deviceName) {
        if (deviceId == null) deviceId = String.valueOf(UUID.randomUUID());
        Device device = new Device();
        device.room = roomName;
        device.id = deviceId;
        device.name = deviceName;
        // Capability volume
        Capability volume = new Capability();
        volume.type = "devices.capabilities.range";
        volume.retrievable = true;
        volume.reportable = true;
        volume.parameters.instance = "volume";
        volume.parameters.random_access = true;
        volume.parameters.range = new Range();
        volume.parameters.range.min = 1;
        volume.parameters.range.max = 100;
        volume.parameters.range.precision = 1;
        volume.state = new State();
        volume.state.instance = "volume";
        volume.state.value = "0";
        volume.state.action_result = new ActionResult();
        volume.state.action_result.status = "DONE";
        device.capabilities.add(volume);
        // Capability channel
        Capability channel = new Capability();
        channel.type = "devices.capabilities.range";
        channel.retrievable = true;
        channel.reportable = true;
        channel.parameters.instance = "channel";
        channel.parameters.random_access = true;
        channel.parameters.range = new Range();
        channel.parameters.range.min = 1;
        channel.parameters.range.max = 200;
        channel.parameters.range.precision = 1;
        channel.state = new State();
        channel.state.instance = "channel";
        channel.state.value = null;
        channel.state.relative = false;
        channel.state.action_result = new ActionResult();
        channel.state.action_result.status = "DONE";
        device.capabilities.add(channel);
        // Capability on_off
        Capability onOff = new Capability();
        onOff.type = "devices.capabilities.on_off";
        onOff.retrievable = true;
        onOff.reportable = true;
        onOff.parameters.instance = "on";
        // onOff.state = null; // необязательно
        device.capabilities.add(onOff);

        devices.add(device);
        return device;
    }

    public Device createNewDeviceOther(String roomName, String deviceName) {
        Device device = new Device();
        device.room = roomName;
        device.id = String.valueOf(UUID.randomUUID());
        device.name = deviceName;
        device.type = "devices.types.other"; // ← основной тип

        //Базовая способность вкл/выкл (рекомендуется для большинства устройств)
        Capability onOff = new Capability();
        onOff.type = "devices.capabilities.on_off";
        onOff.retrievable = true;
        onOff.reportable = true;
        device.capabilities.add(onOff);

        Capability toggle = new Capability();
        toggle.type = "devices.capabilities.toggle";
        toggle.retrievable = false;
        toggle.reportable = false;
        toggle.parameters = new Parameters();
        toggle.parameters.instance = "pause"; // любое уникальное имя
        toggle.parameters.random_access = false;
        device.capabilities.add(toggle);

        Capability range = new Capability();
        range.type = "devices.capabilities.range";
        range.retrievable = true;   // разрешить запрос состояния
        range.reportable = true;    // разрешить уведомления об изменении
        range.parameters = new Parameters();
        range.parameters.random_access = true;
        range.parameters.range = new Range();
        range.parameters.range.min = 0;        // минимальное значение
        range.parameters.range.max = 100;      // максимальное значение
        range.parameters.range.precision = 1;  // шаг изменения
        // state можно добавить при необходимости, но для описания устройства он необязателен
        range.parameters.instance = "volume"; // допустимое значение из списка функций
        device.capabilities.add(range);

        Capability channel = new Capability();
        channel.type = "devices.capabilities.range";
        channel.retrievable = true;   // разрешить запрос состояния
        channel.reportable = true;    // разрешить уведомления об изменении
        channel.parameters = new Parameters();
        channel.parameters.random_access = true;
        channel.parameters.range = new Range();
        channel.parameters.range.min = 0;        // минимальное значение
        channel.parameters.range.max = 8;      // максимальное значение
        channel.parameters.range.precision = 1;  // шаг изменения
        // state можно добавить при необходимости, но для описания устройства он необязателен
        channel.parameters.instance = "channel"; // допустимое значение из списка функций
        device.capabilities.add(channel);

        Capability mode = new Capability();
        mode.type = "devices.capabilities.mode";
        mode.retrievable = false;   // чтобы можно было запросить текущий режим
        mode.reportable = false;
        mode.parameters = new Parameters();
        mode.parameters.instance = "program"; // уникальное имя для этой функции
        List<Mode> modes = new ArrayList<>();
        modes.add(new Mode("auto"));
        modes.add(new Mode("eco"));
        mode.parameters.modes = modes;
        mode.state = new State();
        mode.state.instance = "program";
        mode.state.value = "auto"; // или null, если ничего не выбрано
        device.capabilities.add(mode);

        //Capability button = new Capability();
        //button.type = "devices.capabilities.custom.button";
        //button.retrievable = false;
        //button.reportable = false;
        //button.parameters = new Parameters();
        //button.parameters.instance = "power";       // уникальный ID (например, "power")
        //button.state = new State();
        //button.state.instance = "power";
        //button.state.value = "true"; // будет отправлено при нажатии
        //device.capabilities.add(button);

        log.info("OTHER DEVICE CREATED OK");
        devices.add(device);
        return device;
    }

    public void createButtonsInAllRooms(List<String> roomsWithSpeaker) {
        log.info("ROOMS: " + Yandex.rooms);
        if (roomsWithSpeaker == null || roomsWithSpeaker.isEmpty()) {
            roomsWithSpeaker = Yandex.getRoomsWithAliceOrMusic();
        }
        log.info("ROOMS WITH SPEAKER: " + roomsWithSpeaker);
        // создавать кнопки только разрешенные в настройках класс SettingsButtons
        List<String> enabledButtonDescriptions = Arrays.stream(SettingsButtons.ToggleButton.values())
                .filter(button -> button.getValue())
                .map(button -> button.getDescription())
                .collect(Collectors.toList());
        roomsWithSpeaker.stream().forEach(room -> enabledButtonDescriptions.forEach(button -> createDeviceToggle(room, button)));
    }

    public Device createDeviceToggle(String roomName, String deviceName) {
        // Проверяем, существует ли уже устройство с таким именем в данной комнате
        Device existing = devices.stream()
                .filter(d -> deviceName.equals(d.name) && roomName.equals(d.room))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            log.info("TOGGLE DEVICE ALREADY EXISTS " + deviceName);
            return existing;
        }
        log.info("TOGGLE DEVICE NOT EXISTS - CREATE " + deviceName);
        // Если нет – создаём новое с новым UUID
        Device device = new Device();
        device.room = roomName;
        device.id = String.valueOf(UUID.randomUUID());
        device.name = deviceName;
        device.type = "devices.types.other";
        // Capability on_off
        Capability onOff = new Capability();
        onOff.type = "devices.capabilities.on_off";
        onOff.retrievable = true;
        onOff.reportable = true;
        Parameters params = new Parameters();
        params.instance = "on";
        onOff.parameters = params;
        device.capabilities.add(onOff);
        log.info("NEW TOGGLE DEVICE CREATED: {} in room {} with id {}", deviceName, roomName, device.id);
        devices.add(device);
        return device;
    }

    public Device createDeviceToggle(YandexUtils.DeviceFromYandex deviceFromYandex) {
        // Проверяем, существует ли уже устройство с таким externalId
        Device existing = devices.stream()
                .filter(d -> deviceFromYandex.externalId.equals(d.id)) // или d.external_id, смотря где храните
                .findFirst()
                .orElse(null);
        if (existing != null) {
            log.info("TOGGLE DEVICE ALREADY EXISTS: {} in room {} with id {}", deviceFromYandex.name, deviceFromYandex.roomName, deviceFromYandex.externalId);
            return existing;
        }
        // Если нет – создаём новое
        String room = deviceFromYandex.roomName;
        Device device = new Device();
        device.room = room;
        device.id = deviceFromYandex.externalId;
        device.name = deviceFromYandex.name;
        device.type = "devices.types.other";
        Capability onOff = new Capability();
        onOff.type = "devices.capabilities.on_off";
        onOff.retrievable = true;
        onOff.reportable = true;
        Parameters params = new Parameters();
        params.instance = "on";
        onOff.state = new State();
        onOff.state.value = "false";
        onOff.state.relative = false;
        onOff.parameters = params;
        device.capabilities.add(onOff);
        log.info("NEW TOGGLE DEVICE CREATED: {} in room {} with id {}", deviceFromYandex.name, room, deviceFromYandex.externalId);
        devices.add(device);
        return device;
    }

    /**
     * Убрать дубликаты устройств "музыка" по комнате (историческая порча данных:
     * локальный UUID-id не совпадал с яндексовским — каждый цикл синхронизации
     * добавлял ещё одну копию). Приоритет — устройство, чей id есть в Яндексе.
     */
    public void dedupeMusicDevicesByRoom(Set<String> yandexIds) {
        List<Device> result = new ArrayList<>();
        Map<String, Device> musicByRoom = new LinkedHashMap<>();
        for (Device d : devices) {
            if (d == null || !"музыка".equalsIgnoreCase(d.name)) {
                if (d != null) result.add(d);
                continue;
            }
            Device cur = musicByRoom.get(d.room);
            if (cur == null || (yandexIds.contains(d.id) && !yandexIds.contains(cur.id))) {
                if (cur != null) log.info("DEDUPE MUSIC: drop duplicate room=" + cur.room + " id=" + cur.id);
                musicByRoom.put(d.room, d);
            } else {
                log.info("DEDUPE MUSIC: drop duplicate room=" + d.room + " id=" + d.id);
            }
        }
        result.addAll(musicByRoom.values());
        devices = result;
    }

    /** Удалить устройство "музыка" комнаты (комната осталась без плееров). */
    public void removeMusicDeviceByRoom(String roomName) {
        int before = devices.size();
        devices.removeIf(d -> d != null
                && "музыка".equalsIgnoreCase(d.name)
                && roomName != null && roomName.equals(d.room));
        log.info("REMOVE MUSIC DEVICE room=" + roomName + " (removed " + (before - devices.size()) + ")");
    }

    /**
     * Удалить устройства "музыка" комнат, в которых нет ни одного плеера
     * (инвариант: устройство существует ⇔ в комнате есть плеер).
     */
    public void removeMusicDevicesWithoutPlayers(List<Player> players) {
        Set<String> roomsWithPlayers = players == null ? Set.of()
                : players.stream()
                        .filter(Objects::nonNull)
                        .map(p -> p.room)
                        .filter(r -> r != null && !r.isEmpty())
                        .collect(Collectors.toSet());
        int before = devices.size();
        devices.removeIf(d -> d != null
                && "музыка".equalsIgnoreCase(d.name)
                && !roomsWithPlayers.contains(d.room));
        if (before != devices.size()) {
            log.info("RECONCILE MUSIC DEVICES: removed " + (before - devices.size())
                    + " (rooms with players: " + roomsWithPlayers + ")");
        }
    }

    public void write() {
        log.info("WRITE: " + config.fileDevices);
        JsonUtils.pojoToJsonFile(this, config.fileDevices);
    }

    /**
     * Восстановить локальные устройства из data/devices.json. Без этого список устройств
     * жил только в памяти процесса и после рестарта клиента восстанавливался исключительно
     * из Яндекса — при недоступном Яндексе устройства терялись.
     */
    public void read() {
        SmartHome loaded = JsonUtils.jsonFileToPojo(config.fileDevices, SmartHome.class);
        if (loaded != null && loaded.devices != null && !loaded.devices.isEmpty()) {
            this.devices = loaded.devices;
            log.info("LOCAL DEVICES LOADED: " + devices.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(d -> d.room)
                    .collect(Collectors.toList()));
        } else {
            log.info("NO LOCAL DEVICES IN " + config.fileDevices);
        }
    }

}