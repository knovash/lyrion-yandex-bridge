# BT-пульт на Яндекс Станции Лайт — лабораторный журнал

Версия: 2026-11-17 (после живой сессии на .122)
Станции: .113/.115/.122 (все — Yandex Station Micro v1.2, ядро 4.9.68 aarch64, userland 32-бит)
BT-чип: UNISOC/Spreadtrum UWE5622 (UART /dev/ttyBT0, hciattach_sprd), BT 5.0 dual-mode, BD из OUI B8:87:6E
Сопоставление эпох: .122 = бывший .126, .115 = бывший .120 (по BD-адресам)

## Пульты
| Пульт | MAC | Тип | Статус |
|---|---|---|---|
| G20S PRO | 69:98:98:23:A7:1D | BLE HID (0x1812, vendor 1D5A:C081) | спарен; узел создаётся; отчёты НЕ доходят до input (см. ниже) |
| Xiaomi RC | 44:28:A3:9B:EA:B2 | BLE+BR/EDR (Class 0x0504) | bt-repair на .115; BREDR-ветка не пробована |
| BPR2S | C4:2E:C3:E1:F9:C4 | BLE HID+vendor | спарен ранее на .126 |
| ATV3 | 54:03:84:E2:39:1F | BLE | резерв event3 в btkeys.sh, не тестирован |

## Два BlueZ-стека станции и их возможности (проверено живьём 17.11)
| | Вендор 5.70 (/usr/libexec) | Entware 5.87 (/opt/bin, 64-bit ELF) |
|---|---|---|
| dbus StartDiscovery | МЁРТВ (+28 байт RX/10с) | работает |
| hcitool lescan (вендорский) | работает (436 строк/8с) | — |
| Паринг | — (нет discovery) | pair OK |
| hog attach | НЕ цепляется (initiator false; пинки Disconnect→Connect не помогли) | цепляется: uhid_create, CCC written |
| Пересылка отчётов в uhid | (не дошёл до проверки) | **НЕТ**: btmon видит поток 0x1b, ядро регистрирует input4 «G20S PRO», но event-узел 0 байт |

## Финальный диагноз (17.11)
Ядро полностью исправно: dmesg «input: G20S PRO ... uhid/0005:1D5A:C081.0003/input/input4»,
hid-generic подхватил. Сломано звено bluetoothd(5.87)→/dev/uhid: UHID_CREATE уходит,
UHID_INPUT (отчёты) — нет. Совпадает со старым выводом владельца: «баг HOGP 5.87,
в 5.78 исправлен».

## Грабли дня (все воспроизведены и записаны)
1. Два mgmt-клиента одновременно (btmgmt find при живом discovery bluetoothd) УБИВАЮТ скан.
2. Убийство bluetoothd ВО ВРЕМЯ скана = контроллер BUSY (mgmt 0x0a) для следующего демона.
   Лечение: hciconfig hci0 down/up перед стартом нового демона.
3. dbus-send Connect блокирует до 25с — слепые серии попыток мажут окна рекламы (1–2с).
   Решение: «выстрелил и забыл» (&) или whitelist.
4. Whitelist ядра (btmgmt add-device -a 2 -t 1 <MAC>) ЛОВИТ пульт мгновенно при нажатии
   (directed adv) — connected_callback появляется за секунды.
5. ssh-сессии самоубиваются kill-паттернами: если в cmdline команды есть слово
   «bluetoothd/bluetoothctl/btkeys» — bracket-паттерн (bluetooth[d]) матчит СВОЮ сессию.
6. scp на станциях НЕТ — только cat > файл через ssh.
7. На пульте: паринг-окно = OK+Return (LED мигает); обычные нажатия = directed advertising
   (невидимы сканеру); полный сброс бондов = OK+Return 15с (LED длинная вспышка).

## Хранилища ключей (обнаружено 17.11)
- /var/lib/bluetooth → симлинк /data/opt/bluetooth (персистентное! Entware-демон)
- Вендор-демон: /data/bluez_data/lib/bluetooth (персистентное; найден grep-ом по бинарнику)
- Бонд G20S лежит в обоих. «Storage нет» из старых комментариев НЕ актуально.

## Артефакты на .122 (живые)
- /data/opt/bin/btkeys.sh (listener, фиксирован shebang; ROOM='Гостиная' — ПОПРАВИТЬ на Гараж)
- /data/opt/bin/g20wait.sh (ловит узел → btkeys)
- /data/opt/bin/g20kick.sh, bt-g20s.sh, btg20s.sh, btkeys-xiaomi.sh(.115), bt-repair.sh(.115)
- beep.wav в /data/opt/sounds/

## Следующий шаг (цель: заставить отчёты течь)
1. **Откат Entware bluez до 5.78** (по старому выводу — там HOGP-фикс): проверить наличие
   версии в opkg (`opkg list | grep bluez`), иначе кросс-компиляция aarch64 5.78
   (статика, как alice_ipc_mon) — и повторить «свежая пара → события».
2. Альтернатива: патч hog-lib 5.87 (uhid_notify/UHID_INPUT) — требует toolchain.
3. Обход на сегодня: ноутбук-мост (btkeys-laptop.sh — работает идеально).

## Карта команд (проверенные)
- Чистка+перезапуск Entware: killall → hciconfig down/up → /opt/bin/bluetoothd -n -d -E -p input,hog
- Агент+скан: fifo(tail -f | bluetoothctl --agent NoInputNoOutput) → echo "scan on" > fifo
- Whitelist: /opt/bin/btmgmt add-device -a 2 -t 1 <MAC> (</dev/null, убить через 3с — он интерактивный!)
- Свежая пара: scan on → [CHG] Device MAC RSSI → echo "pair MAC" > fifo → узел в /proc/bus/input/devices
- Проверка отчётов: btmon (HCI) + dd if=/dev/input/eventN bs=1 count=48 (события)
