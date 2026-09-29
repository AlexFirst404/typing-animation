package dev.typinganimation.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypingConfigTest {

    @Test
    void defaultsMatchSpec() {
        TypingConfig c = TypingConfig.defaults();
        assertTrue(c.enabled);
        assertEquals(AppearStyle.SLIDE_UP, c.appearStyle);
        assertEquals(RemoveStyle.FADE, c.removeStyle);
        assertEquals(Easing.AUTO, c.easing);
        assertEquals(220, c.durationMs);
        assertEquals(160, c.removeDurationMs);
        assertEquals(1.0f, c.intensity);
        assertEquals(14, c.staggerMs);
        assertEquals(350, c.maxStaggerMs);
        assertTrue(c.smoothReflow);
        assertTrue(c.smoothCursor);
        assertEquals(55, c.glideMs);
        assertTrue(c.animateChat);
        assertTrue(c.animateOtherFields);
        assertTrue(c.animateMultiline);
        assertTrue(c.onlyWhenFocused);
        assertTrue(c.optionsButton);
        TypingConfig clamped = c.copy();
        clamped.clamp();
        assertEquals(c, clamped, "defaults are within range");
    }

    @Test
    void clampEnforcesRanges() {
        TypingConfig c = new TypingConfig();
        c.durationMs = 5;
        c.removeDurationMs = 99999;
        c.intensity = 0.01f;
        c.staggerMs = -3;
        c.maxStaggerMs = 5000;
        c.glideMs = 301;
        c.appearStyle = null;
        c.removeStyle = null;
        c.easing = null;
        c.clamp();
        assertEquals(40, c.durationMs);
        assertEquals(1000, c.removeDurationMs);
        assertEquals(0.25f, c.intensity);
        assertEquals(0, c.staggerMs);
        assertEquals(2000, c.maxStaggerMs);
        assertEquals(300, c.glideMs);
        assertEquals(AppearStyle.SLIDE_UP, c.appearStyle);
        assertEquals(RemoveStyle.FADE, c.removeStyle);
        assertEquals(Easing.AUTO, c.easing);

        c.intensity = 9f;
        c.staggerMs = 61;
        c.maxStaggerMs = -1;
        c.glideMs = -1;
        c.durationMs = Integer.MAX_VALUE;
        c.removeDurationMs = Integer.MIN_VALUE;
        c.clamp();
        assertEquals(3f, c.intensity);
        assertEquals(60, c.staggerMs);
        assertEquals(0, c.maxStaggerMs);
        assertEquals(0, c.glideMs);
        assertEquals(1000, c.durationMs);
        assertEquals(40, c.removeDurationMs);

        c.intensity = Float.NaN;
        c.clamp();
        assertEquals(1f, c.intensity);
        c.intensity = Float.POSITIVE_INFINITY;
        c.clamp();
        assertEquals(3f, c.intensity);
        c.intensity = Float.NEGATIVE_INFINITY;
        c.clamp();
        assertEquals(0.25f, c.intensity);

        TypingConfig inRange = new TypingConfig();
        inRange.durationMs = 40;
        inRange.intensity = 3f;
        inRange.glideMs = 0;
        TypingConfig before = inRange.copy();
        inRange.clamp();
        assertEquals(before, inRange, "values on the bounds are kept");
    }

    @Test
    void copyIsIndependentAndEqual() {
        TypingConfig a = new TypingConfig();
        a.appearStyle = AppearStyle.SCRAMBLE;
        a.intensity = 2.5f;
        a.onlyWhenFocused = false;
        TypingConfig b = a.copy();
        assertNotSame(a, b);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        b.durationMs = 500;
        assertEquals(220, a.durationMs);
        assertNotEquals(a, b);
        a.copyFrom(b);
        assertEquals(a, b);
        a.copyFrom(null);
        assertEquals(TypingConfig.defaults(), a);
        assertNotNull(a.toString());
    }

    @Test
    void fieldKindIsAnimated() {
        TypingConfig c = new TypingConfig();
        for (FieldKind k : FieldKind.values()) {
            assertTrue(FieldKind.isAnimated(k, c));
        }
        assertTrue(FieldKind.isAnimated(null, c));
        assertFalse(FieldKind.isAnimated(FieldKind.CHAT, null));

        c.animateChat = false;
        assertFalse(FieldKind.isAnimated(FieldKind.CHAT, c));
        assertTrue(FieldKind.isAnimated(FieldKind.OTHER, c));
        assertTrue(FieldKind.isAnimated(FieldKind.MULTILINE, c));

        c.animateChat = true;
        c.animateOtherFields = false;
        assertTrue(FieldKind.isAnimated(FieldKind.CHAT, c));
        assertFalse(FieldKind.isAnimated(FieldKind.OTHER, c));
        assertFalse(FieldKind.isAnimated(null, c));
        assertTrue(FieldKind.isAnimated(FieldKind.MULTILINE, c));

        c.animateOtherFields = true;
        c.animateMultiline = false;
        assertFalse(FieldKind.isAnimated(FieldKind.MULTILINE, c));
        assertTrue(FieldKind.isAnimated(FieldKind.OTHER, c));

        c.animateMultiline = true;
        c.enabled = false;
        for (FieldKind k : FieldKind.values()) {
            assertFalse(FieldKind.isAnimated(k, c));
        }
    }

    @Test
    void modConstants() {
        assertEquals("typinganimation", TypingAnimationMod.MOD_ID);
        assertEquals("Typing Animation", TypingAnimationMod.MOD_NAME);
        assertNotNull(TypingAnimationMod.LOGGER);
    }
}
