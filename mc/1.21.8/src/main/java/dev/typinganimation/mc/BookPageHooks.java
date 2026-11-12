package dev.typinganimation.mc;

import net.minecraft.client.gui.components.MultiLineEditBox;

/** A book page is a separate document, even when it differs from the previous page by one character. */
public final class BookPageHooks {
    private BookPageHooks() { }

    /** Called after vanilla has loaded the selected page, never for ordinary typing or paste. */
    public static void pageChanged(MultiLineEditBox page) {
        if (page instanceof TypingStateHolder holder) holder.typinganimation$animation().resetAll();
    }
}
