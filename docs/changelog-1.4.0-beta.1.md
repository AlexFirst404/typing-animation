# 1.4.0-beta.1 · Books and Scholar

- Added page, deletion and cursor animations to the legacy vanilla book editor on Minecraft 1.20–1.21.5, including the signing title and accepted-input sound feedback.
- Fixed modern vanilla page navigation: switching short, empty or similar pages clears old glyphs, removal effects and cursor movement. Existing text does not replay as newly typed text.
- Added optional Scholar integration for its two independent page fields and centered title/author fields. It preserves Scholar's formatting, colours, wrapping, alignment, selection and book editing behaviour.
- Scholar page changes reset both page animations, including when the next pages have identical contents. Initialization, formatting-only changes and page navigation stay silent.
- Scholar character indices are converted from Unicode code points to Java UTF-16 indices, including supplementary characters. Animation and sound settings work without making Scholar a required dependency.

Book pages use **Fields → Multi-line Fields**. Turning that option off restores native rendering; it does not disable book input. This preview is intended for testing before publication.

## По-русски

- Добавлены анимации набора, удаления и курсора в старом редакторе книг на Minecraft 1.20–1.21.5, включая название при подписании и звуки принятого ввода.
- Исправлено перелистывание в новом редакторе: короткие, пустые и похожие страницы больше не считаются новым набором текста. Старые эффекты и движение курсора сбрасываются.
- Добавлена необязательная совместимость со Scholar: две независимые страницы, название и автор. Сохраняются цвета, форматирование, переносы, выравнивание, выделение и обычное редактирование книги.
- Смена страниц Scholar сбрасывает анимации даже при одинаковом содержимом. Открытие книги, перелистывание и смена одного форматирования не воспроизводят звук печати.
- Учтены разные системы индексов символов Scholar и Java, включая символы за пределами BMP. Устанавливать Scholar для работы Typing Animation не требуется.

Страницы книг управляются настройкой **«Поля → Многострочные поля»**. Выключение анимации сохраняет обычный ввод. Это тестовая сборка перед публикацией.
