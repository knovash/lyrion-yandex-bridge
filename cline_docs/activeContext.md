# Active Context — что сейчас

## Статус: v1.11 опубликован; dalgN-фикс в main (поедет в v1.12); мультирежим облака закрыт

- **09.10 (вечер) ФИКС «dalgN»-спама в лог LMS — ДЕПЛОЙ НА БОКС ВЫПОЛНЕН И ПОДТВЕРЖДЁН (18:30)**:
  каждые 2 мин Tasker-виджеты телефона (профиль «refresh every 2 min», lyrion-tasker-widgets.xml)
  дёргают /cmd get_refresh_json → Tasker.forTaskerWidgetsRefreshJson → p.title() ВСЕХ плееров →
  «playlist name ?» + «playlist album ?» на плеере с ПУСТЫМ плейлистом (JBL white, Гараж) → баг
  LMS 9.1.1 сыплет "_songData ... invalid object or path: dalgN!" (2 пары строк, каденс ровно 120с;
  диагностика 09.10 — команды воспроизведены руками по ИМЕНИ плеера, по MAC не воспроизводится).
  Корень бага LMS + репродьюс — progress.md «БАГ LMS 9.1.1 dalgN» (кандидат на репорт в
  LMS-Community/slimserver). ФИКС в Player.title(): сначала statusFast(); при playlist_tracks==0
  НЕ слать playlist name/artist/album (имя трека — из remoteMeta того же status); trackName(Result)
  — перегрузка без повторного запроса. Для играющих плееров поведение не менялось. Сборка чистая
  (rm -rf target, jar+zip 1.11). **Деплой 09.10 18:30**: scp jar (6915489 б) поверх
  Bin/lyrion-yandex-bridge-1.11.jar (бэкап .bak-preDalgNFix, chown squeezeboxserver, БЕЗ рестарта
  LMS — kill -TERM клиента pid 386199, beat поднял нового pid 387982 через ~15с, инициализация ок,
  6 плееров connected, облако/Яндекс/Spotify true). **Проверка**: e2e get_refresh_json руками —
  счётчик dalgN 46→46; боевые слоты поллера 18:30:55 и 18:32:55 — чистые (последняя пара dalgN
  18:30:01, до рестарта). ВНИМАНИЕ: релиз v1.11 вышел БЕЗ этого фикса — фикс в main поедет в
  v1.12; на боксе jar = 1.11+fix (имя файла то же, автообновление до 1.12 пройдёт штатно).
- **AirPlay2Bridge — ОТДЕЛЬНЫЙ ПРОЕКТ `/home/konstantin/IdeaProjects/airplay2` (GitHub
  knovash/airplay, имя БЕЗ «2»)**: с 09.10 (вечер, решение владельца) в сессиях sa_client
  его НЕ трогаем и НИЧЕГО в нём не делаем. Если владелец просит про AirPlay/HomePod/мост —
  попросить его переключить Cline на тот проект. Банк того проекта: airplay2/cline_docs/
  (локальный, в git не идёт). История: плагин родился тут (lms-plugin/, удалён 0577af7),
  v1.1 опубликован 09.10 (Release+asset, raw-URL/icon/download=200); v1.2 (кривая громкости
  — pyatv маппит % линейно в -30..0 dBFS) и далее — там; клиентские лимиты громкости
  HomePod-плееров на боксе подняты 70→100 (сделано из sa_client-сессии 09.10).
- **LMS-перезапуск на боксе (новое)**: убивается kill -TERM <pid squeezeboxserver>,
  запуск `su -s /bin/bash squeezeboxserver -c 'setsid nohup /usr/bin/perl
  /usr/sbin/squeezeboxserver --prefsdir /var/lib/squeezeboxserver/prefs --logdir
  /opt/lms_logs --cachedir /var/lib/squeezeboxserver/cache --charset utf8 &'`;
  restartserver-JSONRPC не перечитывает правки prefs-файлов с диска (память затирает).
  Prefs плагинов: /var/lib/squeezeboxserver/prefs/plugin/<name>.prefs (YAML-подобный),
  вкл/выкл плагинов: …/plugin/state.prefs. Качественные скачиваемые плагины живут в
  /var/lib/squeezeboxserver/cache/InstalledPlugins/Plugins/.
- **v1.11 ОПУБЛИКОВАН (09.10)**: Release v1.11 + asset (sha1 10869243… совпадает побайтно),
  repo.xml → v1.11 запушен (5eeca28), raw-URL проверен (отдаёт 1.11). Состав: УДЯ-дубли фиксы,
  deterministic ids (+yandexUid в seed), convergeMusicIdsByExternalId, spotify_link,
  artist-fallback, spoty-share.xml; + мультирежим: переживание рестарта облака (WS close 1008 →
  переподключение Яндекс-токеном, /info 401 → ретрай); версия 1.11 в pom/install.xml/ClientProcess.pm.
  + AirPlay2Bridge v1.0. Бокс .131 ещё на старой сборке — обновить (см. Следующие шаги 3).
- **09.10 (вечер) TASKER-ФАЙЛЫ ПЕРЕИМЕНОВАНЫ владельцем** (закоммичено): `lyrion-tasker-tv.xml`
  (бывш. squeeze_tv2.prj.xml), `lyrion-tasker-widgets.xml` (бывш. squeeze_v40.prj.xml, версия
  владельца-экспорта с планшета как эталон), `spoty-share.xml` (бывш. share.xml); старый
  `lyrion-tasker.prj.xml` удалён. README-ссылки обновлены.

## Статус: v1.10 — ОПУБЛИКОВАН (07.10)

- **v1.10 (07.10, поздним утром)**: Release+asset (zip sha 1a723df8), push e20a1d4..ce6e019 в main,
  repo.xml → 1.10. Состав: фикс УДЯ-actions NPE (неизвестный id → DEVICE_UNREACHABLE), guard
  пустого config.silence, история запросов «включи …» (data/search_requests.txt + страница
  /requests + ссылка «Запросы» в вебе + строка Voice requests history на странице плагина),
  умный поиск Spotify (склеенные слова + тай-брейк близости, раньше не был закоммичен),
  «не нашла» вместо «включаю null». Бокс владельца обновлён вручную до 1.10 (jar+ClientProcess.pm
  +install.xml scp, LMS restartserver 11:20, pid 317504) — всё зелёное, история запросов работает
  («включи aphex twin» → Aphex Twin, 11:12).
  **ИНЦИДЕНТ ПОСЛЕ РЕЛИЗА (закрыт 07.10 11:31)**: автообновление v1.10 на боксе упало
  (root-owned файлы от ручных scp → Permission denied), плагин выпарошен, клиент лежал.
  Релиз при этом исправен (asset sha совпадает, структура ок). Лечение: чистый rm -rf +
  распаковка релизного zip от squeezeboxserver + restartserver; теперь плагин стоит
  «по-честному» из релиза v1.10, всё зелёное. Урок — в techContext «грабли».
- **07.10 ДУБЛИ УСТРОЙСТВ В УДЯ — КОРЕНЬ НАЙДЕН И ИСПРАВЛЕН (master+бокс, ждёт v1.11)**:
  `SmartHome.create()` при синхронизации перезаписывал локальный id ВНУТРЕННИМ id Яндекса
  (код противоречил собственному комментарию «external_id сохранять в id»!). Мы отдавали
  внутренний id Яндекса как id провайдера → Яндекс не находил совпадение по external_id →
  реимпортировал устройство как НОВОЕ → дубли в УДЯ при каждом обновлении списка (подтверждено:
  user/info отдаёт 10 муз.устройств = 2 поколения). ФИКСЫ: 1) id больше не перезаписывается
  (дописываем только пустой, из externalId); 2) создание — детерминированный
  `UUID.nameUUIDFromBytes("музыка|<комната>")` вместо randomUUID (Reset/переустановка дают
  тот же id). Проверено на боксе: 0 UPDATE-строк, id devices.json не изменились, e2e action
  по текущему id → DONE+комната. Владельцу: удалить в УДЯ ВСЕ устройства «музыка» (оба
  поколения) и обновить список — создадутся ровно 5 под актуальные id, дальше стабильно.
  **Чистка выполнена владельцем 07.10 ~11:50**: в user/info ровно 5 устройств,
  externalId Яндекса == наши id (полная сходимость), action из приложения работает
  (DEVICE LOCAL 11:55:46), дубли исчезли. **Усиления для v1.11 (07.10 12:01, бокс .131)**:
  1) `SmartHome.convergeMusicIdsByExternalId` — самосходимость к external_id при ОДНОЗНАЧНОМ
  соответствии (одна комната = одно устройство в Яндексе); при дублях — skip+warn (ручная
  чистка); при совпадении — no-op (у владельца проверено: 0 строк). Защищает обновляющихся
  пользователей с историческим дрейфом id от реимпорта/дублей.
  2) seed детерминированного id включает `config.yandexUid` — id уникальны и МЕЖДУ
  пользователями облака (мультирежим: маршрутизация по токену → своему клиенту, но и
  глобальная уникальность провайдера соблюдена).
  **Мультирежим/другие пользователи**: sa_server и CloudClient сегодня НЕ менялись —
  маршрутизация токен→uid→свой клиент не затронута; в v1.10 id-механика не менялась
  (нет регрессии), фиксы id уходят в v1.11.
- **07.10 TASKER-ПРОЕКТЫ ПЕРЕРАБОТАНЫ (tasker/*.prj.xml, ждут импорта владельцем в Tasker)**:
  1) **порт 8010 → выбираемый %PORT (default 8888)** во ВСЕХ трёх (tv2, v40, телефон):
     `Set IP SA` собирает `http://%SERVER2_IP:%PORT`; в tv2/v40 добавлены задача `Set PORT SA`
     (клон с телефона, task241/242) + пункт меню `PORT %PORT` + ветка (goto .*PORT. *).
  2) **единая инициализация глобалов в `Settings default`** (первый старт — guard
     `If %DEF isn't set` в Settings/act0 уже был): в 389-пакет добавлены %PORT=8888 (tv2/v40),
     %FAVCOUNT=30 (все), отдельными действиями %BREAK='\\n' и %SERVERS-дефолт (354, If unset);
     убраны дубли-сеттеры: %break в 3 Minimalistic-задачах (→глобальный %BREAK), %FAVCOUNT в
     Favorites, 354-дефолт из Set IP select from saved.
  3) **tv2 (ТВ без виджетов)**: удалены 11 задач (Action Settings get — все тогглы виджетные,
     Action post my IP, 2× Minimalistic update*, Server wdg/minimalistic, Widget menu, Refresh,
     Settings tv mode, Say, Wats playing? — голосовые); Settings-меню = LMS/SA/PORT/Player/Defaults
     (ветки tv и toggls вырезаны); `Settings select player` → `%WIDGET=%ld_selected` + toast;
     `Refresh volume` переработан = Wait→HTTP→toast «%PLAYER volume %volume» (без Minimalistic);
     `Action playlist jump to index` без ->Refresh; из дефолтов убраны %MY_PORT/%TVMODE.
     Осталось 29 задач.
  4) **v40 (планшет)**: из Settings убран пункт TV + ветка + задача `Settings tv mode`;
     виджеты/профили/ресивер 1821 — не тронуты. 57 задач.
  ГРАБЛИ Tasker-XML: (а) действия в файле идут в ЛЕКСИЧЕСКОМ порядке act-ид (act1,act10,act11,act2!),
  «соседнее действие» искать по содержимому, не по индексу; (б) уникальность act-sr внутри задачи
  обязательна (renum с общего счётчика); (в) новые <Task> вставлять ВНУТРЬ </TaskerData>;
  **(г) ГЛАВНАЯ: <Project><tids> — список id задач — ОБЯЗАТЕЛЬНО синхронизировать при
  добавлении/удалении задач, иначе импорт в Tasker падает «bad data» (07.10 поймали)**;
  (д) значения-переносы в <Str> писать как `&#10;`, не raw-newline;
  **(е) перенос инициализации глобалов в «Settings default при первом старте» ЛОМАЕТ уже
  настроенные устройства (%DEF=set → пакет 389 никогда не выполнится → переменные пустые;
  07.10 сломался Favorites: пустой %FAVCOUNT → LMS favorites items 0 «» → пустой список).
  ПАТТЕРН: у каждого потребителя первым действием guarded-филлер `Variable Set If <var>
  isn't set` (op 13): Favorites/%FAVCOUNT=30, Set IP SA и Set PORT SA/%PORT=8888,
  Minimalistic-задачи и Action Settings get/%BREAK=&#10;. На свежей установке их опережает
  Settings default, на существующей — самозалечиваются.**
  **ИТОГ 07.10 (вечер): ВСЯ переработка tasker-проектов ОТКАЧЕНА на оригиналы** — удаление
  сеттеров глобалов и хирургия веток Settings-меню сломали работу на устройствах (Favorites,
  меню настроек). В файлах оставлен ТОЛЬКО фикс порта: `:8010` → `:8888` (2 строки на файл,
  diff с оригиналом — только они). ПРАВИЛО НА БУДУЩЕЕ: правки Tasker-XML — инкрементально,
  один шаг → импорт на устройство → проверка владельцем → следующий шаг; структурную хирургию
  (ветки меню, сеттеры переменных) не делать вслепую, семантика goto/label в Tasker-экспортах
  не до конца понятна. Оригиналы-бэкапы: /tmp/tasker-backup/.
- **07.10 (вечер) tv2: список плееров — НАПРЯМУЮ ИЗ LMS** (решение владельца): `Settings select
  player` = HTTP POST `%SERVER/jsonrpc.js` `["players","0","99"]` (клон паттерна Favorites:
  %HTTPD → %playerslist → regex → `%titles()` в меню) + guarded-филлер %SERVER + `%WIDGET=%ld_selected`.
  Зависимость от клиента/%SERVER2/%WIDGET/%PLAYLISTLINES ушла. Задача `Action get all from server`
  в tv2 осталась неиспользуемой. **ДИАГНОЗ «400 Bad Request»**: Tasker подставляет буквальные
  `%VAR`-имена пустых глобалов в URL → невалидный percent-escape → HttpServer отбивает 400
  ДО обработчика (в логе клиента следов нет!).
- **ФАКТ (запомнить): проект ТВ (tv2) тестируется НА ПЛАНШЕТЕ; одновременно установлен ТОЛЬКО
  ОДИН проект — v40 ИЛИ tv** (не вместе). Но глобалы Tasker переживают удаление/импорт — при
  переключении проектов старые значения остаются (потенциальный источник «странных» данных).
  tv2 полностью на `%PLAYER` (отказ от %WIDGET, 07.10): выбор плеера и все команды.
- **08.10 УТРО «LMS играет — звука нет» — ДИАГНОЗ И ФИКС (бокс .131)**: избранное содержало
  .pls-ссылки (DI.fm/JazzRadio `api.audioaddict.com/…premium_high/….pls`, SomaFM) — LMS 9.1.1
  для bridge-плееров (UPnP/Raop/Cast) не может собрать командную строку для **pls-контейнера**
  (`Couldn't create command line for pls playback`) → плеер формально play, поток не открыт,
  тишина (титул при этом показывается из pls!). ФИКС: в `prefs/favorites.opml` все 23 .pls-URL
  заменены на прямые потоки (File1 из каждого pls: `prem2.di.fm/<канал>_hi?key`,
  `ice6.somafm.com/*-128-mp3`), порядок/иконки/названия сохранены, бэкап
  `favorites.opml.bak-cline-1008`; LMS перезапущен; проверено: favorite → play → прямой URL,
  ошибок в логе нет. Заодно установлен faad+lame (для транскода на будущее; мосты тянут AAC
  напрямую). НЮАНС: id избранного меняются при каждом старте LMS (Tasker играет по индексам —
  не важно). **Правило: новые радиоканалы добавлять прямой ссылкой на поток, не .pls!**
  **ОТКАТ 08.10 (владелец): замена .pls на прямые ссылки ЗВУК НЕ ВЕРНУЛА — favorites.opml
  восстановлен из бэкапа (все 19 audioaddict .pls на месте), LMS перезапущен. Ошибку
  «Couldn't create command line for pls» лог фиксирует, но она НЕ причина тишины —
  вопрос «играет но звука нет» ОТКРЫТ** (кандидаты дальше: рендереры/мосты Raop/UPnP —
  громкость/соединение на стороне устройств, канал/выход Audio-приёмника). faad+lame
  оставлены установлены (безвредны).
- **08.10 ФИНАЛ «HomePod без звука из LMS»: причина — баг Apple HomePodOS 27** (issue
  philippe44/LMS-Raop #57): OS 27 принимает легаси-RAOP (ANNOUNCE/SETUP/RECORD 200, RTP идёт,
  timing/громкость ок) но не рендерит звук; AirTunes/980.77.2; на 26.6 работает, iPhone работает.
  Фикс 1.8.12 (User-Agent, установлен на боксе вручную из репо — бинарник aarch64 + install.xml,
  бэкап .bak-187) — НЕ помогает (подтверждено и нами, и в треде). Philippe внедрять AirPlay 2
  отказался → штатного фикса нет. РАБОЧИЕ ОБХОДЫ: cayco-squeezelite-airplay2-bridge (squeezelite
  → pyatv/AP2, имена/MAC плееров сохраняются — LMS/Tasker не замечают); рецепт toralt из треда;
  Music Assistant + cliairplay. План: развернуть cayco-мост на боксе для HomePod1/2/3 по решению
  владельца. Диагностика дня: ручной запуск squeeze2raop с -d all=debug -f /tmp/*.log — полный
  след сессии (важно: плагинный лог молчит, syslog хранит только banner).
- **РЕШЕНИЕ ВЛАДЕЛЬЦА (08.10): ЖДАТЬ фикса** по issue philippe44/LMS-Raop #57 — обходы (cayco-мост/
  pyatv/Music Assistant) НЕ разворачиваем. Отслеживание: новые версии >1.8.12 в репо LMS-Raop +
  комментарии в issue. Мост на боксе обновлён до 1.8.12 (оставлен), старый бинарник — .bak-187.
  ПОКА ТИКЕТ ОТКРЫТ: мост держать ЗАПУЩЕННЫМ (Start the Bridge) — иначе HomePod-плееры пропадают
  из LMS и комнаты Гостиная/Спальня/Душ в УДЯ/навыке становятся недоступны (структура важнее
  отсутствия звука). Когда выйдет фикс: обновить мост/бинарник и перезапустить.
- **08.10 НОВОЕ: «Поделиться ссылкой Spotify» на планшете → колонка** (клиент + v40): в клиенте
  новый `/cmd` action **`spotify_link`** (ActionsAsync.spotifyLink: парсит open.spotify.com/
  track|album|playlist|artist|episode|show[/intl-XX]/ID, хвост ?si отбрасывается; или spotify:
  URI; будит плеер, playPath, refresh Tasker/Яндекс; ответ «включаю трек/… на <плеер>»/«не понял
  ссылку»; e2e проверено на боксе). В v40 задача **`Spotify share`** (task243, действия 0-2,
  у каждого условие `%CLIP ~ .*open\.spotify\.com/.*`): %slink=%CLIP → HTTP GET
  `%SERVER2/cmd?action=spotify_link&player=%WIDGET&value=%slink` → тост %http_data.
  **ПРОФИЛЬ создаёт владелец руками в Tasker UI** (коды событий в XML офлайн не верифицируемы):
  Profile+ → Event → UI → **Clipboard Changed** → задача `Spotify share`.
  UX: Spotify ⋮ → Поделиться → Копировать ссылку → играет на текущем %WIDGET.
  **ГРАБЛЯ+ФИКС (08.10, cd3358b): `HandlePathCmd` глобально делал `value.toLowerCase()` —
  Spotify-ID base62 РЕГИСТРОЗАВИСИМЫ → Spotty API 404 → тишина («действие есть, не играет»).
  Убран; e2e: плейлист играет, 404 в LMS-логе исчезли. Урок: проверять e2e не только подстановку
  пути, но и факт ВОСПРОИЗВЕДЕНИЯ (mode=play + отсутствие ошибок Spotty в LMS).** Владелец
  обновил v40 с планшета (свой экспорт с профилем) — версия владельца ВЗЯТА КАК ЭТАЛОН и
  закоммичена (09.10, tasker-файлы переименованы владельцем: squeeze_v40.prj.xml →
  lyrion-tasker-widgets.xml, squeeze_tv2.prj.xml → lyrion-tasker-tv.xml, share.xml →
  spoty-share.xml, старый lyrion-tasker.prj.xml удалён; README-ссылки обновлены).
- **08.10 «Переключи сюда» из Spotify — fallback на артиста (утв. владельцем)**: проблема
  известна владельцу ЕЩЁ со старого проекта — Spotify API отдаёт `context=null` (Spotify Connect,
  очередь, «Любимые треки»), а `spotify:user:…:collection` Spotty не играет → раньше переносился
  ТОЛЬКО ОДИН трек. Теперь цепочка: context (playlist/album/artist) → **artist-радио из популярных**
  (артист играющего трека) → один трек; прыжок на играющий трек по названию работает для всех
  вариантов. **Тема context=null может вернуться — помнить!** Лог-маркер: «Context NULL/collection
  -> artist fallback».
- **08.10 tasker/share.xml — НОВЫЙ МИНИ-ПРОЕКТ «share»** (по запросу владельца, на основе tv2/v40,
  всего 2 задачи): `Select player` (task250 — прямой запрос списка плееров к LMS jsonrpc
  `players 0 99` + меню → `%PLAYER`; филлер %SERVER) и `Spotify share` (task251 — буфер
  open.spotify.com → GET `%SERVER2/cmd?action=spotify_link&player=%PLAYER`; филлер %SERVER2
  дефолт http://192.168.1.131:8888). Профиль Clipboard Changed владелец создаёт руками.
  Единая переменная текущего плеера в проекте — `%PLAYER`.
- **ГЛАВНАЯ ГРАБЛЯ Tasker-XML (07.10, финальная разгадка): id действий — строго 0..N-1 БЕЗ
  пропусков; Tasker обрывает задачу на первом пропущенном id** (нет act0 → задача «пустая»;
  дырка в середине → хвост задачи отбрасывается). Из-за этого были: пустая Settings select
  player, сломанные меню Settings (обрезанные ветки), недостижимые %BREAK/%DEFV сеттеры.
  ЛЮБОЕ удаление/вставку действия сопровождать перенумерацией задачи подряд; контроль:
  sorted(ids)==range(len). Все 3 проекта прогнаны renumber-проходкой (act-скрипт в истории
  коммитов).
  Бэкапы: /tmp/tasker-backup/. Валидация: minidom + инвентарь + колл-граф + grep-контроли — ок.
- **v1.7 ОПУБЛИКОВАНА полностью** (Release 02.10 + repo.xml на main) — старая запись «в процессе»
  была неактуальна.
- **v1.8: master==dev==085db9c**, версия 1.8 в pom/install.xml/ClientProcess.pm, сборка чистая,
  dist-lms/lyrion-yandex-bridge-v1.8.zip (sha 96b7a902369d679bcdb2d2a341fe157067064d71),
  repo.xml → 1.8 (+новое имя/описание плагина). Release v1.8 создан владельцем, asset залит,
  push master:main и dev выполнены (78e79fb..88c0a4c) — v1.8 в релизе.
- **v1.8.1 (antistorm-фикс) ОПУБЛИКОВАН через час после v1.8** (Release + asset,
  sha 312c5207, push 2af8d0d..2be8aaf).
- **v1.9 ОПУБЛИКОВАН (06.10, поздно вечером)**: Release + asset (sha d8070143, push
  778da86..b93d5b8). Инвариант устройств, дедуп, пункт 'no', иконка в списке плагинов,
  оптимизация задержек (страница ~0.5с, async yandexInit/Spotify, без скана сети),
  строка LMS убрана. Бокс владельца обновлён вручную до 1.9 — всё зелёное.
- **sa_server задеплоен на Zeabur (владелец, 03.10)** и опубликован:
  https://github.com/knovash/lyrion-yandex-bridge-server (секреты вычищены, env-only).
  Мультипользовательская маршрутизация по access_token — в проде.
- README обновлён под v1.7 (новые команды навыка, TODO поизбранному убран).

## Что в работе

- **07.10 УТРО «команды не проходят» — РАССЛЕДОВАНО И ИСПРАВЛЕНО (master + бокс .131)**:
  все 5 УДЯ-actions утра (08:58–09:24) шли на СТАРЫЙ id «музыка Гостиная» `3f4cd481…` из кэша
  УДЯ (актуальный `798d99d6…` — id сменился 06.10 20:40 при ресинке после крашей, тогда же
  в логе `UPDATE LOCAL DEVICE ID BY YANDEX`) → ProviderAction NPE «deviceLocal is null» →
  ответ Яндексу НЕ уходил → sa_server через 10с отдавал `client timeout` → УДЯ «не удалось».
  Голос навыка при этом РАБОТАЛ («включи depeche mode» 09:12:59 → заиграло). Фиксы (бокс
  обновлён 09:46, jar sha 08619fbb, старый сохранён как `*.jar.bak-0710`):
  1) ProviderAction: null-check deviceLocal — неизвестный id → WARN + валидный ответ
     ERROR/DEVICE_UNREACHABLE (e2e: 200 за 1.6с вместо таймаута); добавлен потерянный
     `return` на «LMS NULL».
  2) Player.playSilence: guard — не слать `playlist play ""` при пустом config.silence.
  3) На боксе восстановлен `config.silence=loop://natural/rain_outside.mp3` (терялся при
     ресетах 06.10; бэкап `config.json.bak-silence-0710`).
  ЗА НОЧЬ 06→07.10 (после 23:12) клиент стабилен: 0 падений, WS-сессия с облаком непрерывна.
  Владельцу: обновить список устройств в приложении УДЯ (переоткрыть/refresh) и пересоздать
  виджеты/сценарии, где зашит старый id — actions заработают на актуальных id. Кандидат на
  будущее: детерминированные id music-устройств (UUID от имени комнаты), чтобы ресеты/рестарты
  не меняли id и не рассинхронизировали кэш УДЯ.
- **07.10 ИСТОРИЯ ЗАПРОСОВ НАВЫКА (новое, master + бокс .131, e2e ок)**: команды «включи …»
  (Spotify/избранное/файлы) пишут в `data/search_requests.txt` пары строк
  `[dd.MM.yyyy HH:mm:ss] ЗАПРОС: <команда>` и следующей `РЕЗУЛЬТАТ: <найдено|не нашла|ошибка>`
  (`utils/SearchRequests`, хуки в `ActionsAsync`: spotifyPlayCommand — успех/таймаут/ошибка,
  channelPlayByName, filePlayByName). Страница `/requests` (`web/PageRequests`) показывает файл,
  ссылка «Запросы» внизу главной :8888 (target=_blank). Бонус-фикс: Spotify «не найдено»
  (uri/name=null) теперь отвечает «не нашла, скажите точнее» и НЕ зовёт playPath(null)
  (раньше было «включаю null»). НАБЛЮДЕНИЕ из e2e: мусорный запрос «zzzqqqxxx тествый»
  заматчился в «V $ X V PRiNCE» — общий cap score для поиска по-прежнему кандидат на фикс.
- **07.10 СТРАНИЦА ПЛАГИНА: строка «Voice requests history» (перед Help)**: basic.html +
  strings.txt (PLUGIN_LYRION_YANDEX_BRIDGE_REQUESTS/…_DESC/…_OPEN, RU/EN) + Settings.pm
  (`requestsurl` = weburl + /requests). Ссылка открывает /requests клиента в новом окне
  (target=lybreq). На боксе: файлы scp-нуты в InstalledPlugins, LMS перезапущен штатной
  CLI-командой `restartserver` через jsonrpc (юнита systemd у LMS нет), проверено рендером.
  В релизный zip уйдёт при следующем выпуске.
- **~~РАССЛЕДОВАНИЕ ЛОГОВ 06.10 21:16–23:18~~ — ЗАКРЫТО 07.10**: п.2 (NPE) и п.3 (пустой URL
  тишины) исправлены (см. выше), п.1 (бесшумные смерти) за ночь не воспроизвёлся. Детали были:
  пользователь ОДИН —
  Константин Н. (один Bearer в облаке, одна станция application_id B232…=Гостиная, Tasker
  192.168.1.116, 5 голосовых «включи …» + групповое off из УДЯ). Проблемы:
  1) клиент ДВАЖДЫ бесшумно погиб (23:06:48→23:09:02 и 23:10:20→23:12:02) — ни стека, ни
     hs_err, ни OOM в kernel, ни System.exit в коде; LMS beat(30с, alive=kill 0) поднимал
     сам; ранее 20:38/20:40 после 401-шторма Яндекса 20:27–20:33. Кандидат-фикс: добавить
     -XX:+HeapDumpOnOutOfMemoryError/OnOutOfMemoryError в ClientProcess.pm + shutdown-hook лог.
  2) NPE в v1.9 при ГРУППОВОМ выключении из УДЯ: ProviderAction «deviceLocal is null»
     (яндекс-id нет в локальном map) — действие не отрабатывает. Нужен фикс (null-check/ignore).
  3) LMS «Scanner: No path or URL was requested!» после каждой «включи …» (клиент кладёт
     трек с пустым URL). 4) Яндекс callback/state: 429×4, 504×6, 500×3 за окно.
  Логи: клиент data/log.txt и client-stdout.log на боксе (мультидневные, хронологичный хвост;
  stdout — полный append). sa_server на Zeabur отсюда недоступен (Runtime logs только у владельца).
  → Чек-лист проверок при следующих тестах: **cline_docs/testChecks.md**.
- **Умный поиск Spotify (06.10, 88cefd9, e2e: виктория бекхэм -> Victoria Beckham!)**:
  searchBest(): транслит+нормализация, запрос + слова в обратном порядке, выбор ЛУЧШЕГО
  результата по скорингу имени (точное 100 / contains>=60% длины 90 / все слова fuzzy 80-85 /
  половина 60). ГРАБЛИ: contains-90 без проверки длины давал артисту Viktoria 90 за одно
  слово из двух. Действует для artist/track/album/playlist. 1с-таймаут ответа навыка может
  ответить Включаю Spotify — музыка включается фоном, название скажет голосом.
- **ОПТИМИЗАЦИЯ ЗАДЕРЖЕК (06.10, 348156e, измерено: страница 0.53с вместо 8-15с)**:
  yandexInit асинхронно (CompletableFuture: старт клиента + после авторизации) — :8888 готов
  сразу; Spotify /me фон; searchForLmsIp упрощён до проверки 127.0.0.1 (скан сети удалён —
  клиент ТОЛЬКО как плагин на одной машине с LMS, решение владельца); плагин после
  Restart/Reset не ждёт статус/refresh (unless-блок); _clientStatus timeout 3с.
- **ИНВАРИАНТ «устройство музыка ⇔ комната с плеером» (06.10, e60aacf, проверено)**:
  снятие комнаты (—) удаляет устройство комнаты, если плееров в ней не осталось;
  yandexInit делает реконсиляцию (removeMusicDevicesWithoutPlayers) — зачистила 2 осиротевших
  (Душ/Спальня при живых только Веранда+Гостиная). Яндекс при привязке теперь видит только
  устройства комнат с плеерами.
- **Player rooms: пункт «—» = снять комнату (06.10, 2c9a403, e2e проверено)**: селект
  всегда с пустым пунктом 'no' (ASCII! литералы шаблонов — только латиница, тире «—»

  playerRoomSet при пустой комнате делает player.room=null. ГРАБЛИ: Parser.splitByEqual
  без лимита выбрасывал пустой хвост → bodyToMap валился на ЛЮБОМ пустом параметре —
  фикс split(...,2) (заодно корректны «=» внутри значений). e2e: Гостиная→''→Гостиная ок.
- **Дубли Music devices почищены + иконка со страницы настроек убрана (06.10, e456b08)**:
  КОРЕНЬ дублей: create() дедуплицировал только по device.id, а локально созданное
  устройство имело UUID-id ≠ яндексовский → каждый цикл синхронизации добавлял копию.
  Фикс: поиск и по id, И по комнате + перепривязка id к яндексовскому (исходное намерение
  кода); dedupeMusicDevicesByRoom(yandexIds) разово зачистил 12→4. Иконка на странице
  настроек убрана (в СПИСКЕ плагинов осталась). ГРАБЛИ: LMS кэширует TT-шаблоны — после
  деплоя basic.html обязателен рестарт LMS, не только клиента.
- **Иконка плагина — В СПИСКЕ плагинов LMS и на странице настроек (06.10, c6e0b0c, проверено)**:
  html/images/icon.png 128x128 (из img/udy.webp) + <icon>plugins/LyrionYandexBridge/html/
  images/icon.png</icon> в install.xml (формат MaterialSkin — единственный рабочий способ;
  просто файл в каталоге НЕ подхватывается). Рендер списка: /plugins/.../icon_50x50.png
  (LMS сам ресайзит). repo.xml — icon=URL (raw main) для ещё не установивших. Страница
  настроек — та же картинка справа сверху блока Client status. У пользователей — со след. релизом.
- **v1.8 post-release: шторм запросов ломал страницу плагина (06.10, 7b7c18a, на боксе)**:
  live-чек в StatusJson + refresh на каждый saveSettings (вкладка настроек LMS шлёт форму
  повторно) грузили однопоточный HTTP LMS → флаг плясал, status.json >3с → блок Client status
  пропадал. Фикс: live-чек убран (флаг самолечится шедулером), refresh анти-шторм ≥10с,
  _clientStatus timeout 6с. Исправлено и выпущено в v1.8.1 (в тот же день).
- **Player rooms: бейдж присутствия устройства в Яндексе (06.10, a1a53f2, проверено)**:
  рядом с селектом — зелёный 'in Yandex' / красный 'not connected - update devices in
  Yandex Smart Home' (если комната назначена,
  но её нет в musicYandex); без комнаты — ничего. Обновляется при открытии страницы/Apply.
- **LMS disconnected 192.168.1.111 — ГРАБЛИ+фикс (06.10, 4d7c988)**: при старте клиента во
  время рестарта LMS автопоиск находил ЧУЖОЙ LMS в сети (.111) и перезаписывал конфиг.
  Фикс: Config.lmsIpForced (static, ставится --lms.ip из applyArgs) → searchForLmsIp не
  сканирует/не подменяет, НО проверку isLmsServer делает обязательно (18c4358): она
  выставляет lmsServerOnline — без неё updatePlayers NPE (Boolean null) и одноразовый
  60s-ретрай не спасал (клиент навсегда offline после старта при неготовом LMS).
  LOG теперь: FORCED BY ARGS (127.0.0.1) - OK / NOT READY YET.
  + b0389cc: флаг lmsServerOnline «протухал» от разового таймаута HEAD-проверки → updatePlayers
  вечно выходил по LMS OFF LINE (плагин показывал disconnect при живом LMS). Теперь при
  !=true updatePlayers сам перепроверяет isLmsServer (шедулер дёргает периодически →
  самолечение); Boolean.TRUE.equals заодно закрыл NPE на null.
  + 0905441 (финал): КОРЕНЬ — isLmsServer имел read-timeout 1с; под нагрузкой (rescan,
  Apply-рефреш) LMS не отвечал за 1с → флаг ложно false. Теперь 3с + StatusJson при
  false/null делает живую проверку (лечит и флаг, и отображение заодно).

 На боксе вернул
  lmsIp=127.0.0.1 (при ОСТАНОВЛЕННОМ клиенте). Также всплыл известный NPE lmsServerOnline
  при раннем старте — лечится штатным 60s-ретраем (подтверждено: players подхватились).
- **Music devices in Yandex не обновлялся по Apply — ГРАБЛИ+фикс (06.10, 87c52fd)**:
  yandexInfoDevices кэшировался с момента старта; Apply теперь шлёт клиенту POST /form
  action=statusbar_refresh ПЕРЕД чтением /status.json → блок Client status свежий на той же
  отрисовке (Apply может занять до ~8с — ждёт user/info). ГЛУБЖЕ: SmartHome не умел ЧИТАТЬ
  devices.json — устройства жили в памяти и терялись при рестарте с недоступным Яндексом;
  теперь smartHome.read() на старте + yandexInit НЕ затирает локальные при сбое user/info.
  e2e: после фикса musicLocal=musicYandex=[Душ,Гостиная,Улица] и в status.json, и на странице.
- **Комнаты Яндекса после авторизации — ГРАБЛИ+фикс (06.10, f679b27)**: user/info дёргался ТОЛЬКО
  при старте процесса → после веб-авторизации комнаты оставались пустыми до рестарта. Плюс в 11:17
  Яндекс дал разовую 500 → NPE в devicesGetFromYandexInfo (не было null-check). Фикс: Main.yandexInit()
  (выделен из main) вызывается из LocalAuthYandex.applyToken сразу после получения токена; при ошибке
  Яндекса комнаты НЕ затираются (KEEP CURRENT ROOMS). Кнопка «обновить» на Home тоже подтягивает.
  ГРАБЛИ ДЕПЛОЯ: рестарт LMS НЕ убивает detached java-клиент (systemd) — после scp jar проверять
  смену pid и при необходимости kill <pid> + рестарт. Проверено: 14 комнат загрузились.
- **Плагин: секция Help внизу страницы настроек (06.10, деплой+проверено)** — ссылка на
  README blob/main/README.md, текст 'README.md' (strings HELP/HELP_DESC RU/EN).
- **strings плагина (06.10, деплой+проверено)**: имя 'Lyrion Yandex Smart Home Bridge'
  (было 'Lyrion Yandex Bridge (Yandex Smart Home)'), описание EN 'Integrate LMS players into
  Yandex Smart Home' / RU 'Интеграция плееров в умный дом'.
- **Плагин: секция Player rooms — выбор комнаты для каждого плеера (06.10, деплой+проверено)**:
  селекты lybroom_<player> (текущая комната selected; пустая опция = не назначена, POST не шлётся).
  При Save страницы плагин отправляет ТОЛЬКО изменившиеся комнаты клиенту: POST /form
  action=player_room_set (новый лёгкий экшен клиента: ActionsSync.selectNewPlayerInRoom + write,
  БЕЗ delay/volume_max/schedule — их меняет только /players клиента).
  ГРАБЛИ (исправлено d9f272e, 06.10): (1) перл — \$1 из блока grep не живёт снаружи → игрок уходил
  пустым; матчить надо в теле цикла. (2) JSON::XS даёт Unicode-строки, LWP->post(\%form) молча
  теряет wide-char значения → Encode::encode('UTF-8') обязателен. (3) 302 от клиента = ok.
  e2e: HomePod3→Кухня→Спальня через POST страницы плагина, оба (ok), status.json подтвердил.
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
- **Страница плагина LMS, блок Client status (06.10)**: СТРОКА LMS УДАЛЕНА (65d8f5d) — LMS
  всегда localhost вместе с клиентом, статус только путал переходными 'disconnected'.
  Остальное:
  Cloud — только connected/not authorized (URL и дубль Connected убраны, кнопка Login
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
- **sa_server: мультипользовательская маршрутизация (ЗАДЕПЛОЕНА владельцем 03.10, в проде)**:
  `YandexUserResolver` (access_token → login.yandex.ru/info → числовой uid, кэш 6ч) +
  `YandexController`: приоритет `?uid=` → токен запроса (заголовок Authorization или
  session.user.access_token) → свой клиент; fallback anyUid только при одном клиенте.
  Сборка: `JAVA_HOME=~/.jdks/corretto-18.0.2 mvn package` → target/cloud-server-1.0.jar
  (app.jar — старая ручная копия, игнорировать). Деплой на Zeabur — вручную владельцем.
  **09.10 ФИКС РЕСТАРТОВ (b59ec49, ЗАДЕПЛОЕН ВЛАДЕЛЬЦЕМ на Zeabur 09.10, подтверждён снаружи:
  WS-проба с мусорным токеном закрывается через ~460мс = fallback успевает сходить в
  login.yandex.ru; старая сборка закрыла бы мгновенно)**: TokenRegistry in-memory — рестарт
  сервера терял все instanceToken → клиенты навсегда 1008 до ручной переавторизации. Теперь
  WS (/ws) и /info принимают также Яндекс access_token (fallback через YandexUserResolver,
  uid тот же); клиент v1.11 после close 1008 сам переподключается Яндекс-токеном, /info 401 →
  ретрай. Smoke-тест: старт 3.4с, GET / 200, /info без токена 401.
- Рабочий инстанс владельца и тестовый стенд: LMS 192.168.1.131 (root/ssh). Пароль в банк
  НЕ пишем (секреты запрещены) — если потерян, СПРОСИТЬ У ВЛАДЕЛЬЦА. Плагин в
  cache/InstalledPlugins, НЕТ unzip — jar обновлять scp-ом прямо в Bin/ + рестарт LMS
  (поднятие ~60-90с, проверка http://localhost:8888/ с бокса). E2E: POST /alice/ с
  application_id из rooms_and_alice_ids.json. Логи клиента на боксе: /var/lib/squeezeboxserver/
  cache/LyrionYandexBridge/data/log.txt (+ client-stdout.log). CLI LMS открыт через HTTP
  http://192.168.1.131:9000/jsonrpc.js (без авторизации), лог сервера — http://…:9000/log.txt.

## Следующие шаги (мультирежим — финал)
1. ~~ВЛАДЕЛЕЦ: задеплоить sa_server b59ec49 на Zeabur~~ — СДЕЛАНО 09.10, подтверждено WS-пробой.
2. ~~ВЛАДЕЛЕЦ: GitHub Release v1.11 + asset~~ — СДЕЛАНО 09.10: asset sha совпадает, repo.xml
   запушен (5eeca28), raw-URL отдаёт 1.11.
3. Обновить бокс .131 до релизной v1.11 (scp jar+ClientProcess.pm+install.xml, chown!, rm старый
   jar, рестарт LMS по процедуре банка) и проверить, что WS поднялся (лог клиента), УДЯ/навык.
   **ПЕРЕД вторым пользователем: проверить в Runtime logs Zeabur свой `WS CONNECTED: uid=` —
   если там `user-1` (захардкоженный тестовый токен TokenRegistry из старой сборки!), то запросы
   владельца ходят только по anyUid-fallback и ОТВАЛЯТСЯ при подключении второго клиента →
   нужна разовая переавторизация («Подключиться через Яндекс») или обновление бокса до v1.11.**
4. Второй пользователь: плагин из репо (README-инструкция), «Подключиться через Яндекс»,
   провайдер УДЯ; в Runtime logs Zeabur ждать два `WS CONNECTED: uid=…` и
   `ROUTE: by access_token -> uid=…`.
5. Разобрать открытые TODO (список в progress.md).

## Окружение
- Рабочая машина: linux, JDK 14 (target 11), Maven; LMS в локальной сети.
- Облако: sa_server на Zeabur, сервис `service-6a98566721fc3e07432ef076`, логи — Runtime logs.
- Ветки: работаем в `dev` (эксперименты, установка себе вручную zip-ом), релизы — только
  из `master` → `main` по чек-листу.
