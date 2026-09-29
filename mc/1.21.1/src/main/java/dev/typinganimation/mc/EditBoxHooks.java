package dev.typinganimation.mc;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.TypingConfig;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;

/**
 * Logic behind {@code EditBoxMixin}: one frame of {@code EditBox#renderWidget} is
 * HEAD ({@link #begin}) -> visible substring ({@link #visible}) -> text segment draws ({@link #drawText}, 0-2 calls:
 * before and after the cursor) -> caret ({@link #drawCaretString} / {@link #fillCaret}, only while the caret blinks
 * on) -> RETURN ({@link #end}). Everything falls back to the vanilla call when the frame is not active.
 */
public final class EditBoxHooks {
    /** Scroll jumps (in chars) larger than the edit that caused them by more than this snap instead of gliding. */
    private static final int SCROLL_SNAP_CHARS = 3;

    private EditBoxHooks() {
    }

    public static void begin(EditBox box, WidgetAnimator a, Font font, String value, int displayPos, int cursorPos,
                             int highlightPos) {
        a.active = false;
        try {
            final TypingConfig cfg = ConfigManager.get();
            if (!cfg.enabled) {
                a.disable();
                return;
            }
            if (a.failed) {
                return;
            }
            if (!box.isVisible()) {
                a.hidden(); // what changes while hidden must not animate when the box shows again
                return;
            }
            final long now = Util.getMillis();
            final boolean bordered = box.isBordered();
            final int x0 = bordered ? box.getX() + 4 : box.getX();
            final int y0 = bordered ? box.getY() + (box.getHeight() - 8) / 2 : box.getY();
            a.font = font;
            a.originX = x0;
            a.originY = y0;
            a.displayPos = displayPos;
            a.cursorPos = cursorPos;
            a.cursorRel = cursorPos - displayPos;
            a.visibleKnown = false; // captured from renderWidget's own plainSubstrByWidth call (see visible)
            a.visibleLen = 0;
            a.cursorInView = false;
            a.selection = highlightPos != cursorPos;
            a.segment = 0;
            a.seg1Drawn = false;
            a.seg1End = x0;
            a.caretQueried = false;
            a.caretIndex = -1;
            setClip(box, a, cfg, bordered, x0, y0);
            boolean animate = a.touch(now) && a.animate(cfg, box.isFocused());
            // A big scroll jump that the edit does not explain (Home/End, a click or a word jump on a long text, or
            // vanilla jumping back a whole field when the caret reaches the left edge) would make the few chars that
            // stay visible slide across the whole field over the ones that pop in: snap instead. Scrolls caused by
            // the edit itself (typing at the end, pasting, deleting at the start) keep gliding.
            if (animate && a.lastValue != null && a.lastDisplayPos >= 0) {
                final int scroll = Math.abs(displayPos - a.lastDisplayPos);
                if (scroll > SCROLL_SNAP_CHARS && scroll > editSize(a.lastValue, value) + SCROLL_SNAP_CHARS) {
                    animate = false;
                }
            }
            a.lastValue = value;
            a.lastDisplayPos = displayPos;
            a.state.beginFrame(now, x0, y0, displayPos);
            a.state.sync(value, animate, cfg);
            a.idle = a.state.isIdle(cfg) && !TypingRenderer.forcePerChar;
            a.active = true;
        } catch (Throwable t) {
            TypingRenderer.fail(a, box, t);
        }
    }

    /**
     * Clip rectangle for travelling glyphs (appear transforms) and ghosts. A bordered box clips to its inner area:
     * the frame hides what leaves it. An unbordered one (chat, anvil, creative search) has no frame and is often
     * just one line tall, so it only clips horizontally and leaves vertical room for the furthest style offset
     * (9 px x intensity: DROP, FLY_UP, FALL) above and below the line. The text line itself is never clipped.
     */
    private static void setClip(EditBox box, WidgetAnimator a, TypingConfig cfg, boolean bordered, int x0, int y0) {
        final int bx = box.getX();
        final int by = box.getY();
        final int bw = box.getWidth();
        final int bh = box.getHeight();
        int cx0, cy0, cx1, cy1;
        if (bordered) {
            cx0 = bx + 1;
            cy0 = by + 1;
            cx1 = bx + bw - 1;
            cy1 = by + bh - 1;
        } else {
            final int pad = (int) Math.ceil(GlyphDrawer.LINE_HEIGHT * cfg.intensity) + 2;
            cx0 = bx;
            cx1 = bx + bw;
            cy0 = Math.min(by, y0) - pad;
            cy1 = Math.max(by + bh, y0 + (int) GlyphDrawer.LINE_HEIGHT) + pad;
        }
        cx0 = Math.min(cx0, x0 - 1);
        cy0 = Math.min(cy0, y0 - 1);
        cx1 = Math.max(cx1, x0 + box.getInnerWidth() + 1);
        cy1 = Math.max(cy1, y0 + (int) GlyphDrawer.LINE_HEIGHT + 1);
        a.setClip(cx0, cy0, cx1, cy1);
    }

    /** Size of the edit between two values (chars removed or inserted, whichever is more; common prefix/suffix). */
    static int editSize(String old, String cur) {
        if (old == cur) {
            return 0;
        }
        final int ol = old.length();
        final int nl = cur.length();
        final int min = Math.min(ol, nl);
        int p = 0;
        while (p < min && old.charAt(p) == cur.charAt(p)) {
            p++;
        }
        int s = 0;
        while (s < min - p && old.charAt(ol - 1 - s) == cur.charAt(nl - 1 - s)) {
            s++;
        }
        return Math.max(ol - p - s, nl - p - s);
    }

    /** The visible substring {@code renderWidget} computed this frame (captured from its plainSubstrByWidth call). */
    public static void visible(WidgetAnimator a, String visible) {
        if (a.active && visible != null) {
            setVisible(a, visible.length());
            TypingRenderer.STATS.visibleCaptures++;
        }
    }

    private static void setVisible(WidgetAnimator a, int len) {
        a.visibleLen = len;
        a.cursorInView = a.cursorRel >= 0 && a.cursorRel <= len;
        a.visibleKnown = true;
    }

    /** Fallback when the plainSubstrByWidth capture did not apply: compute the visible substring like vanilla. */
    private static void ensureVisible(EditBox box, WidgetAnimator a) {
        if (!a.visibleKnown) {
            String value = a.lastValue == null ? "" : a.lastValue;
            int from = Math.max(0, Math.min(a.displayPos, value.length()));
            setVisible(a, a.font.plainSubstrByWidth(value.substring(from), box.getInnerWidth()).length());
        }
    }

    /** Replacement of the two text-segment {@code drawString} calls (5-arg vanilla or 6-arg NeoForge). */
    public static int drawText(EditBox box, WidgetAnimator a, GuiGraphics gg, Font font, FormattedCharSequence text,
                               int x, int y, int color, boolean shadow, boolean sixArg) {
        if (a.active) {
            try {
                ensureVisible(box, a);
                final int seg = a.segment++;
                int start = -1;
                int len = -1;
                if (seg == 0) {
                    start = a.displayPos;
                    len = a.cursorInView ? a.cursorRel : a.visibleLen;
                } else if (seg == 1 && a.cursorInView) {
                    start = a.cursorPos;
                    len = a.visibleLen - a.cursorRel;
                }
                if (start >= 0) {
                    a.shadow = shadow;
                    int r = TypingRenderer.drawSequence(gg, font, text, x, y, color, shadow, a, start, len);
                    if (seg == 0) {
                        a.seg1Drawn = true;
                        a.seg1End = r;
                    }
                    return r;
                }
            } catch (Throwable t) {
                TypingRenderer.fail(a, box, t);
            }
        }
        return sixArg ? gg.drawString(font, text, x, y, color, shadow) : gg.drawString(font, text, x, y, color);
    }

    /** Replacement of the append-mode caret {@code drawString(font, "_", ...)}: drawn at the smoothed x. */
    public static int drawCaretString(EditBox box, WidgetAnimator a, GuiGraphics gg, Font font, String text, int x,
                                      int y, int color, boolean shadow, boolean sixArg) {
        final float dx = caretDx(box, a, x);
        if (dx == 0f) {
            return sixArg ? gg.drawString(font, text, x, y, color, shadow) : gg.drawString(font, text, x, y, color);
        }
        TypingRenderer.STATS.caretGlides++;
        PoseStack pose = gg.pose();
        pose.pushPose();
        try {
            pose.translate(dx, 0f, 0f);
            return sixArg ? gg.drawString(font, text, x, y, color, shadow) : gg.drawString(font, text, x, y, color);
        } finally {
            pose.popPose();
        }
    }

    /** Replacement of the insert-mode caret {@code fill(RenderType.guiOverlay(), ...)}: drawn at the smoothed x. */
    public static void fillCaret(EditBox box, WidgetAnimator a, GuiGraphics gg, RenderType type, int x1, int y1,
                                 int x2, int y2, int color) {
        final float dx = caretDx(box, a, x1);
        if (dx == 0f) {
            gg.fill(type, x1, y1, x2, y2, color);
            return;
        }
        TypingRenderer.STATS.caretGlides++;
        PoseStack pose = gg.pose();
        pose.pushPose();
        try {
            pose.translate(dx, 0f, 0f);
            gg.fill(type, x1, y1, x2, y2, color);
        } finally {
            pose.popPose();
        }
    }

    /**
     * Caret offset for this frame. With a selection the caret stays on its target: vanilla draws the selection
     * highlight from the unsmoothed caret x, so a gliding caret would lag inside the (colour-inverting) highlight.
     */
    private static float caretDx(EditBox box, WidgetAnimator a, int targetX) {
        if (!a.active) {
            return 0f;
        }
        try {
            return TypingRenderer.caretOffset(a, targetX, a.selection);
        } catch (Throwable t) {
            TypingRenderer.fail(a, box, t);
            return 0f;
        }
    }

    public static void end(EditBox box, WidgetAnimator a, GuiGraphics gg, String value, int cursorPos, int maxLength) {
        if (!a.active) {
            return;
        }
        try {
            // Keep the caret glide running while the caret blinks off (it is only queried when drawn otherwise).
            if (!a.caretQueried && box.isFocused()) {
                ensureVisible(box, a);
                if (a.cursorInView) {
                    int l = a.seg1Drawn ? a.seg1End : (int) a.originX;
                    boolean insert = cursorPos < value.length() || value.length() >= maxLength;
                    TypingRenderer.caretOffset(a, insert ? l - 1 : l, a.selection);
                }
            }
            TypingRenderer.drawGhosts(gg, a);
        } catch (Throwable t) {
            TypingRenderer.fail(a, box, t);
        } finally {
            TypingRenderer.endFrame(a, box);
        }
    }
}
