**Typing in Minecraft, made smooth.** New characters animate into place, deleted ones drift away, and the text and the cursor glide instead of jumping. It works in chat, in the command line and in most other text fields.

*Animated GIFs and screenshots are in the **Gallery** tab.* *Описание на русском языке находится ниже.*

- **Client-side only.** Nothing is needed on the server, and you can join any server with it, including vanilla ones.
- **No Fabric API needed.** On Fabric, Fabric Loader is enough. [Mod Menu](https://modrinth.com/mod/modmenu) is supported but optional.
- **Identical to vanilla at rest.** Once an animation settles, the text is pixel-for-pixel what vanilla draws. An automated in-game test checks this for every build.

## Features

- **11 appear styles** for new characters: Fade, Slide Up, Slide Down, Slide Left, Pop, Grow, Stretch, Drop, Spin, Wave and Scramble. You can also turn them off.
- **5 remove styles** for deleted characters: Fade, Fall, Shrink, Fly Up and Scatter. These can be turned off too. A deleted character leaves from exactly where it was drawn, even if it was still animating in.
- **Smooth reflow and scrolling.** When you edit in the middle of a line or the field scrolls, the characters glide to their new places instead of jumping.
- **Smooth cursor.** The caret glides to its new position, including after Home/End and mouse clicks.
- **Staggered typing.** Pasted text, chat history and command completions are typed out one character after another instead of appearing all at once.
- **10 easing curves plus Auto:** Linear, Sine, Quadratic, Cubic, Quartic, Exponential, Overshoot, Elastic, Bounce and Sine In-Out. Auto picks the curve that suits each style best.
- **Adjustable** duration, intensity (how far characters travel, spin and bounce), stagger delay and glide speed.
- **Separate toggles** for:
  - chat and the command line;
  - other single-line fields: search boxes, the anvil, world and server names, command blocks, and text fields in other mods' screens that use the vanilla text box;
  - multi-line boxes, such as report comments. On 1.21.6 and newer this also covers the book editor and dialog text inputs.
- **Command highlighting is kept.** Commands keep their syntax colours in chat while the characters animate.

## Settings

- **Where to find them:**
  - *Options → Typing Animation...*;
  - the **Config** button in the NeoForge/Forge mods list;
  - **Mod Menu** on Fabric.
- **Live preview.** The settings screen has a preview field to type into and a **Demo** button that types a sample sentence for you. Every change applies immediately, every option has a tooltip, and **Reset** restores the defaults.
- **Config file:** `config/typinganimation.json`.
  - A few advanced options exist only in this file: the remove duration, the cap on the total stagger, and whether fields that are not focused animate.
  - The Options button can be hidden from the settings screen or in this file.
- **Languages:** English and Russian.

## Supported versions

| Minecraft | NeoForge | Forge | Fabric |
|---|:-:|:-:|:-:|
| 26.3 | ✓ | ✓ | ✓ |
| 26.2 | ✓ | ✓ | ✓ |
| 26.1 – 26.1.2 | ✓ | ✓ | ✓ |
| 1.21.11 | ✓ | ✓ | ✓ |
| 1.21.9 – 1.21.10 | ✓ | ✓ | ✓ |
| 1.21.6 – 1.21.8 | ✓ | ✓ | ✓ |
| 1.21.5 | ✓ | ✓ | ✓ |
| 1.21.4 | ✓ | ✓ | ✓ |
| 1.21 – 1.21.1 | ✓ | ✓ | ✓ |
| 1.20 – 1.20.1 | Forge build ¹ | ✓ | ✓ |

1. On NeoForge 1.20.1, use the Forge build. It has been tested on NeoForge 47.1.106 and is also listed under NeoForge.

Each file is made for all the versions in its row. It was tested on the newest version of the row, and the Fabric file also on the oldest one. If something does not work on your version, please report it.

Pick the file for your Minecraft version and loader in the **Versions** tab; the Modrinth App and other launchers do this automatically.

**Quilt.** Only some of the Fabric builds have been tested on Quilt Loader:
- **1.20.1, 1.21.1, 1.21.11 and 26.2:** work on Quilt Loader 0.30.1.
- **26.3:** works on the beta Quilt Loader 0.31.0-beta.4. The stable Quilt Loader 0.30.1 refuses to load it.
- **Other versions:** not tested on Quilt.

The 1.20–1.20.1, 1.21–1.21.1, 1.21.11 and 26.2 builds are listed under Quilt. The 26.3 build is not, because the stable Quilt Loader does not load it yet; with the Quilt Loader beta you can install the Fabric file.

## Installation

1. Install NeoForge, Forge or Fabric Loader for your Minecraft version. Fabric API is **not** required.
2. Download the matching file from the **Versions** tab and put it into your `mods` folder.
3. Start the game and start typing. On Fabric you can also install [Mod Menu](https://modrinth.com/mod/modmenu) to open the settings from the mods list.

## FAQ

**Does the server need it?**
No. Typing Animation is purely client-side: install it on your own game only. It works on any server, including vanilla ones.

**Does it affect performance?**
It is built to stay light:
- Only text fields that are on screen do any work, and the work grows with the number of visible characters.
- When nothing is moving, the field is drawn the vanilla way. Per-character drawing only happens while characters are animating.
- The animation state is reused from frame to frame instead of being reallocated.

**Does it work with other chat or UI mods?**
Usually, yes.
- **How it hooks in:** Typing Animation hooks the drawing code of the vanilla text box (`EditBox`, and `MultiLineEditBox` for multi-line fields) with plain Mixin injections.
- **What works alongside it:** mods that restyle the chat or add features around the input line while keeping the vanilla text box.
- **What may conflict:** a mod that replaces the text box's drawing entirely. In that case its drawing may win, or the two may not load together.
- **If something goes wrong at runtime:** the animated renderer logs the error and falls back to vanilla drawing for that field, so typing keeps working.

If you find an incompatibility, please report it and name both mods.

**Can I turn it off for some fields only?**
Yes. Chat, other single-line fields and multi-line fields each have their own toggle, and there is a master switch as well.

**Which text fields are not animated?**
Sign editing, the book editor before Minecraft 1.21.6, and text fields that other mods draw themselves instead of using the vanilla text box.

**Can I use it in a modpack?**
Yes, the MIT license allows it.

## License

Typing Animation is released under the **MIT License**.

---

# Typing Animation (русский)

**Плавный набор текста в Minecraft.** Новые символы красиво появляются, удалённые исчезают, а текст и курсор не прыгают, а плавно скользят. Работает в чате, в командной строке и в большинстве других полей ввода.

*Анимированные GIF и скриншоты — во вкладке **Gallery**.*

- **Только клиент.** На сервер ставить ничего не нужно. С модом можно заходить на любые серверы, в том числе ванильные.
- **Не нужен Fabric API.** На Fabric достаточно Fabric Loader. [Mod Menu](https://modrinth.com/mod/modmenu) поддерживается, но не обязателен.
- **В покое текст выглядит точно как в ванилле.** Когда анимация закончилась, текст попиксельно совпадает с ванильным. Автотест в игре проверяет это для каждой сборки.

## Возможности

- **11 стилей появления** новых символов: Проявление, Сдвиг вверх, Сдвиг вниз, Сдвиг влево, Выпрыгивание, Рост, Растяжение, Падение, Вращение, Волна и Расшифровка. Анимацию появления можно и выключить.
- **5 стилей удаления** символов: Растворение, Падение, Сжатие, Взлёт и Разлёт. Их тоже можно выключить. Удалённый символ исчезает с того места, где был нарисован, даже если он ещё не успел до конца появиться.
- **Плавный сдвиг и прокрутка.** Когда правишь текст в середине строки или поле прокручивается, символы плавно переезжают на новое место, а не перескакивают.
- **Плавный курсор.** Курсор скользит к новой позиции, в том числе после Home/End и кликов мышью.
- **Печать по символу.** Вставленный текст, сообщения из истории чата и автодополнение команд печатаются по одному символу, а не появляются целиком.
- **10 кривых анимации и режим «Авто»:** линейная, синусоидальная, квадратичная, кубическая, четвёртой степени, экспоненциальная, с перелётом, упругая, с отскоком и S-образная. «Авто» подбирает кривую, которая лучше всего подходит к стилю.
- **Настраиваются** длительность, интенсивность (насколько далеко смещаются символы, как сильно вращаются и пружинят), задержка между символами и скорость скольжения.
- **Отдельные переключатели** для:
  - чата и командной строки;
  - остальных однострочных полей: поиска, наковальни, названий миров и серверов, командных блоков, а также полей ввода в экранах других модов, если они используют стандартное поле;
  - многострочных полей, например комментария к жалобе. На 1.21.6 и новее это ещё и редактор книг и поля ввода в диалогах.
- **Подсветка команд сохраняется.** Пока символы анимируются, команды в чате остаются раскрашенными.

## Настройки

- **Где найти:**
  - «Настройки → Анимация ввода...»;
  - кнопка **«Настройки»** в списке модов NeoForge/Forge;
  - **Mod Menu** на Fabric.
- **Живой предпросмотр.** В экране настроек есть поле, в котором можно попробовать анимацию, и кнопка **«Демо»**: она сама печатает фразу. Изменения видны сразу, у каждой настройки есть подсказка, а **«Сбросить»** возвращает значения по умолчанию.
- **Файл конфигурации:** `config/typinganimation.json`.
  - Несколько дополнительных параметров есть только в нём: длительность удаления, предел общей задержки при вставке и анимация полей без фокуса.
  - Кнопку в «Настройках» можно скрыть в экране настроек мода или в этом файле.
- **Языки интерфейса:** английский и русский.

## Поддерживаемые версии

| Minecraft | NeoForge | Forge | Fabric |
|---|:-:|:-:|:-:|
| 26.3 | ✓ | ✓ | ✓ |
| 26.2 | ✓ | ✓ | ✓ |
| 26.1 – 26.1.2 | ✓ | ✓ | ✓ |
| 1.21.11 | ✓ | ✓ | ✓ |
| 1.21.9 – 1.21.10 | ✓ | ✓ | ✓ |
| 1.21.6 – 1.21.8 | ✓ | ✓ | ✓ |
| 1.21.5 | ✓ | ✓ | ✓ |
| 1.21.4 | ✓ | ✓ | ✓ |
| 1.21 – 1.21.1 | ✓ | ✓ | ✓ |
| 1.20 – 1.20.1 | Forge-сборка ¹ | ✓ | ✓ |

1. На NeoForge 1.20.1 ставьте Forge-сборку. Она проверена на NeoForge 47.1.106 и на Modrinth также отмечена как NeoForge-версия.

Каждый файл сделан для всех версий в своей строке. Он проверен на самой новой версии строки, а Fabric-сборка ещё и на самой старой. Если на вашей версии что-то не работает, сообщите об этом.

Выберите файл для своей версии Minecraft и загрузчика во вкладке **Versions**: Modrinth App и другие лаунчеры делают это сами.

**Quilt.** На Quilt Loader проверены только некоторые Fabric-сборки:
- **1.20.1, 1.21.1, 1.21.11 и 26.2:** работают на Quilt Loader 0.30.1.
- **26.3:** работает на бета-версии Quilt Loader 0.31.0-beta.4. Стабильный Quilt Loader 0.30.1 её не загружает.
- **Остальные версии** на Quilt не проверялись.

Как Quilt-версии на Modrinth отмечены сборки 1.20–1.20.1, 1.21–1.21.1, 1.21.11 и 26.2. Сборка для 26.3 не отмечена, потому что стабильный Quilt Loader её пока не загружает; на бета-версии Quilt Loader можно поставить Fabric-файл.

## Установка

1. Установите NeoForge, Forge или Fabric Loader для своей версии Minecraft. Fabric API **не нужен**.
2. Скачайте подходящий файл во вкладке **Versions** и положите его в папку `mods`.
3. Запустите игру и начните печатать. На Fabric можно дополнительно поставить [Mod Menu](https://modrinth.com/mod/modmenu), чтобы открывать настройки из списка модов.

## Вопросы и ответы

**Нужно ли ставить мод на сервер?**
Нет. Мод работает только на клиенте: ставьте его только себе. Он работает на любых серверах, в том числе ванильных.

**Влияет ли мод на производительность?**
Мод сделан так, чтобы нагрузка была минимальной:
- Работают только поля ввода, которые сейчас на экране, и объём работы зависит от числа видимых символов.
- Когда ничего не движется, поле рисуется так же, как в ванилле. Посимвольная отрисовка включается только на время анимации.
- Состояние анимации переиспользуется от кадра к кадру, а не создаётся заново.

**Совместим ли мод с другими модами на чат и интерфейс?**
Обычно да.
- **Как он встраивается:** мод подключается к отрисовке стандартного поля ввода (`EditBox`, а для многострочных полей `MultiLineEditBox`) обычными Mixin-инъекциями.
- **С чем уживается:** моды, которые меняют вид чата или добавляют что-то вокруг строки ввода, но оставляют стандартное поле.
- **С чем может конфликтовать:** мод, который полностью заменяет отрисовку поля ввода. Тогда может сработать его отрисовка вместо нашей, или моды могут не загрузиться вместе.
- **Если во время игры что-то пошло не так:** анимированная отрисовка записывает ошибку в лог и возвращается к обычной ванильной отрисовке этого поля, так что печатать можно дальше.

Если нашли несовместимость, сообщите о ней и укажите оба мода.

**Можно ли выключить анимацию только для некоторых полей?**
Да. У чата, остальных однострочных полей и многострочных полей свои переключатели, а ещё есть общий выключатель.

**Какие поля ввода не анимируются?**
Редактирование табличек, редактор книг до Minecraft 1.21.6 и поля ввода, которые другие моды рисуют сами, без стандартного поля.

**Можно ли добавить мод в сборку?**
Да, лицензия MIT это разрешает.

## Лицензия

Typing Animation распространяется по **лицензии MIT**.
