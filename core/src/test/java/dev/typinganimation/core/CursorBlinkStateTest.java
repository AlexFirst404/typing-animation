package dev.typinganimation.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CursorBlinkStateTest {
    @Test
    void defaultAndDisabledStatesPreserveNativeBlink() {
        CursorBlinkState blink = new CursorBlinkState();
        TypingConfig config = new TypingConfig();
        assertFalse(CursorBlinkState.customized(config));
        assertTrue(blink.visible(true, 999, 1000, config, true));
        assertFalse(blink.visible(false, 0, 1000, config, true));
        config.cursorSoftBlink = true;
        assertFalse(blink.visible(false, 0, 1000, config, false));
        assertEquals(1f, blink.alpha());
        assertEquals(0xFF123456, blink.tint(0xFF123456));
    }

    @Test
    void chosenPeriodIncludesOneVisibleAndOneHiddenHalf() {
        CursorBlinkState blink = new CursorBlinkState();
        TypingConfig config = new TypingConfig();
        config.cursorBlinkMs = 1000;
        assertTrue(blink.visible(false, 0, 1000, config, true));
        assertTrue(blink.visible(false, 499, 1499, config, true));
        assertFalse(blink.visible(true, 500, 1500, config, true));
        assertFalse(blink.visible(true, 999, 1999, config, true));
        assertTrue(blink.visible(false, 1000, 2000, config, true));
        assertTrue(blink.visible(false, -100, 2000, config, true));
    }

    @Test
    void softBlinkFadesTintAndSkipsInvisiblePhase() {
        CursorBlinkState blink = new CursorBlinkState();
        TypingConfig config = new TypingConfig();
        config.cursorBlinkMs = 1000;
        config.cursorSoftBlink = true;
        assertTrue(blink.visible(false, 250, 1250, config, true));
        assertEquals(.5f, blink.alpha(), .00001f);
        assertEquals(0x40123456, blink.tint(0x80123456));
        assertFalse(blink.visible(true, 500, 1500, config, true));
        assertEquals(0f, blink.alpha(), .00001f);
        assertTrue(blink.visible(false, 1000, 2000, config, true));
        assertEquals(1f, blink.alpha(), .00001f);
    }

    @Test
    void initialContentsDoNotKeepCursorVisibleButEditsAndDeletionDo() {
        CursorBlinkState blink = new CursorBlinkState();
        TypingConfig config = new TypingConfig();
        config.cursorKeepVisibleWhileTyping = true;
        blink.observe("initial", 1000);
        assertFalse(blink.visible(false, 300, 1300, config, true));
        blink.observe("initial+", 1500);
        assertTrue(blink.visible(false, 300, 1500, config, true));
        assertTrue(blink.visible(false, 300, 1849, config, true));
        assertTrue(blink.visible(false, 300, 1850, config, true));
        assertFalse(blink.visible(true, 0, 2150, config, true));
        blink.observe("initial", 2150);
        assertTrue(blink.visible(false, 300, 2150, config, true));
        blink.reset();
        blink.observe("initial", 2200);
        assertFalse(blink.visible(false, 300, 2500, config, true));
    }
}
