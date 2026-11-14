# 1.5.0-beta.1 · Command editor compatibility

- Added optional **CommandBlockUIOverhaul** integration for its published Forge 1.20.1 and NeoForge 1.21.1 editors: appearance, deletion, smooth caret motion and accepted-input sounds.
- Added optional **Better Command Block UI** integration across the supported Minecraft source groups, preserving its native command syntax colours, virtual indentation, wrapping, selection, horizontal/vertical scrolling and editing.
- Both editors use **Fields → Multi-line Fields**. Your existing animation, cursor and sound preferences apply. The upstream mods remain optional and keep their own loader/dependency requirements.
- Programmatic command assignment, block/editor navigation, initial text and rejected input stay quiet. User paste/completion produces one feedback action. Legacy `§`-formatted chunks in Better Command Block UI keep their native drawing.
- Added real-widget compatibility fixtures and a source-level survey of eleven popular text-input mods. Runtime validation is recorded separately; source inspection alone is not a compatibility certificate.

This is a preview build for testing. See [the compatibility survey](compatibility-audit-2026-11-13.md) for current candidates and evidence limits.

## По-русски

- Добавлена необязательная совместимость с **CommandBlockUIOverhaul** на опубликованных сборках Forge 1.20.1 и NeoForge 1.21.1: появление и удаление символов, плавный курсор и звук принятого ввода.
- Добавлена необязательная совместимость с **Better Command Block UI** в поддерживаемых группах Minecraft. Сохраняются подсветка команд, виртуальные отступы, переносы, выделение, прокрутка и обычное редактирование.
- Эффекты в обоих редакторах включаются пунктом **«Поля → Многострочные поля»**. Применяются существующие настройки анимации, курсора и звука. Сами сторонние моды необязательны; для их установки нужны подходящие загрузчики и их собственные зависимости.
- Загрузка и программная замена команды, навигация и отклонённый ввод не воспроизводят звук. Принятая вставка/подстановка даёт один отклик. Фрагменты с форматированием через `§` в Better Command Block UI сохраняют родную отрисовку.
- Добавлены проверки с настоящими сторонними полями и анализ одиннадцати популярных модов. Успешные запуски отмечаются отдельно от предположений по исходникам.

Тестовая сборка перед публикацией. [Отчёт по популярным модам](compatibility-audit-2026-11-13.md) содержит источники и границы проверки.
