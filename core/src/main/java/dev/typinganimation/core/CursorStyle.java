package dev.typinganimation.core;

import java.util.Locale;

/** Caret shape. VANILLA preserves the host widget's bar or end-of-line underscore. */
public enum CursorStyle {
    VANILLA, BAR, UNDERLINE, BLOCK;

    private final String translationKey = "typinganimation.cursor.style." + name().toLowerCase(Locale.ROOT);

    public String translationKey() {
        return translationKey;
    }
}
