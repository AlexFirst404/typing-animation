package dev.typinganimation.core;

/** Input-only audio policy, independent of Minecraft and rendering frame rate. */
public final class TypingSoundFeedback {
    public static final long MIN_INTERVAL_MS = 25;
    private long lastSoundMs;
    private boolean played;

    /** One result per edit: pasting a paragraph never produces a burst of sounds. */
    public static TypingSoundAction classify(String before, String after) {
        if (before == null || after == null || before.equals(after)) return null;
        int start = 0;
        int oldEnd = before.length(), newEnd = after.length();
        while (start < oldEnd && start < newEnd && before.charAt(start) == after.charAt(start)) start++;
        while (oldEnd > start && newEnd > start && before.charAt(oldEnd - 1) == after.charAt(newEnd - 1)) {
            oldEnd--;
            newEnd--;
        }
        int inserted = newEnd - start;
        if (inserted == 0) return TypingSoundAction.BACKSPACE;
        if (inserted == 1) {
            char c = after.charAt(start);
            if (c == '\n' || c == '\r') return TypingSoundAction.ENTER;
            if (c == ' ' || c == '\t') return TypingSoundAction.SPACE;
        }
        return TypingSoundAction.KEY;
    }

    public static boolean shouldPlay(TypingConfig cfg, FieldKind kind, boolean previewField,
                                     TypingSoundAction action) {
        return cfg != null && action != null && cfg.enabled && cfg.typingSounds && cfg.soundVolume > 0
                && (previewField || FieldKind.isAnimated(kind, cfg))
                && (action != TypingSoundAction.BACKSPACE || cfg.soundOnDelete)
                && (action != TypingSoundAction.ENTER || cfg.soundOnEnter);
    }

    /** Bounds overlap during key repeat without dropping normal human typing. Uses a monotonic clock. */
    public boolean tryAcquire(long nowMs) {
        if (played && nowMs >= lastSoundMs && nowMs - lastSoundMs < MIN_INTERVAL_MS) return false;
        played = true;
        lastSoundMs = nowMs;
        return true;
    }

    public void reset() { played = false; }
}
