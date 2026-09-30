# Lyrion Yandex Bridge

Устройства «музыка» из Умного дома Яндекса и голосовой навык «Раз Два» для плееров
Lyrion Music Server (LMS).

Java-клиент запускается плагином LMS как фоновый процесс, подключается к облачному
релею (WebSocket) и выполняет запросы Яндекса локально: управление плеерами и группами,
громкость, TTS-уведомления и звуки, Spotify, голосовые команды. Есть веб-интерфейс
настроек (порт 8888): авторизация в Яндексе/Spotify, комнаты и плееры, устройства УДЯ.

## Установка (LMS → Настройки → Плагины)
1. «Дополнительные репозитории» → добавить:
   `https://raw.githubusercontent.com/knovash/lyrion-yandex-bridge/main/repo.xml`
2. Перезапустить LMS и включить плагин **Lyrion Yandex Bridge** в списке сторонних.
3. Требуется Java 11+ на хосте LMS (например, `sudo apt install openjdk-17-jre-headless`).

Подробности (сборка, установка вручную, настройки плагина): [lms-plugin/README.md](lms-plugin/README.md).

## Состав репозитория
- `src/main/java/knovash/saclient/` — java-клиент: провайдер УДЯ, LMS-клиент, навык «Раз Два»,
  Spotify, голос/TTS, облачный транспорт, веб-настройки
- `lms-plugin/` — perl-плагин LMS: запуск клиента, автоперезапуск, страница настроек
- `repo.xml` — дескриптор plugin-репозитория для LMS (в корне, пушится как есть)
- `dist-lms/` — staging релизных zip (в git не попадают)
- `cline_docs/` + `.clinerules` — Memory Bank: контекст проекта для ИИ-агента (Cline),
  читается при старте сессии и обновляется после работы

## Сборка
```
mvn package
# → target/lyrion-yandex-bridge-<версия>.jar (fat-jar)
# → target/lyrion-yandex-bridge-<версия>-lms-plugin.zip (плагин)
```

Выпуск новой версии — чек-лист в [lms-plugin/README.md](lms-plugin/README.md).
