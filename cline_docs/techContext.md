# Tech Context — стек, сборка, релиз, грабли

## Стек
- Java 11 (source/target; JDK на машине — 14), Maven.
- Jackson (JSON), Log4j2, Lombok, org.json; `com.sun.net.httpserver` (локальный веб),
  `java.net.http` HttpClient/WebSocket (облако).
- Плагин LMS — perl (+ HTML-шаблон Template Toolkit).

## Сборка
```
mvn package        # желательно после rm -rf target (см. «Грабли»)
# → target/lyrion-yandex-bridge-<версия>.jar (fat-jar, shade)
# → target/lyrion-yandex-bridge-<версия>-lms-plugin.zip (assembly)
```
`src/assembly/lms-plugin.xml` берёт имя jar из pom (`${project.build.finalName}.jar`) — версию
в нём править НЕ нужно.

## Релиз (порядок критичен!)
1. Поднять `<version>` в: `pom.xml`, `lms-plugin/LyrionYandexBridge/install.xml`,
   jarName в `ClientProcess.pm`.
2. `mvn package` → скопировать zip в `dist-lms/lyrion-yandex-bridge-v<версия>.zip` (старый удалить).
3. `sha1sum` нового zip → поправить `version`, `sha`, `url` в **корневом `repo.xml`**.
4. Создать GitHub **Release** `v<версия>` и приложить zip (имя asset не менять!). — ОБЯЗАТЕЛЬНО ДО пуша repo.xml,
   иначе LMS пользователей увидит обновление и получит 404.
5. `git add -A && git commit && git push origin master:main`
   (remote: `git@github.com:knovash/lyrion-yandex-bridge.git` по SSH; локальная ветка master).
6. LMS установившим сам предложит обновление.

## Git-политика
- В git НЕ попадают: `config.json` (там живые токены!), `data/`, `target/`, `dist-lms/*.zip`,
  `.idea/`, `*.iml`, `dependency-reduced-pom.xml` — всё в `.gitignore`.
- Перед публикацией чего-либо нового проверять `git grep -I -E 'y0__|BQA…|AQAr…|ins_…'` на токены.
- Ветки: `master` → GitHub `main` = РЕЛИЗНАЯ (repo.xml для пользователей живёт на main).
  `dev` = экспериментальная: пушится как `git push origin dev`, в релизы НЕ идёт,
  repo.xml/Releases на main не трогает; себе ставим вручную zip-ом из dev-сборки.
- Слив dev → master → публикация релиза ТОЛЬКО по чек-листу (Release раньше пуша repo.xml).

## Справочные проекты (готовые решения — искать там!)
- `/home/konstantin/IdeaProjects/squeeze-alice` — предшественник клиента (lms, provider, voice, spotify, Tasker, auth, web).
- `/home/konstantin/IdeaProjects/squeeze-alice-cloud` — старый облачный брокер (приём запросов Яндекса, OAuth, Spotify).
- `/home/konstantin/IdeaProjects/sa_server` — АКТУАЛЬНОЕ облако (деплой Zeabur, host `alice-lms.zeabur.app`).
- **MQTT (Hive/HiveMQ) никогда не используется** — только sa_server (HTTP+WS).

## Тестирование
- Юнит-тестов в проекте нет. Smoke-тест: маленький java-класс вне репо (например `/tmp/altest/CloudAliceTest.java`)
  запускается с fat-jar в classpath, через reflection дергает приватный `CloudClient.handleMessage`
  с реальным конвертом облака, WebSocket подменяется заглушкой → проверяем ответ клиенту.
- Перед запуском теста выставить `Main.lmsServerOnline = false` (иначе NPE в `updatePlayers`).

## Известные грабли
- **root-scp на бокс ЛОМАЕТ автообновление плагина (грабля 07.10!)**: LMS-PluginDownloader
  работает от `squeezeboxserver`; root-owned файлы/каталоги в InstalledPlugins он не может
  заменить/удалить → автообновление падает на полпути и ВЫПАРАШИВАЕТ каталог плагина
  (остаётся огрызок, клиент не стартует; 07.10 11:22 сломало v1.10 на боксе .131).
  ПРАВИЛО: после ЛЮБОГО root-scp в InstalledPlugins (или cache/LyrionYandexBridge) сразу
  `chown -R squeezeboxserver:nogroup <путь>`. Проверка: `find <путь> -user root` — пусто.
  Лечение сломанного: rm -rf каталога плагина → распаковка zip от squeezeboxserver
  (`su -s /bin/bash squeezeboxserver -c 'cd …/InstalledPlugins/Plugins && python3 -m zipfile -e zip .'`,
  unzip на боксе НЕТ) → рестарт LMS (`restartserver` через jsonrpc).
- **Assembly + старый target**: если в `target/` остался jar прошлой версии, assembly раньше хватал его
  (захардкоженное имя) — теперь имя из pom, но всё равно собирать после чистки `target/`.
- **Main.lmsServerOnline** — `Boolean`, неинициализирован до старта → NPE при раннем вызове `updatePlayers()`.
- **Логи облака** — читать в Zeabur (Runtime logs): там видно весь конвейер REQUEST/SENT/RECEIVED.
