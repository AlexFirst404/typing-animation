package dev.typinganimation.mc;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import dev.typinganimation.mixin.BookTestAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/** Real book editor input/render fixture. Only its absent world/player persistence is stubbed. */
final class BookSelfTest {
    private BookSelfTest() {}

    static BookEditScreen create() {
        Player owner = fixturePlayer();
        BookEditScreen book = new BookEditScreen(owner, new ItemStack(Items.WRITABLE_BOOK), InteractionHand.MAIN_HAND, net.minecraft.world.item.component.WritableBookContent.EMPTY) {
            @Override public void tick() { /* Freeze native blink phase; no world exists in this fixture. */ }
            @Override public void removed() { /* Never persist or send a packet from a fixture. */ }
        };
        ((BookTestAccessor) book).typinganimation$pages().set(0, "A real book page\nwith wrapped text for a proper editor test.");
        ((BookTestAccessor) book).typinganimation$pageEdit().setCursorToEnd();
        return book;
    }

    private static Player fixturePlayer() {
        try {
            // BookEditScreen's constructor needs only getName(). Bypass world-dependent entity construction in
            // this self-test-only fixture instead of creating a disposable server or faking editor behaviour.
            Class<?> type = Class.forName("sun.misc.Unsafe");
            var field = type.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Player) type.getMethod("allocateInstance", Class.class).invoke(field.get(null), FixturePlayer.class);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Book fixture player", error); }
    }

    private static char wideTitleCharacter;
    private static String wideTitle;
    private static int[] wideNativePixels, wideBackgroundPixels;

    /** Use the real font: not every Latin W is wider than the old 114px title clipping area. */
    static void prepareWideTitle(BookEditScreen book) {
        var cfg = dev.typinganimation.core.ConfigManager.get();
        cfg.enabled = false;
        cfg.typingSounds = false;
        cfg.appearStyle = dev.typinganimation.core.AppearStyle.FADE;
        cfg.durationMs = 2000;
        cfg.staggerMs = 0;
        cfg.maxStaggerMs = 0;
        cfg.smoothReflow = false;
        cfg.smoothCursor = false;
        var access = (BookTestAccessor) book;
        while (!access.typinganimation$title().isEmpty()) book.keyPressed(259, 0, 0);
        wideTitleCharacter = 0;
        for (char candidate : new char[]{'W', '\u0416', '\uFF37', '\u754C'}) {
            if (Minecraft.getInstance().font.width(String.valueOf(candidate).repeat(15)) > 114 + Minecraft.getInstance().font.width("_") + 2) {
                wideTitleCharacter = candidate;
                break;
            }
        }
        if (wideTitleCharacter == 0) throw new AssertionError("Fixture font has no wide title glyph");
        wideTitle = String.valueOf(wideTitleCharacter).repeat(15);
        for (int i = 0; i < 15; i++) book.charTyped(wideTitleCharacter, 0);
        if (!wideTitle.equals(access.typinganimation$title())) throw new AssertionError("Vanilla rejected wide title");
    }

    static void saveWideNativeAndRemoveLast(BookEditScreen book, int[] pixels) {
        wideNativePixels = pixels;
        book.keyPressed(259, 0, 0);
        dev.typinganimation.core.ConfigManager.get().enabled = true;
    }

    static void saveWideBackgroundAndTypeLast(BookEditScreen book, int[] pixels) {
        wideBackgroundPixels = pixels;
        if (!book.charTyped(wideTitleCharacter, 0) || !wideTitle.equals(((BookTestAccessor) book).typinganimation$title()))
            throw new AssertionError("Wide final title character was not accepted");
    }

    /** Checks actual new-glyph ink outside the former clipping area, not the clip implementation. */
    static void verifyWideTitleAnimation(int[] animated) {
        Minecraft mc = Minecraft.getInstance();
        int scale = (int) Math.round(mc.getWindow().getGuiScale());
        int width = 192 * scale;
        int end = 36 + (114 - mc.font.width(wideTitle + "_")) / 2 + mc.font.width(wideTitle);
        int expected = 0, visible = 0;
        for (int y = scale; y < 9 * scale; y++) for (int x = 150 * scale; x < end * scale; x++) {
            int i = y * width + x;
            if (dark(wideNativePixels[i], 80) && !dark(wideBackgroundPixels[i], 180)) {
                expected++;
                if (dark(animated[i], 190)) visible++;
            }
        }
        if (expected == 0 || visible == 0) {
            dumpWideTitle("native", wideNativePixels, width);
            dumpWideTitle("background", wideBackgroundPixels, width);
            dumpWideTitle("animated", animated, width);
            String geometry = " [U+" + Integer.toHexString(wideTitleCharacter) + ", width="
                    + mc.font.width(wideTitle) + ", underscore=" + mc.font.width("_")
                    + ", end=" + end + ", scale=" + scale + ", expected=" + expected + ", visible=" + visible + "]";
            if (expected == 0) throw new AssertionError("Wide title fixture has no native ink beyond 114px" + geometry);
            throw new AssertionError("Animated wide title glyph was clipped beyond the page text width" + geometry);
        }
    }

    static void verifyWideTitleFidelity(int[] pixels) {
        if (!java.util.Arrays.equals(wideNativePixels, pixels))
            throw new AssertionError("Settled wide title differs from native title pixels");
        wideNativePixels = wideBackgroundPixels = null;
    }

    private static boolean dark(int pixel, int threshold) {
        return (pixel & 255) < threshold && ((pixel >>> 8) & 255) < threshold && ((pixel >>> 16) & 255) < threshold;
    }

    private static void dumpWideTitle(String name, int[] pixels, int width) {
        java.nio.file.Path directory = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("screenshots/typinganimation-selftest");
        try (NativeImage image = new NativeImage(width, pixels.length / width, false)) {
            java.nio.file.Files.createDirectories(directory);
            for (int i = 0; i < pixels.length; i++) image.setPixel(i % width, i / width, pixels[i]);
            image.writeToFile(directory.resolve("wide-title-" + name + ".png"));
        } catch (java.io.IOException error) { throw new IllegalStateException("Wide-title diagnostic image", error); }
    }

    static java.util.concurrent.CompletableFuture<int[]> pixels(BookEditScreen book) { return pixels(book, false); }
    static java.util.concurrent.CompletableFuture<int[]> wideTitlePixels(BookEditScreen book) { return pixels(book, true); }

    private static java.util.concurrent.CompletableFuture<int[]> pixels(BookEditScreen book, boolean signing) {
        Minecraft mc = Minecraft.getInstance();
        int scale = (int) Math.round(mc.getWindow().getGuiScale());
        int left = ((book.width - 192) / 2 + (signing ? 0 : 35)) * scale, top = (signing ? 49 : 31) * scale;
        int width = (signing ? 192 : 116) * scale, height = (signing ? 11 : 130) * scale;
        int[] result = new int[width * height];
        java.util.concurrent.CompletableFuture<int[]> future = new java.util.concurrent.CompletableFuture<>();
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), captured -> {
          try (NativeImage image = captured) {
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                result[y * width + x] = image.getPixel(left + x, top + y);
            future.complete(result);
          } catch (Throwable error) { future.completeExceptionally(error); }
        });
        return future;
    }

    private static final class FixturePlayer extends Player {
        private FixturePlayer() { super(null, BlockPos.ZERO, 0, new GameProfile(new UUID(0, 1), "BookSelfTest")); }
        @Override public Component getName() { return Component.literal("BookSelfTest"); }
        @Override public net.minecraft.world.level.GameType gameMode() { return net.minecraft.world.level.GameType.SURVIVAL; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
    }
}
