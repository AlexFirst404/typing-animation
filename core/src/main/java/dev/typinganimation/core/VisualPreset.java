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
            8, 180, false, 0, false, 0, CursorStyle.BLOCK, CursorColor.MINT, 1),
    TERMINAL(AppearStyle.NONE, RemoveStyle.NONE, Easing.LINEAR, 40, 40, 0.25f, 0.25f,
            0, 0, false, 0, false, 0, CursorStyle.BLOCK, CursorColor.MINT, 1, 900, false, true),
    TYPEWRITER(AppearStyle.SLIDE_DOWN, RemoveStyle.FALL, Easing.EXPO_OUT, 100, 100, 0.3f, 0.35f,
            16, 300, false, 0, false, 0, CursorStyle.BAR, CursorColor.GOLD, 1, 700, false, true),
    SNAPPY(AppearStyle.SLIDE_RIGHT, RemoveStyle.SLIDE_LEFT, Easing.EXPO_OUT, 120, 100, 0.5f, 0.55f,
            3, 120, true, 25, true, 25, CursorStyle.BAR, CursorColor.WHITE, 1, 450, false, true),
    FLOATING(AppearStyle.SLIDE_UP, RemoveStyle.FLY_UP, Easing.SINE_IN_OUT, 420, 300, 0.65f, 0.65f,
            24, 600, true, 120, true, 140, CursorStyle.UNDERLINE, CursorColor.AQUA, 1, 1000, true, true),
    ELASTIC(AppearStyle.GROW, RemoveStyle.SQUASH, Easing.ELASTIC_OUT, 420, 240, 1.1f, 0.85f,
            20, 500, true, 80, true, 90, CursorStyle.BAR, CursorColor.LAVENDER, 2, 650, true, true),
    RETRO(AppearStyle.FLIP, RemoveStyle.SHRINK, Easing.CUBIC_OUT, 220, 140, 0.85f, 0.75f,
            12, 300, true, 35, false, 0, CursorStyle.BLOCK, CursorColor.GOLD, 1, 700, false, true),
    SWING(AppearStyle.SWING, RemoveStyle.FLY_UP, Easing.SINE_IN_OUT, 460, 280, 0.8f, 0.6f,
            22, 520, true, 95, true, 110, CursorStyle.BAR, CursorColor.RED, 1, 900, true, true),
    ORBIT(AppearStyle.SPIRAL, RemoveStyle.SPIRAL, Easing.CUBIC_OUT, 380, 260, 0.65f, 0.7f,
            20, 480, true, 70, true, 85, CursorStyle.UNDERLINE, CursorColor.LAVENDER, 1, 850, true, true),
    PAPER(AppearStyle.DROP, RemoveStyle.FALL, Easing.AUTO, 340, 280, 0.65f, 0.8f,
            26, 550, true, 80, true, 80, CursorStyle.BAR, CursorColor.WHITE, 1, 800, true, true),
    CINEMATIC(AppearStyle.ZOOM_OUT, RemoveStyle.FADE, Easing.SINE_OUT, 480, 320, 0.5f, 0.5f,
            32, 750, true, 150, true, 170, CursorStyle.UNDERLINE, CursorColor.GOLD, 1, 1200, true, true);

    private final String translationKey;
    /** Private template is never returned or mutated after enum construction. */
    private final TypingConfig settings;

    VisualPreset(AppearStyle appear, RemoveStyle remove, Easing easing, int duration, int removeDuration,
                 float intensity, float removeIntensity, int stagger, int maxStagger,
                 boolean reflow, int glide, boolean smoothCursor, int cursorGlide,
                 CursorStyle cursorStyle, CursorColor cursorColor, int cursorWidth) {
        this(appear, remove, easing, duration, removeDuration, intensity, removeIntensity, stagger, maxStagger,
                reflow, glide, smoothCursor, cursorGlide, cursorStyle, cursorColor, cursorWidth, 600, false, false);
    }

    VisualPreset(AppearStyle appear, RemoveStyle remove, Easing easing, int duration, int removeDuration,
                 float intensity, float removeIntensity, int stagger, int maxStagger,
                 boolean reflow, int glide, boolean smoothCursor, int cursorGlide,
                 CursorStyle cursorStyle, CursorColor cursorColor, int cursorWidth,
                 int cursorBlink, boolean softBlink, boolean visibleWhileTyping) {
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
        settings.cursorBlinkMs = cursorBlink;
        settings.cursorSoftBlink = softBlink;
        settings.cursorKeepVisibleWhileTyping = visibleWhileTyping;
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
        config.cursorCustomColor = settings.cursorCustomColor;
        config.cursorHexColor = settings.cursorHexColor;
        config.cursorBlinkMs = settings.cursorBlinkMs;
        config.cursorSoftBlink = settings.cursorSoftBlink;
        config.cursorKeepVisibleWhileTyping = settings.cursorKeepVisibleWhileTyping;
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
                    && config.cursorWidth == s.cursorWidth
                    && config.cursorCustomColor == s.cursorCustomColor
                    && Objects.equals(config.cursorHexColor, s.cursorHexColor)
                    && config.cursorBlinkMs == s.cursorBlinkMs
                    && config.cursorSoftBlink == s.cursorSoftBlink
                    && config.cursorKeepVisibleWhileTyping == s.cursorKeepVisibleWhileTyping) {
                return preset;
            }
        }
        return null;
    }

    /** Avoids a values() array allocation each time a settings screen checks its current label. */
    private static final VisualPreset[] ALL = values();
}
