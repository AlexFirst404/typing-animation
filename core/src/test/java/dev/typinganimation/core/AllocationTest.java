package dev.typinganimation.core;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Verifies the "no per-frame allocation in steady state" rule using HotSpot's per-thread allocation counter. */
class AllocationTest {

    private static final int WHITE = 0xFFFFFFFF;

    private final FieldAnimationState st = new FieldAnimationState();
    private final TypingConfig cfg = TypingConfig.defaults();
    private final CharTransform tf = new CharTransform();
    private long now = 1000;
    private float sink;

    private void frame(String v, float caretTarget) {
        now += 16;
        st.beginFrame(now, 2f, 3f, 0);
        st.sync(v, true, cfg);
        boolean idle = st.isIdle(cfg);
        float x = 2f;
        for (int i = 0; i < v.length(); ) {
            int cp = v.codePointAt(i);
            float drawn = st.layoutChar(i, x, 3f, 6f, cp, null, WHITE);
            if (!idle && st.appearTransform(i, cfg, tf)) {
                sink += tf.alpha + tf.dy;
            }
            sink += drawn;
            x += 6f;
            i += Character.charCount(cp);
        }
        for (int g = 0; g < st.ghostCount(); g++) {
            if (st.ghostTransform(st.ghost(g), cfg, tf)) {
                sink += tf.alpha;
            }
        }
        sink += st.caretX(caretTarget, cfg);
        st.endFrame();
    }

    @Test
    void steadyStateAndTypingDoNotAllocate() {
        java.lang.management.ThreadMXBean base = ManagementFactory.getThreadMXBean();
        assumeTrue(base instanceof com.sun.management.ThreadMXBean, "HotSpot allocation counter unavailable");
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) base;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        long tid = Thread.currentThread().getId();

        // pre-built strings: typing "The quick brown fox..." one char at a time (with an emoji), nothing deleted
        String full = "The quick brown fox 😀 jumps over the lazy dog, twice as fast as before!";
        String[] typing = new String[full.length() + 1];
        for (int i = 0; i <= full.length(); i++) typing[i] = full.substring(0, i);

        // warm-up: grow every array, exercise every path (including deletions and ghosts), JIT a bit
        for (int round = 0; round < 30; round++) {
            for (String s : typing) frame(s, s.length() * 6f);
            for (int i = typing.length - 1; i >= 0; i -= 3) frame(typing[i], i * 6f);
            for (int k = 0; k < 20; k++) frame("", 0f);
        }
        st.reset();
        for (int k = 0; k < 5; k++) frame("", 0f);
        bean.getThreadAllocatedBytes(tid);

        long before = bean.getThreadAllocatedBytes(tid);
        for (int round = 0; round < 3; round++) {
            st.reset(); // keeps the arrays; the next sync is a first sync, so nothing is deleted
            frame("", 0f);
            for (String s : typing) {
                if (s.isEmpty() || Character.isHighSurrogate(s.charAt(s.length() - 1))) continue;
                frame(s, s.length() * 6f);
            }
            for (int k = 0; k < 400; k++) frame(full, full.length() * 6f); // idle + gliding frames
        }
        long typingAndSteady = bean.getThreadAllocatedBytes(tid) - before;

        before = bean.getThreadAllocatedBytes(tid);
        for (int k = 0; k < 5000; k++) frame(full, (k % 7) * 6f); // caret wandering, text constant
        long steady = bean.getThreadAllocatedBytes(tid) - before;

        assertTrue(typingAndSteady < 2048, "typing frames allocated " + typingAndSteady + " bytes");
        assertTrue(steady < 1024, "steady frames allocated " + steady + " bytes");
    }
}
