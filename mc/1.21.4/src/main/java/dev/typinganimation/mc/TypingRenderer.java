package dev.typinganimation.mc;

import dev.typinganimation.core.CharTransform;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.FieldAnimationState;
import dev.typinganimation.core.FieldKind;
import dev.typinganimation.core.TypingAnimationMod;
import dev.typinganimation.core.TypingConfig;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-character text renderer shared by the widget hooks (renderer contract, docs/SPEC.md section 5).
 * Render thread only; all scratch objects are reused, so steady-state frames allocate nothing here.
 */
public final class TypingRenderer {
    /** First exception caught in our render path (the self-test asserts it stays null). */
    public static volatile Throwable firstError;
    /**
     * Self-test switch: bypass the idle fast path so settled text goes through the per-char path too (used by the
     * at-rest fidelity check, which must still be pixel-identical to vanilla).
     */
    public static volatile boolean forcePerChar;
    /** Counters for the self-test (plain longs, render thread only). */
    public static final Stats STATS = new Stats();

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static final Collector COLLECTOR = new Collector();
    private static final CharTransform TF = new CharTransform();
    /** Caret query that snaps (see {@link #caretOffset}); only enabled/smoothCursor/glideMs are read by the core. */
    private static final TypingConfig CARET_SNAP = caretSnapConfig();

    private static TypingConfig caretSnapConfig() {
        TypingConfig c = TypingConfig.defaults();
        c.smoothCursor = false;
        return c;
    }

    private TypingRenderer() {
    }

    /**
     * Draws one formatted segment of a single-line field per character and returns exactly what
     * {@code GuiGraphics#drawString(Font, FormattedCharSequence, x, y, color, shadow)} would return. {@code start} is
     * the absolute (UTF-16) index of the segment's first char and {@code expectedLen} the length of the substring the
     * formatter was given; when the formatter output does not match it, the segment is drawn the vanilla way.
     */
    static int drawSequence(GuiGraphics gg, Font font, FormattedCharSequence text, int x, int y, int color,
                            boolean shadow, WidgetAnimator a, int start, int expectedLen) {
        Collector c = COLLECTOR.begin();
        try {
            text.accept(c);
            if (c.units != expectedLen) {
                STATS.mismatches++;
                return gg.drawString(font, text, x, y, color, shadow);
            }
            return layoutAndDraw(gg, font, text, c, x, y, color, shadow, a, start);
        } finally {
            c.end();
        }
    }

    /**
     * Same for a plain String line (multi-line fields): iterated like {@code Font} does for strings (legacy
     * formatting codes applied), so a length mismatch (formatting codes present) falls back to vanilla drawing.
     */
    static int drawPlain(GuiGraphics gg, Font font, String text, int x, int y, int color, boolean shadow,
                         WidgetAnimator a, int start) {
        Collector c = COLLECTOR.begin();
        try {
            StringDecomposer.iterateFormatted(text, Style.EMPTY, c);
            if (c.units != text.length()) {
                STATS.mismatches++;
                return gg.drawString(font, text, x, y, color, shadow);
            }
            return layoutAndDraw(gg, font, text, c, x, y, color, shadow, a, start);
        } finally {
            c.end();
        }
    }

    private static int layoutAndDraw(GuiGraphics gg, Font font, Object original, Collector c, int x, int y, int color,
                                     boolean shadow, WidgetAnimator a, int start) {
        final TypingConfig cfg = ConfigManager.get();
        final FieldAnimationState st = a.state;
        final StringSplitter.WidthProvider widths = GlyphDrawer.widthProvider(font);
        final int base = GlyphDrawer.opaque(color);
        final boolean idle = a.idle;
        final CharTransform tf = TF;
        // Glyphs with an appear transform (they may travel out of the field) are clipped like the ghosts, in a second
        // scissored batch. Plain glyphs never are: settled text stays exactly vanilla, even where vanilla lets it
        // overflow the box (bold text). The scissor follows translation/scale poses; a rotated pose skips the clip.
        final boolean clip = !idle && a.clip && GlyphDrawer.poseIsScreenAligned(gg);
        int deferred = 0;
        int effects = 0;
        // Same float accumulation as Font.StringRenderOutput, so every rest position equals vanilla's exactly.
        float pen = x;
        int index = start;
        for (int k = 0; k < c.count; k++) {
            final int cp = c.codepoints[k];
            final Style style = c.styles[k];
            final float advance = widths.getWidth(cp, style);
            a.noteCaret(index, pen, y);
            final float drawX = st.layoutChar(index, pen, y, advance, cp, style, base);
            if (!idle) {
                final boolean transformed = st.appearTransform(index, cfg, tf);
                if (transformed && clip) {
                    c.defer(deferred++, k, index, drawX, pen, advance);
                } else {
                    final boolean split = GlyphDrawer.splitEffects(style);
                    final int vertices = drawChar(gg, font, a, cp, style, c.pieceStarts[k], drawX, pen, y, advance,
                            base, shadow, transformed ? tf : null,
                            split ? GlyphDrawer.PASS_GLYPH : GlyphDrawer.PASS_ALL, 0);
                    if (split && vertices != GlyphDrawer.NOT_DRAWN) {
                        c.effect(effects++, k, index, drawX, advance, vertices);
                    }
                }
            }
            pen += advance;
            index += c.units(k);
        }
        a.noteCaret(index, pen, y);
        if (idle) {
            STATS.idleSegments++;
            return original instanceof String s
                    ? gg.drawString(font, s, x, y, color, shadow)
                    : gg.drawString(font, (FormattedCharSequence) original, x, y, color, shadow);
        }
        // underlines/strikethroughs after all glyphs of the segment, like vanilla's Font (see GlyphDrawer)
        for (int e = 0; e < effects; e++) {
            final int k = c.fxK[e];
            // same frame time, same result as in the loop above
            final boolean transformed = st.appearTransform(c.fxIndex[e], cfg, tf);
            drawChar(gg, font, a, c.codepoints[k], c.styles[k], c.pieceStarts[k], c.fxX[e], c.fxX[e], y,
                    c.fxAdvance[e], base, shadow, transformed ? tf : null, GlyphDrawer.PASS_EFFECTS,
                    c.fxVertices[e]);
        }
        GlyphDrawer.flush(gg); // painter's order with later draws (the scissor below would flush anyway)
        if (deferred > 0) {
            GlyphDrawer.pushClip(gg, a.clipX0, a.clipY0, a.clipX1, a.clipY1);
            try {
                // glyphs, then underlines/strikethroughs (see GlyphDrawer)
                for (int d = 0; d < deferred; d++) {
                    final int k = c.defK[d];
                    final boolean split = GlyphDrawer.splitEffects(c.styles[k]);
                    // same frame time, same result as in the loop above
                    final boolean transformed = st.appearTransform(c.defIndex[d], cfg, tf);
                    c.defVertices[d] = drawChar(gg, font, a, c.codepoints[k], c.styles[k], c.pieceStarts[k],
                            c.defX[d], c.defPen[d], y, c.defAdvance[d], base, shadow, transformed ? tf : null,
                            split ? GlyphDrawer.PASS_GLYPH : GlyphDrawer.PASS_ALL, 0);
                }
                for (int d = 0; d < deferred; d++) {
                    final int k = c.defK[d];
                    if (!GlyphDrawer.splitEffects(c.styles[k]) || c.defVertices[d] == GlyphDrawer.NOT_DRAWN) {
                        continue;
                    }
                    final boolean transformed = st.appearTransform(c.defIndex[d], cfg, tf);
                    drawChar(gg, font, a, c.codepoints[k], c.styles[k], c.pieceStarts[k], c.defX[d], c.defPen[d], y,
                            c.defAdvance[d], base, shadow, transformed ? tf : null, GlyphDrawer.PASS_EFFECTS,
                            c.defVertices[d]);
                }
                GlyphDrawer.flush(gg);
            } finally {
                GlyphDrawer.popClip(gg);
            }
        }
        return (int) pen + (shadow ? 1 : 0);
    }

    /** Draws one char in one pass (see GlyphDrawer) and counts it; returns what GlyphDrawer#draw returns. */
    private static int drawChar(GuiGraphics gg, Font font, WidgetAnimator a, int cp, Style style, boolean pieceStart,
                                float drawX, float pen, int y, float advance, int base, boolean shadow,
                                CharTransform tf, int pass, int glyphVertices) {
        final int r = GlyphDrawer.draw(gg, font, cp, style, drawX, y, advance, base, shadow, tf, pieceStart, pass,
                glyphVertices);
        if (r != GlyphDrawer.NOT_DRAWN && pass != GlyphDrawer.PASS_EFFECTS) {
            if (tf != null) {
                STATS.transformedGlyphs++;
            } else {
                STATS.plainGlyphs++;
            }
            if (drawX != pen) {
                STATS.glidingGlyphs++;
            }
            if (a.kind == FieldKind.CHAT) {
                STATS.chatGlyphs++;
            } else if (a.kind == FieldKind.MULTILINE) {
                STATS.multilineGlyphs++;
            }
        }
        return r;
    }

    /**
     * Draws the ghosts of removed characters at their recorded positions, clipped to the widget's clip rectangle
     * when it has one (skipped under a rotated or mirrored pose, which the axis-aligned scissor cannot follow).
     */
    static void drawGhosts(GuiGraphics gg, WidgetAnimator a) {
        final FieldAnimationState st = a.state;
        final int n = st.ghostCount();
        if (n == 0 || a.font == null) {
            return;
        }
        final TypingConfig cfg = ConfigManager.get();
        final boolean scissor = a.clip && GlyphDrawer.poseIsScreenAligned(gg);
        if (scissor) {
            GlyphDrawer.pushClip(gg, a.clipX0, a.clipY0, a.clipX1, a.clipY1);
        }
        try {
            boolean any = false;
            for (int i = 0; i < n; i++) {
                FieldAnimationState.Ghost g = st.ghost(i);
                if (g == null || !st.ghostTransform(g, cfg, TF)) {
                    continue;
                }
                Style style = g.style instanceof Style s ? s : Style.EMPTY;
                // A ghost does not know its old sink position: draw it as a mid-piece char, so an underline or
                // strikethrough stays inside its own cell (no 1 px stub left of a moving/fading ghost).
                if (GlyphDrawer.draw(gg, a.font, g.codepoint, style, g.x, g.y, g.width, g.color, a.shadow, TF,
                        false, GlyphDrawer.PASS_ALL, 0) != GlyphDrawer.NOT_DRAWN) {
                    any = true;
                    STATS.ghostGlyphs++;
                }
            }
            if (any || scissor) {
                GlyphDrawer.flush(gg);
            }
        } finally {
            if (scissor) {
                GlyphDrawer.popClip(gg);
            }
        }
    }

    /**
     * Smoothed caret: queries the glide for this frame's caret target and returns the x offset to draw it with.
     * {@code snap} puts the caret glide at the target for this frame (a later frame glides from there): the core
     * returns the target, and keeps it as the smoothed position, whenever {@code smoothCursor} is off.
     */
    static float caretOffset(WidgetAnimator a, int targetX, boolean snap) {
        float x = a.state.caretX(targetX, snap ? CARET_SNAP : ConfigManager.get());
        a.caretQueried = true;
        float dx = x - targetX;
        return dx != 0f && Float.isFinite(dx) ? dx : 0f;
    }

    /** Ends the core frame (after the RETURN hooks); never throws. */
    static void endFrame(WidgetAnimator a, Object widget) {
        a.active = false;
        try {
            a.state.endFrame();
        } catch (Throwable t) {
            fail(a, widget, t);
        }
    }

    /**
     * Exception in our render path: remember it, log once per widget class, and draw this widget the vanilla way
     * from now on. Never rethrows (except for VM errors).
     */
    static void fail(WidgetAnimator a, Object widget, Throwable t) {
        if (t instanceof VirtualMachineError vme) {
            throw vme;
        }
        if (a != null) {
            a.failed = true;
            a.active = false;
        }
        if (firstError == null) {
            firstError = t;
        }
        String cls = widget == null ? "?" : widget.getClass().getName();
        if (LOGGED.add(cls)) {
            TypingAnimationMod.LOGGER.error("[{}] Animated text rendering failed for {}; falling back to vanilla "
                    + "rendering for this widget", TypingAnimationMod.MOD_ID, cls, t);
        }
    }

    /** Self-test counters. */
    public static final class Stats {
        /** Caret glides: carets drawn off their target (all fields) / multi-line glide offsets (drawn or not). */
        public long transformedGlyphs, plainGlyphs, glidingGlyphs, ghostGlyphs, caretGlides, multilineCaretGlides,
                chatGlyphs, multilineGlyphs, idleSegments, mismatches, visibleCaptures;

        @Override
        public String toString() {
            return "transformed=" + transformedGlyphs + " plain=" + plainGlyphs + " gliding=" + glidingGlyphs
                    + " ghosts=" + ghostGlyphs + " caretGlides=" + caretGlides + " multilineCaretGlides="
                    + multilineCaretGlides + " chat=" + chatGlyphs + " multiline=" + multilineGlyphs
                    + " idleSegments=" + idleSegments + " mismatches=" + mismatches
                    + " visibleCaptures=" + visibleCaptures;
        }
    }

    /**
     * Collects the chars of one segment (code point, style, whether the sink position was 0, UTF-16 units) into
     * reusable arrays. The position itself restarts per piece of a composite sequence, so only "is 0" is kept: it
     * decides the 1.21.4 underline/strikethrough start (see {@link GlyphDrawer#draw}).
     */
    private static final class Collector implements FormattedCharSink {
        int[] codepoints = new int[64];
        Style[] styles = new Style[64];
        boolean[] pieceStarts = new boolean[64];
        int count;
        int units;
        /** Glyphs of the segment drawn in the second, clipped batch: char slot, absolute index, x, pen x, advance. */
        int[] defK = new int[16];
        int[] defIndex = new int[16];
        float[] defX = new float[16];
        float[] defPen = new float[16];
        float[] defAdvance = new float[16];
        /** Vertices of the glyph pass of each deferred glyph (see GlyphDrawer). */
        int[] defVertices = new int[16];
        /**
         * Split glyphs drawn outside the clipped batch, whose effects pass follows: char slot, index, x, advance, vertices
         * of the glyph pass.
         */
        int[] fxK = new int[16];
        int[] fxIndex = new int[16];
        float[] fxX = new float[16];
        float[] fxAdvance = new float[16];
        int[] fxVertices = new int[16];
        private boolean busy;

        void effect(int e, int k, int index, float x, float advance, int vertices) {
            if (e == fxK.length) {
                int n = e * 2;
                fxK = Arrays.copyOf(fxK, n);
                fxIndex = Arrays.copyOf(fxIndex, n);
                fxX = Arrays.copyOf(fxX, n);
                fxAdvance = Arrays.copyOf(fxAdvance, n);
                fxVertices = Arrays.copyOf(fxVertices, n);
            }
            fxK[e] = k;
            fxIndex[e] = index;
            fxX[e] = x;
            fxAdvance[e] = advance;
            fxVertices[e] = vertices;
        }

        void defer(int d, int k, int index, float x, float pen, float advance) {
            if (d == defK.length) {
                int n = d * 2;
                defK = Arrays.copyOf(defK, n);
                defIndex = Arrays.copyOf(defIndex, n);
                defX = Arrays.copyOf(defX, n);
                defPen = Arrays.copyOf(defPen, n);
                defAdvance = Arrays.copyOf(defAdvance, n);
                defVertices = Arrays.copyOf(defVertices, n);
            }
            defK[d] = k;
            defIndex[d] = index;
            defX[d] = x;
            defPen[d] = pen;
            defAdvance[d] = advance;
        }

        Collector begin() {
            if (busy) {
                throw new IllegalStateException("re-entrant text rendering");
            }
            busy = true;
            count = 0;
            units = 0;
            return this;
        }

        void end() {
            Arrays.fill(styles, 0, count, null);
            count = 0;
            busy = false;
        }

        int units(int k) {
            return Character.charCount(codepoints[k]);
        }

        @Override
        public boolean accept(int position, Style style, int codepoint) {
            if (count == codepoints.length) {
                int n = count * 2;
                codepoints = Arrays.copyOf(codepoints, n);
                styles = Arrays.copyOf(styles, n);
                pieceStarts = Arrays.copyOf(pieceStarts, n);
            }
            codepoints[count] = codepoint;
            styles[count] = style;
            pieceStarts[count] = position == 0;
            count++;
            units += Character.charCount(codepoint);
            return true;
        }
    }
}
