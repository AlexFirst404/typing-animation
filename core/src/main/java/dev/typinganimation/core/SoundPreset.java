package dev.typinganimation.core;

import java.util.Locale;

/** Local typing sound families. Minecraft-specific sound resources and playback live in the client adapter. */
public enum SoundPreset {
    TYPEWRITER, MECHANICAL, SOFT, LAPTOP;

    private final String translationKey = "typinganimation.sound." + name().toLowerCase(Locale.ROOT);

    public String translationKey() {
        return translationKey;
    }
}
