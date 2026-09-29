package dev.typinganimation.core;

/** Kind of text widget; decides which per-kind toggle of {@link TypingConfig} applies. */
public enum FieldKind {
    CHAT, OTHER, MULTILINE;

    /** {@code cfg.enabled} and the per-kind toggle. A null kind counts as OTHER; a null config gives false. */
    public static boolean isAnimated(FieldKind kind, TypingConfig cfg) {
        if (cfg == null || !cfg.enabled) {
            return false;
        }
        if (kind == CHAT) {
            return cfg.animateChat;
        }
        if (kind == MULTILINE) {
            return cfg.animateMultiline;
        }
        return cfg.animateOtherFields;
    }
}
