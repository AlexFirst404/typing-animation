package dev.typinganimation.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TypingSoundFeedbackTest {
    @Test void detectsEditsAtAnyCursorPosition() {
        assertEquals(TypingSoundAction.KEY, TypingSoundFeedback.classify("cat", "cart"));
        assertEquals(TypingSoundAction.SPACE, TypingSoundFeedback.classify("ab", "a b"));
        assertEquals(TypingSoundAction.BACKSPACE, TypingSoundFeedback.classify("a long word", "a word"));
        assertEquals(TypingSoundAction.ENTER, TypingSoundFeedback.classify("ab", "a\nb"));
    }

    @Test void replacementIsTypingEvenWhenItShortensTheText() {
        assertEquals(TypingSoundAction.KEY, TypingSoundFeedback.classify("hello world", "hi world"));
        assertEquals(TypingSoundAction.SPACE, TypingSoundFeedback.classify("hello", " "));
    }

    @Test void pasteUnicodeAndDeletionProduceOnlyOneAction() {
        assertEquals(TypingSoundAction.KEY, TypingSoundFeedback.classify("", "Hello\nПривет 👋"));
        assertEquals(TypingSoundAction.KEY, TypingSoundFeedback.classify("A", "A😀"));
        assertEquals(TypingSoundAction.BACKSPACE, TypingSoundFeedback.classify("A😀", "A"));
        assertEquals(TypingSoundAction.BACKSPACE, TypingSoundFeedback.classify("long selection", ""));
    }

    @Test void unchangedOrUninitializedTextStaysSilent() {
        assertNull(TypingSoundFeedback.classify("abc", "abc"));
        assertNull(TypingSoundFeedback.classify(null, "abc"));
        assertNull(TypingSoundFeedback.classify("abc", null));
    }

    @Test void audioHonorsGlobalFieldAndActionSettings() {
        TypingConfig c = new TypingConfig();
        c.typingSounds = true;
        assertTrue(TypingSoundFeedback.shouldPlay(c, FieldKind.CHAT, false, TypingSoundAction.KEY));
        c.animateChat = false;
        assertFalse(TypingSoundFeedback.shouldPlay(c, FieldKind.CHAT, false, TypingSoundAction.KEY));
        assertTrue(TypingSoundFeedback.shouldPlay(c, FieldKind.CHAT, true, TypingSoundAction.KEY));
        c.soundOnDelete = false;
        c.soundOnEnter = false;
        assertFalse(TypingSoundFeedback.shouldPlay(c, FieldKind.OTHER, false, TypingSoundAction.BACKSPACE));
        assertFalse(TypingSoundFeedback.shouldPlay(c, FieldKind.OTHER, false, TypingSoundAction.ENTER));
        c.soundVolume = 0;
        assertFalse(TypingSoundFeedback.shouldPlay(c, FieldKind.OTHER, true, TypingSoundAction.KEY));
        c.soundVolume = .5f;
        c.enabled = false;
        assertFalse(TypingSoundFeedback.shouldPlay(c, FieldKind.OTHER, true, TypingSoundAction.KEY));
    }

    @Test void rateLimiterHandlesRepeatResetAndClockRestart() {
        TypingSoundFeedback f = new TypingSoundFeedback();
        assertTrue(f.tryAcquire(100));
        assertFalse(f.tryAcquire(101));
        assertFalse(f.tryAcquire(124));
        assertTrue(f.tryAcquire(125));
        assertTrue(f.tryAcquire(0));
        f.reset();
        assertTrue(f.tryAcquire(1));
    }
}
