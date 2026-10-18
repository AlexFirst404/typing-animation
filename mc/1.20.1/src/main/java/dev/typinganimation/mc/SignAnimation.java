package dev.typinganimation.mc;

import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.FieldAnimationState;
import dev.typinganimation.core.FieldKind;
import dev.typinganimation.core.TypingConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Four independent text animations and one two-dimensional caret for a sign editor. Coordinates remain in
 * vanilla's sign-local space: the normal/hanging sign scale, dye/glow color, selection and input stay vanilla.
 */
public final class SignAnimation {
    private final WidgetAnimator[] lines = {
            new WidgetAnimator(FieldKind.SIGN), new WidgetAnimator(FieldKind.SIGN),
            new WidgetAnimator(FieldKind.SIGN), new WidgetAnimator(FieldKind.SIGN)
    };
    private final FieldAnimationState caret = new FieldAnimationState();
    private Object owner;
    private GuiGraphics currentGraphics;
    private int lineHeight;
    private Font widthFont;
    private String widthValue;
    private int widthCursor = -1, fullWidth, prefixWidth, caretCellWidth = 6;
    private boolean active, failed;
    private float caretDx, caretDy;
    /** Rendered sign frames and animation evidence used by the dev self-test/showcase. */
    public long frames, animatedFrames, caretGlides, verticalCaretGlides, ghostFrames;

    public void begin(Object owner, GuiGraphics graphics, Font font, String[] messages,
                      int lineHeight, int focusedLine, int cursorPos, boolean selection) {
        this.owner = owner;
        this.currentGraphics = graphics;
        this.lineHeight = lineHeight;
        active = false;
        caretDx = caretDy = 0;
        try {
            TypingConfig cfg = ConfigManager.get();
            // Shaped/bidirectional text keeps vanilla's exact visual/logical cursor behavior.
            if (failed || !cfg.enabled || !cfg.animateSigns || font.isBidirectional()) {
                for (WidgetAnimator a : lines) {
                    a.disable();
                }
                caret.reset();
                return;
            }
            long now = net.minecraft.Util.getMillis();
            for (int i = 0; i < lines.length; i++) {
                WidgetAnimator a = lines[i];
                String value = i < messages.length && messages[i] != null ? messages[i] : "";
                a.active = false;
                a.font = font;
                a.shadow = false;
                a.originX = 0;
                a.originY = 0;
                a.clip = false;
                a.caretIndex = -1;
                boolean animate = a.touch(now) && a.animate(cfg, true);
                a.state.beginFrame(now, 0, 0, 0);
                a.state.sync(value, animate, cfg);
                a.idle = a.state.isIdle(cfg) && !TypingRenderer.forcePerChar;
                a.active = true;
                if (!a.idle) animatedFrames++;
            }
            String value = messages[Math.max(0, Math.min(focusedLine, messages.length - 1))];
            if (value == null) value = "";
            int cursor = Math.max(0, Math.min(cursorPos, value.length()));
            if (font != widthFont || value != widthValue) {
                widthFont = font;
                widthValue = value;
                fullWidth = font.width(value);
                widthCursor = -1;
            }
            if (cursor != widthCursor) {
                widthCursor = cursor;
                prefixWidth = cursor == value.length() ? fullWidth : font.width(value.substring(0, cursor));
                caretCellWidth = cursor == value.length() ? font.width("_")
                        : font.width(value.substring(cursor, cursor + Character.charCount(value.codePointAt(cursor))));
            }
            int targetX = prefixWidth - fullWidth / 2;
            int targetY = focusedLine * lineHeight - 2 * lineHeight;
            // A fixed origin permits both horizontal movement and gliding between sign lines. Continue during
            // blink-off frames; a selection snaps so the caret remains exactly on the selection's edge.
            caret.beginFrame(now, 0, 0, 0);
            caret.sync("", !selection, cfg);
            caretDx = caret.caretX(targetX, cfg) - targetX;
            caretDy = caret.caretY(targetY, cfg) - targetY;
            if (caretDx != 0 || caretDy != 0) caretGlides++;
            if (caretDy != 0) verticalCaretGlides++;
            active = true;
            frames++;
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Replaces only the line glyph draw; vanilla still owns centering, background and selection. */
    public String line(Font font, String value, int x, int y, int color, boolean shadow) {
        if (!active || lineHeight <= 0 || value == null) return value;
        int line = (y + 2 * lineHeight) / lineHeight;
        if (line < 0 || line >= lines.length) return value;
        try {
            TypingRenderer.drawPlain(currentGraphics, font, value, x, y, color, shadow, lines[line], 0);
            return "";
        } catch (Throwable t) {
            fail(t);
            return value;
        }
    }

    /** True when this helper drew the caret. Native geometry is retained unless customization requests it. */
    public boolean drawCaret(GuiGraphics graphics, Font font, int x, int y, int color,
                             int textHeight, boolean insert) {
        if (!active) return false;
        boolean pushed = false;
        try {
            TypingConfig cfg = ConfigManager.get();
            graphics.pose().pushPose();
            pushed = true;
            graphics.pose().translate(caretDx, caretDy, 0f);
            if (!CursorAppearance.draw(graphics, cfg, x, y - 1, textHeight + 1, caretCellWidth, color, insert)) {
                int resolved = CursorAppearance.color(cfg, color);
                if (insert) graphics.fill(x, y - 1, x + 1, y + textHeight, resolved);
                else graphics.drawString(font, "_", x, y, resolved, false);
            }
            return true;
        } catch (Throwable t) {
            fail(t);
            return false;
        } finally {
            if (pushed) graphics.pose().popPose();
        }
    }

    /** Deleted glyphs use the same current sign matrix as the line glyphs. */
    public void end(GuiGraphics graphics, Font font) {
        if (!active) {
            currentGraphics = null;
            return;
        }
        try {
            for (WidgetAnimator a : lines) {
                if (a.active) {
                    if (a.state.ghostCount() > 0) ghostFrames++;
                    TypingRenderer.drawGhosts(graphics, a);
                    TypingRenderer.endFrame(a, owner);
                }
            }
            caret.endFrame();
        } catch (Throwable t) {
            fail(t);
        } finally {
            active = false;
            currentGraphics = null;
        }
    }

    private void fail(Throwable error) {
        failed = true;
        active = false;
        TypingRenderer.fail(lines[0], owner, error);
    }
}
