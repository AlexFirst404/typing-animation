package dev.typinganimation.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for independently configurable animation and two-dimensional caret motion. */
class CustomizationTest {
    private final FieldAnimationState state = new FieldAnimationState();
    private final TypingConfig cfg = new TypingConfig();

    private void frame(long now) {
        state.beginFrame(now, 10f, 20f, 0);
        state.sync("unchanged text", true, cfg);
    }

    @Test
    void caretAxesAdvanceIndependentlyInEitherOrderAndOnlyOncePerFrame() {
        cfg.glideMs = 0; // Text reflow must not disable caret motion.
        cfg.cursorGlideMs = 200;
        frame(1000);
        assertEquals(10f, state.caretX(10f, cfg));
        assertEquals(20f, state.caretY(20f, cfg));
        frame(1016);
        float alpha = (float) (1.0 - Math.exp(-16.0 / 200.0));
        float y = state.caretY(38f, cfg);
        float x = state.caretX(50f, cfg);
        assertEquals(20f + 18f * alpha, y, 1e-5f);
        assertEquals(10f + 40f * alpha, x, 1e-5f);
        assertEquals(x, state.caretX(50f, cfg));
        assertEquals(y, state.caretY(38f, cfg));
        assertFalse(state.isIdle(cfg));
        frame(1032);
        float nextX = state.caretX(50f, cfg);
        float nextY = state.caretY(38f, cfg);
        assertEquals(x + (50f - x) * alpha, nextX, 1e-5f);
        assertEquals(y + (38f - y) * alpha, nextY, 1e-5f);
        cfg.cursorGlideMs = 0;
        frame(1048);
        assertEquals(90f, state.caretX(90f, cfg));
        assertEquals(56f, state.caretY(56f, cfg));
    }

    @Test
    void verticalOnlyMoveKeepsFieldBusyAndSettlesExactly() {
        cfg.cursorGlideMs = TypingConfig.CURSOR_GLIDE_MAX_MS;
        for (long now = 0; now <= 100; now += 20) {
            frame(now);
            state.caretX(20f, cfg);
            state.caretY(20f, cfg);
        }
        assertTrue(state.isIdle(cfg));
        frame(101);
        state.caretX(20f, cfg);
        float y = state.caretY(38f, cfg);
        assertTrue(y > 20f && y < 38f);
        assertFalse(state.isIdle(cfg), "a line change animates even though horizontal position is unchanged");
        for (long now = 102; now < 10_000 && y != 38f; now++) {
            frame(now);
            state.caretX(20f, cfg);
            y = state.caretY(38f, cfg);
        }
        assertEquals(38f, y);
        assertTrue(state.isIdle(cfg));
    }

    @Test
    void hiddenAndNewVerticalAxisDoNotInheritHorizontalClockOrPoisonEachOther() {
        frame(1000);
        state.caretX(10f, cfg);
        frame(1016);
        state.caretX(50f, cfg);
        assertEquals(100f, state.caretY(100f, cfg), "new vertical axis snaps without disturbing X");
        frame(1032);
        float x = state.caretX(50f, cfg);
        assertEquals(Float.NaN, state.caretY(Float.NaN, cfg));
        frame(1048);
        assertTrue(state.caretX(50f, cfg) > x, "invalid Y does not poison X");
        assertEquals(109f, state.caretY(109f, cfg), "invalid axis recovers on its next finite target");
        frame(1064);
        state.caretY(118f, cfg);
        frame(1080);
        state.sync("unchanged text", false, cfg);
        assertEquals(127f, state.caretY(127f, cfg), "disabled field snaps both axes");
        state.reset();
        frame(1096);
        assertEquals(0f, state.caretY(0f, cfg));
        frame(1112);
        state.caretY(18f, cfg);
        state.beginFrame(1128, 11f, 20f, 0);
        state.sync("unchanged text", true, cfg);
        assertEquals(36f, state.caretY(36f, cfg), "moving the widget still snaps both axes");
    }

    @Test
    void removalUsesItsOwnIntensity() {
        cfg.removeStyle = RemoveStyle.SLIDE_RIGHT;
        cfg.easing = Easing.LINEAR;
        cfg.removeDurationMs = 100;
        cfg.intensity = 0.25f;
        cfg.removeIntensity = 3f;
        FieldAnimationState.Ghost ghost = new FieldAnimationState.Ghost('x', null, -1, 0, 0, 6, 1000, 2);
        frame(1050);
        CharTransform out = new CharTransform();
        assertTrue(state.ghostTransform(ghost, cfg, out));
        assertEquals(13.5f, out.dx, 1e-5f);
        assertEquals(0.5f, out.alpha);
        cfg.intensity = 2.5f;
        state.ghostTransform(ghost, cfg, out);
        assertEquals(13.5f, out.dx, 1e-5f, "appearance strength cannot change removal strength");
    }

    @Test
    void newStylesHaveDistinctMotion() {
        CharTransform out = new CharTransform();
        AppearStyle.SLIDE_RIGHT.apply(0.5f, 0.5f, 1f, 0, 0, out);
        assertEquals(-4f, out.dx);
        AppearStyle.ZOOM_OUT.apply(0.5f, 0.5f, 1f, 0, 0, out);
        assertTrue(out.scaleX > 1f && out.scaleY > 1f);
        AppearStyle.FLIP.apply(0.5f, 0.5f, 1f, 0, 0, out);
        assertTrue(out.scaleX < 0.001f && out.scaleY >= 1f);
        AppearStyle.SWING.apply(0.5f, 0.5f, 1f, 0, 0, out);
        assertNotEquals(0f, out.rotation);
        assertEquals(0f, out.pivotY);
        AppearStyle.ROLL.apply(0.5f, 0.5f, 1f, 0, 0, out);
        assertTrue(out.dx < 0f && out.rotation < 0f);
        AppearStyle.SPIRAL.apply(0.25f, 0.25f, 1f, 0, 0, out);
        assertEquals(5.25f, Math.hypot(out.dx, out.dy), 1e-5f);
        assertTrue(out.dy < 0f && out.scaleX < 1f);
        RemoveStyle.SLIDE_LEFT.apply(0.5f, 0.5f, 1f, 0, out);
        assertEquals(-4.5f, out.dx);
        RemoveStyle.SLIDE_RIGHT.apply(0.5f, 0.5f, 1f, 0, out);
        assertEquals(4.5f, out.dx);
        RemoveStyle.SPIN.apply(0.5f, 0.5f, 1f, 0, out);
        assertTrue(out.rotation > 0f && out.scaleX < 1f);
        RemoveStyle.SQUASH.apply(0.5f, 0.5f, 1f, 0, out);
        assertTrue(out.scaleX > 1f && out.scaleY < 1f);
        RemoveStyle.SPIRAL.apply(0.25f, 0.25f, 1f, 0, out);
        assertEquals(2f, Math.hypot(out.dx, out.dy), 1e-5f);
        assertTrue(out.dy > 0f && out.rotation > 0f);
    }

    @Test
    void everyEditableSettingSurvivesCopiesAndParticipatesInEquality() throws IllegalAccessException {
        for (Field field : TypingConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            TypingConfig original = new TypingConfig();
            TypingConfig changed = original.copy();
            if (field.getType() == boolean.class) field.setBoolean(changed, !field.getBoolean(changed));
            else if (field.getType() == int.class) field.setInt(changed, field.getInt(changed) + 1);
            else if (field.getType() == float.class) field.setFloat(changed, field.getFloat(changed) + 0.25f);
            else {
                Object current = field.get(changed);
                Object[] choices = field.getType().getEnumConstants();
                field.set(changed, choices[0] == current ? choices[1] : choices[0]);
            }
            assertNotEquals(original, changed, field.getName() + " must mark the UI config dirty");
            TypingConfig copy = changed.copy();
            assertEquals(field.get(changed), field.get(copy), field.getName() + " must survive preview/reset copies");
            assertEquals(changed, copy);
            assertEquals(changed.hashCode(), copy.hashCode());
        }
    }
}
