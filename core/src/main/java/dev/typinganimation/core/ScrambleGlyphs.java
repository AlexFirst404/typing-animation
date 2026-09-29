package dev.typinganimation.core;

/** Deterministic pseudo-random glyphs for the SCRAMBLE appear style. */
public final class ScrambleGlyphs {
    /** How long one scrambled glyph stays before the next one is picked. */
    public static final int PERIOD_MS = 45;

    private static final int[] GLYPHS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789#$%&*+=?@<>".codePoints().toArray();

    private ScrambleGlyphs() {
    }

    /**
     * Printable ASCII glyph for this seed at this time. It stays the same within one {@link #PERIOD_MS}
     * window and is fully determined by {@code seed} and {@code nowMs}.
     */
    public static int pick(int seed, long nowMs) {
        long bucket = Math.floorDiv(nowMs, (long) PERIOD_MS);
        int h = Mix.mix(seed ^ Mix.mix((int) bucket ^ (int) (bucket >>> 32) * 0x27D4EB2F));
        return GLYPHS[(h >>> 1) % GLYPHS.length];
    }
}
