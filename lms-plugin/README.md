# Lyrion Yandex Bridge — плагин Lyrion Music Server

Запускает Java-клиент (устройства «музыка» в УДЯ для плееров LMS)
как фоновый процесс LMS: автостарт вместе с LMS, автоперезапуск при падении,
страница управления в веб-интерфейсе LMS (Настройки → Плагины → Lyrion Yandex Bridge).

## Сборка
```
mvn package
# → target/lyrion-yandex-bridge-1.5.jar (fat-jar)
# → target/lyrion-yandex-bridge-1.5-lms-plugin.zip (плагин)
```

## Установка (Debian/Ubuntu, LMS из пакета lyrionmusicserver)
Ставим в СИСТЕМНЫЙ каталог плагинов — LMS его сканирует, но механизм удаления
плагинов LMS его не трогает (в отличие от InstalledPlugins, откуда LMS может
удалять плагины при отложенных операциях):
```
sudo unzip -o target/lyrion-yandex-bridge-1.5-lms-plugin.zip -d /usr/share/squeezeboxserver/Plugins/
sudo chown -R squeezeboxserver:nogroup /usr/share/squeezeboxserver/Plugins/LyrionYandexBridge
sudo systemctl restart lyrionmusicserver
```
Для LMS в Docker / других платформ — распакуйте zip в каталог плагинов вашего дистрибутива
(главное — НЕ в cache/InstalledPlugins, откуда LMS может удалить плагин) и перезапустите LMS.

Миграция со старого имени (SAClient): при первом старте плагин сам перенесёт каталог данных
/var/lib/squeezeboxserver/cache/SAClient (токены, настройки) в .../cache/LyrionYandexBridge.
Старый плагин SAClient перед установкой удалите:
```
sudo rm -rf /usr/share/squeezeboxserver/Plugins/SAClient
```

## Требования
- Java 11+ на хосте LMS (проверяются: путь из настроек → java из PATH → /usr/lib/jvm/*/bin/java)
- Если java нет: `sudo apt install openjdk-17-jre-headless` (или укажите путь в настройках плагина)

## Настройки (веб LMS → Настройки → Плагины → Lyrion Yandex Bridge)
- Блок «Client status»: живая сводка из клиента (LMS, плееры, Яндекс, комнаты, устройства,
  Spotify, облако) — обновляется при открытии страницы
- Запуск клиента (автостарт), статус (Running/Not running + pid), кнопка «Restart»,
  ссылка на веб-интерфейс клиента
- Путь к java (пусто = автопоиск)
- Порт веб-интерфейса клиента (по умолчанию 8888)
- Доступ из сети: «Только локально» / «Все интерфейсы» (смена порта/доступа сама перезапускает клиента)
- Дополнительные аргументы (например --lms.ip=192.168.1.131)

## Файлы данных клиента
`/var/lib/squeezeboxserver/cache/LyrionYandexBridge/` — config.json, data/ (плееры, устройства,
настройки), client-stdout.log (консольный вывод клиента).

## Веб-интерфейс клиента
http://<ip-lms>:8888/ — авторизация в Яндексе/Spotify, настройка плееров и комнат, устройства УДЯ.
При запуске плагином клиент подключается к LMS на 127.0.0.1:<порт LMS> автоматически.

## Публикация для всех пользователей (own plugin repository)

Готовые файлы дистрибутива: `dist-lms/lyrion-yandex-bridge-v1.5.zip` (плагин) и `dist-lms/repo.xml`
(дескриптор репозитория с актуальным SHA1).

Одноразовая публикация на GitHub (5 минут):
1. Создайте ПУБЛИЧНЫЙ репозиторий `https://github.com/new` → имя `lyrion-yandex-bridge` (owner: knovash).
2. Загрузите в ветку main файл `dist-lms/repo.xml` (в корень репозитория, имя repo.xml).
3. Создайте Release: Releases → Draft a new release → tag `v1.5` → прикрепите файл
   `dist-lms/lyrion-yandex-bridge-v1.5.zip` → Publish. Имя asset не меняйте
   (URL из repo.xml указывает на него).
4. Готово. Пользователям дать ссылку для LMS (Настройки → Плагины → Дополнительные репозитории):
   `https://raw.githubusercontent.com/knovash/lyrion-yandex-bridge/main/repo.xml`
   После добавления плагин появится в списке сторонних плагинов с кнопкой установки;
   установка штатная (в InstalledPlugins), перезапуск LMS — и плагин работает.

Выпуск новой версии:
1. Поднять `<version>` в `lms-plugin/LyrionYandexBridge/install.xml`, в pom.xml
   и jarName в `LyrionYandexBridge/ClientProcess.pm`.
2. `mvn package` → обновить `dist-lms/lyrion-yandex-bridge-v<версия>.zip`, пересчитать `sha1sum`,
   поправить `version`, `sha` и `url` в `dist-lms/repo.xml` → залить repo.xml в репозиторий,
   zip — в новый Release. LMS у установивших пользователей сам предложит обновление.
