package dev.typinganimation.mc;

import dev.typinganimation.core.CursorColor;
import dev.typinganimation.core.CursorStyle;
import dev.typinganimation.core.TypingConfig;
import net.minecraft.client.gui.GuiGraphics;

/** Shared cursor geometry; callers preserve vanilla blinking, clipping and selection behaviour. */
public final class CursorAppearance {
    private CursorAppearance() { }

    public static boolean custom(TypingConfig cfg) {
        return cfg.cursorStyle != CursorStyle.VANILLA || cfg.cursorColor != CursorColor.VANILLA
                || cfg.cursorWidth != 1;
    }

    public static int color(TypingConfig cfg, int vanillaColor) {
        int rgb = cfg.cursorColor.rgb();
        if (rgb < 0) return vanillaColor;
        int alpha = vanillaColor >>> 24;
        return ((alpha == 0 ? 255 : alpha) << 24) | rgb;
    }

    /** True when custom geometry was drawn; false means the caller should draw its native caret. */
    public static boolean draw(GuiGraphics g, TypingConfig cfg, int x, int top, int height,
                               int cellWidth, int vanillaColor, boolean insert) {
        CursorStyle style = cfg.cursorStyle;
        if (style == CursorStyle.VANILLA) {
            if (!insert || cfg.cursorWidth == 1) return false;
            style = CursorStyle.BAR;
        }
        int h = Math.max(1, height), w = Math.max(1, cellWidth);
        int thickness = Math.max(1, Math.min(4, cfg.cursorWidth));
        int argb = color(cfg, vanillaColor);
        if ((argb >>> 24) == 0) argb |= 0xFF000000;
        switch (style) {
            case BAR:
                g.fill(x, top, x + thickness, top + h, argb);
                break;
            case UNDERLINE:
                g.fill(x, top + h - Math.min(thickness, h), x + w, top + h, argb);
                break;
            case BLOCK:
                // Keep the letter under the caret readable.
                int alpha = Math.max(1, (argb >>> 24) * 2 / 5);
                g.fill(x, top, x + w, top + h, (alpha << 24) | (argb & 0x00FFFFFF));
                break;
            default:
                return false;
        }
        return true;
    }
}

