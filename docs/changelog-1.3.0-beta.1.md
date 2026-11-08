# Typing Animation 1.3.0-beta.1

Local preview build. Not published to GitHub Releases, Modrinth or CurseForge.

## Changes

- Choose a Basic or Detailed settings interface on the first visit. The choice is remembered and can be changed at any time. Basic mode hides technical controls without changing their saved values. Detailed pages remain usable at a scaled screen size of 320 × 240.
- Save named presets containing animation, cursor and sound settings. Apply, duplicate, rename, import and export presets from the local library. Applying a preset preserves the enabled fields, settings entry point and interface mode.
- Optional adaptive typing shortens animations and softens rapid keyboard feedback. Slow typing retains the full configured animation. Pasting is handled as a bulk edit rather than an artificial burst of keystrokes.
- Custom cursor colour in `#RRGGBB` format, adjustable blink period, soft blinking and an option to keep the cursor visible while typing. Focus, clipping and native selection behavior remain in charge.
- Import custom Ogg Vorbis typing sound packs from the Sound Library, audition them and choose a pack without replacing the built-in sound families. Existing Minecraft resource-pack overrides continue to work.
- The settings button may be hidden when Mod Menu provides another entry point. Without Mod Menu the button stays available, including when moving a configuration between loaders.

## Проверка новой версии

При первом открытии настроек выберите **«Базовый»** или **«Подробный»**. Кнопка режима находится внизу и позволяет изменить выбор позже. Смена режима не сбрасывает ваши эффекты и звук.

**«Пресеты»** открывает библиотеку собственных стилей. Укажите имя и сохраните текущие настройки. Для импорта или экспорта можно указать путь к JSON-файлу. При импорте одноимённого пресета создаётся отдельная копия.

**«Звуковые наборы»** открывает библиотеку звуков. Формат ZIP и пример манифеста описаны в [custom-sound-packs.md](custom-sound-packs.md). После импорта игра перезагружает ресурсы; дождитесь завершения, прослушайте набор и нажмите «Использовать».

В подробном режиме доступны HEX-цвет курсора, период и плавность мигания. Некорректное значение HEX подсвечивается и не заменяет последний корректный цвет. Адаптация к скорости печати включается отдельной настройкой.

Звуки остаются выключенными по умолчанию. Готовые визуальные пресеты меняют только визуальные параметры; пользовательские пресеты также сохраняют звук.
