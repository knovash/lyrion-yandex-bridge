# System Patterns — архитектура и потоки

## Общая схема
```
Яндекс (УДЯ /v1.0/... , навык «Раз Два» /alice/)
   │ HTTPS
   ▼
sa_server (Zeabur, Spring; YandexController — «глупый релей»)
   │ WebSocket, конверт: {correlationId, request{method,url,path,query,headers,body}}
   ▼
sa_client: CloudClient.handleMessage()   [авто-реконнект 5 c; reconnect после OAuth]
   │ разбор конверта → buildContext() → HandlerAll.switchPath()  ← общий роутер с локальным сервером
   ├── /v1.0/user/devices|query|action|unlink → Provider* (УДЯ)
   ├── /alice/  → HandleVoiceAlice (навык: room по application_id из roomsAndAliceIds)
   ├── /cmd     → HandlePathCmd (Tasker/пульт)
   └── /spotify → SpotifyPlayerCommands
   │ ответ по WS: {correlationId, …тело для Яндекса}
   ▼
sa_server возвращает ответ Яндексу КАК ЕСТЬ (без разбора)
```

## Форматы ответов клиент → облако → Яндекс (критично!)
- **УДЯ `/v1.0/...`**: `{correlationId, request_id, payload:{…}}` — Яндекс лишние поля игнорирует.
- **Навык `/alice/`**: `{correlationId, response:{text, end_session:true}, version:"1.0"}` —
  формат вебхука Диалогов. Без `response` Яндекс говорит «навык не отвечает» (баг v1.5, фикс в v1.6).
- `correlationId` всегда сверху — облако матчит по нему ответ с запросом.
- `/cmd`, `/spotify`: `{correlationId, payload:"строка"}`.

## Клиент ↔ LMS
- JSON-RPC по HTTP (команды CLI: `status`, `play`, `mixer volume …`, плейлисты, синхронизация).
- `LmsPlayers.updatePlayers()` — актуализация списка плееров (громкости — только по-плеерно).
- `LmsSearchForIp` — поиск LMS в сети; `Main.lmsServerOnline` — флаг доступности (Boolean!).

## Локальный веб (com.sun.net.httpserver, :8888)
- Страницы: `/`, `/players`, `/settings*`, `/lms`; сводка `/status.json` (её читает плагин LMS);
  авторизация `/auth` (Яндекс OAuth → токены; после — `cloudClient.restart()`), `/auth_spotify`.
- Токены доставляются HTTP-поллингом `LocalAuthBase` (без MQTT!).

## Навык «Раз Два»
- Комната привязывается голосом: «это комната X» / «… с колонкой Y» → `roomsAndAliceIds[application_id] = room`.
- Пока комната не привязана — любой командный запрос отвечает «скажите навыку, это комната и название комнаты».
- Дальше: устройство по комнате (`SmartHome.deviceByRoom`) → плеер (`lmsPlayers.playerByRoom`) → команды.

## Плагин LMS (perl)
`ClientProcess.pm` (запуск/рестарты jar, поиск java), `Settings.pm` + `basic.html` (страница настроек,
блок «Client status» из `/status.json`), `strings.txt` (RU/EN), `install.xml`.
