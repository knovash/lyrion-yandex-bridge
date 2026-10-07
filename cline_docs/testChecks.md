# TestChecks — проверки ошибок при следующих тестах (из анализа логов 06.10)

## Стабильность клиента (главная — бесшумные смерти процесса)
- LMS-лог (`http://192.168.1.131:9000/log.txt?lines=1000`): за период теста НЕ должно быть
  `ClientProcess::beat … crashed - restarting`. Чистить HTML: `sed 's/<[^>]*>//g'`.
- Число запусков клиента = числу «LYRION YANDEX BRIDGE START» в client-stdout.log
  (ожидаемо 1 на тест, без самопроизвольных).
- pid жив всё время: `ps aux | grep lyrion-yandex` (без смен pid).
- При смерти процесса: искать hs_err_pid*, стек в stdout, OOM в `journalctl -k`
  (06.10 23:07/23:10 — смерть БЕЗ следов; после добавления heapdump/OnOutOfMemoryError — смотреть дамп).

## УДЯ-actions на неизвестный id (баг 06.10–07.10, исправлен 07.10)
- Сценарий: УДЯ → action (on/channel/volume) на устройство со старым id (кэш УДЯ/виджет).
- В client-stdout.log НЕ должно быть:
  `handleMessage error: NullPointerException … "deviceLocal" is null` (наблюдалось 06.10 23:10:06
  и 07.10 08:58–09:24 ×5). Должно быть: `UNKNOWN DEVICE ID from Yandex: …` (WARN).
- Ответ облаку приходит быстро (e2e 07.10: HTTP 200 ~1.5с) с
  `action_result: ERROR/DEVICE_UNREACHABLE` — НЕ «client timeout».
- После смены id устройств (ресеты/ресинки) — обновить список в приложении УДЯ и виджеты/сценарии.

## Голосовые «включи …»
- После каждой команды навыка в LMS-логе НЕ должно быть
  `Scanner::scanPathOrURL … No path or URL was requested!` — было из-за `playlist play ""`
  из playSilence при пустом config.silence (23:09:41, 23:12:55, 07.10 09:13:00; исправлено
  guard-ом 07.10). При пустом silence в логе клиента: `PLAY SILENCE SKIPPED: config.silence пустой`.

## Яндекс callback/state
- В data/log.txt за окно теста считать коды: 401 (проблема авторизации; шторм был 06.10
  20:27–20:33), 429/504/500 (ретраи/отказы Яндекса — единичные допустимы):
  `grep -oE 'Batch update failed. Status: [0-9]+' | sort | uniq -c`.

## Пользователи (кто заходил)
- В client-stdout.log: `CloudClient] - RX:` → `authorization=[Bearer …]` — префиксы токенов
  должны совпадать со своим; ЧУЖОЙ префикс = другой пользователь облака (fallback anyUid
  при одном клиенте).
- Команды навыка: `Context.contextCreate - BODY: {"application_id":…,"command":…}` —
  сверять application_id с data/rooms_and_alice_ids.json (станция ⇄ комната).
- Захожу в веб :8888 и Tasker-регистрации: `REQUEST: (GET|POST) http://…:8888/…` (+ action=tasker_ip).

## Прочее (мелочь, разовое — допустимо)
- `NoHttpResponseException: 127.0.0.1:9000` (LMS не ответил клиенту) — разовые ок.
- `NoHttpResponseException: 192.168.1.116:1821` (Tasker-телефон спит) — ок.

## Где логи (бокс .131)
- Клиент: `/var/lib/squeezeboxserver/cache/LyrionYandexBridge/client-stdout.log` (append, полный)
  и `…/data/log.txt` (мультидневный, НЕ хронологичен в начале — брать хронологичный хвост
  от времени старта текущего прогона; искать «LYRION YANDEX BRIDGE START»).
- sa_server (Zeabur): логи недоступны с рабочей машины — только Runtime logs у владельца.

## Ручные деплои на бокс (грабля 07.10 — обязательно!)
- После ЛЮБОГО root-scp в `InstalledPlugins/Plugins/LyrionYandexBridge` или
  `cache/LyrionYandexBridge`: `chown -R squeezeboxserver:nogroup <путь>`, затем
  `find <путь> -user root` — должно быть ПУСТО. Иначе автообновление плагина LMS
  упадёт и выпарошит каталог (см. techContext «грабли»).
- Обновление LMS: юнита systemd нет — `restartserver` через jsonrpc (localhost).
