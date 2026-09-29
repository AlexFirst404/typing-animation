package dev.typinganimation.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EasingTest {

    private static final Set<Easing> MONOTONIC = EnumSet.of(Easing.AUTO, Easing.LINEAR, Easing.SINE_OUT,
            Easing.QUAD_OUT, Easing.CUBIC_OUT, Easing.QUART_OUT, Easing.EXPO_OUT, Easing.SINE_IN_OUT);

    @ParameterizedTest
    @EnumSource(Easing.class)
    void endpointsAreExact(Easing e) {
        assertEquals(0f, e.apply(0f));
        assertEquals(1f, e.apply(1f));
    }

    @ParameterizedTest
    @EnumSource(Easing.class)
    void inputIsClampedAndNanSafe(Easing e) {
        assertEquals(0f, e.apply(-0.5f));
        assertEquals(0f, e.apply(Float.NEGATIVE_INFINITY));
        assertEquals(0f, e.apply(Float.NaN));
        assertEquals(1f, e.apply(1.5f));
        assertEquals(1f, e.apply(Float.POSITIVE_INFINITY));
        assertEquals(0f, e.apply(-0f));
    }

    @ParameterizedTest
    @EnumSource(Easing.class)
    void valuesAreFiniteAndBounded(Easing e) {
        for (int i = 0; i <= 1000; i++) {
            float t = i / 1000f;
            float v = e.apply(t);
            assertTrue(Float.isFinite(v), e + " at " + t);
            assertTrue(v >= -0.05f && v <= 1.45f, e + " at " + t + " = " + v);
        }
        // tiny inputs stay tiny, values near 1 get close to 1
        assertTrue(Math.abs(e.apply(1e-6f)) < 0.01f, e.toString());
        assertTrue(Math.abs(e.apply(0.9999f) - 1f) < 0.05f, e.toString());
    }

    @ParameterizedTest
    @EnumSource(Easing.class)
    void monotonicCurvesAreMonotonic(Easing e) {
        if (!MONOTONIC.contains(e)) {
            return;
        }
        float prev = e.apply(0f);
        for (int i = 1; i <= 1000; i++) {
            float v = e.apply(i / 1000f);
            assertTrue(v >= prev, e + " decreases at " + i);
            prev = v;
        }
        assertTrue(e.apply(0.5f) > 0f && e.apply(0.5f) < 1f);
    }

    @Test
    void outCurvesAreAheadOfLinearAndSpecialsBehave() {
        for (Easing e : new Easing[]{Easing.SINE_OUT, Easing.QUAD_OUT, Easing.CUBIC_OUT, Easing.QUART_OUT, Easing.EXPO_OUT}) {
            assertTrue(e.apply(0.3f) > 0.3f, e.toString());
        }
        assertEquals(0.5f, Easing.LINEAR.apply(0.5f));
        assertEquals(0.5f, Easing.SINE_IN_OUT.apply(0.5f), 1e-6f);
        assertEquals(Easing.CUBIC_OUT.apply(0.37f), Easing.AUTO.apply(0.37f));
        // BACK_OUT overshoots, ELASTIC_OUT oscillates above 1, BOUNCE_OUT stays within [0,1]
        float maxBack = 0f, maxElastic = 0f, maxBounce = 0f, minBounce = 1f;
        for (int i = 1; i < 1000; i++) {
            float t = i / 1000f;
            maxBack = Math.max(maxBack, Easing.BACK_OUT.apply(t));
            maxElastic = Math.max(maxElastic, Easing.ELASTIC_OUT.apply(t));
            maxBounce = Math.max(maxBounce, Easing.BOUNCE_OUT.apply(t));
            minBounce = Math.min(minBounce, Easing.BOUNCE_OUT.apply(t));
        }
        assertTrue(maxBack > 1.05f && maxBack < 1.15f, "back " + maxBack);
        assertTrue(maxElastic > 1.2f && maxElastic < 1.45f, "elastic " + maxElastic);
        assertTrue(maxBounce <= 1f && minBounce >= 0f);
    }

    @Test
    void resolve() {
        assertEquals(Easing.BOUNCE_OUT, Easing.AUTO.resolve(Easing.BOUNCE_OUT));
        assertEquals(Easing.CUBIC_OUT, Easing.AUTO.resolve(null));
        assertEquals(Easing.CUBIC_OUT, Easing.AUTO.resolve(Easing.AUTO));
        for (Easing e : Easing.values()) {
            if (e != Easing.AUTO) {
                assertEquals(e, e.resolve(Easing.LINEAR));
                assertEquals(e, e.resolve(null));
            }
        }
    }

    @Test
    void translationKeys() {
        assertEquals("typinganimation.easing.auto", Easing.AUTO.translationKey());
        assertEquals("typinganimation.easing.sine_in_out", Easing.SINE_IN_OUT.translationKey());
        assertEquals("typinganimation.easing.back_out", Easing.BACK_OUT.translationKey());
    }
}
