package dev.typinganimation.mc;

import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.FieldKind;
import dev.typinganimation.core.SoundPreset;
import dev.typinganimation.core.TypingConfig;
import dev.typinganimation.core.TypingSoundAction;
import dev.typinganimation.core.TypingSoundFeedback;
import dev.typinganimation.core.TypingAnimationMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Random;

/** Local, non-positional UI audio. Called only after accepted input, never from rendering or text setters. */
public final class TypingSounds {
    private static final SoundEvent[][] EVENTS = new SoundEvent[SoundPreset.values().length][TypingSoundAction.values().length];
    private static final TypingSoundFeedback LIMIT = new TypingSoundFeedback();
    private static final Random RANDOM = new Random();
    private static final Deque<SoundInstance> VOICES = new ArrayDeque<>();
    private static final TypingSoundAction[] PREVIEW = { TypingSoundAction.KEY, TypingSoundAction.KEY,
            TypingSoundAction.SPACE, TypingSoundAction.KEY, TypingSoundAction.BACKSPACE,
            TypingSoundAction.KEY, TypingSoundAction.ENTER };
    private static final int[] PREVIEW_TIMES = {0, 80, 170, 260, 370, 470, 600};
    private static Object previewOwner;
    private static SoundPreset previewPreset;
    private static int previewIndex;
    private static long previewStart;
    public static Throwable firstError;
    static long playedCount;
    static TypingSoundAction lastAction;
    static SoundPreset lastPreset;
    static float lastVolume, lastPitch;
    private static long inputSerial;

    static {
        for (SoundPreset preset : SoundPreset.values()) {
            for (TypingSoundAction action : TypingSoundAction.values()) {
                String path = "typing." + preset.name().toLowerCase(Locale.ROOT) + "." + action.path();
                EVENTS[preset.ordinal()][action.ordinal()] = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("typinganimation", path));
            }
        }
    }

    private TypingSounds() {}
    private static long now() { return System.nanoTime() / 1_000_000L; }

    public static void widgetEdited(Object widget, String before, String after) {
        if (!(widget instanceof TypingStateHolder holder)) return;
        if (before != null && after != null && !before.equals(after)) inputSerial++;
        edited(before, after, holder.typinganimation$getFieldKind(), holder.typinganimation$animation().forceAnimate);
    }

    public static long inputSerial() { return inputSerial; }

    public static void edited(String before, String after, FieldKind kind, boolean previewField) {
        action(TypingSoundFeedback.classify(before, after), kind, previewField);
    }

    public static void action(TypingSoundAction action, FieldKind kind, boolean previewField) {
        try {
            TypingConfig cfg = ConfigManager.get();
            if (!TypingSoundFeedback.shouldPlay(cfg, kind, previewField, action) || firstError != null) return;
            previewOwner = null;
            if (LIMIT.tryAcquire(now())) play(cfg.soundPreset, action, cfg);
        } catch (Throwable error) { fail(error); }
    }

    /** A short typing rhythm; explicit audition works even when the sound/animation master is off. */
    public static void preview(SoundPreset preset) {
        try {
            TypingConfig cfg = ConfigManager.get();
            if (firstError != null || cfg.soundVolume <= 0) return;
            Minecraft mc = Minecraft.getInstance();
            stopVoices();
            previewOwner = mc.screen;
            previewPreset = preset == null ? cfg.soundPreset : preset;
            previewIndex = 0;
            previewStart = now();
            play(previewPreset, PREVIEW[previewIndex++], cfg);
        } catch (Throwable error) { fail(error); }
    }

    /** Client-tick scheduling means closing the screen or choosing another sample cancels the old rhythm. */
    public static void tick() {
        if (previewOwner == null || firstError != null) { previewOwner = null; return; }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != previewOwner || ConfigManager.get().soundVolume <= 0) {
                previewOwner = null;
                return;
            }
            if (previewIndex < PREVIEW.length && now() - previewStart >= PREVIEW_TIMES[previewIndex]) {
                play(previewPreset, PREVIEW[previewIndex++], ConfigManager.get());
            }
            if (previewIndex >= PREVIEW.length) previewOwner = null;
        } catch (Throwable error) { previewOwner = null; fail(error); }
    }

    private static void play(SoundPreset preset, TypingSoundAction action, TypingConfig cfg) {
        if (preset == null || cfg.soundVolume <= 0) return;
        float volume = Math.max(0, Math.min(1, cfg.soundVolume));
        float pitch = Math.max(.6f, Math.min(1.6f, cfg.soundPitch));
        if (cfg.soundVariation) pitch = Math.max(.6f, Math.min(1.6f, pitch * (.96f + RANDOM.nextFloat() * .08f)));
        SimpleSoundInstance sound = SimpleSoundInstance.forUI(EVENTS[preset.ordinal()][action.ordinal()], pitch, volume);
        var manager = Minecraft.getInstance().getSoundManager();
        while (VOICES.size() >= 8) manager.stop(VOICES.removeFirst());
        VOICES.addLast(sound);
        manager.play(sound);
        playedCount++;
        lastAction = action;
        lastPreset = preset;
        lastVolume = volume;
        lastPitch = pitch;
    }

    /** Diagnostic helper: the policy and resources are also checked by the in-game test. */
    static SoundEvent event(SoundPreset preset, TypingSoundAction action) { return EVENTS[preset.ordinal()][action.ordinal()]; }
    static void resetTestCounters() { LIMIT.reset(); playedCount = 0; lastAction = null; previewOwner = null; }

    private static void stopVoices() {
        var manager = Minecraft.getInstance().getSoundManager();
        while (!VOICES.isEmpty()) manager.stop(VOICES.removeFirst());
    }

    private static void fail(Throwable error) {
        previewOwner = null;
        if (firstError == null) {
            firstError = error;
            TypingAnimationMod.LOGGER.warn("[typinganimation] Typing audio disabled after playback failure", error);
        }
    }
}
