package dev.typinganimation.mc;

import net.minecraft.util.Util;

import java.util.function.LongSupplier;

/**
 * Millisecond clock of the animations (the renderer's frame time and the config screen's demo typing). It is the
 * game's clock ({@code Util.getMillis()}) unless a dev tool installs an override: the showcase recorder does, so that
 * every captured frame advances the animations by exactly one frame interval.
 */
public final class AnimationClock {
    private static volatile LongSupplier override;

    private AnimationClock() {
    }

    /** Current animation time in milliseconds. */
    public static long nowMs() {
        LongSupplier o = override;
        return o != null ? o.getAsLong() : Util.getMillis();
    }

    /** Dev tools only: replaces the clock ({@code null} restores the game's clock). Must be monotonic. */
    public static void setOverride(LongSupplier source) {
        override = source;
    }
}
