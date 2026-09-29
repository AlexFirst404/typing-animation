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
    /** [0, 60] per-char delay when several chars are inserted at once (paste, history). */
    public int staggerMs = 14;
    /** [0, 2000] cap on total stagger spread (json only). */
    public int maxStaggerMs = 350;
    /** Chars glide to new x when text shifts / scrolls. */
    public boolean smoothReflow = true;
    /** Caret glides. */
    public boolean smoothCursor = true;
    /** [0, 300] time constant of glide smoothing. */
    public int glideMs = 55;
    public boolean animateChat = true;
    public boolean animateOtherFields = true;
    public boolean animateMultiline = true;
    /** Unfocused fields change instantly (json only). */
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
        staggerMs = o.staggerMs;
        maxStaggerMs = o.maxStaggerMs;
        smoothReflow = o.smoothReflow;
        smoothCursor = o.smoothCursor;
        glideMs = o.glideMs;
        animateChat = o.animateChat;
        animateOtherFields = o.animateOtherFields;
        animateMultiline = o.animateMultiline;
        onlyWhenFocused = o.onlyWhenFocused;
        optionsButton = o.optionsButton;
    }

    /** Clamps numbers to their ranges, replaces null enums and NaN intensity by defaults. */
    public void clamp() {
        if (appearStyle == null) appearStyle = AppearStyle.SLIDE_UP;
        if (removeStyle == null) removeStyle = RemoveStyle.FADE;
        if (easing == null) easing = Easing.AUTO;
        durationMs = clampInt(durationMs, DURATION_MIN_MS, DURATION_MAX_MS);
        removeDurationMs = clampInt(removeDurationMs, DURATION_MIN_MS, DURATION_MAX_MS);
        if (Float.isNaN(intensity)) intensity = 1.0f;
        intensity = Math.max(INTENSITY_MIN, Math.min(INTENSITY_MAX, intensity));
        staggerMs = clampInt(staggerMs, STAGGER_MIN_MS, STAGGER_MAX_MS);
        maxStaggerMs = clampInt(maxStaggerMs, MAX_STAGGER_MIN_MS, MAX_STAGGER_MAX_MS);
        glideMs = clampInt(glideMs, GLIDE_MIN_MS, GLIDE_MAX_MS);
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
                && maxStaggerMs == o.maxStaggerMs && smoothReflow == o.smoothReflow
                && smoothCursor == o.smoothCursor && glideMs == o.glideMs && animateChat == o.animateChat
                && animateOtherFields == o.animateOtherFields && animateMultiline == o.animateMultiline
                && onlyWhenFocused == o.onlyWhenFocused && optionsButton == o.optionsButton;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, appearStyle, removeStyle, easing, durationMs, removeDurationMs, intensity,
                staggerMs, maxStaggerMs, smoothReflow, smoothCursor, glideMs, animateChat, animateOtherFields,
                animateMultiline, onlyWhenFocused, optionsButton);
    }

    @Override
    public String toString() {
        return "TypingConfig{enabled=" + enabled + ", appearStyle=" + appearStyle + ", removeStyle=" + removeStyle
                + ", easing=" + easing + ", durationMs=" + durationMs + ", removeDurationMs=" + removeDurationMs
                + ", intensity=" + intensity + ", staggerMs=" + staggerMs + ", maxStaggerMs=" + maxStaggerMs
                + ", smoothReflow=" + smoothReflow + ", smoothCursor=" + smoothCursor + ", glideMs=" + glideMs
                + ", animateChat=" + animateChat + ", animateOtherFields=" + animateOtherFields
                + ", animateMultiline=" + animateMultiline + ", onlyWhenFocused=" + onlyWhenFocused
                + ", optionsButton=" + optionsButton + '}';
    }
}
