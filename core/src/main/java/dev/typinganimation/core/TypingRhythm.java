package dev.typinganimation.core;

/** Small, allocation-free cadence estimate. A pause starts a new rhythm; paste sizes never enter it. */
public final class TypingRhythm {
    private long lastMs;
    private boolean observed;
    private float intervalMs = 280f;
    private int intervals;

    public void record(long nowMs) {
        if (!observed || nowMs < lastMs || nowMs - lastMs > 700L) {
            observed = true;
            intervals = 0;
            intervalMs = 280f;
        } else if (nowMs > lastMs) {
            float delta = Math.max(20L, Math.min(700L, nowMs - lastMs));
            intervalMs = intervals == 0 ? delta : intervalMs + (delta - intervalMs) * .35f;
            intervals = Math.min(intervals + 1, 8);
        }
        lastMs = nowMs;
    }

    public float strength(long nowMs) {
        if (!observed || intervals < 2 || nowMs < lastMs || nowMs - lastMs > 700L) return 0f;
        return Math.max(0f, Math.min(1f, (230f - intervalMs) / 180f));
    }

    public float animationScale(long nowMs) { return 1f - .55f * strength(nowMs); }
    public float volumeScale(long nowMs) { return 1f - .45f * strength(nowMs); }
    public int soundIntervalMs(long nowMs) { return 25 + Math.round(35f * strength(nowMs)); }

    public void reset() { observed = false; intervals = 0; intervalMs = 280f; }
}
