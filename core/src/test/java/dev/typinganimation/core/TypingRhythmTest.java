package dev.typinganimation.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypingRhythmTest {
    @Test
    void slowTypingKeepsFullAnimationAndFastTypingBuildsGradually() {
        TypingRhythm slow = new TypingRhythm();
        for (long time : new long[]{1000, 1300, 1600, 1900}) slow.record(time);
        assertEquals(0f, slow.strength(1900));
        assertEquals(1f, slow.animationScale(1900));
        assertEquals(1f, slow.volumeScale(1900));
        TypingRhythm fast = new TypingRhythm();
        fast.record(1000);
        fast.record(1040);
        assertEquals(0f, fast.strength(1040), "one interval is not sustained fast typing");
        fast.record(1080);
        assertEquals(1f, fast.strength(1080));
        assertEquals(.45f, fast.animationScale(1080), .00001f);
        assertEquals(.55f, fast.volumeScale(1080), .00001f);
        assertEquals(60, fast.soundIntervalMs(1080));
    }

    @Test
    void pausesSameTimestampAndClockRestartsDoNotInventCadence() {
        TypingRhythm rhythm = new TypingRhythm();
        for (int i = 0; i < 100; i++) rhythm.record(1000);
        assertEquals(0, rhythm.strength(1000));
        rhythm.record(1040);
        rhythm.record(1080);
        assertTrue(rhythm.strength(1080) > 0);
        assertEquals(0f, rhythm.strength(1781));
        rhythm.record(1781);
        assertEquals(1f, rhythm.animationScale(1781));
        rhythm.record(10);
        assertEquals(0f, rhythm.strength(10));
        rhythm.record(50);
        rhythm.record(90);
        assertTrue(rhythm.strength(90) > 0);
        rhythm.reset();
        assertEquals(0f, rhythm.strength(90));
    }

    @Test
    void adaptiveAudioSoftensRapidRepeatsAndRecoversAfterPauseOrDisable() {
        TypingSoundFeedback feedback = new TypingSoundFeedback();
        assertTrue(feedback.tryAcquire(1000, true));
        assertTrue(feedback.tryAcquire(1040, true));
        assertFalse(feedback.tryAcquire(1080, true));
        assertEquals(.55f, feedback.volumeScale(1080, true), .00001f);
        assertTrue(feedback.tryAcquire(1120, true));
        assertTrue(feedback.tryAcquire(1900, true));
        assertEquals(1f, feedback.volumeScale(1900, true));
        assertTrue(feedback.tryAcquire(1940, false));
        assertEquals(1f, feedback.volumeScale(1940, false));
        feedback.reset();
        assertTrue(feedback.tryAcquire(1, true));
        assertEquals(1f, feedback.volumeScale(1, true));
    }
}
