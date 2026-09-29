package dev.typinganimation.core;

import java.util.Locale;

/**
 * How a newly typed character enters. Distances assume the 9 px Minecraft line height and are multiplied by
 * the intensity; scale-based styles use {@code min(intensity, 1)} as the depth of the effect (a scale can not
 * shrink below 0), rotation is multiplied by the intensity. Every style except NONE also fades alpha in
 * ({@code min(1, e * 1.6)}; FADE uses {@code alpha = e}) and is fully hidden while {@code t < 0}.
 */
public enum AppearStyle {
    NONE(Easing.LINEAR),
    FADE(Easing.QUAD_OUT),
    SLIDE_UP(Easing.CUBIC_OUT),
    SLIDE_DOWN(Easing.CUBIC_OUT),
    SLIDE_LEFT(Easing.CUBIC_OUT),
    POP(Easing.BACK_OUT),
    GROW(Easing.BACK_OUT),
    STRETCH(Easing.QUART_OUT),
    DROP(Easing.BOUNCE_OUT),
    SPIN(Easing.CUBIC_OUT),
    WAVE(Easing.QUAD_OUT),
    SCRAMBLE(Easing.LINEAR);

    /** Linear progress from which SCRAMBLE shows the real glyph. */
    public static final float SCRAMBLE_REVEAL_T = 0.65f;

    private static final float FADE_IN_SPEED = 1.6f;
    private static final float HALF_PI = (float) (Math.PI / 2.0);
    private static final double WAVE_FREQ = 2.5 * Math.PI;

    private final Easing defaultEasing;
    private final String translationKey;

    AppearStyle(Easing defaultEasing) {
        this.defaultEasing = defaultEasing;
        this.translationKey = "typinganimation.appear." + name().toLowerCase(Locale.ROOT);
    }

    /** Curve used when the configured easing is AUTO. */
    public Easing defaultEasing() {
        return defaultEasing;
    }

    /**
     * t = linear progress (may be &lt;0 for not-yet-revealed staggered chars -&gt; fully hidden, alpha 0 unless NONE),
     * e = eased progress, intensity = config multiplier, seed = per-char seed, nowMs for SCRAMBLE glyph cycling.
     * Writes into out (after out.reset()). At t&gt;=1 (and for NaN t) leaves out as identity.
     */
    public void apply(float t, float e, float intensity, int seed, long nowMs, CharTransform out) {
        out.reset();
        if (this == NONE || Float.isNaN(t) || t >= 1f) {
            return;
        }
        if (t < 0f) {
            out.alpha = 0f;
            return;
        }
        if (Float.isNaN(e)) {
            e = t;
        }
        e = Mix.clamp(e, -1f, 2f);
        final float k = Mix.intensity(intensity);
        final float inv = 1f - e;
        final float fadeIn = Mix.clamp01(e * FADE_IN_SPEED);
        switch (this) {
            case FADE:
                out.alpha = Mix.clamp01(e);
                break;
            case SLIDE_UP:
                out.dy = inv * 7f * k;
                out.alpha = fadeIn;
                break;
            case SLIDE_DOWN:
                out.dy = -inv * 7f * k;
                out.alpha = fadeIn;
                break;
            case SLIDE_LEFT:
                out.dx = inv * 8f * k;
                out.alpha = fadeIn;
                break;
            case POP: {
                float s = scale(e, k);
                out.scaleX = s;
                out.scaleY = s;
                out.alpha = fadeIn;
                break;
            }
            case GROW:
                out.scaleY = scale(e, k);
                out.pivotY = 1f;
                out.alpha = fadeIn;
                break;
            case STRETCH:
                out.scaleX = scale(e, k);
                out.pivotX = 0f;
                out.alpha = fadeIn;
                break;
            case DROP:
                out.dy = -inv * 9f * k;
                out.alpha = fadeIn;
                break;
            case SPIN: {
                out.rotation = inv * -HALF_PI * k;
                float s = scale(e, k);
                out.scaleX = s;
                out.scaleY = s;
                out.alpha = fadeIn;
                break;
            }
            case WAVE:
                out.dy = (float) Math.sin(t * WAVE_FREQ) * inv * 3f * k;
                out.alpha = fadeIn;
                break;
            case SCRAMBLE:
                if (t < SCRAMBLE_REVEAL_T) {
                    out.glyph = ScrambleGlyphs.pick(seed, nowMs);
                }
                out.alpha = fadeIn;
                break;
            default:
                break;
        }
        out.sanitize();
    }

    /** Scale that reaches e at intensity >= 1 and is shallower below it; never negative. */
    private static float scale(float e, float k) {
        return Math.max(0f, 1f - (1f - e) * Math.min(k, 1f));
    }

    /** {@code "typinganimation.appear.<lower_name>"}. */
    public String translationKey() {
        return translationKey;
    }
}
