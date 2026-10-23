package dev.typinganimation.core;

import java.util.Locale;
import java.util.Objects;

/**
 * Ready-made visual settings. Applying a preset changes motion and cursor appearance only: sound, enabled state,
 * field toggles, focus behaviour and the options button remain under the user's control. Preset names are derived
 * from current settings with {@link #match}; there is no persisted name that could become stale after manual edits.
 */
public enum VisualPreset {
    DEFAULT(AppearStyle.SLIDE_UP, RemoveStyle.FADE, Easing.AUTO, 220, 160, 1f, 1f,
            14, 350, true, 55, true, 55, CursorStyle.VANILLA, CursorColor.VANILLA, 1),
    MINIMAL(AppearStyle.FADE, RemoveStyle.FADE, Easing.CUBIC_OUT, 100, 90, 0.35f, 0.35f,
            0, 0, true, 30, true, 35, CursorStyle.VANILLA, CursorColor.VANILLA, 1),
    SOFT(AppearStyle.SLIDE_UP, RemoveStyle.FADE, Easing.SINE_OUT, 260, 180, 0.45f, 0.45f,
            10, 280, true, 90, true, 100, CursorStyle.BAR, CursorColor.LAVENDER, 1),
    BOUNCY(AppearStyle.POP, RemoveStyle.SHRINK, Easing.AUTO, 300, 190, 1.2f, 1f,
            18, 420, true, 65, true, 70, CursorStyle.BAR, CursorColor.GOLD, 2),
    WAVE(AppearStyle.WAVE, RemoveStyle.FADE, Easing.AUTO, 360, 200, 1.1f, 0.8f,
            28, 560, true, 75, true, 80, CursorStyle.UNDERLINE, CursorColor.AQUA, 1),
    GLITCH(AppearStyle.SCRAMBLE, RemoveStyle.SCATTER, Easing.LINEAR, 200, 140, 1f, 1.2f,
            8, 180, false, 0, false, 0, CursorStyle.BLOCK, CursorColor.MINT, 1);

    private final String translationKey;
    /** Private template is never returned or mutated after enum construction. */
    private final TypingConfig settings;

    VisualPreset(AppearStyle appear, RemoveStyle remove, Easing easing, int duration, int removeDuration,
                 float intensity, float removeIntensity, int stagger, int maxStagger,
                 boolean reflow, int glide, boolean smoothCursor, int cursorGlide,
                 CursorStyle cursorStyle, CursorColor cursorColor, int cursorWidth) {
        translationKey = "typinganimation.preset." + name().toLowerCase(Locale.ROOT);
        settings = new TypingConfig();
        settings.appearStyle = appear;
        settings.removeStyle = remove;
        settings.easing = easing;
        settings.durationMs = duration;
        settings.removeDurationMs = removeDuration;
        settings.intensity = intensity;
        settings.removeIntensity = removeIntensity;
        settings.staggerMs = stagger;
        settings.maxStaggerMs = maxStagger;
        settings.smoothReflow = reflow;
        settings.glideMs = glide;
        settings.smoothCursor = smoothCursor;
        settings.cursorGlideMs = cursorGlide;
        settings.cursorStyle = cursorStyle;
        settings.cursorColor = cursorColor;
        settings.cursorWidth = cursorWidth;
    }

    public String translationKey() {
        return translationKey;
    }

    /** Applies only this preset's visual fields to a non-null config; never saves or changes unrelated settings. */
    public void applyTo(TypingConfig config) {
        Objects.requireNonNull(config, "config");
        config.appearStyle = settings.appearStyle;
        config.removeStyle = settings.removeStyle;
        config.easing = settings.easing;
        config.durationMs = settings.durationMs;
        config.removeDurationMs = settings.removeDurationMs;
        config.intensity = settings.intensity;
        config.removeIntensity = settings.removeIntensity;
        config.staggerMs = settings.staggerMs;
        config.maxStaggerMs = settings.maxStaggerMs;
        config.smoothReflow = settings.smoothReflow;
        config.glideMs = settings.glideMs;
        config.smoothCursor = settings.smoothCursor;
        config.cursorGlideMs = settings.cursorGlideMs;
        config.cursorStyle = settings.cursorStyle;
        config.cursorColor = settings.cursorColor;
        config.cursorWidth = settings.cursorWidth;
    }

    /** Returns the exact visual match, or null for custom settings / a null config. Ignores unrelated settings. */
    public static VisualPreset match(TypingConfig config) {
        if (config == null) return null;
        for (VisualPreset preset : ALL) {
            TypingConfig s = preset.settings;
            if (config.appearStyle == s.appearStyle && config.removeStyle == s.removeStyle
                    && config.easing == s.easing && config.durationMs == s.durationMs
                    && config.removeDurationMs == s.removeDurationMs
                    && Float.compare(config.intensity, s.intensity) == 0
                    && Float.compare(config.removeIntensity, s.removeIntensity) == 0
                    && config.staggerMs == s.staggerMs && config.maxStaggerMs == s.maxStaggerMs
                    && config.smoothReflow == s.smoothReflow && config.glideMs == s.glideMs
                    && config.smoothCursor == s.smoothCursor && config.cursorGlideMs == s.cursorGlideMs
                    && config.cursorStyle == s.cursorStyle && config.cursorColor == s.cursorColor
                    && config.cursorWidth == s.cursorWidth) {
                return preset;
            }
        }
        return null;
    }

    /** Avoids a values() array allocation each time a settings screen checks its current label. */
    private static final VisualPreset[] ALL = values();
}
