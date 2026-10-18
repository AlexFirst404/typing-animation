package dev.typinganimation.mc;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.TypingConfig;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;

/**
 * Logic behind {@code MultiLineEditBoxMixin} ({@code MultiLineEditBox#renderContents}, drawn inside the scroll
 * widget's scissor and scroll translation). Every visible line is drawn with {@code drawString(Font, String, ...)}
 * of a {@code value.substring(begin, end)}; the substring arguments (captured just before each draw) give the
 * absolute index of the line's first char, so the protected {@code MultilineTextField.StringView} record is never
 * needed. Insert caret = {@code fill(x, y-1, x+1, y+10, CURSOR_COLOR)}, append caret = {@code "_"} in CURSOR_COLOR.
 */
public final class MultiLineHooks {
    /** MultiLineEditBox.CURSOR_INSERT_COLOR (also used for the "_" append caret). */
    private static final int CURSOR_COLOR = -3092272;

    private MultiLineHooks() {
    }

    public static void begin(MultiLineEditBox box, WidgetAnimator a, Font font, String value, int cursor,
                             boolean selection, int originX, int originY, double scrollAmount) {
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
            final long now = Util.getMillis();
            a.font = font;
            a.lastValue = value;
            a.caretOffsetY = 0f;
            a.shadow = true;
            a.originX = originX;
            a.originY = originY;
            a.clip = false; // already inside the scroll widget's scissor
            a.subValid = false;
            a.caretIndex = cursor;
            a.selection = selection;
            a.hasCaretCandidate = false;
            a.caretQueried = false;
            final boolean animate = a.touch(now) && a.animate(cfg, box.isFocused());
            // Content is laid out in unscrolled coordinates (the scroll is a pose translation), so scrolling only
            // makes the frame non-idle.
            a.state.beginFrame(now, originX, originY, (int) Math.round(scrollAmount * 8.0));
            a.state.sync(value, animate, cfg);
            a.idle = a.state.isIdle(cfg) && !TypingRenderer.forcePerChar;
            a.active = true;
        } catch (Throwable t) {
            TypingRenderer.fail(a, box, t);
        }
    }

    /** {@code String#substring(begin, end)} inside renderContents: remember the range the next draw call shows. */
    public static void substring(WidgetAnimator a, int begin, int end) {
        a.subBegin = begin;
        a.subEnd = end;
        a.subValid = true;
    }

    public static int drawString(MultiLineEditBox box, WidgetAnimator a, GuiGraphics gg, Font font, String text,
                                 int x, int y, int color) {
        if (a.active && text != null) {
            if (color == CURSOR_COLOR && "_".equals(text)) {
                float dx = caretDx(box, a, x, y);
                float dy = a.caretOffsetY;
                TypingConfig cfg = ConfigManager.get();
                boolean custom = a.active && a.animate(cfg, box.isFocused()) && CursorAppearance.custom(cfg);
                if (dx != 0f || dy != 0f || custom) {
                    if (dx != 0f || dy != 0f) TypingRenderer.STATS.caretGlides++;
                    PoseStack pose = gg.pose();
                    pose.pushPose();
                    try {
                        pose.translate(dx, dy, 0f);
                        if (custom) {
                            if (CursorAppearance.draw(gg, cfg, x, y - 1, 11, Math.max(1, font.width(text)), color, false)) {
                                return x + font.width(text) + 1;
                            }
                            color = CursorAppearance.color(cfg, color);
                        }
                        return gg.drawString(font, text, x, y, color);
                    } finally {
                        pose.popPose();
                    }
                }
            } else if (a.subValid) {
                a.subValid = false;
                final int start = a.subBegin;
                // Bidirectional shaping (right-to-left languages) reorders the string: keep vanilla there.
                if (text.length() == a.subEnd - a.subBegin && start >= 0 && !font.isBidirectional()) {
                    try {
                        return TypingRenderer.drawPlain(gg, font, text, x, y, color, true, a, start);
                    } catch (Throwable t) {
                        TypingRenderer.fail(a, box, t);
                    }
                }
            }
        }
        return gg.drawString(font, text, x, y, color);
    }

    public static void fill(MultiLineEditBox box, WidgetAnimator a, GuiGraphics gg, int x1, int y1, int x2, int y2,
                            int color) {
        float dx = 0f, dy = 0f;
        TypingConfig cfg = ConfigManager.get();
        boolean cursor = a.active && color == CURSOR_COLOR;
        boolean custom = cursor && a.animate(cfg, box.isFocused()) && CursorAppearance.custom(cfg);
        if (cursor) {
            dx = caretDx(box, a, x1, y1 + 1);
            dy = a.caretOffsetY;
        }
        if (dx == 0f && dy == 0f && !custom) {
            gg.fill(x1, y1, x2, y2, color);
            return;
        }
        if (dx != 0f || dy != 0f) TypingRenderer.STATS.caretGlides++;
        PoseStack pose = gg.pose();
        pose.pushPose();
        try {
            pose.translate(dx, dy, 0f);
            if (custom) {
                if (CursorAppearance.draw(gg, cfg, x1, Math.min(y1, y2), Math.abs(y2 - y1),
                        a.cursorCellWidth(a.caretIndex), color, true)) return;
                color = CursorAppearance.color(cfg, color);
            }
            gg.fill(x1, y1, x2, y2, color);
        } finally {
            pose.popPose();
        }
    }

    public static void end(MultiLineEditBox box, WidgetAnimator a, GuiGraphics gg) {
        if (!a.active) {
            return;
        }
        try {
            if (!a.caretQueried && box.isFocused() && a.hasCaretCandidate) {
                // caret blinking off: keep its glide running
                caretDx(box, a, a.caretCandidateX, a.caretCandidateY);
            }
            TypingRenderer.drawGhosts(gg, a); // no extra clip: already inside the scroll widget's scissor
        } catch (Throwable t) {
            TypingRenderer.fail(a, box, t);
        } finally {
            TypingRenderer.endFrame(a, box);
        }
    }

    /**
     * Smooth both coordinates when changing lines. A selection stays aligned to vanilla's highlight; the second
     * caret at a mid-word wrap remains at its native position, and must not replace the first query's target.
     */
    private static float caretDx(MultiLineEditBox box, WidgetAnimator a, int targetX, int lineY) {
        a.caretOffsetY = 0f;
        if (a.caretQueried) {
            return 0f;
        }
        try {
            final float dx = TypingRenderer.caretOffset(a, targetX, a.selection);
            a.caretOffsetY = TypingRenderer.caretYOffset(a, lineY, a.selection);
            if (dx != 0f || a.caretOffsetY != 0f) {
                TypingRenderer.STATS.multilineCaretGlides++;
            }
            return dx;
        } catch (Throwable t) {
            a.caretOffsetY = 0f;
            TypingRenderer.fail(a, box, t);
            return 0f;
        }
    }
}
