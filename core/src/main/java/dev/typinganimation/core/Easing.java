package dev.typinganimation.core;

import java.util.Locale;

/**
 * Easing curves mapping linear progress {@code t} in [0,1] to eased progress.
 * {@code apply(0) == 0} and {@code apply(1) == 1} exactly for every value; input is clamped to [0,1]
 * (NaN counts as 0). Overshooting curves (BACK_OUT, ELASTIC_OUT) may exceed 1 in between.
 */
public enum Easing {
    AUTO, LINEAR, SINE_OUT, QUAD_OUT, CUBIC_OUT, QUART_OUT, EXPO_OUT, BACK_OUT, ELASTIC_OUT, BOUNCE_OUT, SINE_IN_OUT;

    private static final double BACK_C1 = 1.70158;
    private static final double BACK_C3 = BACK_C1 + 1.0;
    private static final double ELASTIC_C4 = (2.0 * Math.PI) / 3.0;
    private static final double BOUNCE_N1 = 7.5625;
    private static final double BOUNCE_D1 = 2.75;

    private final String translationKey;

    Easing() {
        this.translationKey = "typinganimation.easing." + name().toLowerCase(Locale.ROOT);
    }

    /** Eased value of {@code t}. AUTO behaves like CUBIC_OUT when applied directly. */
    public float apply(float t) {
        if (!(t > 0f)) {
            return 0f; // also NaN
        }
        if (t >= 1f) {
            return 1f;
        }
        final double x = t;
        final double r;
        switch (this) {
            case LINEAR:
                r = x;
                break;
            case SINE_OUT:
                r = Math.sin(x * Math.PI / 2.0);
                break;
            case QUAD_OUT: {
                double u = 1.0 - x;
                r = 1.0 - u * u;
                break;
            }
            case QUART_OUT: {
                double u = 1.0 - x;
                r = 1.0 - u * u * u * u;
                break;
            }
            case EXPO_OUT:
                r = 1.0 - Math.pow(2.0, -10.0 * x);
                break;
            case BACK_OUT: {
                double u = x - 1.0;
                r = 1.0 + BACK_C3 * u * u * u + BACK_C1 * u * u;
                break;
            }
            case ELASTIC_OUT:
                r = Math.pow(2.0, -10.0 * x) * Math.sin((x * 10.0 - 0.75) * ELASTIC_C4) + 1.0;
                break;
            case BOUNCE_OUT:
                r = bounceOut(x);
                break;
            case SINE_IN_OUT:
                r = -(Math.cos(Math.PI * x) - 1.0) / 2.0;
                break;
            case AUTO:
            case CUBIC_OUT:
            default: {
                double u = 1.0 - x;
                r = 1.0 - u * u * u;
                break;
            }
        }
        return (float) r;
    }

    private static double bounceOut(double x) {
        if (x < 1.0 / BOUNCE_D1) {
            return BOUNCE_N1 * x * x;
        } else if (x < 2.0 / BOUNCE_D1) {
            x -= 1.5 / BOUNCE_D1;
            return BOUNCE_N1 * x * x + 0.75;
        } else if (x < 2.5 / BOUNCE_D1) {
            x -= 2.25 / BOUNCE_D1;
            return BOUNCE_N1 * x * x + 0.9375;
        } else {
            x -= 2.625 / BOUNCE_D1;
            return BOUNCE_N1 * x * x + 0.984375;
        }
    }

    /** AUTO -> {@code styleDefault} (CUBIC_OUT if that is null or AUTO itself); any other value -> this. */
    public Easing resolve(Easing styleDefault) {
        if (this != AUTO) {
            return this;
        }
        return styleDefault == null || styleDefault == AUTO ? CUBIC_OUT : styleDefault;
    }

    /** {@code "typinganimation.easing.<lower_name>"}. */
    public String translationKey() {
        return translationKey;
    }
}
