package dev.typinganimation.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static dev.typinganimation.core.FieldAnimationState.NO_BIRTH;
import static org.junit.jupiter.api.Assertions.*;

class FieldAnimationStateTest {
    static final int WHITE = 0xFFFFFFFF;
    static final String EMOJI_A = new String(Character.toChars(0x1F600)); // D83D DE00
    static final String EMOJI_B = new String(Character.toChars(0x1F601)); // D83D DE01
    static final String MATH_A = new String(Character.toChars(0x1D49C)); // D835 DC9C

    /** Minimal renderer simulation: monospace layout of every code point at ox + cpIndex * charW - scrollPx. */
    static final class Sim {
        final FieldAnimationState st = new FieldAnimationState();
        final TypingConfig cfg = TypingConfig.defaults();
        final CharTransform tf = new CharTransform();
        long now = 10_000L;
        float ox = 4f, oy = 8f;
        int scroll = 0;
        float scrollPx = 0f;
        float charW = 6f;
        int firstVisibleCp = 0;
        Set<Integer> hidden = new HashSet<>();
        boolean skipLayout;
        boolean callIdle = true;
        Object style = "style";
        int[] colors;
        float[] xs = new float[0];
        boolean lastIdle;

        void frame(String v) {
            frame(v, true);
        }

        void frame(String v, boolean animate) {
            st.beginFrame(now, ox, oy, scroll);
            st.sync(v, animate, cfg);
            if (callIdle) {
                lastIdle = st.isIdle(cfg);
            }
            if (!skipLayout) {
                layout(v);
            }
            st.endFrame();
        }

        void tick(String v) {
            now += 16;
            frame(v);
        }

        void settle(String v) {
            for (int k = 0; k < 120; k++) {
                tick(v);
            }
        }

        float target(int cpIndex) {
            return ox + cpIndex * charW - scrollPx;
        }

        void layout(String v) {
            xs = new float[v.length()];
            Arrays.fill(xs, Float.NaN);
            int cpIndex = 0;
            for (int i = 0; i < v.length(); ) {
                int cp = v.codePointAt(i);
                if (cpIndex >= firstVisibleCp && !hidden.contains(i)) {
                    int color = colors != null ? colors[i] : WHITE;
                    xs[i] = st.layoutChar(i, target(cpIndex), oy, charW, cp, style, color);
                }
                cpIndex++;
                i += Character.charCount(cp);
            }
        }

        /** beginFrame + sync only (to inspect the state between sync and layout). */
        void beginAndSync(String v) {
            now += 16;
            st.beginFrame(now, ox, oy, scroll);
            st.sync(v, true, cfg);
        }

        void finish(String v) {
            layout(v);
            st.endFrame();
        }

        boolean appear(int i) {
            return st.appearTransform(i, cfg, tf);
        }
    }

    private static float alpha(float dtMs, float glideMs) {
        return (float) (1.0 - Math.exp(-dtMs / (double) glideMs));
    }

    // ------------------------------------------------------------------ births

    @Test
    void firstSyncHasNoAnimation() {
        Sim s = new Sim();
        s.frame("hello");
        for (int i = 0; i < 5; i++) {
            assertEquals(NO_BIRTH, s.st.birthAt(i));
            assertFalse(s.appear(i));
            assertTrue(s.tf.isIdentity());
            assertEquals(s.target(i), s.xs[i]);
        }
        assertEquals(0, s.st.ghostCount());
        assertEquals("hello", s.st.syncedValue());
    }

    @Test
    void typingAtEndAnimatesOnlyTheNewChar() {
        Sim s = new Sim();
        s.frame("hell");
        s.tick("hell");
        s.tick("hello");
        assertEquals(s.now, s.st.birthAt(4));
        for (int i = 0; i < 4; i++) {
            assertEquals(NO_BIRTH, s.st.birthAt(i));
            assertFalse(s.appear(i));
        }
        assertTrue(s.appear(4));
        assertEquals(0f, s.tf.alpha, "t=0: invisible");
        assertEquals(7f, s.tf.dy, 1e-5f);
        assertEquals(s.target(4), s.xs[4], "a new char is placed at its target");

        s.now += 110;
        s.frame("hello");
        assertTrue(s.appear(4));
        float e = Easing.CUBIC_OUT.apply(0.5f);
        assertEquals((1 - e) * 7f, s.tf.dy, 1e-4f);
        assertEquals(1f, s.tf.alpha);

        s.now += 110;
        s.frame("hello");
        assertFalse(s.appear(4));
        assertTrue(s.tf.isIdentity());
        assertEquals(NO_BIRTH, s.st.birthAt(4));
    }

    @Test
    void insertAtStartKeepsIdentityAndGlides() {
        Sim s = new Sim();
        s.frame("bcd");
        s.settle("bcd");
        float oldB = s.xs[0];
        float oldC = s.xs[1];
        s.beginAndSync("abcd");
        assertEquals(s.now, s.st.birthAt(0));
        for (int i = 1; i < 4; i++) assertEquals(NO_BIRTH, s.st.birthAt(i));
        assertEquals(oldB, s.st.smoothedXAt(1), "'b' moved to index 1 with its smoothed x");
        assertEquals(oldC, s.st.smoothedXAt(2));
        s.finish("abcd");
        float a = alpha(16, 55);
        assertEquals(s.target(0), s.xs[0], "new char snaps");
        assertEquals(oldB + (s.target(1) - oldB) * a, s.xs[1], 1e-4f);
        assertEquals(oldC + (s.target(2) - oldC) * a, s.xs[2], 1e-4f);
        assertTrue(s.xs[1] > oldB && s.xs[1] < s.target(1));
    }

    @Test
    void insertInMiddleKeepsIdentity() {
        Sim s = new Sim();
        s.frame("acd");
        s.settle("acd");
        s.beginAndSync("abcd");
        assertEquals(NO_BIRTH, s.st.birthAt(0));
        assertEquals(s.now, s.st.birthAt(1));
        assertEquals(NO_BIRTH, s.st.birthAt(2));
        assertEquals(s.target(0), s.st.smoothedXAt(0));
        assertEquals(s.target(1), s.st.smoothedXAt(2), "'c' keeps its old x");
        assertEquals(s.target(2), s.st.smoothedXAt(3), "'d' keeps its old x");
        s.finish("abcd");
        assertEquals(s.target(0), s.xs[0]);
        assertEquals(s.target(1), s.xs[1]);
        assertTrue(s.xs[2] < s.target(2) && s.xs[2] > s.target(1));
    }

    @Test
    void replaceAllGhostsOldAndStaggersNew() {
        Sim s = new Sim();
        s.frame("hello");
        s.settle("hello");
        s.tick("xyz");
        assertEquals(5, s.st.ghostCount());
        String old = "hello";
        for (int i = 0; i < 5; i++) {
            FieldAnimationState.Ghost g = s.st.ghost(i);
            assertEquals(old.charAt(i), g.codepoint);
            assertEquals(s.target(i), g.x);
            assertEquals(s.now, g.removedAt);
        }
        assertEquals(s.now, s.st.birthAt(0));
        assertEquals(s.now + 14, s.st.birthAt(1));
        assertEquals(s.now + 28, s.st.birthAt(2));
    }

    @Test
    void pasteIsStaggered() {
        Sim s = new Sim();
        s.frame("ab");
        s.tick("ab");
        s.tick("ab0123456789");
        // step = min(staggerMs=14, maxStaggerMs/(n-1)=350/9)
        for (int i = 0; i < 10; i++) {
            assertEquals(s.now + 14L * i, s.st.birthAt(2 + i));
        }
        assertEquals(NO_BIRTH, s.st.birthAt(0));
        // not yet born chars are fully hidden
        assertTrue(s.appear(11));
        assertEquals(0f, s.tf.alpha);
    }

    @Test
    void longPasteIsCappedByMaxStagger() {
        Sim s = new Sim();
        s.frame("ab");
        StringBuilder sb = new StringBuilder("ab");
        for (int i = 0; i < 101; i++) sb.append((char) ('A' + i % 26));
        String v = sb.toString();
        s.tick(v);
        float step = 350f / 100f;
        for (int i = 0; i <= 100; i++) {
            assertEquals(s.now + Math.round(i * step), s.st.birthAt(2 + i), "i=" + i);
        }
        assertEquals(s.now + 350, s.st.birthAt(102));

        Sim z = new Sim();
        z.cfg.staggerMs = 0;
        z.frame("");
        z.tick("abcdef");
        for (int i = 0; i < 6; i++) assertEquals(z.now, z.st.birthAt(i));

        Sim m = new Sim();
        m.cfg.maxStaggerMs = 0;
        m.frame("");
        m.tick("abcdef");
        for (int i = 0; i < 6; i++) assertEquals(m.now, m.st.birthAt(i));
    }

    @Test
    void singleInsertHasNoStagger() {
        Sim s = new Sim();
        s.cfg.staggerMs = 60;
        s.frame("abc");
        s.tick("abXc");
        assertEquals(s.now, s.st.birthAt(2));
    }

    @Test
    void animateFalseMeansNoBirthsNoGhostsAndNoGlide() {
        Sim s = new Sim();
        s.frame("abc");
        s.settle("abc");
        s.now += 16;
        s.frame("xabc", false);
        for (int i = 0; i < 4; i++) {
            assertEquals(NO_BIRTH, s.st.birthAt(i));
            assertEquals(s.target(i), s.xs[i], "instant change");
        }
        s.now += 16;
        s.frame("xab", false);
        assertEquals(0, s.st.ghostCount());
        // disabled config behaves the same even if the renderer passes animate=true
        s.cfg.enabled = false;
        s.tick("xabcd");
        assertEquals(NO_BIRTH, s.st.birthAt(3));
        s.tick("xa");
        assertEquals(0, s.st.ghostCount());
    }

    @Test
    void noneStylesCreateNothing() {
        Sim s = new Sim();
        s.cfg.appearStyle = AppearStyle.NONE;
        s.cfg.removeStyle = RemoveStyle.NONE;
        s.frame("abc");
        s.settle("abc");
        s.tick("abcd");
        assertEquals(NO_BIRTH, s.st.birthAt(3));
        assertFalse(s.appear(3));
        s.tick("ab");
        assertEquals(0, s.st.ghostCount());
    }

    // ------------------------------------------------------------------ ghosts

    @Test
    void deleteAtEndCreatesGhostAtLastPosition() {
        Sim s = new Sim();
        Object style = new Object();
        s.style = style;
        s.colors = new int[]{1, 2, 3, 4, 0xFF00FF00};
        s.frame("hello");
        s.settle("hello");
        s.tick("hell");
        assertEquals(1, s.st.ghostCount());
        FieldAnimationState.Ghost g = s.st.ghost(0);
        assertEquals('o', g.codepoint);
        assertEquals(s.target(4), g.x);
        assertEquals(s.oy, g.y);
        assertEquals(6f, g.width);
        assertEquals(s.now, g.removedAt);
        assertSame(style, g.style);
        assertEquals(0xFF00FF00, g.color);
        assertTrue(s.st.ghostTransform(g, s.cfg, s.tf));
        assertTrue(s.tf.isIdentity(), "t=0: fully visible");
        assertNull(s.st.ghost(1));
        assertNull(s.st.ghost(-1));
    }

    @Test
    void ghostUsesItsSmoothedPositionWhileGliding() {
        Sim s = new Sim();
        s.frame("bc");
        s.settle("bc");
        s.tick("abc"); // 'b','c' start gliding right
        float midC = s.xs[2];
        assertTrue(midC < s.target(2));
        s.tick("ab");
        FieldAnimationState.Ghost g = s.st.ghost(0);
        assertEquals('c', g.codepoint);
        assertEquals(midC, g.x, "ghost starts where the char was drawn last frame");
    }

    /** Removes the last char of {@code v} after it was drawn for {@code framesShown} frames; returns its pose then. */
    private static CharTransform drawThenRemoveLast(Sim s, String v, int framesShown) {
        String shorter = v.substring(0, v.length() - 1);
        s.frame(shorter);
        s.settle(shorter);
        s.tick(v);
        for (int k = 1; k < framesShown; k++) s.tick(v);
        CharTransform drawn = new CharTransform();
        if (!s.st.appearTransform(v.length() - 1, s.cfg, drawn)) drawn.reset();
        s.tick(shorter);
        return drawn;
    }

    /** Where the renderer's matrix (translate to pivot, rotate, scale, translate back, plus dx/dy) maps q. */
    private static float[] map(CharTransform t, float cellX, float cellY, float w, float qx, float qy) {
        float px = cellX + t.pivotX * w, py = cellY + t.pivotY * 9f;
        float sx = (qx - px) * t.scaleX, sy = (qy - py) * t.scaleY;
        float cos = (float) Math.cos(t.rotation), sin = (float) Math.sin(t.rotation);
        return new float[]{px + cos * sx - sin * sy + t.dx, py + sin * sx + cos * sy + t.dy};
    }

    private static void assertSamePose(CharTransform expected, CharTransform actual, float w) {
        assertEquals(expected.alpha, actual.alpha, 1e-6f);
        assertEquals(expected.glyph, actual.glyph);
        float[][] corners = {{0, 0}, {w, 0}, {0, 9}, {w, 9}, {w / 3, 5}};
        for (float[] q : corners) {
            float[] a = map(expected, 0, 0, w, q[0], q[1]);
            float[] b = map(actual, 0, 0, w, q[0], q[1]);
            assertEquals(a[0], b[0], 1e-4f, "x of " + Arrays.toString(q) + " " + expected + " vs " + actual);
            assertEquals(a[1], b[1], 1e-4f, "y of " + Arrays.toString(q) + " " + expected + " vs " + actual);
        }
    }

    @Test
    void ghostOfCharRemovedMidAppearStartsFromItsDrawnPose() {
        Sim s = new Sim(); // SLIDE_UP: partial alpha and dy
        CharTransform drawn = drawThenRemoveLast(s, "hello", 4);
        assertTrue(drawn.alpha > 0.2f && drawn.alpha < 1f && drawn.dy > 0.5f, "precondition " + drawn);
        assertEquals(1, s.st.ghostCount());
        FieldAnimationState.Ghost g = s.st.ghost(0);
        assertTrue(s.st.ghostTransform(g, s.cfg, s.tf));
        assertSamePose(drawn, s.tf, 6f);

        // then it fades out from there: never brighter than the start, gone at the end
        float prev = s.tf.alpha;
        for (long t = 16; t < 160; t += 16) {
            s.now += 16;
            s.frame("hell");
            assertTrue(s.st.ghostTransform(g, s.cfg, s.tf) || t >= 160);
            assertTrue(s.tf.alpha <= prev + 1e-6f, "monotonic fade at " + t);
            assertEquals(drawn.dy, s.tf.dy, 1e-5f, "FADE exit holds the pose it had");
            prev = s.tf.alpha;
        }
        s.now += 16;
        s.frame("hell");
        assertFalse(s.st.ghostTransform(g, s.cfg, s.tf));
    }

    @Test
    void ghostOfCharRemovedInTheFrameAfterItsBirthIsNotCreated() {
        Sim s = new Sim();
        CharTransform drawn = drawThenRemoveLast(s, "hello", 1); // drawn once, at t=0: invisible
        assertEquals(0f, drawn.alpha);
        assertEquals(0, s.st.ghostCount());
    }

    @Test
    void ghostPoseIsReExpressedAroundTheCellCentre() {
        for (AppearStyle style : new AppearStyle[]{AppearStyle.GROW, AppearStyle.STRETCH, AppearStyle.SPIN,
                AppearStyle.POP, AppearStyle.DROP, AppearStyle.SLIDE_LEFT, AppearStyle.WAVE}) {
            Sim s = new Sim();
            s.cfg.appearStyle = style;
            s.cfg.intensity = 2f;
            CharTransform drawn = drawThenRemoveLast(s, "hello", 3);
            assertFalse(drawn.isIdentity(), style + " precondition");
            assertEquals(1, s.st.ghostCount(), style.name());
            assertTrue(s.st.ghostTransform(s.st.ghost(0), s.cfg, s.tf), style.name());
            assertEquals(0.5f, s.tf.pivotX);
            assertEquals(0.5f, s.tf.pivotY);
            assertSamePose(drawn, s.tf, 6f);
        }
    }

    @Test
    void ghostPoseComposesWithEveryRemoveStyle() {
        for (RemoveStyle rs : RemoveStyle.values()) {
            if (rs == RemoveStyle.NONE) continue;
            Sim s = new Sim();
            s.cfg.appearStyle = AppearStyle.SPIN;
            s.cfg.removeStyle = rs;
            CharTransform drawn = drawThenRemoveLast(s, "hello", 3);
            FieldAnimationState.Ghost g = s.st.ghost(0);
            assertTrue(s.st.ghostTransform(g, s.cfg, s.tf), rs.name());
            assertSamePose(drawn, s.tf, 6f); // every exit is identity at t=0
            s.now += 80;
            s.frame("hell");
            assertTrue(s.st.ghostTransform(g, s.cfg, s.tf), rs.name());
            CharTransform exit = new CharTransform();
            rs.apply(0.5f, (s.cfg.easing.resolve(rs.defaultEasing())).apply(0.5f), s.cfg.intensity, g.seed, exit);
            assertEquals(exit.alpha * drawn.alpha, s.tf.alpha, 1e-5f, rs.name());
            assertTrue(s.tf.alpha < drawn.alpha, rs.name());
        }
    }

    @Test
    void scrambledGhostKeepsItsScrambledGlyph() {
        Sim s = new Sim();
        s.cfg.appearStyle = AppearStyle.SCRAMBLE;
        CharTransform drawn = drawThenRemoveLast(s, "hello", 3); // t ~ 0.15 < reveal
        assertNotEquals(-1, drawn.glyph);
        FieldAnimationState.Ghost g = s.st.ghost(0);
        assertEquals('o', g.codepoint);
        assertTrue(s.st.ghostTransform(g, s.cfg, s.tf));
        assertEquals(drawn.glyph, s.tf.glyph, "the ghost keeps the glyph that was on screen");
        s.now += 50;
        s.frame("hell");
        assertTrue(s.st.ghostTransform(g, s.cfg, s.tf));
        assertEquals(drawn.glyph, s.tf.glyph, "frozen, not cycling");
    }

    @Test
    void ghostOfSettledCharAndPublicConstructorStartFullyVisible() {
        FieldAnimationState.Ghost g = new FieldAnimationState.Ghost('x', null, WHITE, 1f, 2f, 6f, 10_000L, 7);
        Sim s = new Sim();
        s.frame("a");
        assertTrue(s.st.ghostTransform(g, s.cfg, s.tf));
        assertTrue(s.tf.isIdentity(), "t=0: fully visible " + s.tf);
    }

    @Test
    void deleteInMiddleKeepsIdentityOfTail() {
        Sim s = new Sim();
        s.frame("abcde");
        s.settle("abcde");
        s.beginAndSync("abde");
        assertEquals(1, s.st.ghostCount());
        assertEquals('c', s.st.ghost(0).codepoint);
        assertEquals(s.target(2), s.st.ghost(0).x);
        assertEquals(s.target(3), s.st.smoothedXAt(2), "'d' keeps its old x");
        assertEquals(s.target(4), s.st.smoothedXAt(3), "'e' keeps its old x");
        s.finish("abde");
        assertTrue(s.xs[2] < s.target(3) && s.xs[2] > s.target(2), "'d' glides left");
        assertEquals(s.target(1), s.xs[1]);
    }

    @Test
    void ghostsExpireAndArePruned() {
        Sim s = new Sim();
        s.frame("abc");
        s.settle("abc");
        s.tick("ab");
        FieldAnimationState.Ghost g = s.st.ghost(0);
        s.now += 80;
        s.frame("ab");
        assertEquals(1, s.st.ghostCount());
        assertTrue(s.st.ghostTransform(g, s.cfg, s.tf));
        assertEquals(1f - Easing.QUAD_OUT.apply(0.5f), s.tf.alpha, 1e-5f);
        assertEquals(g.x, s.st.ghost(0).x, "ghost position is fixed");
        s.now += 80;
        s.frame("ab");
        assertEquals(0, s.st.ghostCount());
        assertFalse(s.st.ghostTransform(g, s.cfg, s.tf));
        assertEquals(0f, s.tf.alpha);
        assertFalse(s.st.ghostTransform(null, s.cfg, s.tf));
    }

    @Test
    void ghostsOnlyForCharsLaidOutInThePreviousFrame() {
        Sim s = new Sim();
        s.frame("abcdef");
        s.settle("abcdef");
        s.skipLayout = true;
        s.tick("abcdef");
        s.skipLayout = false;
        s.tick("abcde"); // 'f' was laid out two frames ago, not in the previous one
        assertEquals(0, s.st.ghostCount());

        s.firstVisibleCp = 2; // scrolled: a, b invisible
        s.settle("abcde");
        s.tick("bcde");
        assertEquals(0, s.st.ghostCount(), "invisible char leaves no ghost");
        s.tick("bcd");
        assertEquals(1, s.st.ghostCount());
        assertEquals('e', s.st.ghost(0).codepoint);
    }

    @Test
    void ghostCountIsCapped() {
        Sim s = new Sim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 700; i++) sb.append((char) ('a' + i % 26));
        s.frame(sb.toString());
        s.tick(sb.toString());
        s.tick("");
        assertEquals(FieldAnimationState.MAX_GHOSTS, s.st.ghostCount());
    }

    @Test
    void originChangeSnapsAndShiftsGhosts() {
        Sim s = new Sim();
        s.frame("bcd");
        s.settle("bcd");
        s.tick("abcd");
        assertTrue(s.xs[1] != s.target(1));
        s.tick("abc");
        assertEquals(1, s.st.ghostCount());
        FieldAnimationState.Ghost before = s.st.ghost(0);
        s.ox += 10;
        s.oy += 3;
        s.tick("abc");
        for (int i = 0; i < 3; i++) assertEquals(s.target(i), s.xs[i]);
        assertEquals(1, s.st.ghostCount(), "the ghost moves with the widget instead of vanishing");
        FieldAnimationState.Ghost after = s.st.ghost(0);
        assertEquals(before.x + 10f, after.x);
        assertEquals(before.y + 3f, after.y);
        assertEquals(before.codepoint, after.codepoint);
        assertEquals(before.removedAt, after.removedAt, "the exit animation is not restarted");
        assertEquals(before.seed, after.seed);
        assertFalse(s.lastIdle);
    }

    @Test
    void ghostCreatedInTheOriginChangeFrameUsesTheNewOrigin() {
        Sim s = new Sim();
        s.frame("abcd");
        s.settle("abcd");
        s.ox += 100; // widget moved in the same frame as a deletion
        s.tick("abc");
        assertEquals(1, s.st.ghostCount());
        assertEquals(s.target(3), s.st.ghost(0).x, "'d' leaves from where it sits in the moved widget");
        assertEquals(s.oy, s.st.ghost(0).y);
    }

    @Test
    void nonFiniteOriginChangeDropsGhosts() {
        Sim s = new Sim();
        s.frame("abcd");
        s.settle("abcd");
        s.tick("abc");
        assertEquals(1, s.st.ghostCount());
        s.ox = Float.NaN; // the old positions can not be mapped
        s.tick("ab");
        assertEquals(0, s.st.ghostCount());
    }

    // ------------------------------------------------------------------ surrogate pairs

    @Test
    void emojiAppendedAtEnd() {
        Sim s = new Sim();
        s.frame("ab");
        s.tick("ab" + EMOJI_A);
        assertEquals(s.now, s.st.birthAt(2));
        assertEquals(s.now, s.st.birthAt(3));
        assertTrue(s.appear(2));
        assertEquals(s.target(2), s.xs[2]);
        assertTrue(Float.isNaN(s.xs[3]), "low surrogate index is never laid out");
    }

    @Test
    void emojiInsertedBeforeEmojiSharingHighSurrogate() {
        Sim s = new Sim();
        s.frame(EMOJI_A);
        s.settle(EMOJI_A);
        s.beginAndSync(EMOJI_B + EMOJI_A);
        assertEquals(s.now, s.st.birthAt(0));
        assertEquals(s.now, s.st.birthAt(1));
        assertEquals(NO_BIRTH, s.st.birthAt(2), "old emoji keeps its identity");
        assertEquals(NO_BIRTH, s.st.birthAt(3));
        assertEquals(s.target(0), s.st.smoothedXAt(2));
        assertEquals(0, s.st.ghostCount());
        s.finish(EMOJI_B + EMOJI_A);
    }

    @Test
    void emojiReplacedByEmojiSharingHighSurrogate() {
        Sim s = new Sim();
        s.frame("x" + EMOJI_A + "y");
        s.settle("x" + EMOJI_A + "y");
        s.beginAndSync("x" + EMOJI_B + "y");
        assertEquals(1, s.st.ghostCount());
        assertEquals(0x1F600, s.st.ghost(0).codepoint);
        assertEquals(NO_BIRTH, s.st.birthAt(0));
        assertEquals(s.now, s.st.birthAt(1));
        assertEquals(s.now, s.st.birthAt(2));
        assertEquals(NO_BIRTH, s.st.birthAt(3));
        assertEquals(s.target(2), s.st.smoothedXAt(3));
        s.finish("x" + EMOJI_B + "y");
    }

    @Test
    void emojiReplacedByEmojiSharingLowSurrogate() {
        // U+1F600 = D83D DE00 and U+1D600 = D835 DE00 share the low surrogate
        String other = new String(Character.toChars(0x1D600));
        assertEquals(EMOJI_A.charAt(1), other.charAt(1));
        Sim s = new Sim();
        s.frame("x" + EMOJI_A + "y");
        s.settle("x" + EMOJI_A + "y");
        s.beginAndSync("x" + other + "y");
        assertEquals(1, s.st.ghostCount());
        assertEquals(0x1F600, s.st.ghost(0).codepoint);
        assertEquals(s.now, s.st.birthAt(1));
        assertEquals(s.now, s.st.birthAt(2));
        assertEquals(NO_BIRTH, s.st.birthAt(3));
        s.finish("x" + other + "y");
    }

    @Test
    void emojiDeletedFromMiddle() {
        Sim s = new Sim();
        s.frame("a" + EMOJI_A + "b");
        s.settle("a" + EMOJI_A + "b");
        s.beginAndSync("ab");
        assertEquals(1, s.st.ghostCount());
        assertEquals(0x1F600, s.st.ghost(0).codepoint);
        assertEquals(s.target(1), s.st.ghost(0).x);
        assertEquals(s.target(2), s.st.smoothedXAt(1), "'b' keeps its identity");
        s.finish("ab");
    }

    @Test
    void identicalEmojiAppendedAndDeleted() {
        Sim s = new Sim();
        s.frame(EMOJI_A);
        s.settle(EMOJI_A);
        s.tick(EMOJI_A + EMOJI_A);
        assertEquals(NO_BIRTH, s.st.birthAt(0));
        assertEquals(s.now, s.st.birthAt(2));
        assertEquals(s.now, s.st.birthAt(3));
        s.settle(EMOJI_A + EMOJI_A);
        s.tick(EMOJI_A);
        assertEquals(1, s.st.ghostCount());
        assertEquals(s.target(1), s.st.ghost(0).x);
    }

    @Test
    void pasteWithEmojiStaggersByCodePoint() {
        Sim s = new Sim();
        s.frame("a");
        s.tick("a" + EMOJI_A + MATH_A + "b");
        assertEquals(s.now, s.st.birthAt(1));
        assertEquals(s.now, s.st.birthAt(2));
        assertEquals(s.now + 14, s.st.birthAt(3));
        assertEquals(s.now + 14, s.st.birthAt(4));
        assertEquals(s.now + 28, s.st.birthAt(5));
    }

    @Test
    void malformedSurrogatesDoNotBreakAnything() {
        Sim s = new Sim();
        String withPair = "a" + EMOJI_A;
        s.frame(withPair);
        s.settle(withPair);
        s.tick("a\uD83D"); // lone high surrogate left behind
        assertEquals(1, s.st.ghostCount());
        assertEquals(0x1F600, s.st.ghost(0).codepoint);
        assertEquals(s.now, s.st.birthAt(1));
        s.tick("a😁");
        s.tick("\uDE01\uDE01\uD83D");
        s.tick("\uD83D😀");
        s.tick("");
        s.tick("\uDE00");
        assertEquals(1, s.st.length());
    }

    /**
     * Random edits on strings whose code points are all distinct (so the edit is unambiguous) must keep every
     * surviving character's identity (tracked through its colour) and turn exactly the removed ones into ghosts.
     */
    @Test
    void fuzzUniqueCodePointsKeepIdentity() {
        Random rnd = new Random(12345);
        int[] pool = buildPool();
        int nextFresh = 0;
        Sim s = new Sim();
        s.cfg.staggerMs = 0;
        s.callIdle = false;
        List<int[]> model = new ArrayList<>(); // {cp, tag}
        int nextTag = 1;
        s.frame("");
        Map<Integer, Float> lastXByTag = new HashMap<>();
        Set<Integer> visibleTags = new HashSet<>(); // chars drawn with a visible appear alpha in the last frame
        for (int iter = 0; iter < 3000; iter++) {
            if (nextFresh > pool.length - 10) {
                // start over with a fresh state when the pool is used up
                nextFresh = 0;
                model.clear();
                s.st.reset();
                s.frame("");
            }
            int n = model.size();
            int a = rnd.nextInt(n + 1);
            int b = Math.min(n, a + (rnd.nextInt(3) == 0 ? rnd.nextInt(5) : 0));
            int ins = rnd.nextInt(4) == 0 ? rnd.nextInt(6) : (b > a ? 0 : 1);
            if (n > 30) {
                b = Math.min(n, a + 3);
                ins = 0;
            }
            if (a == b && ins == 0) ins = 1;
            List<int[]> removed = new ArrayList<>(model.subList(a, b));
            List<int[]> inserted = new ArrayList<>();
            for (int k = 0; k < ins; k++) inserted.add(new int[]{pool[nextFresh++], nextTag++});
            List<int[]> next = new ArrayList<>(model.subList(0, a));
            next.addAll(inserted);
            next.addAll(model.subList(b, n));

            String newText = toText(next);
            int ghostsBefore = s.st.ghostCount();
            s.beginAndSync(newText);

            // removed chars were all laid out last frame -> one ghost each unless still invisible there (born in
            // that very frame: t=0, alpha 0), same order, carrying their colour
            List<int[]> expGhosts = new ArrayList<>();
            for (int[] c : removed) if (visibleTags.contains(c[1])) expGhosts.add(c);
            assertEquals(ghostsBefore + expGhosts.size(), s.st.ghostCount(), "iter " + iter);
            for (int k = 0; k < expGhosts.size(); k++) {
                FieldAnimationState.Ghost g = s.st.ghost(ghostsBefore + k);
                assertEquals(expGhosts.get(k)[1], g.color, "iter " + iter);
                assertEquals(expGhosts.get(k)[0], g.codepoint);
                assertEquals(lastXByTag.get(expGhosts.get(k)[1]), g.x);
            }
            // survivors keep smoothed x; inserted chars are born now
            int idx = 0;
            Set<Integer> insertedTags = new HashSet<>();
            for (int[] c : inserted) insertedTags.add(c[1]);
            for (int[] c : next) {
                if (insertedTags.contains(c[1])) {
                    assertEquals(s.now, s.st.birthAt(idx), "iter " + iter);
                    if (Character.charCount(c[0]) == 2) assertEquals(s.now, s.st.birthAt(idx + 1));
                } else {
                    assertNotEquals(s.now, s.st.birthAt(idx), "iter " + iter);
                    assertEquals(lastXByTag.get(c[1]), s.st.smoothedXAt(idx), "iter " + iter);
                }
                idx += Character.charCount(c[0]);
            }
            assertEquals(newText.length(), s.st.length());

            s.colors = colors(next, newText.length());
            s.finish(newText);
            lastXByTag.clear();
            visibleTags.clear();
            int u = 0;
            for (int[] c : next) {
                lastXByTag.put(c[1], s.xs[u]);
                if (!s.appear(u) || s.tf.alpha >= FieldAnimationState.MIN_GHOST_ALPHA) visibleTags.add(c[1]);
                u += Character.charCount(c[0]);
            }
            assertFalse(visibleTags.containsAll(insertedTags) && !insertedTags.isEmpty(),
                    "chars born this frame are still invisible (t=0)");
            model = next;
        }
    }

    /** Random edits with repeated chars and shared surrogates never split a pair. */
    @Test
    void fuzzRandomEditsNeverSplitSurrogatePairs() {
        String[] alphabet = {"a", "b", EMOJI_A, EMOJI_B, MATH_A, new String(Character.toChars(0x1D600))};
        Random rnd = new Random(987);
        Sim s = new Sim();
        s.cfg.staggerMs = 0;
        s.callIdle = false;
        List<String> cps = new ArrayList<>();
        s.frame("");
        for (int iter = 0; iter < 5000; iter++) {
            int n = cps.size();
            int a = rnd.nextInt(n + 1);
            int b = Math.min(n, a + rnd.nextInt(3));
            List<String> next = new ArrayList<>(cps.subList(0, a));
            int ins = rnd.nextInt(3);
            for (int k = 0; k < ins; k++) next.add(alphabet[rnd.nextInt(alphabet.length)]);
            next.addAll(cps.subList(b, n));
            if (next.size() > 25) next = new ArrayList<>(next.subList(0, 10));
            String text = String.join("", next);
            int ghostsBefore = s.st.ghostCount();
            s.beginAndSync(text);
            assertEquals(text.length(), s.st.length());
            for (int i = 0; i + 1 < text.length(); i++) {
                if (Character.isHighSurrogate(text.charAt(i)) && Character.isLowSurrogate(text.charAt(i + 1))) {
                    assertEquals(s.st.birthAt(i) == s.now, s.st.birthAt(i + 1) == s.now,
                            "pair split at " + i + " iter " + iter);
                    i++;
                }
            }
            for (int g = ghostsBefore; g < s.st.ghostCount(); g++) {
                int cp = s.st.ghost(g).codepoint;
                assertTrue(Character.isValidCodePoint(cp) && !Character.isSurrogate((char) cp) || cp > 0xFFFF,
                        "ghost must be a whole code point: " + Integer.toHexString(cp));
            }
            s.finish(text);
            cps = next;
        }
    }

    private static int[] buildPool() {
        List<Integer> list = new ArrayList<>();
        for (int c = 'A'; c <= 'Z'; c++) list.add(c);
        for (int c = 'a'; c <= 'z'; c++) list.add(c);
        for (int c = 0x410; c <= 0x44F; c++) list.add(c); // Cyrillic
        for (int c = 0x1F600; c <= 0x1F64F; c++) list.add(c); // emoji sharing D83D
        for (int c = 0x1D400; c <= 0x1D4FF; c++) list.add(c); // math letters sharing D835
        for (int c = 0x1D600; c <= 0x1D64F; c++) list.add(c); // low surrogates shared with the emoji block
        java.util.Collections.shuffle(list, new Random(7));
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private static String toText(List<int[]> chars) {
        StringBuilder sb = new StringBuilder();
        for (int[] c : chars) sb.appendCodePoint(c[0]);
        return sb.toString();
    }

    private static int[] colors(List<int[]> chars, int len) {
        int[] out = new int[len];
        int u = 0;
        for (int[] c : chars) {
            out[u] = c[1];
            if (Character.charCount(c[0]) == 2) out[u + 1] = c[1];
            u += Character.charCount(c[0]);
        }
        return out;
    }

    // ------------------------------------------------------------------ smoothing

    @Test
    void smoothingFollowsExponentialFormula() {
        Sim s = new Sim();
        s.frame("bc");
        s.settle("bc");
        float x0 = s.xs[0];
        s.tick("abc");
        float x1 = s.xs[1];
        assertEquals(x0 + (s.target(1) - x0) * alpha(16, 55), x1, 1e-4f);
        s.now += 33;
        s.frame("abc");
        float x2 = s.xs[1];
        assertEquals(x1 + (s.target(1) - x1) * alpha(33, 55), x2, 1e-4f);

        s.cfg.glideMs = 200;
        s.now += 16;
        s.frame("abc");
        assertEquals(x2 + (s.target(1) - x2) * alpha(16, 200), s.xs[1], 1e-4f);
    }

    @Test
    void smoothingSettlesExactlyWithSnap() {
        Sim s = new Sim();
        s.frame("bc");
        s.settle("bc");
        s.tick("abc");
        int frames = 1;
        while (s.xs[1] != s.target(1)) {
            assertTrue(Math.abs(s.target(1) - s.xs[1]) >= FieldAnimationState.SNAP_EPSILON);
            s.tick("abc");
            assertTrue(++frames < 100, "must settle");
        }
        assertEquals(s.target(1), s.xs[1]);
        assertTrue(frames > 3, "should glide for a few frames, took " + frames);
    }

    @Test
    void glideZeroOrReflowOffMeansInstant() {
        for (int mode = 0; mode < 2; mode++) {
            Sim s = new Sim();
            if (mode == 0) s.cfg.glideMs = 0;
            else s.cfg.smoothReflow = false;
            s.frame("bc");
            s.tick("bc");
            s.tick("abc");
            for (int i = 0; i < 3; i++) assertEquals(s.target(i), s.xs[i]);
        }
    }

    @Test
    void dtIsClampedTo100ms() {
        Sim s = new Sim();
        s.frame("bc");
        s.settle("bc");
        float x0 = s.xs[0];
        s.tick("abc");
        float x1 = s.xs[1];
        s.now += 10_000;
        s.frame("abc");
        assertEquals(x1 + (s.target(1) - x1) * alpha(100, 55), s.xs[1], 1e-4f);
        assertTrue(x0 < x1);
    }

    @Test
    void timeGoingBackwardsFreezesSmoothing() {
        Sim s = new Sim();
        s.frame("bc");
        s.settle("bc");
        s.tick("abc");
        float x1 = s.xs[1];
        s.now -= 1000;
        s.frame("abc");
        assertEquals(x1, s.xs[1]);
        s.tick("abc");
        assertTrue(s.xs[1] > x1);
    }

    @Test
    void charsNotLaidOutInPreviousFrameSnap() {
        Sim s = new Sim();
        s.frame("bcd");
        s.settle("bcd");
        s.hidden.add(1); // 'c' not drawn this frame
        s.tick("bcd");
        s.hidden.clear();
        s.tick("abcd");
        assertEquals(s.target(2), s.xs[2], "'c' was not laid out last frame -> snaps");
        assertNotEquals(s.target(1), s.xs[1], "'b' glides");
        assertNotEquals(s.target(3), s.xs[3], "'d' glides");
    }

    @Test
    void scrollChangeGlidesButIsNotIdle() {
        Sim s = new Sim();
        s.frame("abcdef");
        s.settle("abcdef");
        assertTrue(s.lastIdle);
        s.scroll = 1;
        s.scrollPx = 6;
        s.tick("abcdef");
        assertFalse(s.lastIdle);
        assertTrue(s.xs[1] > s.target(1), "glides towards the scrolled position");
        s.tick("abcdef");
        assertFalse(s.lastIdle, "still gliding");
        s.settle("abcdef");
        assertTrue(s.lastIdle);
        for (int i = 0; i < 6; i++) assertEquals(s.target(i), s.xs[i]);
    }

    @Test
    void lineChangeSnaps() {
        FieldAnimationState st = new FieldAnimationState();
        TypingConfig cfg = new TypingConfig();
        st.beginFrame(0, 0, 0, 0);
        st.sync("ab", true, cfg);
        st.layoutChar(1, 50f, 0f, 6, 'b', null, WHITE);
        st.endFrame();
        st.beginFrame(16, 0, 0, 0);
        st.sync("ab", true, cfg);
        assertEquals(10f, st.layoutChar(1, 10f, 9f, 6, 'b', null, WHITE), "moved to the next line: snap");
        st.endFrame();
        st.beginFrame(32, 0, 0, 0);
        st.sync("ab", true, cfg);
        float x = st.layoutChar(1, 20f, 9f, 6, 'b', null, WHITE);
        assertTrue(x > 10f && x < 20f, "same line: glide");
    }

    @Test
    void repeatedLayoutInSameFrameIsStable() {
        Sim s = new Sim();
        s.frame("bc");
        s.settle("bc");
        s.beginAndSync("abc");
        float first = s.st.layoutChar(1, s.target(1), s.oy, 6, 'b', null, WHITE);
        float second = s.st.layoutChar(1, s.target(1), s.oy, 6, 'b', null, WHITE);
        assertEquals(first, second);
        s.st.endFrame();
    }

    // ------------------------------------------------------------------ caret

    @Test
    void caretSmoothing() {
        Sim s = new Sim();
        s.frame("abc");
        FieldAnimationState st = s.st;
        assertEquals(22f, st.caretX(22f, s.cfg), "first call snaps");
        s.tick("abc");
        assertEquals(22f, st.caretX(22f, s.cfg));
        s.tick("abcd");
        float c1 = st.caretX(28f, s.cfg);
        assertEquals(22f + 6f * alpha(16, 55), c1, 1e-4f);
        assertEquals(c1, st.caretX(28f, s.cfg), "second call in the same frame does not advance");
        int frames = 0;
        float c = c1;
        while (c != 28f) {
            s.tick("abcd");
            c = st.caretX(28f, s.cfg);
            assertTrue(++frames < 100);
        }
        assertEquals(28f, c);

        // own dt: caret not queried for 3 frames -> dt 48 ms
        s.tick("abcde");
        s.tick("abcde");
        s.tick("abcde");
        float c2 = st.caretX(34f, s.cfg);
        assertEquals(28f + 6f * alpha(48, 55), c2, 1e-4f);
    }

    @Test
    void caretSnapsWhenDisabledOrOnSnapFrames() {
        Sim s = new Sim();
        s.frame("a");
        s.st.caretX(10f, s.cfg);
        s.tick("a");
        s.cfg.smoothCursor = false;
        assertEquals(40f, s.st.caretX(40f, s.cfg));
        s.cfg.smoothCursor = true;
        s.cfg.glideMs = 0;
        s.tick("a");
        assertEquals(50f, s.st.caretX(50f, s.cfg));
        s.cfg.glideMs = 55;
        s.tick("a");
        assertNotEquals(60f, s.st.caretX(60f, s.cfg));
        s.ox = 99f; // origin change
        s.tick("a");
        assertEquals(70f, s.st.caretX(70f, s.cfg));
        s.now += 16;
        s.frame("a", false); // not animated this frame
        assertEquals(80f, s.st.caretX(80f, s.cfg));
        s.tick("a");
        assertEquals(Float.NaN, s.st.caretX(Float.NaN, s.cfg));
        s.tick("a");
        assertEquals(90f, s.st.caretX(90f, s.cfg), "recovers from NaN");
        s.st.reset();
        s.frame("a");
        assertEquals(5f, s.st.caretX(5f, s.cfg), "reset -> first call snaps");
    }

    // ------------------------------------------------------------------ idle

    @Test
    void idleTransitions() {
        Sim s = new Sim();
        s.frame("abc");
        assertFalse(s.lastIdle, "first frame (origin/text change) is not idle");
        s.tick("abc");
        assertTrue(s.lastIdle);

        s.tick("abcd");
        assertFalse(s.lastIdle, "text changed");
        long born = s.now;
        while (s.now - born < 220) {
            s.tick("abcd");
            if (s.now - born < 220) assertFalse(s.lastIdle, "birth active at " + (s.now - born));
        }
        s.tick("abcd");
        assertTrue(s.lastIdle, "birth finished, nothing else moves");

        s.tick("abc");
        assertFalse(s.lastIdle);
        long removed = s.now;
        while (s.now - removed < 160) {
            s.tick("abc");
            if (s.now - removed < 160) assertFalse(s.lastIdle, "ghost alive at " + (s.now - removed));
        }
        s.tick("abc");
        assertTrue(s.lastIdle, "ghost expired");
        assertEquals(0, s.st.ghostCount());

        s.ox = 50;
        s.tick("abc");
        assertFalse(s.lastIdle, "origin changed");
        s.tick("abc");
        assertTrue(s.lastIdle, "snap frame leaves everything settled");

        s.scroll = 3;
        s.tick("abc");
        assertFalse(s.lastIdle, "scroll key changed");
        s.tick("abc");
        assertTrue(s.lastIdle, "scroll key change alone (no x change) settles immediately");

        // caret glide keeps it busy
        s.st.caretX(10f, s.cfg);
        s.tick("abc");
        s.st.caretX(30f, s.cfg);
        s.tick("abc");
        assertFalse(s.lastIdle, "caret moving");
        for (int k = 0; k < 100; k++) {
            s.tick("abc");
            s.st.caretX(30f, s.cfg);
        }
        s.tick("abc");
        assertTrue(s.lastIdle);

        s.cfg.enabled = false;
        s.tick("abcdef");
        assertTrue(s.lastIdle, "disabled is always idle");
    }

    @Test
    void caretThatStopsBeingDrawnDoesNotBlockIdle() {
        Sim s = new Sim();
        s.frame("abc");
        s.st.caretX(0f, s.cfg);
        s.tick("abc");
        s.st.caretX(40f, s.cfg); // starts gliding, then the field loses focus: caret no longer drawn
        s.tick("abc");
        assertFalse(s.lastIdle, "caret was moving last frame");
        s.tick("abc");
        assertTrue(s.lastIdle, "caret not drawn any more");
    }

    @Test
    void idleWithReflowWaitsUntilSettled() {
        Sim s = new Sim();
        s.cfg.appearStyle = AppearStyle.NONE;
        s.frame("bc");
        s.settle("bc");
        s.tick("abc");
        assertFalse(s.lastIdle);
        s.tick("abc");
        assertFalse(s.lastIdle, "chars still gliding");
        s.settle("abc");
        assertTrue(s.lastIdle);
    }

    @Test
    void idleFrameSnapsLayout() {
        Sim s = new Sim();
        s.frame("abc");
        s.settle("abc");
        s.charW = 7f; // widths changed without any text/scroll/origin change (e.g. style change)
        s.tick("abc");
        assertTrue(s.lastIdle);
        for (int i = 0; i < 3; i++) assertEquals(s.target(i), s.xs[i], "vanilla-drawn frame -> snapped");
    }

    @Test
    void staggeredBirthsKeepItBusyUntilTheLastOneFinishes() {
        Sim s = new Sim();
        s.frame("");
        s.tick("0123456789");
        long start = s.now;
        while (s.now - start < 14 * 9 + 220) {
            s.tick("0123456789");
            if (s.now - start < 14 * 9 + 220) assertFalse(s.lastIdle);
        }
        s.tick("0123456789");
        assertTrue(s.lastIdle);
        for (int i = 0; i < 10; i++) assertEquals(NO_BIRTH, s.st.birthAt(i), "finished births are forgotten");
    }

    // ------------------------------------------------------------------ robustness

    @Test
    void nanInputsAreSafe() {
        Sim s = new Sim();
        s.frame("abc");
        s.tick("abc");
        s.st.beginFrame(s.now += 16, s.ox, s.oy, 0);
        s.st.sync("abc", true, s.cfg);
        assertTrue(Float.isNaN(s.st.layoutChar(1, Float.NaN, s.oy, 6, 'b', null, WHITE)));
        s.st.endFrame();
        s.tick("abc");
        assertEquals(s.target(1), s.xs[1], "recovers by snapping");
        s.tick("abc");
        assertTrue(s.lastIdle, "NaN does not leave the state permanently busy");

        Sim n = new Sim();
        n.ox = Float.NaN;
        n.frame("ab");
        n.tick("ab");
        assertTrue(n.lastIdle, "NaN origin compares equal to itself");

        Sim k = new Sim();
        k.cfg.intensity = Float.NaN;
        k.cfg.durationMs = 0;
        k.cfg.removeDurationMs = -5;
        k.cfg.staggerMs = -10;
        k.cfg.maxStaggerMs = -10;
        k.cfg.easing = null;
        k.frame("a");
        k.tick("abcd");
        for (int i = 1; i < 4; i++) {
            assertEquals(k.now, k.st.birthAt(i));
            k.st.appearTransform(i, k.cfg, k.tf);
            assertTrue(Float.isFinite(k.tf.dy) && Float.isFinite(k.tf.alpha));
        }
        k.tick("a");
        for (int g = 0; g < k.st.ghostCount(); g++) {
            k.st.ghostTransform(k.st.ghost(g), k.cfg, k.tf);
            assertTrue(Float.isFinite(k.tf.alpha));
        }
    }

    @Test
    void nullInputsAreSafe() {
        FieldAnimationState st = new FieldAnimationState();
        CharTransform tf = new CharTransform();
        st.beginFrame(0, 0, 0, 0);
        st.sync(null, true, null);
        assertEquals(0, st.length());
        st.endFrame();
        st.beginFrame(16, 0, 0, 0);
        st.sync("ab", true, null);
        assertEquals(16L, st.birthAt(0));
        assertTrue(st.appearTransform(0, null, tf));
        st.caretX(3f, null);
        st.isIdle(null);
        assertFalse(st.ghostTransform(null, null, tf));
        st.endFrame();
    }

    @Test
    void outOfRangeIndicesAreSafe() {
        Sim s = new Sim();
        s.frame("abc");
        s.tick("abcd");
        assertFalse(s.st.appearTransform(-1, s.cfg, s.tf));
        assertTrue(s.tf.isIdentity());
        assertFalse(s.st.appearTransform(4, s.cfg, s.tf));
        assertFalse(s.st.appearTransform(Integer.MAX_VALUE, s.cfg, s.tf));
        assertEquals(12.5f, s.st.layoutChar(99, 12.5f, 0, 6, 'x', null, WHITE));
        assertEquals(-3f, s.st.layoutChar(-1, -3f, 0, 6, 'x', null, WHITE));
    }

    @Test
    void worksWithoutBeginFrameOrEndFrame() {
        FieldAnimationState st = new FieldAnimationState();
        TypingConfig cfg = new TypingConfig();
        st.sync("ab", true, cfg);
        st.layoutChar(0, 1f, 0f, 6f, 'a', null, WHITE);
        st.sync("abc", true, cfg);
        st.layoutChar(0, 1f, 0f, 6f, 'a', null, WHITE);
        st.sync("bc", true, cfg);
        assertEquals(1, st.ghostCount());
        // beginFrame twice without endFrame
        st.beginFrame(10, 0, 0, 0);
        st.beginFrame(20, 0, 0, 0);
        st.endFrame();
        st.endFrame();
    }

    @Test
    void resetStartsOver() {
        Sim s = new Sim();
        s.frame("abc");
        s.settle("abc");
        s.tick("ab");
        s.st.reset();
        assertEquals(0, s.st.ghostCount());
        assertEquals(0, s.st.length());
        s.tick("hello world");
        for (int i = 0; i < 11; i++) {
            assertEquals(NO_BIRTH, s.st.birthAt(i), "first sync after reset");
            assertEquals(s.target(i), s.xs[i]);
        }
        assertFalse(s.lastIdle, "first frame after reset snaps");
    }

    @Test
    void largeTextsGrowAndShrink() {
        Sim s = new Sim();
        s.callIdle = false;
        s.frame("");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) sb.append((char) ('a' + i % 26));
        String big = sb.toString();
        s.tick(big);
        assertEquals(5000, s.st.length());
        String bigger = big.substring(0, 2500) + "XYZ" + big.substring(2500);
        s.tick(bigger);
        assertEquals(s.now, s.st.birthAt(2500));
        assertNotEquals(s.now, s.st.birthAt(2503), "shifted char keeps its old birth");
        assertNotEquals(s.now, s.st.birthAt(2499));
        s.tick("tiny");
        assertEquals(4, s.st.length());
        s.tick(big);
        assertEquals(5000, s.st.length());
    }
}
