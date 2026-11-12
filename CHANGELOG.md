# Changelog

## v1.13 (2026-11-12)

### Новое

- **Голос настоящей Алисы для уведомлений станций** — `/cmd?action=voice_say&alice=<quasar_device_id>&value=<текст>`:
  облачный сценарий УДЯ «SA voice <id>» (`devices.capabilities.quasar` / `instance=tts`),
  авторизация cookies аккаунта (`VOICE_STRING_YA_COOKIES`) + csrf с yandex.ru/iot;
  PUT текста → POST actions → колонка синтезирует фразы своим движком. Без подписки,
  лимит 100 символов. Тумблер `VOICE_TOGGLE_SAY_ALICE` (Алиса/локальный Piper),
  при ошибке облака — автоматический фолбэк на Piper (`YandexAliceSay.java`).
  Официальный api.iot.yandex.net сценарии редактировать НЕ умеет (проверено) —
  только m/-API (cookies). Переиспользует сценарии «ХА <id>» от старой интеграции
  Home Assistant, если найдены.
- **`tts_file`** — синтез Piper без воспроизведения: возвращает URL wav (для локального
  `aplay` на рутованных станциях, минуя LMS-плееры).
- **`switch_room`** — `/cmd?action=switch_room&value=<комната|плеер>`: локальная фраза
  станций «переключи музыку в/на X». X — комната УДЯ (нечётко, любой падеж:
  «в спальню» → «Спальня») или имя плеера (тогда берётся его комната); далее
  выполняется `switch_here` целевой комнаты (`ActionsAsync.switchRoom`).
- **`whats_playing_text`** — `/cmd?action=whats_playing_text&room=…`: локальная фраза
  «что играет» на станциях. Возвращает текст (та же логика, что у навыка «Раз Два» —
  `ActionsSync.whatsPlaying`), станция озвучивает его как ответы «спотифай»
  (voice_say Алиса / Piper wav + локальный aplay). Старый `whats_playing` (озвучка
  через LMS-плеер) не тронут.
- **Страница «Локальные команды root станции»** (`/local_commands`, пункт в меню):
  справочник локальных фраз станций + внизу краткое описание что такое рут станции
  и его возможности (`PageLocalCommands`).
- **Страница «Настройка cookies»** (`/ya_cookies`, пункт в меню): форма ввода ключа
  из cookie Яндекса (`Session_id`/`yandexuid`) для голоса настоящей Алисы +
  инструкции — как найти (F12 → Cookies), для чего (voice_say), безопасность,
  план QR-логина (passport magic-code). Сохранение в `settings_voice.properties`
  (`VOICE_STRING_YA_COOKIES`), применяется сразу — рестарт клиента не нужен
  (значение читается при каждом вызове).
- **Синхронные `/cmd`-действия для локальных фраз станций**: `spotify_artist/album/track/playlist`,
  `favorite_name` (возвращают текст ответа — станция озвучивает).
- **Per-player чекбокс «Create buttons»** (кнопки-устройства УДЯ) и «Alice: …» в списке
  комнат на странице плагина.
- **Блок «Voice notifications» на странице плагина** (Settings.pm + basic.html):
  - переключатель движка Алиса/Piper (пишет `VOICE_TOGGLE_SAY_ALICE`, рестарт клиента);
  - кнопка **Install Piper (~68 МБ)** — фоновая установка piper+голос irina-medium
    в `<datadir>/piper/` от пользователя LMS, БЕЗ root (`Bin/setup-piper.sh`):
    arch-детект, GitHub/HuggingFace, самопроверка синтеза, прописывание путей,
    авто-рестарт клиента после успеха; при недоступности источников — подсказки
    с путями для ручной загрузки;
  - статус-панель: Java (версия/как доставить), ffmpeg, cookies Алисы, Piper.
- **Локальные фразы на рутованных станциях** (документация + поддержка): whatplays-скрипты
  шлют `room=` явно, озвучка ответов — voice_say (Алиса) или Piper wav + локальный aplay
  через ALSA dmix. См. README, раздел «Локальные фразы…», и репозиторий cline
  (docs/lite-scripts/, alice-root-wizard.sh — мастер рута 2-й и последующих станций).

### Исправления

- **Гонка на static-полях Levenstein** — `filalDistance`/`finalElement` общие для всех
  потоков: параллельные /cmd (Таскер + станции) затирали результаты друг друга,
  нечёткий подбор комнат/плееров иногда возвращал null или чужой ответ (симптом:
  «ERROR ROOM Гостиная NOT EXISTS» на точном имени — било и room= локальных фраз).
  Все методы, трогающие статику, теперь `synchronized` (монитор класса, вложенные
  вызовы reentrant). Прогон: 8 потоков × 2000 итераций — без перекрёстного затирания.
- **TTS-URL указывали на :8010** (порт файлового сервера старого squeeze-alice) —
  уведомления не звучали после перехода на плагин. Теперь `/music/*` раздаёт сам
  sa_client (:8888) с диска /home/music (c8731f7).
- **Reset плагина больше не трогает настройки голоса** (`settings_voice.properties`):
  cookies/пути Piper — не относятся к привязке УДЯ, вручную их восстанавливать дорого.
- Mojibake в шаблоне страницы (Latin-1): текст блока Voice — только ASCII.

### Известные ограничения / TODO

- cookies Яндекса — ручная процедура (браузер, F12); план: QR-логин (passport magic-code)
  как кнопка на странице клиента. Cookies = полная сессия аккаунта: хранить только на
  своём сервере; отзыв — смена пароля.
- Карта `rooms_and_alice_ids.json` (навык «раз два», application_id→комната) стирается
  ресетом и ломается перепривязкой аккаунта — проговорить «это комната …» на каждой
  колонке. Диалоги НЕ присылают quasar device_id (проверено на живом запросе),
  автоматическая привязка из УДЯ невозможна; локальные фразы станций от карты не зависят.
- v1.14 (перенесено из v1.13): keepalive WS-реле (после инцидента с зомби-подключением,
  см. memory bank 08.11).

---

## Чек-лист выпуска версии

1. Версия в трёх местах (все синхронно):
   - `pom.xml` → `<version>X.Y</version>`
   - `lms-plugin/LyrionYandexBridge/install.xml` → `<version>X.Y</version>`
   - `lms-plugin/LyrionYandexBridge/ClientProcess.pm` → `my $jarName = 'lyrion-yandex-bridge-X.Y.jar';`
2. `mvn -DskipTests package` → jar из `target/` положить в `lms-plugin/LyrionYandexBridge/Bin/`.
3. Собрать zip: `dist-lms/lyrion-yandex-bridge-vX.Y.zip` (структура как у v1.12:
   LyrionYandexBridge/ целиком: *.pm, Bin/, HTML/, install.xml, strings.txt).
4. E2E на тестовом боксе: обновить плагин → Restart LMS → клиент поднялся (:8888),
   /status.json 200, тест: музыка вкл/выкл, «раз два» команды, voice_say, speak (Piper),
   страница плагина (блок Voice), кнопка Reset не трогает cookies.
5. Обновить CHANGELOG (дату релиза) и README (если менялось поведение).
6. git commit + tag `vX.Y`, zip приложить к release.
