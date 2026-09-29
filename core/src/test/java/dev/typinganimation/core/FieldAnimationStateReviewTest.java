package dev.typinganimation.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static dev.typinganimation.core.FieldAnimationState.NO_BIRTH;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Review tests for {@link FieldAnimationState}: randomized robustness/invariant fuzzing plus targeted regression
 * tests for defects found in review (ghost start pose, float stall of the glide, caret-only moves vs. isIdle,
 * ghosts created on an origin-change frame).
 */
class FieldAnimationStateReviewTest {
    static final int WHITE = 0xFFFFFFFF;
    static final String EMOJI_A = new String(Character.toChars(0x1F600)); // D83D DE00
    static final String EMOJI_B = new String(Character.toChars(0x1F601)); // D83D DE01
    static final String MATH_A = new String(Character.toChars(0x1D49C)); // D835 DC9C
    static final String SHARES_LOW = new String(Character.toChars(0x1D600)); // D835 DE00

    // =================================================================================================== fuzz

    /**
     * Many random edits (incl. surrogate pairs, lone surrogates, big pastes, clears), random frame timing (0 ms,
     * backwards, huge gaps), random config changes, origin/scroll changes, random visible subsets, repeated syncs
     * in one frame, missing begin/endFrame, resets. Invariants: no exception, length == value length, idle frames
     * draw every char at its target with no appear/ghost transform, and the state becomes idle once left alone.
     */
    @Test
    void fuzzRandomEditsFramesAndConfigChanges() {
        String[] alphabet = {"a", "b", "c", " ", "W", "Ж", EMOJI_A, EMOJI_B, MATH_A, SHARES_LOW, "\uD83D",
                "\uDE00", "\uD835"};
        AppearStyle[] appear = AppearStyle.values();
        RemoveStyle[] remove = RemoveStyle.values();
        Easing[] easings = Easing.values();
        int idleFrames = 0, appearing = 0, visibleGhosts = 0, maxLen = 0;
        for (int seed = 0; seed < 60; seed++) {
            Random rnd = new Random(seed * 7919L + 17);
            FieldAnimationState st = new FieldAnimationState();
            TypingConfig cfg = TypingConfig.defaults();
            CharTransform tf = new CharTransform();
            long now = 5_000L;
            float ox = 4f, oy = 8f;
            int scroll = 0;
            String value = "";
            for (int iter = 0; iter < 2000; iter++) {
                String ctx = "seed " + seed + " iter " + iter;
                int r = rnd.nextInt(24);
                long dt = r == 0 ? 0 : r == 1 ? -rnd.nextInt(40) : r == 2 ? 150 + rnd.nextInt(3000) : 1 + rnd.nextInt(33);
                now += dt;
                if (rnd.nextInt(60) == 0) ox += rnd.nextInt(5) - 2;
                if (rnd.nextInt(80) == 0) oy += rnd.nextInt(3) - 1;
                if (rnd.nextInt(20) == 0) scroll = rnd.nextInt(4);
                if (rnd.nextInt(25) == 0) mutateConfig(cfg, rnd, appear, remove, easings);

                boolean skipBegin = rnd.nextInt(80) == 0;
                if (!skipBegin) st.beginFrame(now, ox, oy, scroll);
                boolean animate = rnd.nextInt(8) != 0;
                String next = rnd.nextInt(3) == 0 ? randomEdit(value, rnd, alphabet) : value;
                if (rnd.nextInt(150) == 0) next = null;
                st.sync(next, animate, rnd.nextInt(200) == 0 ? null : cfg);
                value = next == null ? "" : next;
                if (rnd.nextInt(40) == 0) { // a second sync in the same frame
                    value = randomEdit(value, rnd, alphabet);
                    st.sync(value, animate, cfg);
                }
                assertEquals(value.length(), st.length(), ctx);
                assertEquals(value, st.syncedValue(), ctx);

                boolean idle = rnd.nextInt(4) != 0 && st.isIdle(cfg);
                if (idle) idleFrames++;
                maxLen = Math.max(maxLen, value.length());
                int cpIndex = 0;
                float caretTarget = ox;
                for (int i = 0; i < value.length(); ) {
                    int cp = value.codePointAt(i);
                    float target = ox + (cpIndex - scroll) * 6f;
                    if (cpIndex >= scroll && rnd.nextInt(30) != 0) {
                        float y = oy + (rnd.nextInt(200) == 0 ? 9f : 0f);
                        float x = st.layoutChar(i, target, y, 6f, cp, "style", WHITE);
                        assertTrue(Float.isFinite(x), ctx);
                        if (idle) assertEquals(target, x, "idle frame must draw at target; " + ctx);
                        boolean has = st.appearTransform(i, cfg, tf);
                        assertSane(tf, ctx);
                        if (idle) assertFalse(has, "idle frame must not have appear transforms; " + ctx);
                        if (!has) assertTrue(tf.isIdentity(), ctx);
                        else appearing++;
                    }
                    if (rnd.nextInt(10) == 0) caretTarget = target;
                    cpIndex++;
                    i += Character.charCount(cp);
                }
                // hostile indices
                assertEquals(1f, st.layoutChar(value.length() + rnd.nextInt(3), 1f, oy, 6f, 'x', null, WHITE), ctx);
                assertEquals(2f, st.layoutChar(-1 - rnd.nextInt(3), 2f, oy, 6f, 'x', null, WHITE), ctx);
                assertFalse(st.appearTransform(value.length(), cfg, tf), ctx);

                int gc = st.ghostCount();
                assertTrue(gc >= 0 && gc <= FieldAnimationState.MAX_GHOSTS, ctx);
                for (int g = 0; g < gc; g++) {
                    FieldAnimationState.Ghost gh = st.ghost(g);
                    assertNotNull(gh, ctx);
                    boolean vis = st.ghostTransform(gh, cfg, tf);
                    assertSane(tf, ctx);
                    if (idle) assertFalse(vis, "idle frame must not have visible ghosts; " + ctx);
                    if (vis) visibleGhosts++;
                }
                assertNull(st.ghost(gc), ctx);
                if (rnd.nextBoolean()) assertTrue(Float.isFinite(st.caretX(caretTarget, cfg)), ctx);
                if (rnd.nextInt(60) != 0) st.endFrame();
                if (rnd.nextInt(500) == 0) {
                    st.reset();
                    assertEquals(0, st.length(), ctx);
                    assertEquals(0, st.ghostCount(), ctx);
                }
            }

            // liveness: leave the field alone (fixed text/origin/scroll/caret, sane config) -> must become idle
            cfg.enabled = true;
            if (cfg.appearStyle == null) cfg.appearStyle = AppearStyle.FADE;
            if (cfg.removeStyle == null) cfg.removeStyle = RemoveStyle.FADE;
            int idleRun = 0;
            for (int k = 0; k < 600 && idleRun < 10; k++) {
                now += 16;
                st.beginFrame(now, ox, oy, scroll);
                st.sync(value, true, cfg);
                boolean idle = st.isIdle(cfg);
                int cpIndex = 0;
                for (int i = 0; i < value.length(); ) {
                    int cp = value.codePointAt(i);
                    if (cpIndex >= scroll) {
                        float target = ox + (cpIndex - scroll) * 6f;
                        float x = st.layoutChar(i, target, oy, 6f, cp, "style", WHITE);
                        if (idle) assertEquals(target, x);
                        boolean has = st.appearTransform(i, cfg, tf);
                        if (idle) assertFalse(has);
                    }
                    cpIndex++;
                    i += Character.charCount(cp);
                }
                for (int g = 0; g < st.ghostCount(); g++) {
                    boolean vis = st.ghostTransform(st.ghost(g), cfg, tf);
                    if (idle) assertFalse(vis);
                }
                st.caretX(ox + 30f, cfg); // caret moves vs. idle frames are covered by caretOnlyMove* tests
                st.endFrame();
                idleRun = idle ? idleRun + 1 : 0;
            }
            assertEquals(10, idleRun, "seed " + seed + ": state must become (and stay) idle once left alone");
        }
        // the fuzz must actually exercise the interesting paths
        assertTrue(idleFrames > 1000, "idle frames " + idleFrames);
        assertTrue(appearing > 1000, "appearing chars " + appearing);
        assertTrue(visibleGhosts > 1000, "visible ghosts " + visibleGhosts);
        assertTrue(maxLen > 100, "max length " + maxLen);
    }

    /**
     * Unique code points (identity tracked through the colour tag), random visible subsets, random animate flag and
     * isIdle calls: a removed char becomes a ghost iff it was laid out in the previous frame and was visible there
     * (appear alpha &gt;= MIN_GHOST_ALPHA), and the ghost carries exactly that frame's
     * position/colour/style/width/code point and starts at the alpha the char was drawn with; survivors keep
     * smoothed x and birth.
     */
    @Test
    void fuzzGhostsComeExactlyFromThePreviousFrameLayout() {
        int[] pool = buildPool();
        for (int seed = 0; seed < 8; seed++) {
            Random rnd = new Random(seed * 31L + 5);
            List<Integer> shuffled = new ArrayList<>();
            for (int cp : pool) shuffled.add(cp);
            Collections.shuffle(shuffled, rnd);
            int nextFresh = 0;
            int nextTag = 1;

            FieldAnimationState st = new FieldAnimationState();
            TypingConfig cfg = TypingConfig.defaults();
            long now = 1_000;
            float ox = 4f, oy = 8f;
            List<int[]> model = new ArrayList<>(); // {cp, tag}
            // per tag: last laid frame, x, y, width, style
            Map<Integer, Long> laidIter = new HashMap<>();
            Map<Integer, Float> lastX = new HashMap<>();
            Map<Integer, Float> lastY = new HashMap<>();
            Map<Integer, Float> lastW = new HashMap<>();
            Map<Integer, Object> lastStyle = new HashMap<>();
            Map<Integer, Float> lastAlpha = new HashMap<>();
            Map<Integer, Long> expBirth = new HashMap<>();
            CharTransform tf = new CharTransform();

            st.beginFrame(now, ox, oy, 0);
            st.sync("", true, cfg);
            st.endFrame();
            for (long iter = 1; iter < 2500 && nextFresh < shuffled.size() - 12; iter++) {
                String ctx = "seed " + seed + " iter " + iter;
                now += 1 + rnd.nextInt(40);
                int n = model.size();
                int a = rnd.nextInt(n + 1);
                int b = Math.min(n, a + (rnd.nextInt(3) == 0 ? rnd.nextInt(5) : 0));
                int ins = rnd.nextInt(4) == 0 ? rnd.nextInt(7) : (b > a ? 0 : 1);
                if (n > 25) {
                    b = Math.min(n, a + 4);
                    ins = 0;
                }
                if (rnd.nextInt(3) != 0 && a == b) ins = Math.max(ins, 1);
                List<int[]> removed = new ArrayList<>(model.subList(a, b));
                List<int[]> inserted = new ArrayList<>();
                for (int k = 0; k < ins; k++) inserted.add(new int[]{shuffled.get(nextFresh++), nextTag++});
                List<int[]> next = new ArrayList<>(model.subList(0, a));
                next.addAll(inserted);
                next.addAll(model.subList(b, n));
                String text = toText(next);
                boolean animate = rnd.nextInt(6) != 0;

                st.beginFrame(now, ox, oy, 0);
                int ghostsBefore = st.ghostCount();
                st.sync(text, animate, cfg);
                assertEquals(text.length(), st.length(), ctx);

                // ghosts: exactly the removed chars that were laid out (and visible) in the previous frame
                List<int[]> expGhosts = new ArrayList<>();
                if (animate && !toText(removed).isEmpty()) {
                    for (int[] c : removed) {
                        Long li = laidIter.get(c[1]);
                        if (li != null && li == iter - 1 && lastAlpha.get(c[1]) >= FieldAnimationState.MIN_GHOST_ALPHA) {
                            expGhosts.add(c);
                        }
                    }
                }
                int expCount = Math.min(FieldAnimationState.MAX_GHOSTS, ghostsBefore + expGhosts.size());
                assertEquals(expCount, st.ghostCount(), ctx);
                for (int k = 0; k < expCount - ghostsBefore; k++) {
                    FieldAnimationState.Ghost g = st.ghost(ghostsBefore + k);
                    int[] c = expGhosts.get(k);
                    assertEquals(c[0], g.codepoint, ctx);
                    assertEquals(c[1], g.color, ctx);
                    assertEquals(lastX.get(c[1]), g.x, ctx);
                    assertEquals(lastY.get(c[1]), g.y, ctx);
                    assertEquals(lastW.get(c[1]), g.width, ctx);
                    assertSame(lastStyle.get(c[1]), g.style, ctx);
                    assertEquals(now, g.removedAt, ctx);
                    // the exit starts exactly as bright as the char was drawn (FADE at t=0 is alpha 1)
                    assertTrue(st.ghostTransform(g, cfg, tf), ctx);
                    assertEquals(lastAlpha.get(c[1]), tf.alpha, 1e-6f, ctx);
                }

                // births of inserted chars (stagger by code point), survivors keep theirs (or finished -> NO_BIRTH)
                int nIns = inserted.size();
                float step = nIns > 1 ? Math.min((float) cfg.staggerMs, (float) cfg.maxStaggerMs / (nIns - 1)) : 0f;
                int k = 0;
                for (int[] c : inserted) {
                    expBirth.put(c[1], animate ? now + Math.round(k * step) : NO_BIRTH);
                    k++;
                }
                int idx = 0;
                for (int[] c : next) {
                    long bAt = st.birthAt(idx);
                    long exp = expBirth.get(c[1]);
                    assertTrue(bAt == exp || bAt == NO_BIRTH && exp <= now, ctx + " birth of tag " + c[1]);
                    // (appearTransform clears only the high half's finished birth; the low half is never read)
                    if (Character.charCount(c[0]) == 2 && bAt != NO_BIRTH) assertEquals(bAt, st.birthAt(idx + 1), ctx);
                    if (laidIter.containsKey(c[1])) assertEquals(lastX.get(c[1]), st.smoothedXAt(idx), ctx);
                    idx += Character.charCount(c[0]);
                }

                if (rnd.nextBoolean()) st.isIdle(cfg);
                Object style = new Object();
                int cpIndex = 0;
                idx = 0;
                for (int[] c : next) {
                    boolean laid = rnd.nextInt(5) != 0;
                    if (laid) {
                        float y = oy + (rnd.nextInt(40) == 0 ? 9f : 0f);
                        float w = 5f + rnd.nextInt(3);
                        float x = st.layoutChar(idx, ox + cpIndex * 6f, y, w, c[0], style, c[1]);
                        laidIter.put(c[1], iter);
                        lastX.put(c[1], x);
                        lastY.put(c[1], y);
                        lastW.put(c[1], w);
                        lastStyle.put(c[1], style);
                    }
                    boolean has = st.appearTransform(idx, cfg, tf);
                    if (laid) lastAlpha.put(c[1], has ? tf.alpha : 1f);
                    cpIndex++;
                    idx += Character.charCount(c[0]);
                }
                st.endFrame();
                model = next;
            }
        }
    }

    // ============================================================================== review regressions

    /**
     * Regression: the exponential glide is computed in float as {@code cur + (target - cur) * a}. With a long
     * glide (glideMs=300, allowed by the slider) and a high frame rate (1 ms per frame, dt=1 => a ~= 0.0033), once
     * |target - cur| drops below ~0.018 px the increment is smaller than half an ulp of x (~1500 GUI px,
     * ulp = 1.2e-4) and x used to stop moving, still above SNAP_EPSILON (0.01), so the char never settled and
     * isIdle() never became true again. A step without progress now snaps to the target.
     */
    @Test
    void glideDoesNotStallAboveSnapEpsilonAtHighFrameRate() {
        FieldAnimationState st = new FieldAnimationState();
        TypingConfig cfg = TypingConfig.defaults();
        cfg.glideMs = TypingConfig.GLIDE_MAX_MS;
        cfg.appearStyle = AppearStyle.NONE;
        long now = 1_000;
        float ox = 1400f;
        st.beginFrame(now, ox, 8f, 0);
        st.sync("b", true, cfg);
        st.layoutChar(0, 1494f, 8f, 6f, 'b', null, WHITE);
        st.endFrame();
        // 'a' typed before 'b': 'b' must glide from 1494 to 1500
        boolean everIdle = false;
        float x = 0;
        for (int f = 0; f < 10_000 && !everIdle; f++) {
            now += 1;
            st.beginFrame(now, ox, 8f, 0);
            st.sync("ab", true, cfg);
            everIdle = st.isIdle(cfg);
            st.layoutChar(0, 1494f, 8f, 6f, 'a', null, WHITE);
            x = st.layoutChar(1, 1500f, 8f, 6f, 'b', null, WHITE);
            st.endFrame();
        }
        assertTrue(everIdle, "after 10 s of 1 ms frames the glide never settled; stuck at x=" + x
                + " (target 1500, distance " + (1500f - x) + ")");
    }

    /** Regression (same root cause as above) for the caret: caretX stalled above SNAP_EPSILON, keeping it busy. */
    @Test
    void caretGlideDoesNotStallAboveSnapEpsilonAtHighFrameRate() {
        FieldAnimationState st = new FieldAnimationState();
        TypingConfig cfg = TypingConfig.defaults();
        cfg.glideMs = TypingConfig.GLIDE_MAX_MS;
        long now = 1_000;
        st.beginFrame(now, 4f, 8f, 0);
        st.sync("", true, cfg);
        st.caretX(1494f, cfg);
        st.endFrame();
        float c = 0;
        for (int f = 0; f < 10_000; f++) {
            now += 1;
            st.beginFrame(now, 4f, 8f, 0);
            st.sync("", true, cfg);
            c = st.caretX(1500f, cfg);
            st.endFrame();
        }
        now += 1;
        st.beginFrame(now, 4f, 8f, 0);
        st.sync("", true, cfg);
        boolean idle = st.isIdle(cfg);
        st.endFrame();
        assertEquals(1500f, c, "caret never reached its target after 10 s of 1 ms frames");
        assertTrue(idle, "a stalled caret keeps the field non-idle forever");
    }

    /**
     * Caret-only move (arrow keys / Home / End / click, text unchanged), recommended in-frame order: caretX is
     * called before isIdle, so the frame in which the caret starts gliding is not idle, and neither is any frame
     * of the glide.
     */
    @Test
    void caretOnlyMoveIsNotIdleWhenCaretXPrecedesIsIdle() {
        FieldAnimationState st = new FieldAnimationState();
        TypingConfig cfg = TypingConfig.defaults();
        long now = 1_000;
        for (int f = 0; f < 40; f++) {
            now += 16;
            st.beginFrame(now, 4f, 8f, 0);
            st.sync("abc", true, cfg);
            st.caretX(22f, cfg);
            st.isIdle(cfg);
            for (int i = 0; i < 3; i++) st.layoutChar(i, 4f + 6f * i, 8f, 6f, "abc".charAt(i), null, WHITE);
            st.endFrame();
        }
        float caret;
        int frames = 0;
        boolean idle;
        do { // Home pressed: caret target jumps from 22 to 4
            now += 16;
            st.beginFrame(now, 4f, 8f, 0);
            st.sync("abc", true, cfg);
            caret = st.caretX(4f, cfg);
            idle = st.isIdle(cfg);
            for (int i = 0; i < 3; i++) st.layoutChar(i, 4f + 6f * i, 8f, 6f, "abc".charAt(i), null, WHITE);
            st.endFrame();
            assertFalse(idle && caret != 4f,
                    "isIdle() == true but the caret is gliding in the same frame (caretX=" + caret + ", target 4)");
            if (frames == 0) assertTrue(caret > 4f && caret < 22f, "the caret glides: " + caret);
            assertTrue(++frames < 200, "caret must settle");
        } while (caret != 4f);
        assertTrue(frames > 3, "the glide lasts a few frames, took " + frames);
        now += 16;
        st.beginFrame(now, 4f, 8f, 0);
        st.sync("abc", true, cfg);
        assertEquals(4f, st.caretX(4f, cfg));
        assertTrue(st.isIdle(cfg), "settled caret -> idle again");
        st.endFrame();
    }

    /**
     * Caret-only move with the late order (isIdle before caretX, e.g. when the caret target is only known after
     * the text is drawn): isIdle may report the text idle in the first frame, but caretX must still glide in that
     * frame (idle frames never snap the caret; the renderer draws the caret through caretX on idle frames too),
     * and from the next frame on the state is non-idle until the caret settles.
     */
    @Test
    void caretOnlyMoveStillGlidesWhenCaretXFollowsAnIdleVerdict() {
        FieldAnimationState st = new FieldAnimationState();
        TypingConfig cfg = TypingConfig.defaults();
        long now = 1_000;
        for (int f = 0; f < 40; f++) {
            now += 16;
            st.beginFrame(now, 4f, 8f, 0);
            st.sync("abc", true, cfg);
            st.isIdle(cfg);
            for (int i = 0; i < 3; i++) st.layoutChar(i, 4f + 6f * i, 8f, 6f, "abc".charAt(i), null, WHITE);
            st.caretX(22f, cfg);
            st.endFrame();
        }
        now += 16;
        st.beginFrame(now, 4f, 8f, 0);
        st.sync("abc", true, cfg);
        boolean idle = st.isIdle(cfg);
        for (int i = 0; i < 3; i++) st.layoutChar(i, 4f + 6f * i, 8f, 6f, "abc".charAt(i), null, WHITE);
        float caret = st.caretX(4f, cfg); // Home pressed: caret target jumps from 22 to 4
        st.endFrame();
        assertTrue(idle, "the text is at rest; the caret move is not known yet");
        assertTrue(caret > 4f && caret < 22f, "the caret glides even though the text was idle: " + caret);

        int frames = 0;
        while (caret != 4f) {
            now += 16;
            st.beginFrame(now, 4f, 8f, 0);
            st.sync("abc", true, cfg);
            idle = st.isIdle(cfg);
            for (int i = 0; i < 3; i++) st.layoutChar(i, 4f + 6f * i, 8f, 6f, "abc".charAt(i), null, WHITE);
            caret = st.caretX(4f, cfg);
            st.endFrame();
            assertFalse(idle, "the caret was gliding last frame -> not idle (frame " + frames + ")");
            assertTrue(++frames < 200, "caret must settle");
        }
        now += 16;
        st.beginFrame(now, 4f, 8f, 0);
        st.sync("abc", true, cfg);
        assertTrue(st.isIdle(cfg), "settled caret -> idle again");
        st.endFrame();
    }

    /**
     * Regression: a ghost used to start fully visible at its layout position (alpha 1, no offset) even if the char
     * was still invisible when it was removed. Paste (staggered, t<=0 -> alpha 0) followed by an immediate
     * delete/undo made chars that were never visible flash in and fade out. Now such chars leave no ghost at all.
     */
    @Test
    void ghostOfStillInvisibleCharDoesNotFlashIn() {
        FieldAnimationState st = new FieldAnimationState();
        TypingConfig cfg = TypingConfig.defaults();
        CharTransform tf = new CharTransform();
        long now = 1_000;
        st.beginFrame(now, 4f, 8f, 0);
        st.sync("", true, cfg);
        st.endFrame();
        String paste = "0123456789";
        now += 16;
        st.beginFrame(now, 4f, 8f, 0);
        st.sync(paste, true, cfg);
        float[] drawnAlpha = new float[paste.length()];
        int visible = 0;
        for (int i = 0; i < paste.length(); i++) {
            st.layoutChar(i, 4f + 6f * i, 8f, 6f, paste.charAt(i), null, WHITE);
            drawnAlpha[i] = st.appearTransform(i, cfg, tf) ? tf.alpha : 1f;
            if (drawnAlpha[i] >= FieldAnimationState.MIN_GHOST_ALPHA) visible++;
        }
        st.endFrame();
        assertEquals(0, visible, "precondition: in its first frame the whole paste is still invisible");
        now += 16;
        st.beginFrame(now, 4f, 8f, 0);
        st.sync("", true, cfg); // e.g. Ctrl+Z / select-all + delete right after the paste
        assertEquals(visible, st.ghostCount(), "only chars that were visible leave a ghost");
        StringBuilder flashes = new StringBuilder();
        for (int g = 0; g < st.ghostCount(); g++) {
            FieldAnimationState.Ghost gh = st.ghost(g);
            boolean vis = st.ghostTransform(gh, cfg, tf);
            int i = paste.indexOf(gh.codepoint);
            if (vis && tf.alpha > drawnAlpha[i] + 0.05f) {
                flashes.append((char) gh.codepoint).append(": drawn alpha ").append(drawnAlpha[i])
                        .append(" -> ghost alpha ").append(tf.alpha).append("; ");
            }
        }
        st.endFrame();
        assertEquals("", flashes.toString(), "ghosts brighter than the chars were ever drawn");
    }

    /**
     * Same defect in a common flow: cycling chat tab-completions ~150 ms apart. The replaced suggestion tail
     * ("oat") was still sliding in (partly transparent, dy > 0); its ghosts used to restart at full opacity on the
     * baseline (a visible jump/flash before fading). They must continue from the drawn pose.
     */
    @Test
    void ghostDoesNotJumpWhenReplacingStillAppearingTabCompletion() {
        FieldAnimationState st = new FieldAnimationState();
        TypingConfig cfg = TypingConfig.defaults();
        CharTransform tf = new CharTransform();
        long now = 1_000;
        String v0 = "/give @p minecraft:a";
        String v1 = "/give @p minecraft:acacia_boat";
        String v2 = "/give @p minecraft:acacia_button";
        float[] drawnAlpha = new float[v1.length()];
        float[] drawnDy = new float[v1.length()];
        String cur = v0;
        for (int f = 0; f < 40; f++) {
            now += 10;
            if (f == 10) cur = v1; // Tab
            if (f == 25) cur = v2; // Tab again 150 ms later
            st.beginFrame(now, 4f, 8f, 0);
            st.sync(cur, true, cfg);
            if (f == 25) {
                assertTrue(st.ghostCount() >= 3, "precondition: the replaced tail is animated out, ghosts "
                        + st.ghostCount());
                StringBuilder jumps = new StringBuilder();
                for (int g = 0; g < st.ghostCount(); g++) {
                    FieldAnimationState.Ghost gh = st.ghost(g);
                    st.ghostTransform(gh, cfg, tf);
                    int i = (int) ((gh.x - 4f) / 6f);
                    if (tf.alpha > drawnAlpha[i] + 0.1f || Math.abs(tf.dy - drawnDy[i]) > 1f) {
                        jumps.append((char) gh.codepoint).append(" alpha ").append(drawnAlpha[i]).append("->")
                                .append(tf.alpha).append(" dy ").append(drawnDy[i]).append("->").append(tf.dy)
                                .append("; ");
                    }
                }
                assertEquals("", jumps.toString(), "ghost state jumps relative to the last drawn frame");
            }
            for (int i = 0; i < cur.length(); i++) {
                st.layoutChar(i, 4f + 6f * i, 8f, 6f, cur.charAt(i), null, WHITE);
                boolean has = st.appearTransform(i, cfg, tf);
                if (cur == v1) {
                    drawnAlpha[i] = has ? tf.alpha : 1f;
                    drawnDy[i] = has ? tf.dy : 0f;
                }
            }
            st.endFrame();
        }
    }

    /**
     * Regression: beginFrame() used to drop ghosts on an origin change, but ghosts created by sync() in that same
     * frame were recorded from last frame's (pre-move) layout, so they were drawn at the old widget position for
     * the whole exit animation. Now all ghosts move with the text by the origin delta.
     */
    @Test
    void ghostCreatedOnOriginChangeFrameMovesWithTheText() {
        FieldAnimationState st = new FieldAnimationState();
        TypingConfig cfg = TypingConfig.defaults();
        long now = 1_000;
        float ox = 4f;
        for (int f = 0; f < 30; f++) {
            now += 16;
            st.beginFrame(now, ox, 8f, 0);
            st.sync("abc", true, cfg);
            for (int i = 0; i < 3; i++) st.layoutChar(i, ox + 6f * i, 8f, 6f, "abc".charAt(i), null, WHITE);
            st.endFrame();
        }
        now += 16;
        ox = 104f; // widget moved (re-layout) in the same frame as a deletion
        st.beginFrame(now, ox, 8f, 0);
        st.sync("ab", true, cfg);
        for (int i = 0; i < 2; i++) st.layoutChar(i, ox + 6f * i, 8f, 6f, "ab".charAt(i), null, WHITE);
        FieldAnimationState.Ghost g = st.ghost(0);
        st.endFrame();
        assertTrue(g == null || g.x >= ox,
                "ghost of 'c' drawn at stale pre-move x=" + (g == null ? "-" : g.x) + " while the text is now at "
                        + ox);
        assertNotNull(g, "a visible deleted char still animates out");
        assertEquals('c', g.codepoint);
        assertEquals(ox + 12f, g.x, "exactly where 'c' now sits relative to the moved widget");
        assertEquals(8f, g.y);
    }

    // ================================================================================================ helpers

    private static void mutateConfig(TypingConfig cfg, Random rnd, AppearStyle[] appear, RemoveStyle[] remove,
                                     Easing[] easings) {
        switch (rnd.nextInt(12)) {
            case 0: cfg.enabled = rnd.nextInt(4) != 0; break;
            case 1: cfg.appearStyle = appear[rnd.nextInt(appear.length)]; break;
            case 2: cfg.removeStyle = remove[rnd.nextInt(remove.length)]; break;
            case 3: cfg.easing = easings[rnd.nextInt(easings.length)]; break;
            case 4: cfg.durationMs = 40 + rnd.nextInt(961); break;
            case 5: cfg.removeDurationMs = 40 + rnd.nextInt(961); break;
            case 6: cfg.staggerMs = rnd.nextInt(61); break;
            case 7: cfg.maxStaggerMs = rnd.nextInt(2001); break;
            case 8: cfg.glideMs = rnd.nextInt(4) == 0 ? 0 : rnd.nextInt(301); break;
            case 9: cfg.smoothReflow = rnd.nextBoolean(); break;
            case 10: cfg.smoothCursor = rnd.nextBoolean(); break;
            default: cfg.intensity = 0.25f + rnd.nextFloat() * 2.75f; break;
        }
    }

    private static String randomEdit(String v, Random rnd, String[] alphabet) {
        int n = v.length();
        int kind = rnd.nextInt(10);
        if (kind == 0) return ""; // clear
        if (kind == 1) { // big paste / history replace
            StringBuilder sb = new StringBuilder();
            int len = rnd.nextInt(120);
            for (int i = 0; i < len; i++) sb.append(alphabet[rnd.nextInt(alphabet.length)]);
            return rnd.nextBoolean() ? sb.toString() : v.substring(0, rnd.nextInt(n + 1)) + sb;
        }
        int a = rnd.nextInt(n + 1);
        int b = Math.min(n, a + (rnd.nextInt(3) == 0 ? rnd.nextInt(6) : 0));
        StringBuilder sb = new StringBuilder(v.substring(0, a));
        int ins = rnd.nextInt(3) == 0 ? rnd.nextInt(8) : (b > a ? 0 : 1);
        for (int i = 0; i < ins; i++) sb.append(alphabet[rnd.nextInt(alphabet.length)]);
        sb.append(v, b, n);
        String out = sb.toString();
        return out.length() > 300 ? out.substring(0, 50) : out;
    }

    private static void assertSane(CharTransform tf, String ctx) {
        assertTrue(Float.isFinite(tf.dx) && Float.isFinite(tf.dy) && Float.isFinite(tf.rotation), ctx + " " + tf);
        assertTrue(Float.isFinite(tf.scaleX) && tf.scaleX >= 0f && Float.isFinite(tf.scaleY) && tf.scaleY >= 0f,
                ctx + " " + tf);
        assertTrue(tf.alpha >= 0f && tf.alpha <= 1f, ctx + " " + tf);
        assertTrue(tf.glyph == -1 || Character.isValidCodePoint(tf.glyph), ctx + " " + tf);
    }

    private static int[] buildPool() {
        List<Integer> list = new ArrayList<>();
        for (int c = 'A'; c <= 'Z'; c++) list.add(c);
        for (int c = 'a'; c <= 'z'; c++) list.add(c);
        for (int c = 0x410; c <= 0x44F; c++) list.add(c);
        for (int c = 0x1F600; c <= 0x1F64F; c++) list.add(c);
        for (int c = 0x1D400; c <= 0x1D4FF; c++) list.add(c);
        for (int c = 0x1D600; c <= 0x1D64F; c++) list.add(c);
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private static String toText(List<int[]> chars) {
        StringBuilder sb = new StringBuilder();
        for (int[] c : chars) sb.appendCodePoint(c[0]);
        return sb.toString();
    }
}
