package dev.typinganimation.mc;

import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.CursorBlinkState;
import dev.typinganimation.core.FieldKind;
import dev.typinganimation.core.TypingConfig;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;

/** Render-only integration for the legacy vanilla book editor. Editing, wrapping and selection remain vanilla. */
public final class BookAnimation {
    private final WidgetAnimator animation = new WidgetAnimator(FieldKind.MULTILINE);
    private Object owner;
    private String value = "", layoutValue;
    private Font layoutFont;
    private int[] lineStarts = {0};
    private int page = -1, originX, cursor, frameTick;
    private boolean signing, selection;
    public long frames, animatedFrames, ghostFrames, caretGlides, pageResets;

    public void begin(Object owner, Font font, String text, int page, boolean signing, int cursor,
                      boolean selection, int screenWidth, int frameTick) {
        animation.active = false;
        this.owner = owner;
        try {
            TypingConfig cfg = ConfigManager.get();
            if (!cfg.enabled || !cfg.animateMultiline || font.isBidirectional()) {
                animation.disable();
                this.page = -1;
                return;
            }
            if (animation.failed) return;
            if (this.page != page || this.signing != signing) {
                animation.disable();
                this.page = page;
                this.signing = signing;
                pageResets++;
            }
            value = text == null ? "" : text;
            this.cursor = Math.max(0, Math.min(cursor, value.length()));
            this.selection = selection;
            this.frameTick = frameTick;
            originX = (screenWidth - 192) / 2 + 36;
            if (layoutFont != font || !value.equals(layoutValue)) {
                layoutFont = font;
                layoutValue = value;
                ArrayList<Integer> starts = new ArrayList<>();
                font.getSplitter().splitLines(value, 114, Style.EMPTY, true,
                        (style, begin, end) -> starts.add(begin));
                lineStarts = starts.isEmpty() ? new int[]{0} : starts.stream().mapToInt(Integer::intValue).toArray();
            }
            long now = Util.getMillis();
            animation.font = font;
            animation.lastValue = value;
            animation.shadow = false;
            animation.originX = originX;
            animation.originY = signing ? 50 : 32;
            animation.caretIndex = this.cursor;
            animation.caretQueried = false;
            animation.hasCaretCandidate = false;
            animation.selection = selection;
            // Legacy titles can exceed the 114px page column (15 wide Unicode characters are valid).
            animation.setClip(signing ? originX - 36 : originX, signing ? 45 : 32,
                    signing ? originX + 156 : originX + 114, signing ? 62 : 160);
            boolean animate = animation.touch(now);
            animation.cursorBlink.observe(value, now);
            animation.state.beginFrame(now, animation.originX, animation.originY, 0);
            animation.state.sync(value, animate, cfg);
            animation.idle = animation.state.isIdle(cfg) && !TypingRenderer.forcePerChar;
            animation.active = true;
            frames++;
            if (!animation.idle) animatedFrames++;
        } catch (Throwable error) { TypingRenderer.fail(animation, owner, error); }
    }

    public int line(GuiGraphics graphics, Font font, Component text, int x, int y, int color, boolean shadow) {
        if (animation.active && !signing && y >= 32 && (y - 32) % 9 == 0) {
            int line = (y - 32) / 9;
            String contents = text.getString();
            if (line < lineStarts.length && value.startsWith(contents, lineStarts[line])) {
                try {
                    return TypingRenderer.drawSequence(graphics, font, text.getVisualOrderText(), x, y, color,
                            shadow, animation, lineStarts[line], contents.length());
                } catch (Throwable error) { TypingRenderer.fail(animation, owner, error); }
            }
        }
        return graphics.drawString(font, text, x, y, color, shadow);
    }

    /** The native title draw combines the title with a black/grey underscore. Keep that geometry exactly. */
    public int title(GuiGraphics graphics, Font font, FormattedCharSequence original, int x, int y,
                     int color, boolean shadow) {
        if (!animation.active || !signing) return graphics.drawString(font, original, x, y, color, shadow);
        try {
            TypingRenderer.drawSequence(graphics, font, FormattedCharSequence.forward(value, Style.EMPTY),
                    x, y, color, shadow, animation, 0, value.length());
            int target = x + font.width(value);
            drawCaret(graphics, font, target, y, true, true);
            return x + font.width(original) + (shadow ? 1 : 0);
        } catch (Throwable error) {
            TypingRenderer.fail(animation, owner, error);
            return graphics.drawString(font, original, x, y, color, shadow);
        }
    }

    /** Receives the exact local cursor position calculated by vanilla's display cache. */
    public boolean caret(GuiGraphics graphics, Font font, int localX, int localY, boolean append) {
        if (!animation.active || signing) return false;
        try {
            drawCaret(graphics, font, originX + localX, 32 + localY, append, false);
            return true;
        } catch (Throwable error) { TypingRenderer.fail(animation, owner, error); return false; }
    }

    private void drawCaret(GuiGraphics graphics, Font font, int x, int y, boolean append, boolean title) {
        TypingConfig cfg = ConfigManager.get();
        long now = Util.getMillis();
        boolean nativeVisible = frameTick / 6 % 2 == 0;
        boolean visible = animation.cursorBlink.visible(nativeVisible, (long) frameTick * 50L, now, cfg, true);
        float dx = TypingRenderer.caretOffset(animation, x, selection);
        float dy = TypingRenderer.caretYOffset(animation, y, selection);
        if (dx != 0 || dy != 0) caretGlides++;
        // Signing's uncustomized blink alternates black and grey instead of hiding the underscore.
        boolean greyTitle = title && !CursorAppearance.custom(cfg) && !nativeVisible;
        if (!visible && !greyTitle) return;
        int color = animation.cursorBlink.tint(greyTitle ? 0xFFAAAAAA : 0xFF000000);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(dx, dy, 0f);
            if (!CursorAppearance.draw(graphics, cfg, x, y - 1, 10,
                    animation.cursorCellWidth(cursor), color, !append)) {
                color = CursorAppearance.color(cfg, color);
                if (append) graphics.drawString(font, "_", x, y, color, false);
                else graphics.fill(x, y - 1, x + 1, y + 9, color);
            }
        } finally { graphics.pose().popPose(); }
    }

    public void end(GuiGraphics graphics) {
        if (!animation.active) return;
        try {
            if (animation.state.ghostCount() > 0) ghostFrames++;
            TypingRenderer.drawGhosts(graphics, animation);
        } catch (Throwable error) { TypingRenderer.fail(animation, owner, error); }
        finally { TypingRenderer.endFrame(animation, owner); }
    }

    public int ghostCount() { return animation.state.ghostCount(); }
}
