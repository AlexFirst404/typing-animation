package dev.typinganimation.mc;

import dev.typinganimation.core.CharTransform;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.FieldAnimationState;
import dev.typinganimation.core.FieldKind;
import dev.typinganimation.core.TypingConfig;
import dev.typinganimation.mixin.StringSplitterAccessor;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.TextCursorUtils;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static dev.typinganimation.core.TypingAnimationMod.LOGGER;

/**
 * Per-character renderer for text widgets (Minecraft 26.1 - 26.1.2 group), driven by the widget mixins.
 *
 * <p>Frame protocol per widget (renderer contract, docs/SPEC.md section 5):
 * {@code begin*} (beginFrame, sync, isIdle) -&gt; one {@code *Segment} call per vanilla text draw call (layoutChar
 * for every visible char, per-char drawing when not idle) -&gt; caret through {@code caretX} -&gt; {@code end*}
 * (chars revealed by a scroll glide, ghosts, endFrame). Any exception disables our path for that widget (vanilla
 * drawing from then on).
 *
 * <p>At rest the vanilla draw call itself is kept, so settled text is pixel-identical to vanilla. While animating,
 * the vanilla call receives an empty text and every char is drawn here; runs of untransformed chars that share the
 * same glide offset are merged into one submit each.
 *
 * <p><b>Horizontal scrolling (EditBox).</b> The scroll position is part of the origin passed to the core
 * ({@code textX - width(value[0, displayPos))}), so a scroll snaps the per-char glides and moves the ghosts with the
 * text. The scroll itself glides as one value: everything (text, ghosts, caret, selection, suggestion) is drawn
 * with the offset {@link WidgetAnimation#shift} between vanilla's scroll position and the smoothed one, and the
 * chars that the smoothed view shows outside vanilla's window are laid out and drawn too (clipped to the text
 * area). The whole line therefore slides as one piece; no char snaps or vanishes at the edges.
 *
 * <p>Everything that touches the Minecraft drawing API is in the "draw primitives" section at the end of this
 * class; porting to another Minecraft version mostly means rewriting those few methods.
 */
public final class TypingRenderer {
    /** Multi-line text fields (MultiLineEditBox) are supported in this API group. */
    public static final boolean MULTILINE_SUPPORTED = true;

    /** First exception caught in our render path (self-test asserts it stays null). */
    public static volatile Throwable firstError;
    /** Self-test only: settled frames also go through the per-char path (fidelity check). */
    public static volatile boolean debugForcePerChar;
    /** Self-test only: never merge chars into runs (every glyph is its own submit). */
    public static volatile boolean debugNoMerge;

    // statistics (read by the self-test)
    public static long statGlyphs, statRuns, statGhosts, statCaretOffsets, statFallbacks, statIdleSegments,
            statScrollGlideFrames, statRevealed;
    /** Selection highlight / suggestion x values moved by a scroll glide. */
    public static long statShiftedHighlights, statShiftedSuggestions;

    private static final float MIN_ALPHA = 0.02f;
    private static final float MIN_SCALE = 0.01f;
    /** Height of the glyph cell CharTransform#pivotY refers to (Minecraft line height). */
    private static final float CELL_HEIGHT = 9f;
    /** Chars whose glide offsets differ by less than this share one run. */
    private static final float RUN_EPSILON = 1e-3f;
    /** A scroll glide ends when the offset is below this (same threshold as the core's glides). */
    private static final float SCROLL_EPSILON = 0.01f;
    /** Upper bound of chars revealed on one side of vanilla's window during a scroll glide. */
    private static final int MAX_REVEALED = 512;
    /**
     * Chars revealed right of vanilla's window fade out over the last pixels of a glide: at rest vanilla hides a
     * char that does not fit completely, so it must not pop away when the glide ends.
     */
    private static final float REVEAL_FADE_PX = 6f;
    /** Multi-line fields: replacing at least this many chars by at least this many others is not animated. */
    private static final int REPLACE_MIN = 32;
    /** First code unit that may need bidirectional reordering (Hebrew block); everything below is left-to-right. */
    private static final char FIRST_RTL_CHAR = (char) 0x0590;
    /** Formatting code prefix (section sign). */
    private static final char FORMAT_CODE_CHAR = (char) 0x00a7;

    private static final int FALLBACK = 0;
    private static final int VANILLA = 1;
    private static final int DRAWN = 2;

    private static final Set<Class<?>> LOGGED = ConcurrentHashMap.newKeySet();
    private static final CharTransform TF = new CharTransform();
    private static final LengthCheck CHECK = new LengthCheck();
    private static final WidthSum WIDTH_SUM = new WidthSum();
    private static final SegmentSink SINK = new SegmentSink();

    /** Number of rendered frames so far (advanced at the end of every frame by MinecraftMixin). */
    private static long renderFrame;

    private TypingRenderer() {
    }

    /** End of a rendered frame (client frame loop): lets widgets tell whether they were drawn in the last frame. */
    public static void onFrameEnd() {
        renderFrame++;
    }

    // =================================================================== frame begin

    /**
     * Begins a frame of an {@link EditBox}: beginFrame (origin includes the scroll position) + sync + isIdle, the
     * clip rectangles and the scroll glide offset. {@code color} is the colour vanilla draws the text with.
     */
    public static void beginEditBox(WidgetAnimation anim, EditBox box, GuiGraphicsExtractor graphics, Font font,
                                    int textX, int textY, int displayPos, String value, int color) {
        anim.frameActive = false;
        anim.graphics = null;
        anim.shift = 0f;
        try {
            TypingConfig cfg = ConfigManager.get();
            if (!active(anim, cfg)) {
                return;
            }
            float scrollPx = anim.prefixWidth(widthProvider(font), value, displayPos);
            if (!beginFrame(anim, cfg, graphics, textX - scrollPx, textY, displayPos, value, box.isFocused())) {
                return;
            }
            int innerWidth = box.getInnerWidth();
            int x0 = box.getX();
            int y0 = box.getY();
            int x1 = x0 + box.getWidth();
            int y1 = y0 + box.getHeight();
            if (box.isBordered()) {
                x0++;
                y0++;
                x1--;
                y1--;
            }
            // never clip the text line itself (e.g. borderless fields lower than a line of text)
            x0 = Math.min(x0, textX - 1);
            y0 = Math.min(y0, textY - 1);
            x1 = Math.max(x1, textX + innerWidth + 1);
            y1 = Math.max(y1, textY + 10);
            anim.setClip(x0, y0, x1, y1);
            anim.textClipX0 = Math.max(x0, textX);
            anim.textClipX1 = Math.min(x1, textX + innerWidth);

            anim.value = value;
            anim.displayPos = displayPos;
            anim.textX = textX;
            anim.textY = textY;
            anim.textColor = color;
            anim.innerWidth = innerWidth;
            anim.scrollPx = scrollPx;
            anim.windowEnd = displayPos;
            anim.windowEndX = textX;

            float shift = scrollShift(anim, cfg, scrollPx, textX, textY, innerWidth);
            anim.shift = shift;
            if (shift != 0f) {
                anim.idle = false; // the text is displaced: vanilla must not draw it
                anim.lastIdle = false;
                statScrollGlideFrames++;
            }
            Trace t = anim.armedTrace;
            if (t != null) {
                anim.armedTrace = null;
                t.textX = textX;
                t.innerWidth = innerWidth;
                t.textClipX0 = anim.textClipX0;
                t.textClipX1 = anim.textClipX1;
                t.valueLength = value.length();
                anim.trace = t;
            }
        } catch (Throwable t) {
            fail(anim, box, t);
        }
    }

    /**
     * Begins a frame of a multi-line field. Coordinates are content coordinates (the widget itself applies the
     * scroll translation and the scissor around its contents, so no extra clip is needed).
     */
    public static void beginMultiline(WidgetAnimation anim, Object widget, GuiGraphicsExtractor graphics, int innerLeft,
                                      int innerTop, double scroll, String value, boolean focused) {
        anim.frameActive = false;
        anim.graphics = null;
        anim.shift = 0f;
        try {
            TypingConfig cfg = ConfigManager.get();
            if (active(anim, cfg)) {
                beginFrame(anim, cfg, graphics, innerLeft, innerTop, (int) Math.round(scroll), value, focused);
            }
        } catch (Throwable t) {
            fail(anim, widget, t);
        }
    }

    /**
     * False (and the state forgotten) when this widget is not animated at all: mod disabled, field kind switched
     * off, or an earlier error. Such frames cost nothing; re-enabling starts cleanly (the first sync never animates).
     */
    private static boolean active(WidgetAnimation anim, TypingConfig cfg) {
        if (anim.failed) {
            return false;
        }
        if (!cfg.enabled || (!anim.forceAnimate && !FieldKind.isAnimated(anim.kind, cfg))) {
            if (anim.dirty) {
                anim.resetAll();
            }
            return false;
        }
        return true;
    }

    private static boolean beginFrame(WidgetAnimation anim, TypingConfig cfg, GuiGraphicsExtractor graphics,
                                      float originX, float originY, int scrollKey, String value, boolean focused) {
        boolean animate = anim.forceAnimate || !cfg.onlyWhenFocused || focused;
        if (animate && anim.kind == FieldKind.MULTILINE && isWholesaleReplacement(anim.lastValue, value)) {
            // e.g. PageUp/PageDown in the book editor: a new page, not typing (no cross-fade of two whole pages)
            animate = false;
        }
        long now = Util.getMillis();
        FieldAnimationState st = anim.state;
        st.beginFrame(now, originX, originY, scrollKey);
        st.sync(value, animate, cfg);
        anim.lastValue = value;
        anim.dirty = true;
        anim.nowMs = now;
        anim.animate = animate;
        anim.idle = st.isIdle(cfg);
        anim.lastIdle = anim.idle;
        anim.clip = false;
        anim.caretMode = WidgetAnimation.CARET_NONE;
        anim.caretArgFont = null;
        anim.segmentText[0] = null;
        anim.segmentText[1] = null;
        anim.graphics = graphics;
        anim.activeFrames++;
        anim.frameActive = true;
        return true;
    }

    /** True when {@code cur} replaces at least {@link #REPLACE_MIN} chars of {@code old} by at least as many others. */
    static boolean isWholesaleReplacement(String old, String cur) {
        if (old == null || cur == null || old == cur) {
            return false;
        }
        final int oldLen = old.length();
        final int newLen = cur.length();
        if (oldLen < REPLACE_MIN || newLen < REPLACE_MIN) {
            return false;
        }
        final int min = Math.min(oldLen, newLen);
        int p = 0;
        while (p < min && old.charAt(p) == cur.charAt(p)) p++;
        int s = 0;
        final int maxS = min - p;
        while (s < maxS && old.charAt(oldLen - 1 - s) == cur.charAt(newLen - 1 - s)) s++;
        return oldLen - p - s >= REPLACE_MIN && newLen - p - s >= REPLACE_MIN;
    }

    /**
     * Advances the smoothed scroll position of an EditBox towards {@code target} (px scrolled, vanilla's value) and
     * returns the draw offset {@code target - smoothed}. Exponential smoothing with the glide time constant, like
     * the core's reflow glide; snaps when reflow smoothing is off, the field is not animated this frame, the widget
     * moved or was not drawn in the previous frame (it may have scrolled meanwhile). The offset never exceeds one
     * field width.
     */
    private static float scrollShift(WidgetAnimation anim, TypingConfig cfg, float target, int textX, int textY,
                                     int innerWidth) {
        final long now = anim.nowMs;
        final long frame = renderFrame;
        final boolean glide = anim.animate && cfg.smoothReflow && cfg.glideMs > 0 && anim.scrollValid
                && !anim.scrollGlideOff && textX == anim.scrollTextX && textY == anim.scrollTextY
                && anim.scrollFrame >= frame - 1
                && Float.isFinite(target) && Float.isFinite(anim.scrollSmooth);
        float s = target;
        if (glide && anim.scrollSmooth != target) {
            final float cur = anim.scrollSmooth;
            final long d = now - anim.scrollLastMs;
            final float dt = d <= 0L ? 0f : (float) Math.min(d, 100L);
            final float a = (float) (1.0 - Math.exp(-dt / (double) cfg.glideMs));
            s = cur + (target - cur) * a;
            final float max = Math.max(1, innerWidth);
            if (s - target > max) {
                s = target + max;
            } else if (target - s > max) {
                s = target - max;
            }
            // no progress = the increment rounded away: stop instead of stalling off target (see the core)
            if (!(Math.abs(target - s) >= SCROLL_EPSILON) || (a > 0f && s == cur)) {
                s = target;
            }
        }
        anim.scrollSmooth = s;
        anim.scrollValid = true;
        anim.scrollLastMs = now;
        anim.scrollFrame = frame;
        anim.scrollTextX = textX;
        anim.scrollTextY = textY;
        return target - s;
    }

    // =================================================================== text segments

    /** Remembers the plain text and absolute start index of EditBox segment {@code slot} (0 = before caret). */
    public static void captureSegment(WidgetAnimation anim, int slot, String text, int start) {
        if (anim.frameActive) {
            anim.segmentText[slot] = text;
            anim.segmentStart[slot] = start;
        }
    }

    /**
     * EditBox segment {@code slot} is about to be drawn by vanilla with {@code seq}. Returns what vanilla should
     * draw: {@code seq} itself (idle frame / fallback) or an empty sequence when the chars were drawn here.
     */
    public static FormattedCharSequence editBoxSegment(WidgetAnimation anim, Object widget, int slot, Font font,
                                                       FormattedCharSequence seq, int x, int y, int color,
                                                       boolean shadow) {
        String text = anim.segmentText[slot];
        anim.segmentText[slot] = null;
        if (!anim.frameActive || text == null || seq == null || anim.graphics == null) {
            return seq;
        }
        int start = anim.segmentStart[slot];
        int r = segment(anim, widget, font, seq, text, start, x, y, color, shadow, false, true);
        if (r == FALLBACK) {
            // vanilla draws this segment at its own position: never displace the text of this widget again
            anim.scrollGlideOff = true;
        } else {
            anim.windowEnd = start + text.length();
            anim.windowEndX = SINK.endX;
        }
        return r == DRAWN ? FormattedCharSequence.EMPTY : seq;
    }

    /**
     * A line (part) of a multi-line field starting at absolute index {@code start} (-1 = unknown) is about to be
     * drawn by vanilla. Returns what vanilla should draw: {@code str} or "" when the chars were drawn here.
     */
    public static String multilineSegment(WidgetAnimation anim, Object widget, int start, Font font, String str,
                                          int x, int y, int color, boolean shadow) {
        if (!anim.frameActive || start < 0 || str == null || str.isEmpty() || anim.graphics == null) {
            return str;
        }
        try {
            // exactly what GuiGraphicsExtractor#text(Font, String, ...) draws; for plain left-to-right lines
            // (the usual case) the same chars in the same order, without the bidi reordering work
            boolean plain = isPlainLeftToRight(str);
            FormattedCharSequence seq = plain ? FormattedCharSequence.forward(str, Style.EMPTY)
                    : Language.getInstance().getVisualOrder(FormattedText.of(str));
            return segment(anim, widget, font, seq, str, start, x, y, color, shadow, !plain, false) == DRAWN ? "" : str;
        } catch (Throwable t) {
            fail(anim, widget, t);
            return str;
        }
    }

    /**
     * True when bidi reordering of {@code str} is the identity: no formatting codes, no char from the Hebrew block
     * on (no right-to-left, Arabic shaping or bidi control chars), and a left-to-right default direction.
     */
    private static boolean isPlainLeftToRight(String str) {
        for (int i = 0, n = str.length(); i < n; i++) {
            char c = str.charAt(i);
            if (c >= FIRST_RTL_CHAR || c == FORMAT_CODE_CHAR) {
                return false;
            }
        }
        return !Language.getInstance().isDefaultRightToLeft();
    }

    /**
     * Lays out (and, unless idle, draws) one segment. Returns {@link #DRAWN} when the chars were drawn here,
     * {@link #VANILLA} when vanilla must draw {@code seq} (idle frame) and {@link #FALLBACK} when the formatter
     * output does not match the text (vanilla draws it, nothing laid out).
     */
    private static int segment(WidgetAnimation anim, Object widget, Font font, FormattedCharSequence seq,
                               String text, int start, int x, int y, int color, boolean shadow,
                               boolean checkCodepoints, boolean editBox) {
        boolean drawn = false;
        try {
            if (SINK.busy || (checkCodepoints || editBox) && !CHECK.matches(seq, text, checkCodepoints)) {
                // formatter output does not match the plain text (or re-entrant call): vanilla for this segment
                statFallbacks++;
                return FALLBACK;
            }
            if (anim.idle && !debugForcePerChar) {
                SINK.layout(font, anim.state, seq, start, x, y, color);
                statIdleSegments++;
                return VANILLA;
            }
            drawn = true;
            SINK.draw(anim.graphics, font, anim, ConfigManager.get(), seq, start, x, y, color, shadow,
                    anim.shift != 0f, 1f);
            return DRAWN;
        } catch (Throwable t) {
            fail(anim, widget, t);
            // if nothing was drawn yet let vanilla draw it; otherwise drop the rest of this frame's segment
            return drawn ? DRAWN : FALLBACK;
        }
    }

    /**
     * X of a vanilla element that belongs to the text (suggestion, selection highlight) moved by the scroll glide
     * offset; with {@code clamp} kept inside the widget.
     */
    public static int shiftedX(WidgetAnimation anim, int x, boolean clamp) {
        if (!anim.frameActive || anim.shift == 0f) {
            return x;
        }
        int sx = x + Math.round(anim.shift);
        if (clamp && anim.clip) {
            sx = Math.max(anim.clipX0, Math.min(anim.clipX1, sx));
        }
        if (clamp) {
            statShiftedHighlights++;
        } else {
            statShiftedSuggestions++;
        }
        return sx;
    }

    // =================================================================== caret

    /**
     * The EditBox caret is about to be drawn at {@code x}: its smoothed x (plus the scroll glide offset) is
     * computed. When it differs from {@code x}, the colour vanilla gets is made transparent and the caret is drawn
     * moved by {@link #caretDrawn} right after vanilla's call (inside one push/pop, so a mod cancelling the method
     * at that call can not leave the pose stack unbalanced). Returns the colour vanilla should use.
     */
    public static int editBoxCaret(WidgetAnimation anim, Object widget, int mode, Font font, int x, int y, int color,
                                   int lineHeight, boolean shadow) {
        if (!anim.frameActive || anim.graphics == null) {
            return color;
        }
        try {
            // The caret is smoothed in content coordinates (x + scrolled px), by a state whose origin is the widget:
            // scrolling does not move the target, so the caret glide goes on across scrolls (and across frames in
            // which the blinking caret is hidden); it is drawn displaced by the scroll glide offset like the text.
            TypingConfig cfg = ConfigManager.get();
            FieldAnimationState cs = anim.caretState();
            float target = x + anim.scrollPx;
            if (Math.abs(target - anim.caretTarget) > 2f * Math.max(anim.innerWidth, 1) || !anim.caretTargetValid) {
                cs.reset(); // a jump across a long text: no glide over thousands of pixels
            }
            anim.caretTarget = target;
            anim.caretTargetValid = true;
            cs.beginFrame(anim.nowMs, anim.textX, anim.textY, 0);
            cs.sync("", anim.animate, cfg);
            float cx = cs.caretX(target, cfg);
            cs.endFrame();
            return moveCaret(anim, mode, font, x, y, color, lineHeight, shadow, (cx - target) + anim.shift);
        } catch (Throwable t) {
            fail(anim, widget, t);
            return color;
        }
    }

    /**
     * Multi-line caret (see {@link #editBoxCaret}): the core state smooths x only, so a dedicated state whose origin
     * is the caret line is used — a line change is an origin change and snaps the caret instead of sweeping it
     * across the line.
     */
    public static int multilineCaret(WidgetAnimation anim, Object widget, int lineOriginX, int mode, Font font, int x,
                                     int y, int color, int lineHeight, boolean shadow) {
        if (!anim.frameActive || anim.graphics == null) {
            return color;
        }
        try {
            TypingConfig cfg = ConfigManager.get();
            FieldAnimationState cs = anim.caretState();
            cs.beginFrame(anim.nowMs, lineOriginX, y, 0);
            cs.sync("", anim.animate, cfg);
            float cx = cs.caretX(x, cfg);
            cs.endFrame();
            return moveCaret(anim, mode, font, x, y, color, lineHeight, shadow, cx - x);
        } catch (Throwable t) {
            fail(anim, widget, t);
            return color;
        }
    }

    private static int moveCaret(WidgetAnimation anim, int mode, Font font, int x, int y, int color, int lineHeight,
                                 boolean shadow, float dx) {
        if (dx == 0f || !Float.isFinite(dx)) {
            return color; // at rest: vanilla draws the caret itself
        }
        anim.caretMode = mode;
        anim.caretArgX = x;
        anim.caretArgY = y;
        anim.caretArgColor = color;
        anim.caretArgLineHeight = lineHeight;
        anim.caretArgShadow = shadow;
        anim.caretArgFont = font;
        anim.caretDx = dx;
        return color & 0x00FFFFFF;
    }

    /** Vanilla's caret call returned: draws the moved caret prepared by the caret method, if any. */
    public static void caretDrawn(WidgetAnimation anim) {
        int mode = anim.caretMode;
        if (mode == WidgetAnimation.CARET_NONE) {
            return;
        }
        anim.caretMode = WidgetAnimation.CARET_NONE;
        Font font = anim.caretArgFont;
        anim.caretArgFont = null;
        GuiGraphicsExtractor g = anim.graphics;
        if (!anim.frameActive || g == null) {
            return;
        }
        try {
            drawCaret(g, anim, mode, font);
            statCaretOffsets++;
        } catch (Throwable t) {
            fail(anim, null, t);
        }
    }

    // =================================================================== frame end

    /** EditBox: draws the chars revealed by a scroll glide and the ghosts, and ends the frame. */
    public static void endEditBox(WidgetAnimation anim, Object widget, Font font, boolean shadow) {
        end(anim, widget, font, shadow, true);
    }

    /** Multi-line field: draws the ghosts and ends the frame. */
    public static void end(WidgetAnimation anim, Object widget, Font font, boolean shadow) {
        end(anim, widget, font, shadow, false);
    }

    private static void end(WidgetAnimation anim, Object widget, Font font, boolean shadow, boolean editBox) {
        anim.caretMode = WidgetAnimation.CARET_NONE;
        anim.caretArgFont = null;
        if (!anim.frameActive) {
            anim.graphics = null;
            anim.value = null;
            anim.trace = null;
            return;
        }
        GuiGraphicsExtractor g = anim.graphics;
        try {
            TypingConfig cfg = ConfigManager.get();
            if (editBox && anim.shift != 0f) {
                drawRevealed(anim, g, widget, font, cfg, shadow);
            }
            FieldAnimationState st = anim.state;
            int n = st.ghostCount();
            if (n > 0) {
                boolean clipped = false;
                try {
                    if (anim.clip) {
                        pushClip(g, anim.clipX0, anim.clipY0, anim.clipX1, anim.clipY1);
                        clipped = true;
                    }
                    float shift = anim.shift;
                    for (int i = 0; i < n; i++) {
                        FieldAnimationState.Ghost ghost = st.ghost(i);
                        if (ghost != null && st.ghostTransform(ghost, cfg, TF)) {
                            // a ghost does not know its old sink position: drawn as a mid-piece char, so an
                            // underline/strikethrough stays inside its own cell (no 1 px stub left of it)
                            drawGlyph(g, font, ghost.codepoint, styleOf(ghost.style), ghost.x + shift, ghost.y,
                                    ghost.width, TF, ghost.color, shadow, false);
                            statGhosts++;
                        }
                    }
                } finally {
                    if (clipped) {
                        popClip(g);
                    }
                }
            }
            st.endFrame();
        } catch (Throwable t) {
            fail(anim, widget, t);
        } finally {
            anim.frameActive = false;
            anim.graphics = null;
            anim.value = null;
            Trace trace = anim.trace;
            anim.trace = null;
            if (trace != null) {
                trace.shift = anim.shift;
                trace.done = true;
            }
        }
    }

    /**
     * During a scroll glide the text is displaced by {@code shift}, which uncovers part of the text area on one
     * side of vanilla's window: lays out and draws the chars of the value that the smoothed view shows there
     * (formatted like the widget formats its segments), clipped to the text area.
     */
    private static void drawRevealed(WidgetAnimation anim, GuiGraphicsExtractor g, Object widget, Font font,
                                     TypingConfig cfg, boolean shadow) {
        final float d = anim.shift;
        final String value = anim.value;
        if (value == null || !(widget instanceof EditBoxAccess access)) {
            return;
        }
        final StringSplitter.WidthProvider widths = widthProvider(font);
        final int len = value.length();
        int from;
        int to;
        if (d > 0f) {
            // text displaced to the right: the chars before the window come into view on the left
            to = Math.min(Math.max(anim.displayPos, 0), len);
            from = to;
            float need = d + (anim.textX - anim.textClipX0) + 1f;
            float acc = 0f;
            for (int n = 0; from > 0 && acc < need && n < MAX_REVEALED; n++) {
                int cp = value.codePointBefore(from);
                from -= Character.charCount(cp);
                acc += widths.getWidth(cp, Style.EMPTY);
            }
        } else {
            // text displaced to the left: the chars after the window come into view on the right
            from = Math.min(Math.max(anim.windowEnd, 0), len);
            to = from;
            float need = -d + (anim.textClipX1 - anim.windowEndX) + 1f;
            float acc = 0f;
            for (int n = 0; to < len && acc < need && n < MAX_REVEALED; n++) {
                int cp = value.codePointAt(to);
                to += Character.charCount(cp);
                acc += widths.getWidth(cp, Style.EMPTY);
            }
        }
        if (from >= to) {
            return;
        }
        String text = value.substring(from, to);
        FormattedCharSequence seq = access.typinganimation$format(text, from);
        if (seq == null || SINK.busy || !CHECK.matches(seq, text, false)) {
            anim.scrollGlideOff = true;
            statFallbacks++;
            return;
        }
        float x = d > 0f ? anim.textX - WIDTH_SUM.sum(seq, widths) : anim.windowEndX;
        float alpha = d > 0f ? 1f : Math.min(1f, -d / REVEAL_FADE_PX);
        SINK.draw(g, font, anim, cfg, seq, from, x, anim.textY, anim.textColor, shadow, true, alpha);
        statRevealed += to - from;
    }

    // =================================================================== errors

    /** Records an error of our path: logs once per widget class, disables the widget's animation. */
    static void fail(WidgetAnimation anim, Object widget, Throwable t) {
        if (anim != null) {
            anim.failed = true;
            anim.frameActive = false;
        }
        if (firstError == null) {
            firstError = t;
        }
        Class<?> cls = widget != null ? widget.getClass() : TypingRenderer.class;
        if (LOGGED.add(cls)) {
            LOGGER.error("[typinganimation] Text animation of {} failed; this widget is drawn the vanilla way from now on",
                    cls.getName(), t);
        }
    }

    private static Style styleOf(Object o) {
        return o instanceof Style s ? s : Style.EMPTY;
    }

    // =================================================================== self-test trace

    /** Self-test: where the chars of one frame of one EditBox were drawn. */
    public static final class Trace {
        public static final int MAX = 1024;
        public final int[] index = new int[MAX];
        public final float[] x = new float[MAX];
        public final float[] width = new float[MAX];
        /** The char had an appear transform (its position is not its layout position). */
        public final boolean[] transformed = new boolean[MAX];
        public int count;
        public float shift;
        public int textX, innerWidth, textClipX0, textClipX1, valueLength;
        public volatile boolean done;

        void add(int i, float drawX, float w, boolean tf) {
            if (count < MAX) {
                index[count] = i;
                x[count] = drawX;
                width[count] = w;
                transformed[count] = tf;
                count++;
            }
        }
    }

    /** Self-test: records the next frame our path draws for {@code widget} (an EditBox). */
    public static Trace traceNextFrame(TypingStateHolder widget) {
        Trace t = new Trace();
        widget.typinganimation$animation().armedTrace = t;
        return t;
    }

    // =================================================================== per-char iteration

    /** Checks that a formatted sequence emits exactly the chars of the plain text (UTF-16 length, codepoints). */
    private static final class LengthCheck implements FormattedCharSink {
        private String text;
        private boolean codepoints;
        private int off;
        private boolean ok;

        boolean matches(FormattedCharSequence seq, String text, boolean codepoints) {
            this.text = text;
            this.codepoints = codepoints;
            this.off = 0;
            this.ok = true;
            try {
                seq.accept(this);
            } finally {
                this.text = null;
            }
            return ok && off == text.length();
        }

        @Override
        public boolean accept(int position, Style style, int codepoint) {
            if (codepoints && (off >= text.length() || text.codePointAt(off) != codepoint)) {
                ok = false;
                return false;
            }
            off += Character.charCount(codepoint);
            if (off > text.length()) {
                ok = false;
                return false;
            }
            return true;
        }
    }

    /** Float width of a formatted sequence, accumulated like the font does. */
    private static final class WidthSum implements FormattedCharSink {
        private StringSplitter.WidthProvider widths;
        private float sum;

        float sum(FormattedCharSequence seq, StringSplitter.WidthProvider widths) {
            this.widths = widths;
            this.sum = 0f;
            try {
                seq.accept(this);
            } finally {
                this.widths = null;
            }
            return sum;
        }

        @Override
        public boolean accept(int position, Style style, int codepoint) {
            sum += widths.getWidth(codepoint, style);
            return true;
        }
    }

    /**
     * Walks a segment char by char: target x = the same float accumulation the font uses, layoutChar, and (when
     * drawing) either extends the current run of untransformed chars with the same glide offset or draws the char
     * with its transform. Everything is drawn {@code anim.shift} to the right of its layout position (scroll glide).
     * One reusable instance (render thread only, guarded against re-entrance).
     */
    private static final class SegmentSink implements FormattedCharSink {
        boolean busy;
        /** Float x after the last char of the most recent segment (layout coordinates). */
        float endX;
        private boolean layoutOnly;
        private GuiGraphicsExtractor g;
        private Font font;
        private FieldAnimationState st;
        private TypingConfig cfg;
        private StringSplitter.WidthProvider widths;
        private FormattedCharSequence seq;
        private Trace trace;
        private int start;
        private int y;
        private int color;
        private boolean shadow;
        private boolean merge;
        private float shift;
        private float alphaMul;
        private int off;
        private float cur;
        private int runFrom = -1;
        private int runTo;
        private float runX;
        private float runDx;

        void layout(Font font, FieldAnimationState st, FormattedCharSequence seq, int start, float x, int y, int color) {
            busy = true;
            try {
                setup(null, font, st, null, null, seq, start, x, y, color, false, true, 0f, 1f);
                seq.accept(this);
                endX = cur;
            } finally {
                clear();
            }
        }

        void draw(GuiGraphicsExtractor g, Font font, WidgetAnimation anim, TypingConfig cfg, FormattedCharSequence seq,
                  int start, float x, int y, int color, boolean shadow, boolean textClip, float alphaMul) {
            busy = true;
            boolean clipped = false;
            try {
                setup(g, font, anim.state, cfg, anim.trace, seq, start, x, y, color, shadow, false, anim.shift,
                        alphaMul);
                if (anim.clip) {
                    if (textClip) {
                        pushClip(g, anim.textClipX0, anim.clipY0, anim.textClipX1, anim.clipY1);
                    } else {
                        pushClip(g, anim.clipX0, anim.clipY0, anim.clipX1, anim.clipY1);
                    }
                    clipped = true;
                }
                seq.accept(this);
                flushRun();
                endX = cur;
            } finally {
                if (clipped) {
                    popClip(g);
                }
                clear();
            }
        }

        private void setup(GuiGraphicsExtractor g, Font font, FieldAnimationState st, TypingConfig cfg, Trace trace,
                           FormattedCharSequence seq, int start, float x, int y, int color, boolean shadow,
                           boolean layoutOnly, float shift, float alphaMul) {
            this.g = g;
            this.font = font;
            this.st = st;
            this.cfg = cfg;
            this.trace = trace;
            this.seq = seq;
            this.start = start;
            this.y = y;
            this.color = color;
            this.shadow = shadow;
            this.layoutOnly = layoutOnly;
            this.merge = !debugNoMerge;
            this.shift = shift;
            this.alphaMul = alphaMul;
            this.widths = widthProvider(font);
            this.off = 0;
            this.cur = x; // the font starts at (float) x and adds each advance: same float sequence
            this.runFrom = -1;
        }

        private void clear() {
            g = null;
            font = null;
            st = null;
            cfg = null;
            seq = null;
            widths = null;
            trace = null;
            runFrom = -1;
            busy = false;
        }

        @Override
        public boolean accept(int position, Style style, int codepoint) {
            final int o = off;
            final int n = Character.charCount(codepoint);
            off = o + n;
            final float w = widths.getWidth(codepoint, style);
            final float tx = cur;
            cur = tx + w;
            final int index = start + o;
            final float x = st.layoutChar(index, tx, y, w, codepoint, style, color);
            if (layoutOnly) {
                return true;
            }
            boolean transformed = st.appearTransform(index, cfg, TF); // identity in TF when false
            if (trace != null) {
                trace.add(index, x + shift, w, transformed);
            }
            if (alphaMul < 1f) {
                TF.alpha *= alphaMul;
                transformed = true;
            }
            if (!transformed && merge) {
                final float dx = x - tx;
                if (runFrom >= 0 && Math.abs(dx - runDx) <= RUN_EPSILON) {
                    runTo = o + n;
                    return true;
                }
                flushRun();
                runFrom = o;
                runTo = o + n;
                runX = x + shift;
                runDx = dx;
                return true;
            }
            flushRun();
            drawGlyph(g, font, codepoint, style, x + shift, y, w, TF, color, shadow, position == 0);
            return true;
        }

        private void flushRun() {
            if (runFrom >= 0) {
                int from = runFrom;
                runFrom = -1;
                drawRun(g, font, new RangeSequence(seq, from, runTo), runX, y, color, shadow);
            }
        }
    }

    /**
     * Immutable view of the chars [from, to) (UTF-16 offsets within the segment) of a formatted sequence. Keeps the
     * original sink positions, so run output matches what the font does for the whole segment.
     */
    private static final class RangeSequence implements FormattedCharSequence {
        private final FormattedCharSequence base;
        private final int from;
        private final int to;

        RangeSequence(FormattedCharSequence base, int from, int to) {
            this.base = base;
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean accept(FormattedCharSink output) {
            return base.accept(new FormattedCharSink() {
                private int off;

                @Override
                public boolean accept(int position, Style style, int codepoint) {
                    int o = off;
                    off = o + Character.charCount(codepoint);
                    if (o < from) {
                        return true;
                    }
                    if (o >= to) {
                        return false; // stop the walk: nothing after the run is needed
                    }
                    return output.accept(position, style, codepoint);
                }
            });
        }
    }

    // =================================================================== draw primitives (version-specific surface)

    /** Float advance of one char, exactly as the font accumulates it (Font#width rounds the sum up). */
    private static StringSplitter.WidthProvider widthProvider(Font font) {
        return ((StringSplitterAccessor) font.getSplitter()).typinganimation$widthProvider();
    }

    /** Draws a run of untransformed chars starting at float x (fraction through the pose). */
    private static void drawRun(GuiGraphicsExtractor g, Font font, FormattedCharSequence run, float x, int y, int color,
                                boolean shadow) {
        int ix = (int) Math.floor(x);
        float frac = x - ix;
        if (frac == 0f) {
            g.text(font, run, ix, y, color, shadow);
        } else {
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            try {
                pose.translate(frac, 0f);
                g.text(font, run, ix, y, color, shadow);
            } finally {
                pose.popMatrix();
            }
        }
        statRuns++;
    }

    /**
     * Draws one glyph at (x, y) with a transform: translate to the pivot (fraction of the cell {@code cellWidth} x 9),
     * rotate, scale, translate back; alpha multiplied into the ARGB colour (style colours keep the base alpha).
     * {@code tf.glyph} (SCRAMBLE) replaces the codepoint. Nearly invisible or degenerate glyphs are skipped.
     *
     * <p>{@code pieceStart}: the char has sink position 0 in the sequence vanilla draws (the first char of a
     * {@code forward}/string piece or of a segment). {@code Font.PreparedTextBuilder#accept} starts a char's
     * underline/strikethrough 1 px further left exactly at position 0 ({@code position == 0 ? x - 1 : x}), so the
     * single-char sequence reports 0 only for such a char: a mid-piece char drawn on its own keeps its effects inside
     * its own cell, as in vanilla ({@code FormattedCharSequence.codepoint} would always report 0).
     */
    private static void drawGlyph(GuiGraphicsExtractor g, Font font, int codepoint, Style style, float x, float y,
                                  float cellWidth, CharTransform tf, int color, boolean shadow,
                                  boolean pieceStart) {
        float alpha = tf.alpha;
        if (!(alpha >= MIN_ALPHA) || !(tf.scaleX >= MIN_SCALE) || !(tf.scaleY >= MIN_SCALE)) {
            return;
        }
        int argb = color;
        if (alpha < 1f) {
            int a = Math.round(ARGB.alpha(color) * alpha);
            if (a <= 0) {
                return;
            }
            argb = ARGB.color(a, color);
        }
        int glyph = tf.glyph != -1 ? tf.glyph : codepoint;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        try {
            if (tf.hasScaleOrRotation()) {
                float px = tf.pivotX * cellWidth;
                float py = tf.pivotY * CELL_HEIGHT;
                pose.translate(x + tf.dx + px, y + tf.dy + py);
                if (tf.rotation != 0f) {
                    pose.rotate(tf.rotation);
                }
                pose.scale(tf.scaleX, tf.scaleY);
                pose.translate(-px, -py);
            } else {
                pose.translate(x + tf.dx, y + tf.dy);
            }
            // a fresh immutable sequence per submit (render-state era), with vanilla's sink position of the char
            final int position = pieceStart ? 0 : 1;
            FormattedCharSequence single = sink -> sink.accept(position, style, glyph);
            g.text(font, single, 0, 0, argb, shadow);
        } finally {
            pose.popMatrix();
        }
        statGlyphs++;
    }

    /** Draws the caret vanilla was about to draw, moved by {@code anim.caretDx}, clipped to the widget. */
    private static void drawCaret(GuiGraphicsExtractor g, WidgetAnimation anim, int mode, Font font) {
        boolean clipped = false;
        try {
            if (anim.clip) {
                pushClip(g, anim.clipX0, anim.clipY0, anim.clipX1, anim.clipY1);
                clipped = true;
            }
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            try {
                pose.translate(anim.caretDx, 0f);
                if (mode == WidgetAnimation.CARET_INSERT) {
                    TextCursorUtils.extractInsertCursor(g, anim.caretArgX, anim.caretArgY, anim.caretArgColor,
                            anim.caretArgLineHeight);
                } else if (font != null) {
                    TextCursorUtils.extractAppendCursor(g, font, anim.caretArgX, anim.caretArgY, anim.caretArgColor,
                            anim.caretArgShadow);
                }
            } finally {
                pose.popMatrix();
            }
        } finally {
            if (clipped) {
                popClip(g);
            }
        }
    }

    private static void pushClip(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.enableScissor(x0, y0, x1, y1);
    }

    private static void popClip(GuiGraphicsExtractor g) {
        g.disableScissor();
    }
}
