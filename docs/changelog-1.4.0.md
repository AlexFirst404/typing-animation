# 1.4.0 · Books and Scholar

- **Bring your books to life:** typing, deletion and smooth cursor animations now work in the legacy vanilla book editor on Minecraft 1.20–1.21.5, including the title when signing a book and sound feedback for accepted input.
- **Optional Scholar support:** animate its two page fields independently, plus its centered title and author fields. Scholar keeps control of formatting, colours, wrapping, alignment, selection and editing.
- **Clean page transitions:** turning pages clears the previous page's animation state, including short, empty and identical pages. Existing text stays still instead of replaying as newly typed text.
- **Quiet navigation:** opening a book, turning pages and changing only Scholar formatting do not play typing sounds. Your existing animation, cursor and sound preferences apply while editing.
- **Unicode-aware integration:** Scholar's character positions are mapped to Java text indices, including supplementary characters. Wide signing titles in the legacy editor stay visible within the book's full bounds.

Book pages and Scholar fields use **Fields → Multi-line Fields**. Turn that setting off to use native rendering while keeping normal book input. Typing sounds remain **off by default**.

Client-side only, by **AlexFirst**. **Scholar is optional**, and its own requirements depend on the version you install. Typing Animation does not require Fabric API. **Mod Menu is recommended and optional** on Fabric/Quilt. Choose the file matching your Minecraft version and loader.

## По-русски

- **Анимации в книгах:** набор, удаление и плавное движение курсора теперь работают в старом редакторе книг на Minecraft 1.20–1.21.5, включая название при подписании и звук принятого ввода.
- **Необязательная поддержка Scholar:** две страницы анимируются независимо; также поддерживаются поля названия и автора. Сохраняются форматирование, цвета, переносы, выравнивание, выделение и обычное редактирование.
- **Аккуратное перелистывание:** смена страницы сбрасывает старые эффекты, даже если следующая страница короткая, пустая или с таким же текстом. Уже записанный текст не проигрывает анимацию заново.
- **Тихая навигация:** открытие книги, перелистывание и изменение только форматирования Scholar не воспроизводят звук печати. При редактировании применяются ваши настройки анимации, курсора и звука.
- **Корректная работа с Unicode:** учтены разные системы индексов символов Scholar и Java, включая символы за пределами BMP. Широкие названия при подписании в старом редакторе остаются видимыми в пределах книги.

Страницы книг и поля Scholar управляются настройкой **«Поля → Многострочные поля»**. Её выключение возвращает обычную отрисовку и сохраняет ввод. **Звуки печати изначально выключены.**

Автор: **AlexFirst**. Мод только клиентский. **Scholar необязателен**; его собственные зависимости зависят от установленной версии. Typing Animation не требует Fabric API. **Mod Menu** рекомендуется как необязательная зависимость на Fabric/Quilt. Выбирайте файл под свою версию Minecraft и загрузчик.
