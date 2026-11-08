package dev.typinganimation.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveAnimationTest {
    private static FieldAnimationStateTest.Sim simulation(boolean adaptive) {
        var sim = new FieldAnimationStateTest.Sim();
        sim.cfg.adaptiveTyping = adaptive;
        sim.cfg.appearStyle = AppearStyle.FADE;
        sim.cfg.removeStyle = RemoveStyle.FADE;
        sim.cfg.easing = Easing.LINEAR;
        sim.cfg.smoothReflow = false;
        sim.frame("");
        return sim;
    }

    private static void typeFast(FieldAnimationStateTest.Sim sim) {
        sim.now += 40; sim.frame("a");
        sim.now += 40; sim.frame("ab");
        sim.now += 40; sim.frame("abc");
    }

    @Test
    void rapidSingleEditsShortenAnimationWhileSlowAndBulkEditsStayFullLength() {
        var fast = simulation(true);
        typeFast(fast);
        fast.now += 110; fast.frame("abc");
        assertFalse(fast.appear(2), "220 ms animation completes in99 ms at sustained fast cadence");
        var unmodified = simulation(false);
        typeFast(unmodified);
        unmodified.now += 110; unmodified.frame("abc");
        assertTrue(unmodified.appear(2));
        var slow = simulation(true);
        slow.now += 300; slow.frame("a");
        slow.now += 300; slow.frame("ab");
        slow.now += 300; slow.frame("abc");
        slow.now += 110; slow.frame("abc");
        assertTrue(slow.appear(2));
        assertEquals(.5f, slow.tf.alpha, .00001f);
        var bulk = simulation(true);
        typeFast(bulk);
        bulk.now += 40; bulk.frame("abcHello world");
        bulk.now += 110; bulk.frame("abcHello world");
        assertTrue(bulk.appear(3));
        assertEquals(.5f, bulk.tf.alpha, .00001f);
    }

    @Test
    void cadenceChangesCannotRewindExistingCharactersAndPausesRestoreFullDuration() {
        var sim = simulation(true);
        typeFast(sim);
        sim.now += 50; sim.frame("abc");
        assertTrue(sim.appear(2));
        float alpha = sim.tf.alpha;
        sim.frame("PASTEabc"); // Same clock: cadence changes and the existing suffix shifts.
        assertTrue(sim.appear(7));
        assertEquals(alpha, sim.tf.alpha, .00001f);
        sim.now += 800; sim.frame("PASTEabc!");
        sim.now += 110; sim.frame("PASTEabc!");
        assertTrue(sim.appear(8));
        assertEquals(.5f, sim.tf.alpha, .00001f);
    }

    @Test
    void olderFullDurationCharacterRemainsActiveAfterNewerFastCharacterSettles() {
        var sim = simulation(true);
        sim.cfg.durationMs = 1000;
        typeFast(sim);
        sim.now += 480; sim.frame("abc");
        assertFalse(sim.lastIdle);
        assertFalse(sim.appear(2));
        assertTrue(sim.appear(0));
        assertEquals(.56f, sim.tf.alpha, .00001f);
    }

    @Test
    void ghostsKeepIndividualCadenceAcrossBulkEditsAndOriginMoves() {
        var sim = simulation(true);
        sim.cfg.durationMs = 1000;
        sim.cfg.removeDurationMs = 1000;
        sim.frame("abcdefghij", false); // Initial contents are settled and never establish cadence.
        sim.now += 40; sim.frame("abcdefghi");
        sim.now += 40; sim.frame("abcdefgh");
        sim.now += 40; sim.frame("abcdefg");
        assertEquals(3, sim.st.ghostCount());
        sim.now += 50; sim.frame("abcdefg");
        assertTrue(sim.st.ghostTransform(sim.st.ghost(2), sim.cfg, sim.tf));
        float alpha = sim.tf.alpha;
        sim.frame("abcdefgPASTE");
        assertTrue(sim.st.ghostTransform(sim.st.ghost(2), sim.cfg, sim.tf));
        assertEquals(alpha, sim.tf.alpha, .00001f);
        sim.ox += 3;
        sim.now += 10; sim.frame("abcdefgPASTE");
        assertTrue(sim.st.ghostTransform(sim.st.ghost(2), sim.cfg, sim.tf));
        assertEquals(1f - 60f / 450f, sim.tf.alpha, .00001f);
        sim.now += 420; sim.frame("abcdefgPASTE");
        assertEquals(2, sim.st.ghostCount(), "fast ghost expires without dropping the earlier slow ones");
    }
}
