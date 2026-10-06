# Active Context — что сейчас

## Статус: v1.7 — релиз в процессе (03.10)

- **v1.7 слита в master** (все фичи dev: «включи избранное…», «найди…», «найди файл…»,
  фикс JsonUtils, пороги матчинга). Сборка чистая, тесты 15+21 зелёные, dist-lms/repo.xml готовы.
  Порядок публикации: владелец создаёт GitHub Release v1.7 с zip → потом push master→main.
- **sa_server задеплоен на Zeabur (владелец, 03.10)** и опубликован:
  https://github.com/knovash/lyrion-yandex-bridge-server (секреты вычищены, env-only).
  Мультипользовательская маршрутизация по access_token — в проде.
- README обновлён под v1.7 (новые команды навыка, TODO поизбранному убран).

## Что в работе
- **Плагин: секция Help внизу страницы настроек (06.10, деплой+проверено)** — ссылка на
  README github.com/knovash/lyrion-yandex-bridge (strings HELP/HELP_DESC RU/EN).
- **strings плагина (06.10, деплой+проверено)**: имя 'Lyrion Yandex Smart Home Bridge'
  (было 'Lyrion Yandex Bridge (Yandex Smart Home)'), описание EN 'Integrate LMS players into
  Yandex Smart Home' / RU 'Интеграция плееров в умный дом'.
- **Плагин: секция Player rooms — выбор комнаты для каждого плеера (06.10, деплой+проверено)**:
  селекты lybroom_<player> (текущая комната selected; пустая опция = не назначена, POST не шлётся).
  При Save страницы плагин отправляет ТОЛЬКО изменившиеся комнаты клиенту: POST /form
  action=player_room_set (новый лёгкий экшен клиента: ActionsSync.selectNewPlayerInRoom + write,
  БЕЗ delay/volume_max/schedule — их меняет только /players клиента). e2e: no-op POST и рендер ок.
- **Плагин: bind по умолчанию All interfaces + кнопка Reset (06.10, деплой на .131)**:
  Plugin.pm prefs->init bind='0.0.0.0' (для новых установок; сохранённое значение не трогается).
  Reset = ПОЛНЫЙ вайп (решение владельца 06.10): все *.json/*.properties из data/ уходят в
  *.bak-reset-<ts> (кроме логов, client.pid, старых бэкапов) — токены, комнаты плееров, привязки
  ГРАБЛИ (исправлено f71c2fd, 06.10): состояние лежит в ДВУХ местах — config.json в корне
  dataDir и *.json/*.properties в dataDir/data/; первый вариант вайпа обходил только корень
  → комнаты выживали Reset. Теперь glob по обоим уровням (dry-run проверен на боксе: 7 файлов).
  навыка, devices, tasker-настройки. Кнопка с confirm; на боксе НЕ нажимали.
  Cloud при отключении показывает 'not authorized' + кнопка 'Login to Yandex' (единообразно со
  Spotify 'not authorized' + 'Login to Spotify').
- **Страница плагина LMS, блок Client status (06.10, деплой на .131, проверено рендером)**:
  Cloud — только connected/no connection (URL и дубль Connected убраны, кнопка Подключиться
  осталась при отключении); LMS — состояние ПЕРЕД адресом; строка Yandex rooms убрана;
  Music devices — двумя отдельными строками 'Music devices in plugin'/'Music devices in Yandex'; Spotify — connected + имя пользователя из
  api /v1/me (StatusJson: spotify.user, кэш 10 мин, fallback id); при not authorized — кнопка
  'Login to Spotify' → /auth_spotify клиента (strings: LOGINSPOTIFY RU/EN). /status.json поле
  minutesLeft оставлено. Проверка рендера: /settings/plugins/LyrionYandexBridge/settings/basic.html.
- **Шум при пробуждении плееров — ВОССТАНОВЛЕНО 05.10**: при миграции squeeze-alice(systemd,
  /opt/squeeze-alice-1.0) → плагин потерялся config.silence (был пустой → PLAY SILENCE играл '').
  Вернул `silence=loop://natural/rain_outside.mp3` (встроенный звук LMS «Rain Outside», delay=3).
  ГРАБЛИ: клиент ПЕРЕЗАПИСЫВАЕТ config.json при остановке — править ТОЛЬКО при остановленном
  сервисе (stop → правка → start). Проверено: URL играет после wipecache. Бэкак конфига на боксе:
  config.json.bak-20261005. TODO-кандидат: поля «silence» нет в веб-настройках плагина.
- **Tasker-проект перенесён в репо (05.10, коммит с tasker/ + README)**: `tasker/lyrion-tasker.prj.xml`
  + пресеты `tasker/MinimalisticTextPreferences/*.mtpref` (побайтово из squeeze-alice). ГРАБЛИ с отступом
  в виджетах (нашёл владелец): глобальная %break задавалась в задаче 'Variable Set %break' со значением-
  многострочным литералом XML → в значение попадали табы отступа файла → каждая строка виджета с отступом.
  ФИКС: во всех 3 местах значение = `&#10;` (чистый перенос строки). На телефоне: ре-импорт проекта или
  руками поправить %break (только перенос строки). README: TODO про перенос виджетов убраны (3 места).
- **05.10 «найди файл» включал Spotify — РЕШЕНО НАСТРОЙКОЙ LMS, код НЕ меняли (решение владельца)**:
  причина — LMS Online Music Library Integration (Spotty/Я.Музыка) импортирует стриминг-каталог
  в общую БД (было: 574 трека = 72 локальных + 502 spotify-призрака). Дампы artists/albums/titles
  0 99999 отдают и призраков, db:contributor.id/album.id тянут спотифай-хвост (пример: артист id=44
  Electrypnose = 7 spt + 1 локальный файл). Владелец отключил интеграцию в настройках LMS
  («Online Music Library Integration»: Spotty/Я.Музыка — Do not integrate). Настройка стопит
  импорт НОВОГО, но СТАРЫЕ записи обычный `rescan` НЕ чистит (проверено: 502 остались) —
  вычистил `wipecache` = Clear cache and rescan (CLI, 05.10). ИТОГ: БД чистая — 68 треков
  (все file:), 19 альбомов, 48 артистов, spotify-призраков 0; albums search:Klungsum → 0;
  Electrypnose теперь id=304 (1 локальный файл Perce Oreille). На будущее: чистка призраков —
  только wipecache, НЕ обычный rescan.
- **Матчер «найди файл»: для запроса >5 симв. НЕТ абсолютного порога score** — берётся лучший из плохих
  (e2e 05.10: «клунгсум» → альбом wap.kengu.ru, score 4 — хотя «в файлах не нашла» ожидалось). Кандидат
  на будущий фикс: общий cap score (~2) для всех запросов, не только коротких.
- **dev: «найди файл <название>» — ГОТОВО, e2e на живом LMS (01.10)**: приоритет альбом → артист
  (все файлы артиста, db:contributor.id) → файл/трек (db:track.id). Берём ВСЮ медиатеку
  (артисты+альбомы+треки, 3 запроса) и матчим нечётко на клиенте — подстрочный поиск LMS
  не понимает транслит («смэк»~Smack). Кириллические имена файлов — тоже работают
  (нормализация сводит кириллицу и транслит к одному виду). Проверено: «костяника»→файл,
  «меззанин»→альбом, «дипеш мод»→всё от артиста, «смэк май бич ап»→файл, мусор→«не нашла».
- **«найди <название>» УДАЛЕНА 05.10** (dev, commit 0faa8e6, решение владельца; e2e ок: «найди костяника»
  → «Я не поняла команду»). Осталась единственная команда поиска — с 05.10 фраза «включи файл <название>»
  (переименована из «найди файл»; работает также «включить файл» и мн.ч. «включи файлы»; старая фраза
  больше НЕ распознаётся)
  (дамп artists/albums/titles 0 99999 + нечёткий матч, альбом→артист→файл). Удалено: libraryPlayByName,
  searchLibraryCandidates, searchWords, Player.librarySearch*, RequestParameters.search*, 2-арг findBest.
  Новый jar задеплоен себе на .131 (v1.7, имя то же), кандидатов в пуле теперь 135 (после чистки БД).
- **dev: «включи избранное <название>»** — готово, e2e ранее.
- **ГРАБЛИ (исправлено в dev)**: `JsonUtils.jsonToPojo` делал `json.replace("\\","")` ДО парсинга —
  валидный JSON с эскейпами (название трека `7\" Version`) ломался и ВЕСЬ список треков
  терялся (234 кандидата вместо 808). Теперь: сначала честный парс, fallback с чисткой только при ошибке.
- **Матчинг**: wordThreshold для 2-симв. слов = 1 («ап»~up); редукция слов запроса — только
  score<=1 и длина >=5; короткий запрос (<=5 симв.) — допуск score<=1 (иначе «зюзя»~SZA).
- **РЕШЕНИЕ: все три фичи живут только в dev, в релиз не идут**, пока владелец не решит иначе.
  При выпуске: merge dev→master + чек-лист релиза + убрать TODO из README.
- **sa_server: мультипользовательская маршрутизация готова (локально, ждёт деплой на Zeabur)**:
  `YandexUserResolver` (access_token → login.yandex.ru/info → числовой uid, кэш 6ч) +
  `YandexController`: приоритет `?uid=` → токен запроса (заголовок Authorization или
  session.user.access_token) → свой клиент; fallback anyUid только при одном клиенте.
  Сборка: `JAVA_HOME=~/.jdks/corretto-18.0.2 mvn package` → target/cloud-server-1.0.jar
  (app.jar — старая ручная копия, игнорировать). Деплой на Zeabur — вручную владельцем.
- Рабочий инстанс владельца и тестовый стенд: LMS 192.168.1.131 (root/ssh). Пароль в банк
  НЕ пишем (секреты запрещены) — если потерян, СПРОСИТЬ У ВЛАДЕЛЬЦА. Плагин в
  cache/InstalledPlugins, НЕТ unzip — jar обновлять scp-ом прямо в Bin/ + рестарт LMS
  (поднятие ~60-90с, проверка http://localhost:8888/ с бокса). E2E: POST /alice/ с
  application_id из rooms_and_alice_ids.json. Логи клиента на боксе: /var/lib/squeezeboxserver/
  cache/LyrionYandexBridge/data/log.txt (+ client-stdout.log). CLI LMS открыт через HTTP
  http://192.168.1.131:9000/jsonrpc.js (без авторизации), лог сервера — http://…:9000/log.txt.

## Следующие шаги (кандидаты)
1. Обновить СВОЙ рабочий инстанс плагина до v1.6 и проверить навык вживую:
   сказать навыку «это комната <название>», затем «что играет».
2. Разобрать открытые TODO (список в progress.md).

## Окружение
- Рабочая машина: linux, JDK 14 (target 11), Maven; LMS в локальной сети.
- Облако: sa_server на Zeabur, сервис `service-6a98566721fc3e07432ef076`, логи — Runtime logs.
- Ветки: работаем в `dev` (эксперименты, установка себе вручную zip-ом), релизы — только
  из `master` → `main` по чек-листу.
