package dev.typinganimation.mc;

import dev.typinganimation.core.FieldAnimationState;
import dev.typinganimation.core.FieldKind;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;

/**
 * Animation data of one text widget (held by the widget through {@link TypingStateHolder}), plus the
 * bookkeeping of the frame currently being drawn. Render thread only.
 */
public final class WidgetAnimation {
    /** Text animation state (core). */
    public final FieldAnimationState state = new FieldAnimationState();
    /** Which config toggle applies. */
    public FieldKind kind;
    /** Animate regardless of focus / per-kind toggles (config screen preview). */
    public boolean forceAnimate;

    // ------------------------------------------------------------------ frame bookkeeping (TypingRenderer)
    /** True between a successful begin and the end of the widget's draw call: our path is active. */
    boolean frameActive;
    /** This frame's text is drawn the vanilla way ({@code state.isIdle(cfg)} and no scroll glide). */
    boolean idle;
    /** This frame's "animate" flag passed to {@code sync}. */
    boolean animate;
    /** The state has been used since the last reset (so a later disable resets it). */
    boolean dirty;
    /** An exception happened in our path: this widget is drawn the vanilla way from now on. */
    boolean failed;
    /** Time of the current frame. */
    long nowMs;
    /** The graphics object of the draw call in progress (only while {@link #frameActive}). */
    GuiGraphics graphics;
    /** Value passed to the last {@code sync} (wholesale-replacement detection of multi-line fields). */
    String lastValue;

    /** Clip rectangle for animated text, ghosts and the moved caret (GUI coordinates); false: no extra scissor. */
    boolean clip;
    int clipX0, clipY0, clipX1, clipY1;
    /** EditBox text area (x range vanilla draws text in): clip of the text while a scroll glides. */
    int textClipX0, textClipX1;

    /** Segment texts/start indices captured just before the widget builds each segment's formatted sequence. */
    final String[] segmentText = new String[2];
    final int[] segmentStart = new int[2];

    // ------------------------------------------------------------------ EditBox: horizontal scroll glide
    /**
     * X offset (GUI px) added to everything drawn this frame: the difference between the scroll position vanilla
     * uses and the smoothed one. Non-zero only while a scroll glides; the text is then never drawn by vanilla.
     */
    float shift;
    boolean scrollValid;
    float scrollSmooth;
    long scrollLastMs;
    /** Render frame (TypingRenderer frame counter) of the last scroll update. */
    long scrollFrame;
    int scrollTextX, scrollTextY;
    /** The formatter output did not match the text once: no scroll glide for this widget (vanilla fallbacks). */
    boolean scrollGlideOff;
    /** Cached float width of {@code value[0, displayPos)} (plain style, as vanilla scrolls). */
    private String prefixValue;
    private int prefixPos = -1;
    private float prefixWidth;

    /** This frame's EditBox data (for the chars revealed around the vanilla window during a scroll glide). */
    String value;
    int displayPos;
    int textX, textY, textColor, innerWidth;
    /** Px scrolled this frame (float width of the value before displayPos). */
    float scrollPx;
    /** Last caret target in content coordinates (caret x + scrolled px). */
    float caretTarget;
    boolean caretTargetValid;
    /** End (exclusive index, float x) of the last segment laid out this frame = end of vanilla's window. */
    int windowEnd;
    float windowEndX;

    // ------------------------------------------------------------------ caret drawn by us (moved caret)
    public static final int CARET_NONE = 0, CARET_INSERT = 1, CARET_APPEND = 2;
    int caretMode;
    int caretArgX, caretArgY, caretArgX1, caretArgY1, caretArgColor;
    boolean caretArgShadow;
    Font caretArgFont;
    float caretDx;

    /** Multi-line widgets: separate state used only to smooth the caret (its origin is the caret line). */
    FieldAnimationState caretState;

    // ------------------------------------------------------------------ read-only info (self-test / debugging)
    /** Number of frames our path was active for this widget. */
    long activeFrames;
    /** {@link #idle} of the most recent active frame. */
    boolean lastIdle;
    /** Draw trace of this frame (self-test), or null. */
    TypingRenderer.Trace trace;
    /** Trace to record in the next frame our path draws (self-test), or null. */
    TypingRenderer.Trace armedTrace;

    public WidgetAnimation(FieldKind kind) {
        this.kind = kind == null ? FieldKind.OTHER : kind;
    }

    public long activeFrames() {
        return activeFrames;
    }

    public boolean lastFrameIdle() {
        return lastIdle;
    }

    public boolean hasFailed() {
        return failed;
    }

    FieldAnimationState caretState() {
        FieldAnimationState cs = caretState;
        if (cs == null) {
            cs = new FieldAnimationState();
            caretState = cs;
        }
        return cs;
    }

    void setClip(int x0, int y0, int x1, int y1) {
        clip = true;
        clipX0 = x0;
        clipY0 = y0;
        clipX1 = x1;
        clipY1 = y1;
    }

    /** Forgets all animation state (mod or field kind switched off). */
    void resetAll() {
        state.reset();
        if (caretState != null) {
            caretState.reset();
        }
        scrollValid = false;
        caretTargetValid = false;
        lastValue = null;
        dirty = false;
    }

    /**
     * Float width of {@code value[0, pos)} with plain style (vanilla scrolls by plain widths), recomputed only when
     * the value or the position changed.
     */
    float prefixWidth(StringSplitter.WidthProvider widths, String value, int pos) {
        if (value == prefixValue && pos == prefixPos) {
            return prefixWidth;
        }
        int end = Math.min(Math.max(pos, 0), value.length());
        float w = 0f;
        for (int i = 0; i < end; ) {
            int cp = value.codePointAt(i);
            w += widths.getWidth(cp, Style.EMPTY);
            i += Character.charCount(cp);
        }
        prefixValue = value;
        prefixPos = pos;
        prefixWidth = w;
        return w;
    }
}
