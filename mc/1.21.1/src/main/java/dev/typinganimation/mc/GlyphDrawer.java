package dev.typinganimation.mc;

import dev.typinganimation.core.CharTransform;
import dev.typinganimation.mixin.StringSplitterAccessor;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import org.joml.Matrix4f;

/**
 * The version-specific drawing primitives (1.21.1 era: immediate-mode {@code GuiGraphics}, {@code PoseStack} with a
 * {@code Matrix4f}, {@code Font#drawInBatch} into the GUI buffer source, pre-1.21.6 alpha rules). Everything that a
 * port to another Minecraft version has to touch for glyph output is in this class.
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

    /**
     * One reusable single-character sequence. Allowed here because 1.21.1 draws immediately (the Font consumes the
     * sequence inside drawInBatch); render-state versions (1.21.6+) must allocate a fresh one per draw instead.
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
     * Queues one glyph into the GUI buffer source (no flush). {@code baseColor} must already be {@link #opaque}.
     * With a transform: alpha multiplied into the colour, offset, and rotation/scale around the pivot (fraction of
     * the {@code advance} x 9 px cell). Returns false when nothing was drawn (invisible).
     */
    static boolean draw(GuiGraphics gg, Font font, int codepoint, Style style, float x, float y, float advance,
                        int baseColor, boolean shadow, CharTransform tf) {
        int color = baseColor;
        int cp = codepoint;
        boolean moved = false;
        if (tf != null) {
            if (tf.alpha < 1f) {
                float a = (baseColor >>> 24) * tf.alpha;
                if (!(a >= MIN_ALPHA_BYTE)) {
                    return false;
                }
                color = (Math.min(255, Math.round(a)) << 24) | (baseColor & 0x00FFFFFF);
            }
            if (!(tf.scaleX >= MIN_SCALE) || !(tf.scaleY >= MIN_SCALE)) {
                return false;
            }
            if (tf.glyph != -1) {
                cp = tf.glyph;
            }
            moved = tf.dx != 0f || tf.dy != 0f || tf.hasScaleOrRotation();
        }
        SINGLE.set(cp, style);
        try {
            final Matrix4f pose = gg.pose().last().pose();
            if (!moved) {
                font.drawInBatch(SINGLE, x, y, color, shadow, pose, gg.bufferSource(), Font.DisplayMode.NORMAL, 0,
                        FULL_BRIGHT);
                return true;
            }
            // A scratch copy of the pose instead of PoseStack push/pop (which allocates a Pose per glyph): drawInBatch
            // consumes the matrix before it returns (1.21.1 draws immediately), so reusing it is safe.
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
            font.drawInBatch(SINGLE, x, y, color, shadow, m, gg.bufferSource(), Font.DisplayMode.NORMAL, 0,
                    FULL_BRIGHT);
            return true;
        } finally {
            SINGLE.clear();
        }
    }

    /** Ends the batch like every vanilla GuiGraphics draw call does (keeps z-order with later immediate draws). */
    static void flush(GuiGraphics gg) {
        gg.flush();
    }

    /** True when the pose is a plain z-translation, so GUI-space scissor rectangles line up with widget bounds. */
    static boolean poseIsScreenAligned(GuiGraphics gg) {
        Matrix4f m = gg.pose().last().pose();
        return m.m00() == 1f && m.m11() == 1f && m.m01() == 0f && m.m10() == 0f && m.m30() == 0f && m.m31() == 0f;
    }

    /**
     * Pushes a GUI-space scissor rectangle (intersected with the enclosing one). Glyphs still queued in the buffer
     * source must be flushed first, or they would be drawn with this scissor.
     */
    static void pushClip(GuiGraphics gg, int x0, int y0, int x1, int y1) {
        gg.enableScissor(x0, y0, x1, y1);
    }

    static void popClip(GuiGraphics gg) {
        gg.disableScissor();
    }

    /** Mutable one-codepoint sequence (see {@link #SINGLE}). */
    private static final class SingleChar implements FormattedCharSequence {
        private int codepoint;
        private Style style = Style.EMPTY;

        void set(int codepoint, Style style) {
            this.codepoint = codepoint;
            this.style = style == null ? Style.EMPTY : style;
        }

        void clear() {
            style = Style.EMPTY;
        }

        @Override
        public boolean accept(FormattedCharSink sink) {
            return sink.accept(0, style, codepoint);
        }
    }
}
