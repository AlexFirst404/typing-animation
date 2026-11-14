package dev.typinganimation.mc;

/** Optional per-editor state; ordinary EditBox animation remains independent. */
public interface BetterCommandStateHolder {
    BetterCommandAnimation typinganimation$betterCommandAnimation();
}
