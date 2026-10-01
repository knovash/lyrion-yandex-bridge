# Active Context — что сейчас

## Статус: v1.6 опубликован, всё зелёное (проверено 01.10)

- **v1.6** в GitHub Release (sha zip совпадает с repo.xml), код запушен в `main`
  (коммиты: `fafbf98` → `820f325` → `08c5df8`).
- Основной фикс v1.6: **формат ответа навыка «Раз Два»** — раньше текст уходил в `payload`,
  Яндекс говорил «навык не отвечает»; теперь `response/text/end_session/version` (см. systemPatterns.md).
- `repo.xml` живёт в корне проекта = корень репозитория; `dist-lms/` — staging zip-ов.
- Remote: SSH `git@github.com:knovash/lyrion-yandex-bridge.git`, ветка master → main.

## Что в работе
- **dev: «включи избранное <название>» — ГОТОВО, подтверждено владельцем (01.10)**, работает
  на живом LMS (192.168.1.131, jar заменён вручную). **РЕШЕНИЕ: в релиз НЕ выпускать,
  фича живёт только в ветке dev**, пока владелец не решит иначе. При выпуске:
  merge dev→master + чек-лист релиза (Release раньше пуша repo.xml) + убрать TODO из README.
  Реализация: Utils.translit (дж→j/х→h/я→a) + FavoritesSearch (4 уровня матчинга,
  в т.ч. по-словам-по-порядку и префиксный) + ActionsAsync.channelPlayByName + ветка в HandleVoiceAlice.
- Тестовый стенд: LMS 192.168.1.131 (root/ssh, пароль у владельца), плагин в
  cache/InstalledPlugins, НЕТ unzip — jar обновлять scp-ом прямо в Bin/ + рестарт LMS.
  E2E-проверка: POST http://192.168.1.131:8888/alice/ с application_id из rooms_and_alice_ids.json.

## Следующие шаги (кандидаты)
1. Обновить СВОЙ рабочий инстанс плагина до v1.6 и проверить навык вживую:
   сказать навыку «это комната <название>», затем «что играет».
2. Разобрать открытые TODO (список в progress.md).

## Окружение
- Рабочая машина: linux, JDK 14 (target 11), Maven; LMS в локальной сети.
- Облако: sa_server на Zeabur, сервис `service-6a98566721fc3e07432ef076`, логи — Runtime logs.
- Ветки: работаем в `dev` (эксперименты, установка себе вручную zip-ом), релизы — только
  из `master` → `main` по чек-листу.
