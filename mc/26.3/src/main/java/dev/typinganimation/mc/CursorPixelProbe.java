package dev.typinganimation.mc;

import com.mojang.blaze3d.platform.NativeImage;
import dev.typinganimation.core.CursorStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;

/** Dev-only framebuffer proof that a real, settled EditBox draws the requested caret geometry and color. */
final class CursorPixelProbe {
    private final Minecraft mc;
    private final EditBox box;
    private final CursorStyle style;
    private final int thickness;
    private volatile boolean pending, passed;
    private volatile String error = "no visible cursor captured";
    private int attempts;

    CursorPixelProbe(Minecraft mc, EditBox box, CursorStyle style, int thickness) {
        this.mc = mc;
        this.box = box;
        this.style = style;
        this.thickness = thickness;
    }

    boolean poll() {
        if (passed) return true;
        if (pending) return false;
        if (++attempts > 40) throw new AssertionError("Custom cursor " + style + ": " + error);
        pending = true;
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try (NativeImage pixels = image) { inspect(pixels); }
            catch (Throwable t) { error = t.toString(); }
            finally { pending = false; }
        });
        return passed;
    }

    private void inspect(NativeImage image) {
        int scale = (int) Math.round(mc.getWindow().getGuiScale());
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1, count = 0;
        for (int y = (box.getY() + 1) * scale; y < (box.getY() + box.getHeight() - 1) * scale; y++) {
            for (int x = (box.getX() + 1) * scale; x < (box.getX() + 16) * scale; x++) {
                int pixel = image.getPixel(x, y);
                int r = (pixel >>> 16) & 255, g = (pixel >>> 8) & 255, b = pixel & 255;
                // RED = FF7A90. BLOCK is alpha blended; the field is empty so no text can match this hue.
                boolean match = style == CursorStyle.BLOCK ? r > 60 && r > g * 1.5 && b > g && b < r
                        : r >= 254 && Math.abs(g - 122) <= 1 && Math.abs(b - 144) <= 1;
                if (match) {
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                    count++;
                }
            }
        }
        int expectedWidth = (style == CursorStyle.BAR ? thickness : mc.font.width("_")) * scale;
        int expectedHeight = (style == CursorStyle.UNDERLINE ? thickness : 11) * scale;
        int width = count == 0 ? 0 : maxX - minX + 1;
        int height = count == 0 ? 0 : maxY - minY + 1;
        passed = width == expectedWidth && height == expectedHeight && count == expectedWidth * expectedHeight;
        error = "expected colored rectangle " + expectedWidth + "x" + expectedHeight + ", found " + width + "x" + height + " (" + count + " pixels)";
    }
}
