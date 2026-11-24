# lite-bt.md — Живой журнал: Bluetooth-пульт на Яндекс Станции Лайт

> **СТАТУС: РЕШЕНО 17.11.2026.** Пульты (ATV3, G20S PRO) работают с Лайтом напрямую:
> бип при нажатиях + управление LMS через sa_client /cmd.

## РЕШЕНИЕ (коротко)
Вендорское ядро 4.9 доставляет input-события evdev **только клиентам с EVIOCGRAB**
(патч Яндекса; maind хватает родные узлы grab'ом, всем остальным — тишина).
BlueZ и пульты были невиновны: hog исправно создаёт uhid-устройство и получает отчёты,
но экспортёры событий без grab их не видят.
**Рабочий стек:** Entware bluetoothd 5.87 (`-E -p input,hog`) → узел /dev/input/eventN →
**btrcv** (наш статик aarch64: находит узел по Uniq, EVIOCGRAB, парсит input_event) →
**btkeys-grab.sh** (маппинг кодов → HTTP /cmd sa_client + бип aplay beep.wav).
Скрипты: `/data/opt/bin/{btrcv, btkeys-grab.sh, bt-connect.sh, bt-listeners-start.sh}`;
исходники: `~/IdeaProjects/cline/bt-lite/` (btrcv.c, btkeys-grab.sh, bt-connect.sh).

## Как пришли к решению (ключевые повороты)
1. Матрица возможностей двух стеков: вендор 5.70 (нет discovery, hog не аттачится) vs
   Entware 5.87 (всё есть, но события не текут) → подозрение на «баг 5.87» из старых заметок.
2. Живые паринги G20S и ATV3: узлы создаются, btmon видит поток нотификаций, events = 0.
3. (толчок Алексея) сняли с ноутбука дескриптор + live-events G20S → нашли его брак
   (3 байта вместо 4 в consumer-репорте) → гипотеза «кривой пульт».
4. Контрольный эксперимент с эталонным ATV3 — ТА ЖЕ тишина → гипотеза убита: баг общий.
5. opkg 5.78 нет → маячила кросс-компиляция (часы, исход неясен).
6. **ПОВОРОТ: вместо слепой сборки — прямой зонд ядра.** Написали uhidtest (статик aarch64):
   ядро принимает UHID_CREATE2+INPUT2, хендшейк START/OPEN полный — и 0 событий →
   ядро виновато, BlueZ оправдан (сборка 5.78 была бы потерей времени).
7. uinput-тест — тоже 0; родные узлы держит maind → гипотеза «доставка только grab-клиентам».
8. Тест с EVIOCGRAB → **288 байт, РАБОТАЕТ** → секрет вендор-ядра раскрыт.
9. btrcv + btkeys-grab → бип и команды с пульта. Победа.

Роль Алексея: катализатор диагностики (его запрос профиля заставил снять дескриптор и
live-events — это дало полную карту кнопок, использованную в финальном маппинге, и убило
версию «пульт виноват» контрольным экспериментом). Но само решение (зонд ядра → EVIOCGRAB)
найдено независимым путём; совет «откатиться на 5.78» оказался бы тупиком.

## Эксплуатация
- Подключение пульта: `sh /data/opt/bin/bt-connect.sh [MAC] [room]`.
  **Без MAC — АВТООПРЕДЕЛЕНИЕ (30с, удлинить: `BT_WAIT=120 bt-connect.sh`):** ищет
  HID-UUID 0x1812 — какой пульт сейчас
  в паринге (мигает), тот и подключится; уже спаренный — сразу connect без pair.
  Отсчёт каждые 5с; Ctrl+C гасит скан.
  автоматически (стек→скан→pair→trust→connect→узел→бонд→слушатель). Комбо паринга пультов —
  в шапке скрипта. MAC известных пультов — там же.
- После ребута станции: стек и слушатели поднимаются САМИ (S99btkeys в /etc/init.d,
  задержка ~25с — ждём вендорский стек). Вручную: `sh /data/opt/bin/bt-listeners-start.sh`.
  Бонды персистентны — парить заново не надо, пульт подключится по нажатию кнопки.
- Смена bridge-сервера: строка `server <ip:8888>` в /data/opt/bt.conf (дефолт зашит в
  btkeys-grab.sh) + перезапуск слушателей (bt-listeners-start.sh). Ошибки /cmd — в
  /tmp/btkeys.log («✗ … подряд: N»), после 3 подряд — двойной бип; heartbeat
  слушателей — /tmp/bt.status.<uniq>.
- Слушатели не мешают друг другу: по одному на Uniq; узел исчезает при disconnect пульта —
  btrcv сам ждёт переподключения.
- Комната/плеер: второй аргумент btkeys-grab.sh (сейчас «Гараж» → плеер JBL white).


**Задача:** подключить BT-пульт напрямую к рутованной Станции Лайт; кнопки пульта →
input-события → btkeys.sh → HTTP `/cmd` клиента sa_client (управление музыкой LMS) +
звуковой бип из колонки при нажатиях.

**Железо:** все три Лайта (.113/.115/.122) одинаковые — Yandex Station Micro v1.2,
ядро 4.9.68 aarch64 (userland 32-бит), BT-чип UNISOC/Spreadtrum (UART `/dev/ttyBT0`,
`hciattach_sprd`), BT 5.0 dual-mode. BD-адреса из OUI `B8:87:6E` (= wlan MAC станции).
Эпохи IP: `.122` = бывший `.126`, `.115` = бывший `.120`.

---

## Пульты

| Пульт | MAC | Тип | Где пробовался | Текущий статус |
|---|---|---|---|---|
| **G20S PRO** | `69:98:98:23:A7:1D` | BLE HID (0x1812, vendor 1D5A:C081) | .126→.122 | основной; спарен (бонд персистентен), узел создаётся, ядро видит; **отчёты не текут** (диагноз ниже) |
| **Xiaomi RC** | `44:28:A3:9B:EA:B2` | BLE + BR/EDR dual (Class 0x0504) | .120→.115 | bt-repair.sh + btkeys-xiaomi.sh; BREDR-ветка (`bearer … bredr`) так и не попробована |
| **BPR2S** | `C4:2E:C3:E1:F9:C4` | BLE HID + vendor | .126 | спаривался (кэш в storage); полный прогон не делался |
| **ATV Remote r3** | `54:03:84:E2:39:1F` | BLE HOG | резерв | прописан event3 в btkeys.sh; не тестировался |
| MiBox ×2 | — | BLE HID | — | не трогали (аналог Xiaomi RC) |

## Программные варианты (все испробовано)

### Вариант A: вендорский BlueZ 5.70 (`/usr/libexec/bluetooth/bluetoothd`)
- Запуск: `-E` / `-n -d -E -p input`; bluetoothctl этой сборки ВИСНЕТ → всё через `dbus-send`.
- **Скан через dbus StartDiscovery МЁРТВ** (+28 байт RX за 10с — реклама не принимается).
- **НО** вендорский `hcitool lescan` РАБОТАЕТ (436 строк за 8с) — сырой скан у чипа живой.
- **hog-драйвер не аттачится**: при подключении GATT-сервисы разбираются
  (`Primary services found: 7`), но строк hog нет; `ConnectProfile(0x1812)` →
  `br-connection-profile-unavailable`; пинки `Disconnect`→`Connect` (initiator) не помогли.
- Вывод: парить нечем (нет discovery), форвардить нечем (hog не цепляется).

### Вариант B: Entware BlueZ 5.87 (`/opt/bin/bluetoothd`, ELF 64-bit aarch64)
- Установлен `opkg install bluez-daemon bluez-libs bluez-utils` (+ dbus, glib2; python3-dbus НЕТ).
- Конфиги: `/opt/etc/bluetooth/main.conf` (ControllerMode=dual — критично!, Experimental=true,
  KernelExperimental=true, Privacy=device, TemporaryTimeout=0, JustWorksRepairing=always),
  `input.conf` (UserspaceHID=true/false — без разницы, LEAutoSecurity=true, ClassicBondedOnly=false).
- D-Bus: machine-id в /opt/var/lib/dbus, симлинк сокета /var/run/dbus → /opt/var/run/dbus,
  политика в /etc/dbus-1/system.d, активация org.bluez.service → /opt/bin/bluetoothd.
- **Скан работает** (54+ устройств), **паринг работает** (агент `bluetoothctl --agent
  NoInputNoOutput`; bt-agent ненадёжен — требует yes в stdin), hog цепляется:
  `uhid_create() HoG created uHID device`, `report_ccc_written_cb() notifications enabled`.
- **НО пересылка отчётов в uhid НЕ работает**: btmon видит поток `ATT Handle Value
  Notification (0x1b)` (Handle 0x0030/0x003c = consumer/keyboard reports) при каждом
  нажатии, а `/dev/input/eventN` отдаёт 0 байт. И после свежей пары, и после реконнекта.
  `chmod 0666 /dev/uhid` не помогает. Совпадает со старым выводом: **баг HOGP 5.87, фикс в 5.78**.

### Вариант C: гибрид/обходы
- **Whitelist ядра**: `/opt/bin/btmgmt add-device -a 2 -t 1 <MAC>` (⚠ btmgmt интерактивный —
  с `</dev/null`, убивать через ~3с). Ядро само ловит пульт при нажатии (directed
  advertising) — `connected_callback` за секунды. Работает!
- Кикеры (g20kick/g20attach): Connect / Disconnect→Connect при живом линке — hog вендора не будят.
- **Ноутбук-мост**: btkeys-laptop.sh — РАБОТАЕТ ИДЕАЛЬНО (пульт → ноут → SSH → колонка).

## Диагноз (текущий, 17.11)

Цепочка: пульт → HCI → bluetoothd → **uhid** → hid-generic → /dev/input/eventN → btkeys → бип.
Сломано ровно одно звено в варианте B: **bluetoothd 5.87 не пишет UHID_INPUT** (создание
устройства есть, отчётов нет). Ядро исправно — доказательство в dmesg:
`input: G20S PRO as /devices/virtual/misc/uhid/0005:1D5A:C081.0003/input/input4` +
`hid-generic ... BLUETOOTH HID v0.00 Keyboard [G20S PRO]`.
В варианте A сломано раньше (hog не аттачится + discovery мёртв).

## Хранилища ключей (важно!)
- `/var/lib/bluetooth` → симлинк `/data/opt/bluetooth` (персистентное; пишет Entware 5.87).
- Вендор-демон: `/data/bluez_data/lib/bluetooth` (персистентное; найден grep-ом по бинарнику).
- Бонды КОПИРУЮТСЯ между ними при переключении демона. «Storage нет» из старых комментариев
  скриптов устарело — ключи переживают ребут.

## Каталог ошибок → решений (накопленный)

| Ошибка/симптом | Причина | Решение |
|---|---|---|
| System does not support input plugin | ControllerMode=le/bredr | ControllerMode=dual |
| AuthenticationFailed при pair | bt-agent требует yes в stdin | `bluetoothctl --agent NoInputNoOutput` |
| le-connection-abort-by-local | пульт спит/не в паринге | разбудить кнопкой + connect сразу |
| Device not available | scan off удалил LE из кэша | не делать scan off до pair; TemporaryTimeout=0 |
| AlreadyExists при pair | старый LTK | remove MAC, rm -rf storage/<MAC>, перезагрузка |
| NoReply (dbus) | таймаут 25с у dbus-send | async-выстрелы (&); серия с retry |
| Input/output error при Connect | пульт спит | кнопка + connect |
| hcitool lescan «Set scan parameters failed» | Entware hcitool + BT 5.0 | вендорский hcitool работает; btmgmt find |
| bluetoothctl виснет | вендор-сборка | dbus-send / Entware bluetoothctl |
| Скан демона умер после рестартов | убийство bluetoothd во время скана → BUSY (0x0a) | `hciconfig hci0 down/up` перед новым демоном |
| Скан замолкает при диагностике | два mgmt-клиента (btmgmt при живом discovery) | не смешивать btmgmt и discovery |
| Слепые Connect-серии мажут | реклама пульта окнами 1–2с | whitelist ядра (ловит мгновенно) |
| Пара есть, узла нет | вендор hog не аттачится | свежая пара под Entware; вендор — не лечится пока |
| Узел есть, событий нет (0 байт) | 5.87 не пишет UHID_INPUT | **откат на 5.78 — главный кандидат** |
| Пульт не виден в скане при нажатиях | directed advertising (только хосту) | whitelist; в паринг-окне реклама общая |
| LED мигает, пара не ловится | короткое окно паринга | OK+Return 3–5с + жать кнопки 20–30с |
| ssh-сессия умирает в kill-циклах | паттерн матчит собственный cmdline | bracket-трюк; killall по имени; без слов в команде |
| scp на станцию | нет scp | только `cat > файл` через ssh-пайп |
| btkeys.sh не стартовал | битая первая строка «жпун#!/bin/sh» | `sed -i '1s/^жпун//'` (исправлено 17.11) |

## Комбо-кнопки пульта G20S PRO
- Паринг: `OK`+`Return` 3–5с (LED мигает) → реклама общая, имя G20S PRO.
- Полный сброс бондов: те же кнопки 15с (LED длинная вспышка/погас).
- Обычные нажатия: directed advertising — видит только whitelist ядра.

## Скрипты-артефакты
- `/data/opt/bin/btkeys.sh` (.122) — слушатель eventN → /cmd + бип (beep.wav в /data/opt/sounds/).
  ⚠ ROOM='Гостиная' — поправить под комнату станции. Карта кнопок: ◀▶=prev/next, OK=play/pause,
  VOL±, цифры=избранное, ▲▼=избранное∓1, BACK=повтор, DEL=рандом, HOME=switch_here, SEARCH=whats_playing.
- `/data/opt/bin/bt-repair.sh`, `btkeys-xiaomi.sh` (.115) — ветка Xiaomi RC (Entware hog).
- `/data/opt/bin/bt-g20s.sh`, `btg20s.sh` (.122) — исторические (dbus-send на вендоре).
- `/data/opt/bin/g20wait.sh` (.122) — вечный ловец узла → btkeys.
- `/tmp/g20*` (.122) — одноразовые орудия 17.11 (очистка/скан/пара/whitelist/аттачер; /tmp — до ребута).
- `S99btkeys` в init.d — ОТКЛЮЧЁН (.disabled).
- Ноутбук: btkeys-laptop.sh (squeeze-alice/squeeze_remote) — рабочий мост.

## Хронология
- **до 10.11** (документ владельца, станции .126/.120): апгрейд вендор 5.70 → Entware 5.87,
  настройка D-Bus/main.conf/input.conf, паринги G20S/BPR2S/Xiaomi, eventN не создавался,
  вердикт «баг 5.87, фикс 5.78», ноутбук-мост работает.
- **10–11.11**: G20S PRO на .122 (вендор+dbus) — event2 + btkeys + БИП (успех); Xiaomi RC на
  .115 (Entware+bt-repair); симлинк хранилища на /data.
- **17.11 (сессия Cline)**: карта возможностей снята живьём (варианты A/B/C), свежая пара G20S
  на .122 (бонд в обоих хранилищах), whitelist-автоконнект, доказано: ядро OK (dmesg input4),
  вендор hog не аттачится, 5.87 не пишет UHID_INPUT. Станция .122 возвращена в штат
  (вендор bluetoothd -E -d + bluealsa). Детали марафона — банк activeContext 17.11.

- **17.11 (вечер)**: запрос помощи на форуме (Алексей Diskless; идея ESP32-клиента отклонена —
  цель = Лайт как хаб). Для него подготовлен пакет /home/konstantin/IdeaProjects/cline/bt-lite/
  (README + 01-проблема + 02-система + 03-HID-профиль с маппингом репортов + 04-образцы отчётов
  + 05-доказательства + 06-инструкция снятия rdesc). Ждём Report Descriptor с ноутбука
  (/sys/kernel/debug/hid/0005:1D5A:C081.*/rdesc) → файл 07, затем переслать пакет Алексею.

- **17.11 (позднее)**: в пакет bt-lite добавлен blercatv.cpp — ESP32-эмулятор ATV-пульта
  (форк ESP32-BLE-Keyboard) с обкатанным HID-дескриптором ATV-профиля (consumer id1 с
  numpad-null + мышь id3 + vendor 0xFF01 голос 20б id2/out id4 + клавиатура id7 c LED id8).
  Разбор в bt-lite/09-blercatv-analysis.md. Это ЭТАЛОН для диффа с дескриптором G20S
  (07-g20s-rdesc.txt) в анализаторе Алексея. Гипотеза-кандидат №2: парсер hid ядра 4.9
  спотыкается о ATV-конструкции G20S (numpad-null/vendor/2-байтный LogMax) → input есть,
  маппинг репортов пуст → событий нет. Отличается от гипотезы №1 (5.87 не пишет UHID_INPUT)
  тем, что предсказывает: на ядре 4.98+ всё бы заработало.

- **17.11 (22:44, ответ Алексея)**: подтвердил методику — HID-дескриптор подключённого пульта
  лежит в /sys (kernel debugfs), прислал разбор эталона = дескриптор из blercatv.cpp
  (идентичен нашему 09-blercatv-analysis.md: media id1 с numpad-null + мышь id3 +
  vendor 0xFF01 id2(20б)/id4(out) + клавиатура id7 + LED id8). Ключевое отличие эталона:
  consumer у него — ARRAY (UsageMin 0..Max 0x29c, 2 поля по 16 бит), не битовое поле.
  ЖДЁМ от владельца: rdesc РЕАЛЬНОГО G20S с ноутбука (0005:1D5A:C081.*, команды в
  bt-lite/06) → файл 07 → Алексею на дифф с эталоном.

- **17.11 (вечер, ноутбук)**: G20S PRO спарен с ноутбуком (свежая пара, Connected), снят
  и декодирован его Report Descriptor (bt-lite/07-g20s-rdesc.txt + 10-descriptor-decoded.md):
  id1=клавиатура 8Б+LED-out 1Б (LogMax 0xFF), id3=consumer ARRAY 2×16 UsageMax 0x29C,
  id2=мышь с wheel (LogMin 0x81!), id1E=vendor 20Б+out, id5=SystemControl(pwr/sleep).
  ⚠ Найдено расхождение: пульт шлёт id3 ТРИ байта при объявленных ЧЕТЫРЁХ (захват «41 00 00»).
  Профиль — ATV-класс, почти клон blercatv-эталона (UsageMax 0x29C совпадает). Пакет bt-lite
  готов к отправке Алексею. Станция .122: BT выключен на время ноутбука (hciconfig down) — восстановить!

- **17.11 (вечер++, события ноутбука)**: сняты живые events (08) + анализ (11). ГЛАВНАЯ
  НАХОДКА: пульт шлёт consumer id3 ТРИ байта при объявленных ЧЕТЫРЁХ (фактически один
  usage16+паддинг) — ноут (ядро 6.x) прощает, стек Лайта (4.9 + bluez) вероятно отбрасывает
  репорт по размеру → событий нет. Это главный кандидат-причина. Полная карта кнопок:
  клавиатура ◀▶▲▼=0x50/4F/52/51, PageUp/Dn=4B/4E, цифры 1-9,0=0x1E-0x27; consumer:
  OK=0x41, VOL±=E9/EA, Next/Prev=B6/B5, Back=0x224, Home=0x223. Пакет bt-lite (12 файлов)
  ГОТОВ — отправить Алексею.

- **17.11 (вечер+++, ЭКСПЕРИМЕНТ ATV3 на .122)**: ATV3 (после сброса его памяти долгим
  зажатием Back+Home 10-12с; обычные нажатия = directed adv, станция не видит; также
  выяснено: станция не слышит пульт из-за ДИСТАНЦИИ — ноут −51, станция 0, помог подход
  к станции) СПАРЕН со станцией, узел создан («RemoterATV3» event2). РЕЗУЛЬТАТ: btmon
  51 нотификация при нажатиях, event2 = 0 байт — **ТО ЖЕ САМОЕ, что с G20S**. ВЫВОД:
  баг пересылки UHID_INPUT ГЕНЕРИЧЕСКИЙ для Entware 5.87 на этом ядре/чипе, «кривые
  репорты G20S» — не единственная причина. opkg: только 5.87, 5.78 НЕТ → путь =
  кросс-компиляция статик 5.78 aarch64. Оба бонда (G20S + ATV3) сохранены в ОБОИХ
  персистентных хранилищах станции — при появлении рабочего демона оба пульта подключатся
  сразу. Станция возвращена в штат (вендор-демон). Ноут: бонды обоих пультов удалены
  (G20S — remove, ATV3 — сбросом пульта).

- **17.11 (19:10, 🏆 ФИНАЛЬНАЯ ПОБЕДА — ПУЛЬТ РАБОТАЕТ НА ЛАЙТЕ!)**: разгадано ГЛАВНОЕ ЯДРО
  ТАЙНЫ: вендорское ядро 4.9 доставляет input-события evdev ТОЛЬКО клиентам, сделавшим
  EVIOCGRAB (доказано тестами: uhid без grab = 0 байт при полном хендшейке START/OPEN;
  uinput без grab = 0; с grab = 288 байт!). Именно поэтому maind работает, а btkeys/dd — нет.
  BlueZ всех версий был НЕВИНОВЕН (не делает grab — на нормальных ядрах и не надо).
  РЕШЕНИЕ: btrcv (наш статик aarch64, ~/IdeaProjects/cline/bt-lite/btrcv.c) — находит узел
  по Uniq, делает EVIOCGRAB, читает input_event, выводит "code value"; обвязка
  btkeys-grab.sh: маппинг кодов → /cmd sa_client + бип. Оба слушателя (G20S + ATV3)
  подняты на .122, ПЕРВЫЕ КОМАНДЫ УШЛИ (⇒ switch_home Гараж 19:10:37-38, кнопка HOME).
  Ключевые KEY-коды из маппинга: 105/165=prev, 106/163=next, 353/164=OK/pause,
  115/114=VOL±, 172=Home→switch_here, 2-11=цифры→избранное.
  Осталось: S99-автозапуск (демон+оба слушателя), выбор ROOM, чистка тестовых утилит
  (uhidtest*, uinput*, /tmp/g20*), задокументировать grab-секрет Алексею и в bt-lite.

- **17.11 (20:08, bt-connect.sh v3 + BPR2S)**: найден и устранён грабли-2 автоопределения:
  (а) BPR2S рекламируется БЕЗ UUID и БЕЗ имени (только MAC+RSSI) → фильтр по 0x1812 его
  не видел → добавлен список известных MAC; (б) fifo-агент ВНУТРИ одного скрипта на
  busybox-ash стабильно висит (do_wait — тот же баг был у g20v6!) → v3 переписана БЕЗ fifo:
  серии `bluetoothctl --timeout 5 scan le` + одноразовые `--agent NoInputNoOutput pair/connect`.
  Итог: **BPR2S подключён автоматически за 29с** (НАЙДЕН→Paired→узел event2→слушатель).
  Теперь на .122 три пульта в бондах: G20S, ATV3, BPR2S — у каждого свой слушатель,
  подключение по нажатию кнопки (BLE idle-disconnect — норма, btrcv переграбает).

- **17.11 (20:15, тройной бип)**: bt-connect.sh — колонка подаёт ТРИ бипа при успешном
  подключении нового пульта (после старта слушателя, перед «ГОТОВО»). Заодно вычищен
  задвоенный v2-хвост из скрипта — он и был источником «зомби»-строк в логе после
  завершения v3 (скрипт продолжал исполнять старый дубль).

- **17.11 (20:20, bt-remove + бип на любое подключение)**: bt-connect.sh — тройной бип
  теперь срабатывает при появлении УЗЛА (т.е. и для нового пульта, и для переподключения
  существующего); имя «НАЙДЕН» берётся из бонда (починено «Connected: no» вместо имени).
  Новый bt-remove.sh — интерактивное меню удаления спаренных пультов (список с именами,
  выбор по номеру/MAC/all; удаляет бонды из ОБОИХ хранилищ, disconnect+remove в bluez,
  гасит слушатели этого пульта). Оба на /data/opt/bin/.

- **18.11 (06:30, ГРАБЛЯ .115: Алиса молчала)**: bt-listeners-start.sh v1 делал
  `killall bluetoothd` → косвенно убивал вендорский D-Bus (pid-файл конфликтовал) →
  без шины умирали bluealsa (аудио) и maind --platformd (voice pipeline) → Алиса
  молчала. На .122 оба D-Bus сосуществуют (вендор на /var/run, Entware на /opt/var/run)
  — эталон. ФИКС: v2 bt-listeners-start.sh НЕ трогает вендорские D-Bus/bluealsa/maind,
  поднимает только Entware D-Bus + bluetoothd + слушателей. Для .115: ребут →
  bt-listeners-start.sh v2 → Алиса + пульт работают вместе.

- **24.11 (ревью скриптов: v3/v4, автозапуск, bt.conf-server)**: полный аудит всех BT-скриптов
  (журнал + код + живая .115). Фиксы: (а) kill-паттерны bt-listeners-start/bt-remove не видели
  имя btkeys-listener (половина стека звала grab, половина listener → дубли супервизоров,
  слушатели-призраки) → единый паттерн `btke[y]s-(grab|listener)`; (б) дубль-копия
  btkeys-listener.sh УДАЛЕНА из payload — единственное имя btkeys-grab.sh; (в) bt-connect v4:
  whitelist ядра (btmgmt add-device) после trust — мгновенный автоконнект по directed adv;
  killall tail убран (ронял ssh-диагностику); дедуп супервизора слушателя; гасит супервизор
  демона перед своим стартом (нет двух bluetoothd); (г) btkeys-grab v3: bridge из bt.conf
  `server <ip:8888>` (хардкод .131 — только дефолт; смена сервера = одна строка), ошибки /cmd
  в лог «✗ … подряд: N» + двойной бип после 3 подряд, трим логов, бип без параллельных aplay,
  heartbeat /tmp/bt.status.<uniq>, убраны мёртвый lms() и ложный «room mode» (switch_here
  идёт по плееру — проверено в исходниках sa_client); (д) bt-listeners-start v3: демон под
  супервизором (упал — поднимется) и БЕЗ -d (лог не растёт: было 425КБ/нед на .115),
  D-Bus-провал = честный exit 1; (е) bt_remote.sh v2: SHA-сверка каждой заливки (грабля
  «жпун» закрыта), bridge пишется в bt.conf, ставит S99btkeys в /etc/init.d, dbus-uuidgen
  с явным путём; (ж) bt-remove v2: чистит bt.conf + heartbeat (призраки не возвращаются).
  Синтаксис проверен dash sh -n (POSIX, как busybox ash). ЗАЛИВКА НА СТАНЦИИ — по запросу
  владельца (.115 жива; .122 с 24.11 не в сети).

- **24.11 (13:0x, ДЕПЛОЙ v3/v4 на ЭТАЛОН .115)**: BT-скрипты v3/v4 залиты на Гостиная-Лайт
  (бэкапы *.bak-241124; SHA-сверка ок): btkeys-grab v3 ПРОВЕРЕН В БОЮ — взял SAC из
  bt.conf `server 192.168.1.131:8888` (лог: "=== btkeys-grab v3 старт ... sac=..."),
  слушатель+супервизор+btrcv подняты bt-listeners-start v3, демон под супервизором
  БЕЗ -d; heartbeat /tmp/bt.status.69.98.98.23.a7.1d создаётся; старое имя
  btkeys-listener.sh на станции удалено; S97cmdlistener/S99btkeys в /etc/init.d
  (автозапуск); whitelist ядра G20S заряжен (btmgmt add-device). Рестарт стека —
  пульт переподключится по нажатию кнопки. Публичная проверка на слух — за владельцем.

## Дальше (план)
Устарело: откат bluez 5.78 не понадобился — решение было в EVIOCGRAB (см. РЕШЕНИЕ).
По желанию:
1. Интеграция статуса BT в страницу /stations sa_client (heartbeat /tmp/bt.status.*).
2. Маппинг ▲▼/PageUp/PageDown (в старой карте — «избранное∓1»).
3. .122 (Гараж) не в сети с 24.11 — проверить питание/IP, залить payload v3/v4
   (bt_remote.sh v2, с SHA-сверкой и S99-автозапуском).



