package dev.typinganimation.mc;

import net.minecraft.util.FormattedCharSequence;

/**
 * Duck interface implemented by {@code EditBox} (EditBoxMixin): formats any part of the value exactly like the
 * widget formats the parts it draws. Used for the chars revealed around vanilla's visible window while a scroll
 * glides.
 */
public interface EditBoxAccess {
    /** The widget's formatted form of {@code text}, which starts at index {@code offset} of the value. */
    FormattedCharSequence typinganimation$format(String text, int offset);
}
