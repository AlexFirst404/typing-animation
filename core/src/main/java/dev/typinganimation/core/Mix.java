package dev.typinganimation.core;

/** Small numeric helpers (package-private, allocation-free). */
final class Mix {
    private Mix() {
    }

    /** Avalanching 32-bit mix (murmur3 finaliser with a golden-ratio offset so that 0 does not map to 0). */
    static int mix(int h) {
        h += 0x9E3779B9;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return h;
    }

    static float clamp01(float v) {
        return v <= 0f ? 0f : (v >= 1f ? 1f : v);
    }

    static float clamp(float v, float min, float max) {
        return v <= min ? min : (v >= max ? max : v);
    }

    /** Sanitises a style intensity: NaN/Infinity -> 1, then clamped to [0, 10]. */
    static float intensity(float k) {
        if (!Float.isFinite(k)) {
            return 1f;
        }
        return clamp(k, 0f, 10f);
    }
}
