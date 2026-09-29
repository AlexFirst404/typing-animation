package dev.typinganimation.mc;

import dev.typinganimation.core.FieldAnimationState;
import dev.typinganimation.core.FieldKind;
import dev.typinganimation.core.TypingConfig;
import net.minecraft.client.gui.Font;

/**
 * Per-widget render bookkeeping: the core {@link FieldAnimationState} plus what the hooks of one frame need to share
 * between the HEAD inject, the redirected draw calls and the RETURN inject. Render thread only.
 */
public final class WidgetAnimator {
    /**
     * A frame that comes this long after the widget's previous one (the game stalled) snaps to the current value
     * instead of animating what changed in the meantime, like a widget that skipped a frame (see {@link #touch}).
     */
    static final long RENDER_GAP_MS = 500L;
    /** Client frame counter (incremented at the start of every {@code Minecraft#runTick}); render thread only. */
    private static long frame;

    /** Called once per client frame, before anything is rendered. */
    public static void nextFrame() {
        frame++;
    }

    public final FieldAnimationState state = new FieldAnimationState();
    public FieldKind kind;
    public boolean preview;

    /** Set after an exception: this widget is drawn the vanilla way from then on. */
    boolean failed;
    /** True while {@link #state} is freshly reset (nothing to forget when the mod gets disabled). */
    private boolean clean = true;
    /** Time and client frame of the last frame that reached the core (lastFrame -1 = never). */
    private long lastFrameMs;
    private long lastFrame = -1L;
    /** The widget was invisible (not drawn) since the last frame that reached the core. */
    private boolean hiddenGap;

    // ------------------------------------------------------------------ per frame
    /** The hooks of this frame are live (mod enabled, widget visible, no failure). */
    boolean active;
    /** {@link FieldAnimationState#isIdle} said nothing animates: draw the text the vanilla way. */
    boolean idle;
    Font font;
    boolean shadow = true;
    float originX, originY;
    /**
     * Clip rectangle (GUI coordinates) for glyphs drawn with an appear transform and for ghosts; {@code clip=false}
     * means no extra scissor (e.g. multi-line fields, which are already drawn inside their scroll area's scissor).
     */
    boolean clip;
    int clipX0, clipY0, clipX1, clipY1;

    // EditBox
    /** Value and scroll position of the previous frame (big scroll jumps without an edit snap instead of gliding). */
    String lastValue;
    int lastDisplayPos = -1;
    int segment;
    int displayPos, cursorPos;
    /** False until this frame's visible substring is known (captured from renderWidget, else computed lazily). */
    boolean visibleKnown;
    int visibleLen, cursorRel;
    boolean cursorInView;
    /** A selection is shown: the caret is drawn at its target so it stays on the selection highlight's edge. */
    boolean selection;
    boolean seg1Drawn;
    int seg1End;
    boolean caretQueried;

    // MultiLineEditBox
    boolean subValid;
    int subBegin, subEnd;
    /** Absolute index of the caret whose x the per-char loop should record (-1 = none). */
    int caretIndex = -1;
    boolean hasCaretCandidate;
    int caretCandidateX, caretCandidateY;
    /** Line (y) the caret was last drawn on; a change of line snaps the caret glide. */
    int lastCaretY = Integer.MIN_VALUE;

    public WidgetAnimator(FieldKind kind) {
        this.kind = kind == null ? FieldKind.OTHER : kind;
    }

    /** Whether edits of this widget animate this frame. */
    boolean animate(TypingConfig cfg, boolean focused) {
        if (preview) {
            return cfg.enabled;
        }
        return FieldKind.isAnimated(kind, cfg) && (!cfg.onlyWhenFocused || focused);
    }

    /**
     * Called when a frame starts: the state is about to hold data again. Returns false when this frame follows a
     * rendering gap: the widget was not drawn in the previous client frame (hidden: {@code AbstractWidget#render}
     * skips invisible widgets; its screen was replaced for a while; the creative search box across a tab switch) or
     * the game stalled for {@link #RENDER_GAP_MS}. Edits made during a gap must not animate: the diff would be against
     * text that has not been on screen for a while.
     */
    boolean touch(long nowMs) {
        clean = false;
        final long f = frame;
        boolean continuous = !hiddenGap && lastFrame >= 0L && f - lastFrame <= 1L && nowMs - lastFrameMs <= RENDER_GAP_MS;
        hiddenGap = false;
        lastFrameMs = nowMs;
        lastFrame = f;
        return continuous;
    }

    /** The widget is not drawn this frame (invisible). */
    void hidden() {
        hiddenGap = true;
    }

    /** The mod is disabled: forget everything once, so re-enabling starts from a clean first sync. */
    void disable() {
        active = false;
        if (!clean) {
            clean = true;
            lastCaretY = Integer.MIN_VALUE;
            lastValue = null;
            lastDisplayPos = -1;
            lastFrameMs = 0L;
            lastFrame = -1L;
            hiddenGap = false;
            state.reset();
        }
    }

    void setClip(int x0, int y0, int x1, int y1) {
        clip = x1 > x0 && y1 > y0;
        clipX0 = x0;
        clipY0 = y0;
        clipX1 = x1;
        clipY1 = y1;
    }

    /** Records the caret x (the per-char loop reached the caret index) for frames that do not draw the caret. */
    void noteCaret(int index, float penX, int y) {
        if (index == caretIndex && !hasCaretCandidate) {
            hasCaretCandidate = true;
            caretCandidateX = (int) penX;
            caretCandidateY = y;
        }
    }
}
