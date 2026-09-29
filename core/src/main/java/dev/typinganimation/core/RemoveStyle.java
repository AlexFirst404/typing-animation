package dev.typinganimation.core;

import java.util.Locale;

/**
 * How a deleted character leaves (drawn as a ghost). Distances assume the 9 px Minecraft line height and are
 * multiplied by the intensity. Every style fades alpha out ({@code 1 - e}); NONE is always invisible.
 */
public enum RemoveStyle {
    NONE(Easing.LINEAR),
    FADE(Easing.QUAD_OUT),
    FALL(Easing.SINE_IN_OUT),
    SHRINK(Easing.CUBIC_OUT),
    FLY_UP(Easing.CUBIC_OUT),
    SCATTER(Easing.QUAD_OUT);

    private static final double TWO_PI = 2.0 * Math.PI;

    private final Easing defaultEasing;
    private final String translationKey;

    RemoveStyle(Easing defaultEasing) {
        this.defaultEasing = defaultEasing;
        this.translationKey = "typinganimation.remove." + name().toLowerCase(Locale.ROOT);
    }

    /** Curve used when the configured easing is AUTO. */
    public Easing defaultEasing() {
        return defaultEasing;
    }

    /**
     * t = linear progress 0..1 of the exit; at t&gt;=1 (and for NaN t) the ghost is gone (alpha 0).
     * t &lt; 0 is treated as 0. Writes into out (after out.reset()).
     */
    public void apply(float t, float e, float intensity, int seed, CharTransform out) {
        out.reset();
        if (this == NONE || !(t < 1f)) {
            out.alpha = 0f;
            return;
        }
        if (t < 0f) {
            t = 0f;
        }
        if (Float.isNaN(e)) {
            e = t;
        }
        e = Mix.clamp(e, -1f, 2f);
        final float k = Mix.intensity(intensity);
        final float fadeOut = Mix.clamp01(1f - e);
        final int h = Mix.mix(seed);
        switch (this) {
            case FADE:
                out.alpha = fadeOut;
                break;
            case FALL: {
                float dir = (h & 1) == 0 ? 1f : -1f;
                out.dy = e * 9f * k;
                out.rotation = e * 0.35f * dir * k;
                out.alpha = fadeOut;
                break;
            }
            case SHRINK: {
                float s = Math.max(0f, 1f - e * Math.min(k, 1f));
                out.scaleX = s;
                out.scaleY = s;
                out.alpha = fadeOut;
                break;
            }
            case FLY_UP:
                out.dy = -e * 9f * k;
                out.alpha = fadeOut;
                break;
            case SCATTER: {
                double angle = ((h >>> 8) & 0xFFFF) / 65536.0 * TWO_PI;
                float dist = e * 6f * k;
                out.dx = (float) Math.cos(angle) * dist;
                out.dy = (float) Math.sin(angle) * dist;
                float spin = 0.3f + ((h >>> 24) & 0x7F) / 127f * 0.6f;
                out.rotation = e * spin * ((h & 1) == 0 ? 1f : -1f) * k;
                out.alpha = fadeOut;
                break;
            }
            default:
                break;
        }
        out.sanitize();
    }

    /** {@code "typinganimation.remove.<lower_name>"}. */
    public String translationKey() {
        return translationKey;
    }
}
