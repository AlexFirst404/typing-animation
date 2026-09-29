package dev.typinganimation.mc;

import dev.typinganimation.core.FieldAnimationState;
import dev.typinganimation.core.FieldKind;

/**
 * Duck interface mixed into every animated text widget ({@code EditBox}, {@code MultiLineEditBox}). The per-widget
 * animation state lives in a {@link WidgetAnimator} that the mixin creates lazily.
 */
public interface TypingStateHolder {
    /** The widget's animator (created on first use, never null). */
    WidgetAnimator typinganimation$animator();

    default FieldAnimationState typinganimation$state() {
        return typinganimation$animator().state;
    }

    default FieldKind typinganimation$kind() {
        return typinganimation$animator().kind;
    }

    /** Marks the widget's kind (e.g. CHAT from the ChatScreen mixin); null counts as OTHER. */
    default void typinganimation$setKind(FieldKind kind) {
        typinganimation$animator().kind = kind == null ? FieldKind.OTHER : kind;
    }

    /**
     * A preview widget (the config screen's) animates whenever the mod is enabled, regardless of focus and of the
     * per-kind toggles.
     */
    default void typinganimation$setPreview(boolean preview) {
        typinganimation$animator().preview = preview;
    }
}
