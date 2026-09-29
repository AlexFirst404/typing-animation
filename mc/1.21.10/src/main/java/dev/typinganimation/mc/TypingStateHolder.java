package dev.typinganimation.mc;

import dev.typinganimation.core.FieldAnimationState;
import dev.typinganimation.core.FieldKind;

/**
 * Duck interface implemented (via mixins) by every animated text widget: {@code EditBox} and
 * {@code MultiLineEditBox}. The per-widget animation data lives in a {@link WidgetAnimation} owned by the widget.
 */
public interface TypingStateHolder {
    /** The widget's animation data; created on first use, never null. */
    WidgetAnimation typinganimation$animation();

    /** Animation state of the widget's text (convenience). */
    default FieldAnimationState typinganimation$state() {
        return typinganimation$animation().state;
    }

    /** Which config toggle applies to this widget (chat input = CHAT, set by the ChatScreen mixin). */
    default FieldKind typinganimation$getFieldKind() {
        return typinganimation$animation().kind;
    }

    default void typinganimation$setFieldKind(FieldKind kind) {
        typinganimation$animation().kind = kind == null ? FieldKind.OTHER : kind;
    }

    /**
     * Animate this widget whenever the mod is enabled, regardless of focus and of the per-kind toggles
     * (used by the live preview of the config screen).
     */
    default void typinganimation$setForceAnimate(boolean force) {
        typinganimation$animation().forceAnimate = force;
    }
}
