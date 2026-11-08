package dev.typinganimation.core;

/** Per-editor blink state; observing initial contents never counts as typing. */
public final class CursorBlinkState {
    private String value;
    private long lastEditMs;
    private boolean edited;
    private float alpha = 1f;

    public void observe(String text, long nowMs) {
        if (text == null) text = "";
        if (value != null && !value.equals(text)) {
            lastEditMs = nowMs;
            edited = true;
        }
        value = text;
    }

    public static boolean customized(TypingConfig cfg) {
        return cfg.cursorBlinkMs != 600 || cfg.cursorSoftBlink || cfg.cursorKeepVisibleWhileTyping;
    }

    /** Outer vanilla focus, visibility and clipping conditions stay in charge. */
    public boolean visible(boolean vanilla, long elapsedMs, long nowMs, TypingConfig cfg, boolean enabled) {
        alpha = 1f;
        if (!enabled || !customized(cfg)) return vanilla;
        if (cfg.cursorKeepVisibleWhileTyping && edited && nowMs >= lastEditMs && nowMs - lastEditMs < 350L) return true;
        int period = Math.max(200, Math.min(2000, cfg.cursorBlinkMs));
        // Restart visibly after typing when requested; otherwise retain vanilla's focus-based phase.
        long elapsed = cfg.cursorKeepVisibleWhileTyping && edited && nowMs >= lastEditMs
                ? Math.max(0L, nowMs - lastEditMs - 350L) : Math.max(0L, elapsedMs);
        float phase = Math.floorMod(elapsed, period) / (float) period;
        if (cfg.cursorSoftBlink) {
            alpha = (float) ((1.0 + Math.cos(2.0 * Math.PI * phase)) * .5);
            // Older vanilla fonts turn nearly transparent colours opaque; skip that region entirely.
            return alpha >= .04f;
        }
        return phase < .5f;
    }

    public int tint(int color) {
        int a = color >>> 24;
        if (a == 0) a = 255;
        return (Math.max(1, Math.round(a * alpha)) << 24) | (color & 0xFFFFFF);
    }

    public float alpha() { return alpha; }
    public void reset() { value = null; edited = false; alpha = 1f; }
}
