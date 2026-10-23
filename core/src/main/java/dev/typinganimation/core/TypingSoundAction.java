package dev.typinganimation.core;

import java.util.Locale;

/** An audible user edit, rather than a character in an animation frame. */
public enum TypingSoundAction {
    KEY, SPACE, BACKSPACE, ENTER;

    public String path() { return name().toLowerCase(Locale.ROOT); }
}
