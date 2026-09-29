package dev.typinganimation.mc;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.typinganimation.core.CharTransform;
import dev.typinganimation.mixin.GuiGraphicsAccessor;
import dev.typinganimation.mixin.StringSplitterAccessor;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import org.joml.Matrix4f;

/**
 * The version-specific drawing primitives (1.21.4, immediate-mode {@code GuiGraphics}: {@code PoseStack}
 * with a {@code Matrix4f}, {@code Font#drawInBatch} into the GuiGraphics' own buffer source (read through
 * {@link GuiGraphicsAccessor}: the public getter of 1.21.1 is gone), pose-aware scissor, pre-1.21.6 alpha rules).
 * Everything that a port to another Minecraft version has to touch for glyph output is in this class.
 */
final class GlyphDrawer {
    /** LightTexture.FULL_BRIGHT, what GuiGraphics#drawString passes. */
    static final int FULL_BRIGHT = 15728880;
    /**
     * Glyphs whose alpha byte would be below this are skipped: pre-1.21.6 {@code Font} treats an alpha below 4 as
     * "opaque" and the text shader discards alpha below 0.1 anyway.
     */
    private static final float MIN_ALPHA_BYTE = 5f;
    private static final float MIN_SCALE = 0.02f;
    /** Height of the glyph cell the transform pivot refers to. */
    static final float LINE_HEIGHT = 9f;

    /*
     * Draw passes. Vanilla's Font emits all glyph quads of a draw call first (StringRenderOutput#renderCharacters:
     * shadow at z 0, main at 0.03) and then all underline/strikethrough quads (shadow at 0.01, main at 0.04), under the
     * GUI depth test. A char drawn with its own effects before the next char would put e.g. its strikethrough shadow's
     * 1 px overhang under the next glyph's ink, where vanilla's depth test rejects it (visible with translucent text).
     * So decorated chars are split: PASS_GLYPH with the other glyphs of the batch (the style without
     * underline/strikethrough), PASS_EFFECTS after all of them (the full style, with the glyph quads, which the Font
     * emits first, dropped by QuadFilter). Together they emit exactly vanilla's quads in vanilla's order.
     */
    /** {@link #draw} pass: the glyph with its underline/strikethrough (ghosts, undecorated or obfuscated chars). */
    static final int PASS_ALL = 0;
    /** {@link #draw} pass: the glyph only; returns the number of vertices it emitted. */
    static final int PASS_GLYPH = 1;
    /** {@link #draw} pass: the underline/strikethrough only (the first {@code glyphVertices} vertices dropped). */
    static final int PASS_EFFECTS = 2;
    /** {@link #draw} result: nothing drawn (invisible or degenerate). */
    static final int NOT_DRAWN = -1;
    private static final QuadFilter FILTER = new QuadFilter();
    /** One-entry cache of {@link #undecorated} (render thread only). */
    private static Style lastDecorated, lastUndecorated;

    /**
     * One reusable single-character sequence. Allowed here because 1.21.4 still draws immediately (the Font consumes
     * the sequence inside drawInBatch); render-state versions (1.21.6+) must allocate a fresh one per draw instead.
     */
    private static final SingleChar SINGLE = new SingleChar();
    /** Pose of the glyph being drawn with a transform (render thread only, consumed by drawInBatch at once). */
    private static final Matrix4f SCRATCH = new Matrix4f();

    private GlyphDrawer() {
    }

    /** Exact float advance provider of this font (the same one the Font uses while drawing). */
    static StringSplitter.WidthProvider widthProvider(Font font) {
        return ((StringSplitterAccessor) font.getSplitter()).typinganimation$widthProvider();
    }

    /** What {@code Font#adjustColor} does: an alpha below 4 means "opaque". */
    static int opaque(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }

    /**
     * True when a char with this style is drawn in two passes ({@link #PASS_GLYPH} with the other glyphs of its batch,
     * {@link #PASS_EFFECTS} after them). Obfuscated chars are not split: the two passes would pick different random
     * glyphs.
     */
    static boolean splitEffects(Style style) {
        return style != null && (style.isUnderlined() || style.isStrikethrough()) && !style.isObfuscated();
    }

    private static Style undecorated(Style style) {
        if (style != lastDecorated) {
            lastDecorated = style;
            lastUndecorated = style.withUnderlined(false).withStrikethrough(false);
        }
        return lastUndecorated;
    }

    /**
     * Queues one glyph into the GUI buffer source (no flush). {@code baseColor} must already be {@link #opaque}.
     * With a transform: alpha multiplied into the colour, offset, and rotation/scale around the pivot (fraction of
     * the {@code advance} x 9 px cell).
     * <p>
     * {@code pieceStart}: the char had sink position 0 in the sequence vanilla draws (first char of a
     * {@code forward}/string piece). 1.21.4 {@code Font.StringRenderOutput#accept} starts a char's
     * underline/strikethrough 1 px further left only for that position ({@code p == 0 ? x - 1 : x}; 1.21.1 did it for
     * every char), so the flag is passed through to keep the effect geometry of each char exactly vanilla's.
     * <p>
     * {@code pass}: {@link #PASS_ALL}, {@link #PASS_GLYPH} or {@link #PASS_EFFECTS} with the {@code glyphVertices}
     * the glyph pass of the same char returned (see the pass constants). Returns {@link #NOT_DRAWN}, or the number of
     * vertices emitted (counted in the glyph pass only, 0 otherwise).
     */
    static int draw(GuiGraphics gg, Font font, int codepoint, Style style, float x, float y, float advance,
                    int baseColor, boolean shadow, CharTransform tf, boolean pieceStart, int pass, int glyphVertices) {
        int color = baseColor;
        int cp = codepoint;
        boolean moved = false;
        if (tf != null) {
            if (tf.alpha < 1f) {
                float a = (baseColor >>> 24) * tf.alpha;
                if (!(a >= MIN_ALPHA_BYTE)) {
                    return NOT_DRAWN;
                }
                color = (Math.min(255, Math.round(a)) << 24) | (baseColor & 0x00FFFFFF);
            }
            if (!(tf.scaleX >= MIN_SCALE) || !(tf.scaleY >= MIN_SCALE)) {
                return NOT_DRAWN;
            }
            if (tf.glyph != -1) {
                cp = tf.glyph;
            }
            moved = tf.dx != 0f || tf.dy != 0f || tf.hasScaleOrRotation();
        }
        SINGLE.set(cp, pass == PASS_GLYPH && style != null ? undecorated(style) : style, pieceStart);
        final MultiBufferSource target = pass == PASS_ALL ? buffers(gg)
                : FILTER.begin(buffers(gg), pass == PASS_EFFECTS ? glyphVertices : 0);
        try {
            final Matrix4f pose = gg.pose().last().pose();
            if (!moved) {
                font.drawInBatch(SINGLE, x, y, color, shadow, pose, target, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
                return pass == PASS_GLYPH ? FILTER.vertices : 0;
            }
            // A scratch copy of the pose instead of PoseStack push/pop (which allocates a Pose per glyph): drawInBatch
            // consumes the matrix before it returns (1.21.4 draws immediately), so reusing it is safe.
            final Matrix4f m = SCRATCH.set(pose);
            if (tf.hasScaleOrRotation()) {
                float px = x + tf.pivotX * advance;
                float py = y + tf.pivotY * LINE_HEIGHT;
                m.translate(px + tf.dx, py + tf.dy, 0f);
                if (tf.rotation != 0f) {
                    m.rotateZ(tf.rotation); // +z rotation is clockwise on the y-down GUI
                }
                if (tf.scaleX != 1f || tf.scaleY != 1f) {
                    m.scale(tf.scaleX, tf.scaleY, 1f);
                }
                m.translate(-px, -py, 0f);
            } else {
                m.translate(tf.dx, tf.dy, 0f);
            }
            font.drawInBatch(SINGLE, x, y, color, shadow, m, target, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
            return pass == PASS_GLYPH ? FILTER.vertices : 0;
        } finally {
            SINGLE.clear();
            FILTER.end();
        }
    }

    /** The buffer source {@code gg} draws text into (the same one its drawString calls use). */
    private static MultiBufferSource.BufferSource buffers(GuiGraphics gg) {
        return ((GuiGraphicsAccessor) gg).typinganimation$bufferSource();
    }

    /**
     * Ends the batch. 1.21.4 GuiGraphics draw calls ({@code drawString}, {@code fill}) do not flush on their own:
     * everything waits in the buffer source until a scissor change, an item/special draw or the end of the frame.
     * Flushing after an animated segment keeps the painter's order with draws of other render types that follow
     * (caret, selection highlight). Scissor changes ({@link #pushClip}/{@link #popClip}) flush on their own before
     * switching the rectangle, so a flush right before them is redundant but harmless.
     */
    static void flush(GuiGraphics gg) {
        gg.flush();
    }

    /**
     * True when the pose keeps rectangles axis-aligned (translation and positive scale only). Unlike 1.21.1,
     * {@code GuiGraphics#enableScissor} transforms the rectangle's corners by the current pose, so widget-space clip
     * rectangles line up with the widget under any such pose; only a rotated or mirrored pose would clip wrongly.
     */
    static boolean poseIsScreenAligned(GuiGraphics gg) {
        Matrix4f m = gg.pose().last().pose();
        return m.m01() == 0f && m.m10() == 0f && m.m00() > 0f && m.m11() > 0f;
    }

    /**
     * Pushes a scissor rectangle in the current pose's coordinates (intersected with the enclosing one). In 1.21.4
     * {@code enableScissor} flushes the glyphs still queued in the buffer source before it switches the rectangle, so
     * they keep the previous clip.
     */
    static void pushClip(GuiGraphics gg, int x0, int y0, int x1, int y1) {
        gg.enableScissor(x0, y0, x1, y1);
    }

    static void popClip(GuiGraphics gg) {
        gg.disableScissor();
    }

    /**
     * Buffer source (and consumer) around the GUI buffer source for one drawInBatch call of a split char: counts the
     * vertices the Font emits (glyph pass), or drops the first {@code drop} of them, the glyph quads (effects pass),
     * forwarding the rest. The Font asks for a buffer and fills it before it asks for the next one, so one reusable
     * consumer that forwards to the buffer asked for last is enough (render thread only).
     */
    private static final class QuadFilter implements MultiBufferSource, VertexConsumer {
        private MultiBufferSource.BufferSource source;
        private VertexConsumer target;
        int vertices;
        private int drop;
        private boolean dropping;

        QuadFilter begin(MultiBufferSource.BufferSource source, int drop) {
            this.source = source;
            this.target = null;
            this.vertices = 0;
            this.drop = drop;
            this.dropping = false;
            return this;
        }

        void end() {
            source = null;
            target = null;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            target = source.getBuffer(type);
            return this;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            dropping = vertices++ < drop;
            if (!dropping) {
                target.addVertex(x, y, z);
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            if (!dropping) {
                target.setColor(red, green, blue, alpha);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (!dropping) {
                target.setUv(u, v);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            if (!dropping) {
                target.setUv1(u, v);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (!dropping) {
                target.setUv2(u, v);
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            if (!dropping) {
                target.setNormal(normalX, normalY, normalZ);
            }
            return this;
        }
    }

    /**
     * Mutable one-codepoint sequence (see {@link #SINGLE}). Reports sink position 0 only for a piece start: the
     * 1.21.4 {@code Font.StringRenderOutput} extends a char's underline/strikethrough 1 px to the left exactly when
     * the position is 0, and the position is used for nothing else.
     */
    private static final class SingleChar implements FormattedCharSequence {
        private int codepoint;
        private Style style = Style.EMPTY;
        private int position;

        void set(int codepoint, Style style, boolean pieceStart) {
            this.codepoint = codepoint;
            this.style = style == null ? Style.EMPTY : style;
            this.position = pieceStart ? 0 : 1;
        }

        void clear() {
            style = Style.EMPTY;
        }

        @Override
        public boolean accept(FormattedCharSink sink) {
            return sink.accept(position, style, codepoint);
        }
    }
}
