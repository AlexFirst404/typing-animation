package dev.typinganimation.core;

import java.util.Objects;

/** Plain mutable config with defaults; {@link #clamp()} enforces the documented ranges. */
public final class TypingConfig {
    // Extra public helpers: the allowed ranges (for sliders and clamp()).
    public static final int DURATION_MIN_MS = 40, DURATION_MAX_MS = 1000;
    public static final float INTENSITY_MIN = 0.25f, INTENSITY_MAX = 3.0f;
    public static final int STAGGER_MIN_MS = 0, STAGGER_MAX_MS = 60;
    public static final int MAX_STAGGER_MIN_MS = 0, MAX_STAGGER_MAX_MS = 2000;
    public static final int GLIDE_MIN_MS = 0, GLIDE_MAX_MS = 300;
    public static final int CURSOR_GLIDE_MIN_MS = 0, CURSOR_GLIDE_MAX_MS = 500;
    public static final int CURSOR_WIDTH_MIN = 1, CURSOR_WIDTH_MAX = 4;
    public static final float SOUND_VOLUME_MIN = 0f, SOUND_VOLUME_MAX = 1f;
    public static final float SOUND_PITCH_MIN = 0.6f, SOUND_PITCH_MAX = 1.6f;

    public boolean enabled = true;
    public AppearStyle appearStyle = AppearStyle.SLIDE_UP;
    public RemoveStyle removeStyle = RemoveStyle.FADE;
    public Easing easing = Easing.AUTO;
    /** [40, 1000] */
    public int durationMs = 220;
    /** [40, 1000] */
    public int removeDurationMs = 160;
    /** [0.25, 3.0] */
    public float intensity = 1.0f;
    /** [0.25, 3.0] strength of removal effects, independently of appearance. */
    public float removeIntensity = 1.0f;
    /** [0, 60] per-char delay when several chars are inserted at once (paste, history). */
    public int staggerMs = 14;
    /** [0, 2000] cap on total stagger spread. */
    public int maxStaggerMs = 350;
    /** Chars glide to new x when text shifts / scrolls. */
    public boolean smoothReflow = true;
    /** Caret glides. */
    public boolean smoothCursor = true;
    /** [0, 300] time constant of glide smoothing. */
    public int glideMs = 55;
    /** [0, 500] time constant of horizontal and vertical caret smoothing. */
    public int cursorGlideMs = 55;
    public CursorStyle cursorStyle = CursorStyle.VANILLA;
    public CursorColor cursorColor = CursorColor.VANILLA;
    /** [1, 4] bar width / underline thickness in GUI pixels. */
    public int cursorWidth = 1;
    /** Optional local typing feedback; existing installations remain silent until enabled. */
    public boolean typingSounds = false;
    public SoundPreset soundPreset = SoundPreset.MECHANICAL;
    /** [0, 1] local typing feedback volume. */
    public float soundVolume = 0.35f;
    /** [0.6, 1.6] base playback pitch. */
    public float soundPitch = 1.0f;
    public boolean soundVariation = true;
    public boolean soundOnDelete = true;
    public boolean soundOnEnter = true;
    public boolean animateChat = true;
    public boolean animateOtherFields = true;
    public boolean animateMultiline = true;
    public boolean animateSigns = true;
    /** Unfocused fields change instantly. */
    public boolean onlyWhenFocused = true;
    /** Show button in vanilla Options screen. */
    public boolean optionsButton = true;

    public static TypingConfig defaults() {
        return new TypingConfig();
    }

    public TypingConfig copy() {
        TypingConfig c = new TypingConfig();
        c.copyFrom(this);
        return c;
    }

    /** Extra helper: overwrites every field of this instance with the values of {@code o} (null = defaults). */
    public void copyFrom(TypingConfig o) {
        if (o == null) {
            o = new TypingConfig();
        }
        enabled = o.enabled;
        appearStyle = o.appearStyle;
        removeStyle = o.removeStyle;
        easing = o.easing;
        durationMs = o.durationMs;
        removeDurationMs = o.removeDurationMs;
        intensity = o.intensity;
        removeIntensity = o.removeIntensity;
        staggerMs = o.staggerMs;
        maxStaggerMs = o.maxStaggerMs;
        smoothReflow = o.smoothReflow;
        smoothCursor = o.smoothCursor;
        glideMs = o.glideMs;
        cursorGlideMs = o.cursorGlideMs;
        cursorStyle = o.cursorStyle;
        cursorColor = o.cursorColor;
        cursorWidth = o.cursorWidth;
        typingSounds = o.typingSounds;
        soundPreset = o.soundPreset;
        soundVolume = o.soundVolume;
        soundPitch = o.soundPitch;
        soundVariation = o.soundVariation;
        soundOnDelete = o.soundOnDelete;
        soundOnEnter = o.soundOnEnter;
        animateChat = o.animateChat;
        animateOtherFields = o.animateOtherFields;
        animateMultiline = o.animateMultiline;
        animateSigns = o.animateSigns;
        onlyWhenFocused = o.onlyWhenFocused;
        optionsButton = o.optionsButton;
    }

    /** Clamps numbers to their ranges, replaces null enums and NaN floating-point values by defaults. */
    public void clamp() {
        if (appearStyle == null) appearStyle = AppearStyle.SLIDE_UP;
        if (removeStyle == null) removeStyle = RemoveStyle.FADE;
        if (easing == null) easing = Easing.AUTO;
        if (cursorStyle == null) cursorStyle = CursorStyle.VANILLA;
        if (cursorColor == null) cursorColor = CursorColor.VANILLA;
        if (soundPreset == null) soundPreset = SoundPreset.MECHANICAL;
        durationMs = clampInt(durationMs, DURATION_MIN_MS, DURATION_MAX_MS);
        removeDurationMs = clampInt(removeDurationMs, DURATION_MIN_MS, DURATION_MAX_MS);
        if (Float.isNaN(intensity)) intensity = 1.0f;
        intensity = Math.max(INTENSITY_MIN, Math.min(INTENSITY_MAX, intensity));
        if (Float.isNaN(removeIntensity)) removeIntensity = 1.0f;
        removeIntensity = Math.max(INTENSITY_MIN, Math.min(INTENSITY_MAX, removeIntensity));
        staggerMs = clampInt(staggerMs, STAGGER_MIN_MS, STAGGER_MAX_MS);
        maxStaggerMs = clampInt(maxStaggerMs, MAX_STAGGER_MIN_MS, MAX_STAGGER_MAX_MS);
        glideMs = clampInt(glideMs, GLIDE_MIN_MS, GLIDE_MAX_MS);
        cursorGlideMs = clampInt(cursorGlideMs, CURSOR_GLIDE_MIN_MS, CURSOR_GLIDE_MAX_MS);
        cursorWidth = clampInt(cursorWidth, CURSOR_WIDTH_MIN, CURSOR_WIDTH_MAX);
        if (Float.isNaN(soundVolume)) soundVolume = 0.35f;
        soundVolume = Math.max(SOUND_VOLUME_MIN, Math.min(SOUND_VOLUME_MAX, soundVolume));
        if (Float.isNaN(soundPitch)) soundPitch = 1.0f;
        soundPitch = Math.max(SOUND_PITCH_MIN, Math.min(SOUND_PITCH_MAX, soundPitch));
    }

    private static int clampInt(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TypingConfig)) return false;
        TypingConfig o = (TypingConfig) obj;
        return enabled == o.enabled && appearStyle == o.appearStyle && removeStyle == o.removeStyle
                && easing == o.easing && durationMs == o.durationMs && removeDurationMs == o.removeDurationMs
                && Float.compare(intensity, o.intensity) == 0 && staggerMs == o.staggerMs
                && Float.compare(removeIntensity, o.removeIntensity) == 0
                && maxStaggerMs == o.maxStaggerMs && smoothReflow == o.smoothReflow
                && smoothCursor == o.smoothCursor && glideMs == o.glideMs && animateChat == o.animateChat
                && cursorGlideMs == o.cursorGlideMs && cursorStyle == o.cursorStyle
                && cursorColor == o.cursorColor && cursorWidth == o.cursorWidth
                && typingSounds == o.typingSounds && soundPreset == o.soundPreset
                && Float.compare(soundVolume, o.soundVolume) == 0 && Float.compare(soundPitch, o.soundPitch) == 0
                && soundVariation == o.soundVariation && soundOnDelete == o.soundOnDelete && soundOnEnter == o.soundOnEnter
                && animateOtherFields == o.animateOtherFields && animateMultiline == o.animateMultiline
                && animateSigns == o.animateSigns
                && onlyWhenFocused == o.onlyWhenFocused && optionsButton == o.optionsButton;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, appearStyle, removeStyle, easing, durationMs, removeDurationMs, intensity,
                removeIntensity, staggerMs, maxStaggerMs, smoothReflow, smoothCursor, glideMs, cursorGlideMs,
                cursorStyle, cursorColor, cursorWidth, typingSounds, soundPreset, soundVolume, soundPitch,
                soundVariation, soundOnDelete, soundOnEnter, animateChat, animateOtherFields,
                animateMultiline, animateSigns, onlyWhenFocused, optionsButton);
    }

    @Override
    public String toString() {
        return "TypingConfig{enabled=" + enabled + ", appearStyle=" + appearStyle + ", removeStyle=" + removeStyle
                + ", easing=" + easing + ", durationMs=" + durationMs + ", removeDurationMs=" + removeDurationMs
                + ", intensity=" + intensity + ", removeIntensity=" + removeIntensity
                + ", staggerMs=" + staggerMs + ", maxStaggerMs=" + maxStaggerMs
                + ", smoothReflow=" + smoothReflow + ", smoothCursor=" + smoothCursor + ", glideMs=" + glideMs
                + ", cursorGlideMs=" + cursorGlideMs + ", cursorStyle=" + cursorStyle
                + ", cursorColor=" + cursorColor + ", cursorWidth=" + cursorWidth
                + ", typingSounds=" + typingSounds + ", soundPreset=" + soundPreset
                + ", soundVolume=" + soundVolume + ", soundPitch=" + soundPitch
                + ", soundVariation=" + soundVariation + ", soundOnDelete=" + soundOnDelete
                + ", soundOnEnter=" + soundOnEnter
                + ", animateChat=" + animateChat + ", animateOtherFields=" + animateOtherFields
                + ", animateMultiline=" + animateMultiline + ", animateSigns=" + animateSigns
                + ", onlyWhenFocused=" + onlyWhenFocused
                + ", optionsButton=" + optionsButton + '}';
    }
}
