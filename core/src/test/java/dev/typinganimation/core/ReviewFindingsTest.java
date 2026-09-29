package dev.typinganimation.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review findings (core module), kept as regression tests. Each test drives the state exactly like a renderer
 * following the documented per-frame order: beginFrame, sync, caretX, isIdle, layoutChar + appearTransform per
 * char, ghosts, endFrame (caretX before isIdle is the recommended order; see the class comment of
 * {@link FieldAnimationState} for the late order).
 */
class ReviewFindingsTest {
    private static final int WHITE = 0xFFFFFFFF;
    private static final float CHAR_W = 6f;

    private final FieldAnimationState st = new FieldAnimationState();
    private final TypingConfig cfg = TypingConfig.defaults();
    private final CharTransform tf = new CharTransform();
    private long now = 10_000L;

    /** Alpha each char was drawn with in the most recent frame (index = UTF-16 index), 1 when no transform. */
    private float[] drawnAlpha = new float[0];

    private boolean frame(String v, float caretTarget, float[] caretOut) {
        now += 16;
        st.beginFrame(now, 4f, 8f, 0);
        st.sync(v, true, cfg);
        float c = st.caretX(caretTarget, cfg);
        if (caretOut != null) caretOut[0] = c;
        boolean idle = st.isIdle(cfg);
        drawnAlpha = new float[v.length()];
        for (int i = 0; i < v.length(); i++) {
            st.layoutChar(i, 4f + i * CHAR_W, 8f, CHAR_W, v.charAt(i), null, WHITE);
            drawnAlpha[i] = !idle && st.appearTransform(i, cfg, tf) ? tf.alpha : 1f;
        }
        st.endFrame();
        return idle;
    }

    private boolean frame(String v) {
        return frame(v, 4f + v.length() * CHAR_W, null);
    }

    /**
     * Chat history recall (Up arrow) inserts a whole message with stagger; pressing Up again ~100 ms later
     * replaces it. Characters of the first message that were still hidden (t &lt; 0, drawn with alpha 0 in the
     * previous frame) become ghosts that start fully opaque (RemoveStyle at t = 0 is identity) -> the unrevealed
     * tail of the old message flashes into view and then fades out. Same for paste followed by select-all +
     * delete. A ghost must never start more visible than its char was drawn in the previous frame.
     */
    @Test
    void ghostOfNotYetRevealedStaggeredCharMustNotFlashIn() {
        String first = "the first recalled chat message";   // 31 chars -> step = min(14, 350/30) ~ 11.7 ms
        String second = "/gamemode creative";
        frame("");
        frame("");
        frame(first);                                       // recalled: staggered births
        for (int k = 0; k < 5; k++) frame(first);           // ~80 ms later: the tail is still hidden
        float[] before = drawnAlpha.clone();
        int hiddenBefore = 0;
        for (float a : before) if (a == 0f) hiddenBefore++;
        assertTrue(hiddenBefore > 10, "precondition: many chars still hidden, got " + hiddenBefore);

        now += 16;
        st.beginFrame(now, 4f, 8f, 0);
        st.sync(second, true, cfg);                         // Up pressed again
        int flashing = 0;
        StringBuilder detail = new StringBuilder();
        for (int g = 0; g < st.ghostCount(); g++) {
            FieldAnimationState.Ghost ghost = st.ghost(g);
            int idx = Math.round((ghost.x - 4f) / CHAR_W);
            boolean visible = st.ghostTransform(ghost, cfg, tf);
            if (visible && tf.alpha > before[idx] + 0.05f) {
                flashing++;
                if (detail.length() < 200) {
                    detail.append(" '").append((char) ghost.codepoint).append("' drawn a=").append(before[idx])
                            .append(" ghost a=").append(tf.alpha);
                }
            }
        }
        assertEquals(0, flashing, flashing + " ghosts start brighter than their char was drawn:" + detail);
    }

    /** Same defect with a single, partially faded-in char deleted by a quick Backspace (FADE appear). */
    @Test
    void ghostOfPartiallyFadedInCharMustNotPopToFullAlpha() {
        cfg.appearStyle = AppearStyle.FADE;
        frame("hell");
        frame("hell");
        frame("hello");                                     // 'o' typed
        for (int k = 0; k < 5; k++) frame("hello");         // ~96 ms later: alpha ~ 0.68
        float drawn = drawnAlpha[4];
        assertTrue(drawn > 0.3f && drawn < 0.9f, "precondition, drawn alpha " + drawn);

        now += 16;
        st.beginFrame(now, 4f, 8f, 0);
        st.sync("hell", true, cfg);                         // quick Backspace
        assertEquals(1, st.ghostCount());
        assertTrue(st.ghostTransform(st.ghost(0), cfg, tf));
        assertTrue(tf.alpha <= drawn + 0.05f,
                "ghost starts at alpha " + tf.alpha + " but the char was drawn at alpha " + drawn);
    }

    /**
     * Moving only the caret (arrow keys / click) while the text is at rest. With the old documented order
     * (isIdle before caretX) isIdle reported true for the frame in which the caret starts gliding, because the new
     * caret target was only supplied later. The contract is now: caretX before isIdle when the target is known
     * (then isIdle covers the caret move), and caretX on idle frames too (the idle fast path covers the text
     * only), so the caret glides either way.
     */
    @Test
    void isIdleMustNotBeTrueInAFrameWhereTheCaretStartsGliding() {
        String v = "hello world";
        frame(v, 4f + v.length() * CHAR_W, null);
        for (int k = 0; k < 60; k++) frame(v, 4f + v.length() * CHAR_W, null);   // settled, caret at end
        assertTrue(frame(v, 4f + v.length() * CHAR_W, null), "precondition: at rest");

        float[] caret = new float[1];
        float target = 4f + 2 * CHAR_W;                     // Home/Left: caret jumps 54 px to the left
        boolean idle = frame(v, target, caret);
        boolean caretGliding = caret[0] != target;
        assertTrue(caretGliding, "smoothCursor applies to a pure caret move");
        assertFalse(idle && caretGliding,
                "isIdle=" + idle + " but caretX returned " + caret[0] + " for target " + target);
    }

    /** Late order (isIdle, then caretX): the caret still glides in the idle frame and the next frame is busy. */
    @Test
    void caretQueriedAfterAnIdleVerdictStillGlidesAndMakesTheNextFrameBusy() {
        String v = "hello world";
        for (int k = 0; k < 62; k++) frame(v, 4f + v.length() * CHAR_W, null);   // settled, caret at end

        float target = 4f + 2 * CHAR_W;
        now += 16;
        st.beginFrame(now, 4f, 8f, 0);
        st.sync(v, true, cfg);
        boolean idle = st.isIdle(cfg);
        for (int i = 0; i < v.length(); i++) st.layoutChar(i, 4f + i * CHAR_W, 8f, CHAR_W, v.charAt(i), null, WHITE);
        float c = st.caretX(target, cfg);
        st.endFrame();
        assertTrue(idle, "text at rest and the caret move is not known yet");
        assertTrue(c > target, "idle frames must not snap the caret: " + c);

        now += 16;
        st.beginFrame(now, 4f, 8f, 0);
        st.sync(v, true, cfg);
        assertFalse(st.isIdle(cfg), "the caret glide started last frame keeps the field busy");
        float c2 = st.caretX(target, cfg);
        assertTrue(c2 < c && c2 >= target, "the glide continues: " + c + " -> " + c2);
        st.endFrame();
    }
}
