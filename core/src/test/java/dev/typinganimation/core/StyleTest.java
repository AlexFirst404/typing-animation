package dev.typinganimation.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StyleTest {
    private static final float[] INTENSITIES = {0f, 0.25f, 1f, 3f, 10f, Float.NaN, Float.POSITIVE_INFINITY, -2f};
    private static final int[] SEEDS = {0, 1, -1, 42, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x12345678};
    private static final long[] TIMES = {0L, 17L, 123_456_789L, -5000L, Long.MAX_VALUE / 4};

    private static void assertSane(CharTransform t, String ctx) {
        assertTrue(Float.isFinite(t.dx), ctx + " dx " + t);
        assertTrue(Float.isFinite(t.dy), ctx + " dy " + t);
        assertTrue(Float.isFinite(t.scaleX) && t.scaleX >= 0f, ctx + " scaleX " + t);
        assertTrue(Float.isFinite(t.scaleY) && t.scaleY >= 0f, ctx + " scaleY " + t);
        assertTrue(Float.isFinite(t.rotation), ctx + " rot " + t);
        assertTrue(t.alpha >= 0f && t.alpha <= 1f, ctx + " alpha " + t);
        assertTrue(t.pivotX >= 0f && t.pivotX <= 1f && t.pivotY >= 0f && t.pivotY <= 1f, ctx + " pivot " + t);
        assertTrue(t.glyph == -1 || (t.glyph >= 0x21 && t.glyph <= 0x7E), ctx + " glyph " + t);
    }

    // ------------------------------------------------------------------ appear

    @ParameterizedTest
    @EnumSource(AppearStyle.class)
    void appearIsIdentityAtOrAfterEnd(AppearStyle s) {
        CharTransform out = new CharTransform();
        for (float t : new float[]{1f, 1.0001f, 2f, 1000f, Float.POSITIVE_INFINITY, Float.NaN}) {
            for (float e : new float[]{1f, 0f, 0.5f, 1.3f, Float.NaN}) {
                for (float k : INTENSITIES) {
                    out.dx = 99; // must be reset
                    s.apply(t, e, k, 7, 1234L, out);
                    assertTrue(out.isIdentity(), s + " t=" + t + " e=" + e + " k=" + k + " " + out);
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(AppearStyle.class)
    void appearIsHiddenBeforeStart(AppearStyle s) {
        CharTransform out = new CharTransform();
        for (float t : new float[]{-0.0001f, -0.5f, -3f, Float.NEGATIVE_INFINITY}) {
            s.apply(t, 0f, 1f, 3, 100L, out);
            if (s == AppearStyle.NONE) {
                assertTrue(out.isIdentity(), s + " " + out);
            } else {
                assertEquals(0f, out.alpha, s + " t=" + t);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(AppearStyle.class)
    void appearRangesAreSaneForEveryEasing(AppearStyle s) {
        CharTransform out = new CharTransform();
        for (Easing easing : Easing.values()) {
            Easing ez = easing.resolve(s.defaultEasing());
            for (int i = 0; i < 100; i++) {
                float t = i / 100f;
                float e = ez.apply(t);
                for (float k : INTENSITIES) {
                    for (int seed : SEEDS) {
                        for (long now : TIMES) {
                            s.apply(t, e, k, seed, now, out);
                            assertSane(out, s + "/" + easing + " t=" + t + " k=" + k);
                        }
                    }
                }
            }
        }
    }

    @Test
    void noneIsAlwaysIdentity() {
        CharTransform out = new CharTransform();
        for (int i = -10; i <= 20; i++) {
            AppearStyle.NONE.apply(i / 10f, i / 10f, 1f, i, i, out);
            assertTrue(out.isIdentity());
        }
    }

    @Test
    void appearSemantics() {
        CharTransform o = new CharTransform();
        float e = 0.25f, t = 0.1f;

        AppearStyle.FADE.apply(t, e, 1f, 0, 0, o);
        assertEquals(0.25f, o.alpha, 1e-6f);
        assertEquals(0f, o.dx);
        assertEquals(0f, o.dy);

        AppearStyle.SLIDE_UP.apply(t, e, 1f, 0, 0, o);
        assertEquals(0.75f * 7f, o.dy, 1e-5f); // starts below (GUI y grows downwards) and moves up
        assertEquals(Math.min(1f, e * 1.6f), o.alpha, 1e-6f);
        AppearStyle.SLIDE_UP.apply(t, e, 2f, 0, 0, o);
        assertEquals(0.75f * 14f, o.dy, 1e-5f);

        AppearStyle.SLIDE_DOWN.apply(t, e, 1f, 0, 0, o);
        assertEquals(-0.75f * 7f, o.dy, 1e-5f);

        AppearStyle.SLIDE_LEFT.apply(t, e, 1f, 0, 0, o);
        assertEquals(0.75f * 8f, o.dx, 1e-5f);

        AppearStyle.POP.apply(t, e, 1f, 0, 0, o);
        assertEquals(e, o.scaleX, 1e-6f);
        assertEquals(e, o.scaleY, 1e-6f);
        assertEquals(0.5f, o.pivotX);
        assertEquals(0.5f, o.pivotY);
        assertEquals(Easing.BACK_OUT, AppearStyle.POP.defaultEasing());

        AppearStyle.GROW.apply(t, e, 1f, 0, 0, o);
        assertEquals(1f, o.scaleX);
        assertEquals(e, o.scaleY, 1e-6f);
        assertEquals(1f, o.pivotY);

        AppearStyle.STRETCH.apply(t, e, 1f, 0, 0, o);
        assertEquals(e, o.scaleX, 1e-6f);
        assertEquals(1f, o.scaleY);
        assertEquals(0f, o.pivotX);

        AppearStyle.DROP.apply(t, e, 1f, 0, 0, o);
        assertEquals(-0.75f * 9f, o.dy, 1e-5f);
        assertEquals(Easing.BOUNCE_OUT, AppearStyle.DROP.defaultEasing());

        AppearStyle.SPIN.apply(t, e, 1f, 0, 0, o);
        assertEquals(0.75f * (float) (-Math.PI / 2), o.rotation, 1e-5f);
        assertEquals(e, o.scaleX, 1e-6f);

        AppearStyle.WAVE.apply(t, e, 1f, 0, 0, o);
        assertEquals((float) Math.sin(t * 2.5 * Math.PI) * 0.75f * 3f, o.dy, 1e-5f);

        // shallower scale effect at low intensity, never negative at high intensity
        AppearStyle.POP.apply(t, 0f, 0.25f, 0, 0, o);
        assertEquals(0.75f, o.scaleX, 1e-6f);
        AppearStyle.POP.apply(t, 0f, 3f, 0, 0, o);
        assertEquals(0f, o.scaleX, 1e-6f);
    }

    @Test
    void scrambleShowsRandomGlyphsThenTheRealOne() {
        CharTransform o = new CharTransform();
        Set<Integer> seen = new HashSet<>();
        for (long now = 0; now < 45 * 40; now += 45) {
            AppearStyle.SCRAMBLE.apply(0.3f, 0.3f, 1f, 99, now, o);
            assertNotEquals(-1, o.glyph);
            seen.add(o.glyph);
        }
        assertTrue(seen.size() > 10, "glyphs should cycle: " + seen);
        AppearStyle.SCRAMBLE.apply(0.64f, 0.64f, 1f, 99, 5, o);
        assertNotEquals(-1, o.glyph);
        AppearStyle.SCRAMBLE.apply(0.65f, 0.65f, 1f, 99, 5, o);
        assertEquals(-1, o.glyph);
        assertTrue(o.alpha > 0.99f);
    }

    @Test
    void appearNanInputsAreSafe() {
        CharTransform o = new CharTransform();
        for (AppearStyle s : AppearStyle.values()) {
            s.apply(0.5f, Float.NaN, Float.NaN, 1, 1, o);
            assertSane(o, s + " nan");
            s.apply(0.5f, Float.POSITIVE_INFINITY, 1f, 1, 1, o);
            assertSane(o, s + " inf e");
            s.apply(Float.NaN, 0.5f, 1f, 1, 1, o);
            assertTrue(o.isIdentity(), s + " nan t");
        }
    }

    // ------------------------------------------------------------------ remove

    @ParameterizedTest
    @EnumSource(RemoveStyle.class)
    void removeIsGoneAtEnd(RemoveStyle s) {
        CharTransform o = new CharTransform();
        for (float t : new float[]{1f, 1.5f, 100f, Float.POSITIVE_INFINITY, Float.NaN}) {
            s.apply(t, 1f, 1f, 5, o);
            assertEquals(0f, o.alpha, s + " t=" + t);
        }
    }

    @ParameterizedTest
    @EnumSource(RemoveStyle.class)
    void removeStartsFullyVisible(RemoveStyle s) {
        CharTransform o = new CharTransform();
        s.apply(0f, 0f, 1f, 5, o);
        if (s == RemoveStyle.NONE) {
            assertEquals(0f, o.alpha);
        } else {
            assertTrue(o.isIdentity(), s + " " + o);
        }
        s.apply(-1f, 0f, 1f, 5, o); // before start: clamp to start
        if (s != RemoveStyle.NONE) {
            assertEquals(1f, o.alpha);
        }
    }

    @ParameterizedTest
    @EnumSource(RemoveStyle.class)
    void removeRangesAreSane(RemoveStyle s) {
        CharTransform o = new CharTransform();
        for (Easing easing : Easing.values()) {
            Easing ez = easing.resolve(s.defaultEasing());
            for (int i = 0; i < 100; i++) {
                float t = i / 100f;
                for (float k : INTENSITIES) {
                    for (int seed : SEEDS) {
                        s.apply(t, ez.apply(t), k, seed, o);
                        assertSane(o, s + "/" + easing + " t=" + t);
                        assertEquals(-1, o.glyph);
                    }
                }
            }
        }
    }

    @Test
    void removeSemantics() {
        CharTransform o = new CharTransform();
        float e = 0.5f, t = 0.5f;
        RemoveStyle.FADE.apply(t, e, 1f, 0, o);
        assertEquals(0.5f, o.alpha, 1e-6f);

        RemoveStyle.FALL.apply(t, e, 1f, 0, o);
        assertEquals(4.5f, o.dy, 1e-5f);
        assertTrue(o.rotation != 0f && Math.abs(o.rotation) < 0.3f);
        assertEquals(0.5f, o.alpha, 1e-6f);

        RemoveStyle.SHRINK.apply(t, e, 1f, 0, o);
        assertEquals(0.5f, o.scaleX, 1e-6f);
        assertEquals(0.5f, o.scaleY, 1e-6f);
        assertEquals(0.5f, o.pivotX);
        assertEquals(0.5f, o.pivotY);
        assertTrue(o.alpha < 1f);

        RemoveStyle.FLY_UP.apply(t, e, 1f, 0, o);
        assertEquals(-4.5f, o.dy, 1e-5f);
        assertTrue(o.alpha < 1f);

        Set<Long> directions = new HashSet<>();
        for (int seed = 0; seed < 50; seed++) {
            RemoveStyle.SCATTER.apply(t, e, 1f, seed, o);
            assertEquals(3f, Math.hypot(o.dx, o.dy), 1e-4, "distance = e*6");
            assertNotEquals(0f, o.rotation);
            directions.add(Math.round(Math.atan2(o.dy, o.dx) * 4));
            // deterministic for a seed
            CharTransform again = new CharTransform();
            RemoveStyle.SCATTER.apply(t, e, 1f, seed, again);
            assertEquals(o.dx, again.dx);
            assertEquals(o.dy, again.dy);
            assertEquals(o.rotation, again.rotation);
        }
        assertTrue(directions.size() > 10, "scatter directions should vary with the seed");
        RemoveStyle.SCATTER.apply(t, e, 2f, 3, o);
        assertEquals(6f, Math.hypot(o.dx, o.dy), 1e-4);
    }

    @Test
    void translationKeysAndDefaults() {
        assertEquals("typinganimation.appear.slide_up", AppearStyle.SLIDE_UP.translationKey());
        assertEquals("typinganimation.appear.scramble", AppearStyle.SCRAMBLE.translationKey());
        assertEquals("typinganimation.remove.fly_up", RemoveStyle.FLY_UP.translationKey());
        for (AppearStyle s : AppearStyle.values()) {
            assertNotNull(s.defaultEasing());
            assertNotEquals(Easing.AUTO, s.defaultEasing());
        }
        for (RemoveStyle s : RemoveStyle.values()) {
            assertNotNull(s.defaultEasing());
            assertNotEquals(Easing.AUTO, s.defaultEasing());
        }
    }

    // ------------------------------------------------------------------ CharTransform / ScrambleGlyphs

    @Test
    void charTransformResetAndIdentity() {
        CharTransform t = new CharTransform();
        assertTrue(t.isIdentity());
        assertFalse(t.hasScaleOrRotation());
        t.pivotX = 0f;
        t.pivotY = 1f;
        assertTrue(t.isIdentity(), "pivot alone does not change the drawing");
        t.dy = 0.5f;
        assertFalse(t.isIdentity());
        t.reset();
        assertTrue(t.isIdentity());
        assertEquals(0.5f, t.pivotX);
        t.glyph = 'x';
        assertFalse(t.isIdentity());
        t.reset();
        t.rotation = 0.1f;
        assertTrue(t.hasScaleOrRotation());
        CharTransform c = new CharTransform();
        c.set(t);
        assertEquals(0.1f, c.rotation);
        t.reset();
        t.alpha = 0.999f;
        assertFalse(t.isIdentity());
    }

    @Test
    void charTransformSanitize() {
        CharTransform t = new CharTransform();
        t.dx = Float.NaN;
        t.dy = Float.POSITIVE_INFINITY;
        t.scaleX = -1f;
        t.scaleY = Float.NaN;
        t.rotation = Float.NEGATIVE_INFINITY;
        t.alpha = 7f;
        t.pivotX = Float.NaN;
        t.glyph = -5;
        t.sanitize();
        assertEquals(0f, t.dx);
        assertEquals(0f, t.dy);
        assertEquals(0f, t.scaleX);
        assertEquals(1f, t.scaleY);
        assertEquals(0f, t.rotation);
        assertEquals(1f, t.alpha);
        assertEquals(0.5f, t.pivotX);
        assertEquals(-1, t.glyph);
        t.alpha = Float.NaN;
        t.sanitize();
        assertEquals(1f, t.alpha);
        t.alpha = -3f;
        t.sanitize();
        assertEquals(0f, t.alpha);
    }

    @Test
    void scrambleGlyphsAreDeterministicPrintableAndCycle() {
        for (int seed : SEEDS) {
            for (long now : TIMES) {
                int g = ScrambleGlyphs.pick(seed, now);
                assertTrue(g >= 0x21 && g <= 0x7E, "printable: " + g);
                assertEquals(g, ScrambleGlyphs.pick(seed, now));
            }
        }
        // constant within one period
        long base = 45L * 1000;
        int g0 = ScrambleGlyphs.pick(5, base);
        for (long d = 0; d < ScrambleGlyphs.PERIOD_MS; d++) {
            assertEquals(g0, ScrambleGlyphs.pick(5, base + d));
        }
        // changes over time and differs between seeds
        Set<Integer> overTime = new HashSet<>();
        Set<Integer> overSeeds = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            overTime.add(ScrambleGlyphs.pick(5, base + i * 45L));
            overSeeds.add(ScrambleGlyphs.pick(i, base));
        }
        assertTrue(overTime.size() > 20, overTime.toString());
        assertTrue(overSeeds.size() > 20, overSeeds.toString());
    }
}
