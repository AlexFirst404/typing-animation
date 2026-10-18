# Typing Animation

**A little motion in every keystroke.**

![Client only](https://img.shields.io/badge/CLIENT-ONLY-AE72FF?style=for-the-badge&labelColor=25174A) ![17 appearance styles](https://img.shields.io/badge/17-APPEAR_STYLES-E860AD?style=for-the-badge&labelColor=572544) ![10 removal styles](https://img.shields.io/badge/10-REMOVE_STYLES-39C9DD?style=for-the-badge&labelColor=174A5A)

Give Minecraft's text a little personality. Letters animate in and out, edits glide into place, and the cursor follows smoothly — in chat, commands, search fields, **signs and hanging signs**. Once letters settle, their vanilla appearance returns.

**Fabric · Forge · NeoForge · English & Russian · No Fabric API required**

## ▸ Type. Edit. Enjoy.

Insert a word, erase a sentence or paste a message. Text shifts smoothly, while pasted text and autocomplete unfold letter by letter.

![Typing, editing and pasting in Minecraft chat](https://raw.githubusercontent.com/AlexFirst404/typing-animation/master/modrinth/gallery/chat-typing.gif)

## ✦ Find your favourite motion

| Appearance | Removal | Timing |
|:---:|:---:|:---:|
| **17 effects** + Off | **10 effects** + Off | **10 easing curves** + Auto |
| Pop, Wave, Scramble, Flip, Swing, Roll, Spiral and more | Fade, Fall, Scatter, Spin, Squash and more | Separate duration and intensity; adjustable stagger and text glide |

![Pop, Scramble, Wave and Drop paired with removal effects](https://raw.githubusercontent.com/AlexFirst404/typing-animation/master/modrinth/gallery/styles.gif)

**New in 1.1:** Slide Right, Zoom Out, Flip, Swing, Roll and Spiral — with five new ways for letters to disappear.

![New animation styles in Typing Animation 1.1](https://raw.githubusercontent.com/AlexFirst404/typing-animation/master/modrinth/gallery/new-styles.gif)

## │ A cursor that follows you

Smooth horizontal **and vertical** movement, with its own speed setting. Keep the vanilla cursor or choose a **bar, underline or translucent block**; pick a colour and thickness to match your style.

![Smooth caret movement with custom shapes and colours](https://raw.githubusercontent.com/AlexFirst404/typing-animation/master/modrinth/gallery/smooth-cursor.gif)

## ▣ Leave a sign

The same animations now work while editing **ordinary and hanging signs**, across all four lines. Text colours, glowing text and normal editing controls stay intact.

![Animated typing and cursor movement on ordinary and hanging signs](https://raw.githubusercontent.com/AlexFirst404/typing-animation/master/modrinth/gallery/signs.gif)

## ⚙ Make it yours

Three tidy settings tabs: **Animations · Cursor · Where to animate**. Tune appearance and removal separately, cap paste delays, choose where animations run, and try everything in the **live preview** with **Demo**.

![Settings tabs and live animation preview](https://raw.githubusercontent.com/AlexFirst404/typing-animation/master/modrinth/gallery/settings-tabs.gif)

Open **Options → Typing Animation…**, the **Config** button in Forge/NeoForge's mod list, or **Mod Menu** on Fabric. Changes apply immediately. Settings are saved in `config/typinganimation.json`.

## / Commands keep their colours

Syntax highlighting stays intact while typing and completing commands with **Tab**.

![Command colours and Tab completion during animations](https://raw.githubusercontent.com/AlexFirst404/typing-animation/master/modrinth/gallery/command-highlighting.gif)

---

### Install & recommended companion

Download the JAR for your **Minecraft version and loader**, then put it in `mods`. Install on your client; the server does not need the mod. **Fabric API is not required.**

**Recommended for Fabric/Quilt: Mod Menu** — opens the settings directly from your mod list. It is an **optional dependency**: [Modrinth](https://modrinth.com/mod/modmenu) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/modmenu).

**Minecraft:** 1.20–1.20.1 · 1.21–1.21.1 · 1.21.4 · 1.21.5 · 1.21.6–1.21.8 · 1.21.9–1.21.10 · 1.21.11 · 26.1–26.1.2 · 26.2 · 26.3.

Use the Forge file on NeoForge 1.20.1. Quilt support is limited to the Fabric files specifically tagged Quilt. Book input is supported from Minecraft 1.21.6. Mods replacing the standard text renderer may conflict.

**Made by AlexFirst** · [Source code on GitHub](https://github.com/AlexFirst404/typing-animation) · **MIT** · Modpacks welcome.

### 🇷🇺 Коротко на русском

Плавный набор в чате, командах, поиске и **обычных и подвесных табличках**. **17 эффектов появления, 10 эффектов удаления**, отдельные длительность и интенсивность, 10 кривых плавности и «Авто».

Курсор плавно движется по горизонтали и между строками. Настраиваются его скорость, цвет, толщина и форма: обычный, палка, подчёркивание или полупрозрачный блок. Три вкладки настроек, живой предпросмотр и «Демо» помогают подобрать свой вариант.

**Настройки → Анимация ввода…**. Для Fabric/Quilt рекомендуем **Mod Menu** по ссылкам выше; зависимость необязательная. Мод только клиентский, Fabric API не нужен. Цвета команд сохраняются, книги поддерживаются с 1.21.6. Автор — **AlexFirst**.

---

## Build & contribute

Each `targets/<Minecraft>-<loader>/` directory is an independent Gradle project. Shared animation code lives in `core/`, Minecraft integrations in `mc/<version>/`, and translations and the pixel logo in `common-resources/`.

Install JDK **17, 21 and 25**. Build scripts discover them automatically; use `JDK17_HOME`, `JDK21_HOME` and `JDK25_HOME` for custom locations. Gradle runs on JDK 21 for Forge/NeoForge and JDK 25 for Fabric; toolchains select the compilation version.

```bash
bash scripts/build-all.sh
# Windows without Bash:
powershell -ExecutionPolicy Bypass -File scripts/build-all.ps1

# One target / an actual in-game test:
bash scripts/run-target.sh 26.3-neoforge build
bash scripts/run-target.sh 26.3-neoforge selftest
```

Release JARs are collected in `dist/`. Always use `--no-daemon` for direct Gradle commands. The standalone `core` Gradle project contains the JUnit tests.

In-game self-tests exercise mixin application, typing, deletion, selections, scrolling, animation styles, both sign editors and all settings tabs. They compare settled text against vanilla pixels, save screenshots, report `SELFTEST PASS` or `SELFTEST FAIL`, then close the client.

```bash
# Record actual Minecraft UI and build GIFs/screenshots:
bash scripts/make-gallery.sh
# Regenerate the two identical platform descriptions and this README:
node scripts/sync-description.mjs
node scripts/sync-description.mjs --check
# Validate release kits without uploading:
node scripts/modrinth-publish.mjs
node scripts/curseforge-publish.mjs
```

The media recorder creates a disposable showcase world and saves frames in the system temporary directory. `tools/MakeGif.java` produces the GIFs in `modrinth/gallery/`. The same media are used on all three platforms.

**Documentation:** [Engineering spec](docs/SPEC.md) · [Modrinth publishing](modrinth/README.md) · [CurseForge publishing](curseforge/README.md). Edit the shared public description in [`docs/project-description.md`](docs/project-description.md), then run the sync script. Publish credentials belong only in process environment variables, never in repository files.

**По-русски:** для сборки нужны JDK 17, 21 и 25; готовые JAR находятся в `dist/`. Команда `selftest` запускает проверку в настоящем клиенте Minecraft. Общее описание для Modrinth, CurseForge и GitHub редактируется в `docs/project-description.md` и переносится командой `node scripts/sync-description.mjs`.
