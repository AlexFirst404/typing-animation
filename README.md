# Typing Animation

**RU** · [EN](#english)

Клиентский мод для Minecraft, который делает набор текста плавным. Новые символы красиво появляются, удалённые исчезают, а текст и курсор движутся без рывков.
Работает в чате, в командах и в большинстве других полей ввода: наковальня, поиск в творческом режиме, названия миров и серверов, командные блоки. Там, где версия позволяет, поддерживаются и многострочные поля (книги).

- **Только клиент.** На сервер ставить не нужно, мод никак не влияет на игру по сети.
- **Не нужен Fabric API.** На Fabric достаточно Fabric Loader, Mod Menu поддерживается по желанию.
- **В покое текст выглядит точно как в ванилле.** Автотест проверяет это попиксельно для каждой сборки.

## Возможности

- **Появление символов**, 11 стилей: Проявление, Сдвиг вверх, Сдвиг вниз, Сдвиг влево, Выпрыгивание, Рост, Растяжение, Падение, Вращение, Волна, Расшифровка (или без анимации).
- **Удаление символов**, 5 стилей: Растворение, Падение, Сжатие, Взлёт, Разлёт. Удалённый символ исчезает из того положения, в котором был нарисован.
- **Плавный сдвиг.** Когда правишь текст в середине строки или поле прокручивается, символы скользят на новое место, а не прыгают.
- **Плавный курсор.** Курсор скользит к новой позиции, в том числе при Home/End и кликах мышью.
- **Печать по символу.** Вставленный текст, история чата и автодополнение печатаются по одному символу.
- **10 кривых анимации и режим «Авто»:** линейная, синусоидальная, квадратичная, кубическая, 4-й степени, экспоненциальная, с перелётом, упругая, с отскоком, S-образная.
- **Настройки** длительности, интенсивности, задержки между символами и скорости скольжения. Анимацию можно отдельно включать для чата, других полей и многострочных полей.
- **Цвета подсветки команд** в чате сохраняются во время анимации.

## Настройки

- **Где найти:** «Настройки → Анимация ввода...». В NeoForge и Forge ещё кнопка «Настройки» в списке модов, в Fabric — [Mod Menu](https://modrinth.com/mod/modmenu).
- **Экран настроек** содержит поле живого предпросмотра и кнопку «Демо», которая сама печатает фразу. Изменения видны сразу.
- **Файл конфигурации:** `config/typinganimation.json`. Кнопку в «Настройках» можно скрыть там же.

## Поддерживаемые версии

| Minecraft | NeoForge | Forge | Fabric |
|---|---|---|---|
| 26.3 | ✅ | ✅ | ✅ |
| 26.2 | ✅ | ✅ | ✅ |
| 26.1 – 26.1.2 | ✅ | ✅ | ✅ |
| 1.21.11 | ✅ | ✅ | ✅ |
| 1.21.9 – 1.21.10 | ✅ | ✅ | ✅ |
| 1.21.6 – 1.21.8 | ✅ | ✅ | ✅ |
| 1.21.5 | ✅ | ✅ | ✅ |
| 1.21.4 | ✅ | ✅ | ✅ |
| 1.21 – 1.21.1 | ✅ | ✅ | ✅ |
| 1.20 – 1.20.1 | через Forge-сборку | ✅ | ✅ |

Имена файлов: `typinganimation-1.0.0+<версии>-<загрузчик>.jar`, например `typinganimation-1.0.0+26.3-neoforge.jar`.
Forge-сборку для 1.20.1 можно ставить и на NeoForge 1.20.1 (проверено на NeoForge 47.1.106).

**Quilt.** На Quilt Loader проверены только некоторые Fabric-сборки:
- 1.20.1, 1.21.1, 1.21.11 и 26.2 работают на Quilt Loader 0.30.1;
- 26.3 работает на Quilt Loader 0.31.0-beta.4, а стабильный Quilt Loader 0.30.1 её не загружает;
- остальные версии на Quilt не проверялись.

## Установка

1. Установите загрузчик: NeoForge, Forge или Fabric (для Fabric API не нужен).
2. Положите jar для своей версии и загрузчика в папку `mods`.
3. Запустите игру.

## Сборка из исходников

Каждая пара «версия + загрузчик» — отдельный Gradle-проект в `targets/<версия>-<загрузчик>/` со своим wrapper-ом.

- **JDK:** нужны 17, 21 и 25.
  - JDK 21 запускает Gradle у NeoForge и Forge.
  - JDK 25 запускает Gradle у Fabric (этого требует Loom 1.18).
  - Версию Java для компиляции Gradle выбирает сам через toolchains.
- **Команды:**

```bash
bash scripts/build-all.sh
```

```bash
bash scripts/run-target.sh 26.3-neoforge build
```

- **Без bash:** `powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1`.
- **Результат:** готовые jar складываются в `dist/`.
- **Пути к JDK:** скрипты сами находят JDK в обычных местах установки. Если ваши JDK лежат в другом месте, задайте пути через `JDK17_HOME`, `JDK21_HOME` и `JDK25_HOME`.

**Автотест в игре.** Команда `bash scripts/run-target.sh <цель> selftest` запускает dev-клиент в режиме `TYPINGANIMATION_SELFTEST=1`. Тест проверяет:
- что все mixin-ы применились;
- набор, вставку, удаление, прокрутку и все стили анимации;
- что текст в покое попиксельно совпадает с ванильным;
- кнопку в «Настройках» и экран настроек.

По ходу он сохраняет скриншоты, в конце пишет `SELFTEST PASS` или `SELFTEST FAIL` и закрывает игру.

**Медиа для Modrinth.** Команда `bash scripts/make-gallery.sh` запускает клиент 26.3 (NeoForge) в режиме `TYPINGANIMATION_SHOWCASE=1`: он создаёт плоский мир, печатает в настоящий чат, записывает кадры во временную папку и проверяет, что анимация работала именно в чате. Затем `tools/MakeGif.java` собирает из кадров GIF, и всё складывается в `modrinth/gallery/`. Только проверить чат, без медиа: `bash scripts/run-target.sh 26.3-fabric showcase`.

**Публикация на Modrinth.** Всё для страницы мода лежит в `modrinth/`, пошаговая инструкция — в `modrinth/README.md`. Проверка всех jar и файлов страницы, без отправки на Modrinth: `node scripts/modrinth-publish.mjs`.

### Структура

```
core/            чистая Java без Minecraft: стили, кривые, состояние анимации, конфиг, тесты JUnit
common-resources/ переводы (en_us, ru_ru) и иконка
mc/<версия>/     код для Minecraft (mixin-ы, рендер, экран настроек, автотест), общий для всех загрузчиков этой версии
targets/<версия>-<загрузчик>/  тонкая обвязка загрузчика и сборка
docs/SPEC.md     техническая спецификация
tools/MakeGif.java  сборка GIF из кадров (для галереи Modrinth)
modrinth/        страница мода на Modrinth, галерея и инструкция по публикации (modrinth/README.md)
```

## Лицензия

MIT.

---

<a id="english"></a>
# Typing Animation (English)

A client-side Minecraft mod that makes typing feel smooth. New characters animate in, deleted ones animate out, and the text and caret glide instead of jumping.
It works in chat, commands and most other text fields: anvil, creative search, world and server names, command blocks. Multi-line fields such as books are supported where the version allows it.

- **Client only.** Nothing is needed on the server.
- **No Fabric API needed.** Mod Menu is supported but optional.
- **Text at rest is pixel-identical to vanilla.** This is verified for every build by an in-game self-test.

**Features:**
- 11 appear styles: Fade, Slide Up, Slide Down, Slide Left, Pop, Grow, Stretch, Drop, Spin, Wave, Scramble.
- 5 remove styles: Fade, Fall, Shrink, Fly Up, Scatter.
- Smooth reflow and scrolling, and a smooth caret.
- Pasted text, chat history and completions are typed out one character at a time.
- 10 easing curves plus Auto, and adjustable duration, intensity, stagger and glide.
- Per-context toggles for chat, other fields and multi-line fields.
- Chat command colouring is preserved while animating.

**Settings:**
- Open them from *Options → Typing Animation...*, from the mod list's Config button (NeoForge/Forge), or through Mod Menu (Fabric).
- The settings screen has a live preview field and a Demo button, and changes apply immediately.
- The config file is `config/typinganimation.json`.

**Supported versions:** see the table above. That covers 1.20–1.20.1, 1.21–1.21.1, 1.21.4, 1.21.5, 1.21.6–1.21.8, 1.21.9–1.21.10, 1.21.11, 26.1–26.1.2, 26.2 and 26.3 on NeoForge, Forge and Fabric. On NeoForge 1.20.1 use the Forge jar (tested with NeoForge 47.1.106).

**Quilt:** only some Fabric jars have been tested on Quilt Loader:
- 1.20.1, 1.21.1, 1.21.11 and 26.2 work on Quilt Loader 0.30.1;
- 26.3 works on Quilt Loader 0.31.0-beta.4, and the stable Quilt Loader 0.30.1 refuses to load it;
- other versions have not been tested on Quilt.

**Building:**
- Run `bash scripts/build-all.sh`, or `scripts\build-all.ps1` in PowerShell. Jars are collected in `dist/`.
- JDK 17, 21 and 25 are required: JDK 21 runs Gradle for NeoForge and Forge, and JDK 25 runs it for Fabric.
- The scripts find the JDKs in the usual install locations. Set `JDK17_HOME`, `JDK21_HOME` and `JDK25_HOME` if yours are elsewhere.
- `bash scripts/run-target.sh <target> selftest` runs the in-game self-test.
- `modrinth/` holds the Modrinth page, gallery and project data; `node scripts/modrinth-publish.mjs` checks the release kit and all jars without sending anything, and `modrinth/README.md` (in Russian) describes the publishing steps.
- `bash scripts/make-gallery.sh` records the Modrinth gallery media in a 26.3 world through the real chat and builds `modrinth/gallery/` (GIFs by `tools/MakeGif.java`); `bash scripts/run-target.sh 26.3-<loader> showcase` runs only that in-world chat check.

**License:** MIT.
