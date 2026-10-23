# Публикация Typing Animation на CurseForge

Страница: [Typing Animation](https://www.curseforge.com/minecraft/mc-mods/typing-animation). **Project ID: `1741028`**, slug: `typing-animation`. Публичные параметры страницы сохранены в `curseforge/project.json`; описание — в `curseforge/description.md`, примечания к выпуску — в `curseforge/changelog.md`.

`scripts/curseforge-publish.mjs` загружает файлы в **уже созданный** проект Minecraft. Проект, описание, иконка, категории, лицензия и ссылка на исходники настраиваются в [кабинете автора](https://authors.curseforge.com/). Создание нового проекта через этот скрипт не поддерживается.

Нужен Node.js 18 или новее. Зависимости устанавливать не требуется.

## Проверить выпуск

```powershell
node scripts/curseforge-publish.mjs
node scripts/curseforge-publish.mjs --json
```

По умолчанию выполняется проверка без загрузок и без записи квитанций. Скрипт читает версию мода из `scripts/target-common.gradle`, цели из `targets/*/target.properties`, проверенные дополнительные загрузчики из `modrinth/project.json` и примечания к выпуску из `curseforge/changelog.md`. Он проверяет все 29 JAR текущего выпуска в `dist/`, даже если выбран `--only`: старые файлы другого выпуска не попадают в загрузку. Проверяются ZIP, метаданные загрузчика, ID и версия мода, заявленный диапазон Minecraft, версия Java в классах и наличие LICENSE. Сборка: `bash scripts/build-all.sh`.

Точные версии Minecraft внутри диапазона берутся из официального манифеста Mojang. Каждому файлу назначаются `Client`, конкретные версии Minecraft, основной и проверенные дополнительные загрузчики, а также `Java N` из **java.release**. У Fabric-файлов Mod Menu (`modmenu`) указан как необязательная зависимость. Quilt добавляется только тем Fabric-целям, которые перечислены в `extra_loaders`; Forge 1.20–1.20.1 также получает NeoForge.

Для проверки существования названий на CurseForge нужен авторский API-токен. Без него локальная проверка показывает предупреждение о непроверенных тегах. Токен создаётся в [Settings → API Tokens](https://authors.curseforge.com/#/settings/api-tokens). Это авторский токен `X-Api-Token`, а не ключ публичного API `x-api-key`.

## Загрузить файлы

Укажите токен в переменной окружения `CURSEFORGE_TOKEN` и числовой ID созданного проекта в `CURSEFORGE_PROJECT`. Не сохраняйте токен в репозитории и не передавайте его аргументом командной строки. ID можно также указать через `--project`.

```powershell
$env:CURSEFORGE_PROJECT = '1741028'
node scripts/curseforge-publish.mjs --dry-run
node scripts/curseforge-publish.mjs --publish --only 26.3-neoforge
node scripts/curseforge-publish.mjs --publish
```

Перед загрузкой проверяются все артефакты и названия тегов через `GET /api/game/versions`. В метаданные передаются **gameVersionNames**: некоторые названия Minecraft повторяются в разных группах, поэтому скрипт не выбирает произвольные числовые ID. Файлы загружаются по одному, начиная со старых версий Minecraft. Тип выпуска берётся из `modrinth/project.json`; текущий выпуск — Release. После одобрения модератором файлы публикуются автоматически.

Успешные загрузки немедленно записываются в `curseforge/releases/<версия>.json` (для текущего выпуска — `1.2.0.json`). Квитанция содержит название файла, SHA-512, ID файла, ID проекта и хеш метаданных; токен в неё не записывается. При повторном запуске такие файлы пропускаются. Изменённый JAR или изменённые метаданные при существующей квитанции останавливают загрузку: обновляйте сведения уже загруженного файла через кабинет автора или документированный `update-file`, вместо создания копии.

## Если загрузка прервалась

Перед каждым POST в квитанцию записывается `pending`. Таймаут, обрыв сети, HTTP 5xx, некорректный ответ или сбой записи после успешной загрузки оставляют эту запись и останавливают работу. Повторный запуск блокируется: автоматических повторов POST нет. HTTP 4xx также останавливает работу, но снимает `pending`, поскольку запрос был отклонён.

Откройте Files нужного проекта и проверьте, появился ли указанный в `pending` файл. Если он появился, перенесите запись `pending` в массив `files`, добавьте подтверждённый числовой `fileId` и время `uploadedAt`, затем установите `pending: null`. Если после проверки установлено, что файл не был создан, установите `pending: null` и повторите запуск. Не снимайте запись, пока результат запроса неизвестен. Не удаляйте весь файл квитанций: остальные записи защищают от повторных загрузок.

На время работы создаётся `<версия>.json.lock`, защищающий от двух одновременных публикаций. После обычного завершения или ошибки он удаляется. После аварийного завершения процесса проверьте, что другой издатель не работает, прежде чем удалить оставшийся lock; запись `pending` всё равно требует проверки.

## Вызов из JavaScript с токеном в памяти

```javascript
const { prepareRelease, publishRelease } = await import('./scripts/curseforge-publish.mjs');
const preview = await prepareRelease({ token, projectId: '1741028' });
// preview не содержит токен и не выполняет загрузок.
const result = await publishRelease({ token, projectId: '1741028', onProgress: console.log });
```

`only` — массив названий папок целей. Для повторного использования уже полученных справочников можно передать `gameVersions` (массив ответа CurseForge) и `manifest` (ответ Mojang). Публикация заново проверяет локальные файлы и сравнивает SHA-512 непосредственно перед каждым POST. `fetchImpl`, `root`, `timeoutMs`, `uploadTimeoutMs` и `apiBase` позволяют выполнять изолированные проверки с подменённым HTTP.

Документация: [CurseForge Upload API](https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-upload-api), [создание и отправка проекта](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project).
