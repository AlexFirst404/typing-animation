package dev.typinganimation.core;

import java.util.Locale;

/** Caret colour overrides. VANILLA leaves the widget's colour and opacity intact. */
public enum CursorColor {
    VANILLA(-1), WHITE(0xFFFFFF), LAVENDER(0xBCA7FF), MINT(0x8FE3B0),
    GOLD(0xFFD166), AQUA(0x70D6FF), RED(0xFF7A90);

    private final int rgb;
    private final String translationKey;

    CursorColor(int rgb) {
        this.rgb = rgb;
        translationKey = "typinganimation.cursor.color." + name().toLowerCase(Locale.ROOT);
    }

    /** 24-bit RGB colour, or -1 for the host widget's vanilla colour. */
    public int rgb() {
        return rgb;
    }

    public String translationKey() {
        return translationKey;
    }
}
