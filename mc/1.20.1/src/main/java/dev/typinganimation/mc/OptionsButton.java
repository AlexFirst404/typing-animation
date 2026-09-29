package dev.typinganimation.mc;

import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.TypingAnimationMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The "Typing Animation..." button of the vanilla Options screen (docs/SPEC.md section 6). The 1.20.x options grid
 * has no room for another row on a 240 px tall screen, so this group uses the SPEC fallback: a compact button in the
 * top-left corner (x=6, y=6, height 20, width = text width + 16), clear of the centred title (y=15) and of the grid
 * (which starts at y >= 28).
 */
public final class OptionsButton {
    public static final String KEY = "typinganimation.options.button";
    static final int X = 6;
    static final int Y = 6;
    static final int HEIGHT = 20;

    /** Mixed into {@link OptionsScreen}: the screen it returns to (needed to open a fresh copy of it). */
    public interface ParentAccess {
        Screen typinganimation$lastScreen();
    }

    private OptionsButton() {
    }

    /** The button for this Options screen, or null when the setting is off (or on failure). */
    public static Button create(Screen optionsScreen, Font font) {
        try {
            if (!ConfigManager.get().optionsButton) {
                return null;
            }
            Component label = Component.translatable(KEY);
            return Button.builder(label, b -> Minecraft.getInstance().setScreen(new ConfigScreen(optionsScreen)))
                    .bounds(X, Y, font.width(label) + 16, HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("typinganimation.description")))
                    .build();
        } catch (Throwable e) {
            TypingAnimationMod.LOGGER.warn("[{}] Could not add the Options screen button", TypingAnimationMod.MOD_ID,
                    e);
            return null;
        }
    }

    /** Our button among the screen's widgets, or null. */
    public static Button find(Screen screen) {
        for (GuiEventListener l : screen.children()) {
            if (l instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents tc
                    && KEY.equals(tc.getKey())) {
                return b;
            }
        }
        return null;
    }

    /**
     * The screen the config screen returns to. In 1.20.x {@code setScreen} re-initializes an Options screen that was
     * already shown (rebuildWidgets), which re-evaluates the setting anyway; a fresh Options screen (returning to the
     * same screen) is still used when the button presence no longer matches the setting, so this does not depend on
     * that detail.
     */
    static Screen returnTarget(Screen parent) {
        try {
            if (parent instanceof OptionsScreen && parent instanceof ParentAccess access
                    && (find(parent) != null) != ConfigManager.get().optionsButton) {
                Minecraft mc = Minecraft.getInstance();
                return new OptionsScreen(access.typinganimation$lastScreen(), mc.options);
            }
        } catch (Throwable e) {
            TypingAnimationMod.LOGGER.warn("[{}] Could not refresh the Options screen", TypingAnimationMod.MOD_ID, e);
        }
        return parent;
    }
}
