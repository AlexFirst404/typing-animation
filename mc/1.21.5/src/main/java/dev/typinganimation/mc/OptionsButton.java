package dev.typinganimation.mc;

import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.TypingAnimationMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** The "Typing Animation..." button of the vanilla Options screen (docs/SPEC.md section 6). */
public final class OptionsButton {
    public static final String KEY = "typinganimation.options.button";

    /** Mixed into {@link OptionsScreen}: the screen it returns to (needed to open a fresh copy of it). */
    public interface ParentAccess {
        Screen typinganimation$lastScreen();
    }

    private OptionsButton() {
    }

    /**
     * Adds the button as a new row of the options grid: 150 px wide, centred under both columns (the grid centres its
     * cells), as in every other group. No-op when disabled or when the contents are not a grid.
     */
    public static void addTo(LayoutElement contents, Screen optionsScreen) {
        try {
            if (!ConfigManager.get().optionsButton || !(contents instanceof GridLayout grid)) {
                return;
            }
            int[] children = {0};
            grid.visitChildren(child -> children[0]++);
            int row = (children[0] + 1) / 2; // vanilla fills the grid two per row
            Button button = Button.builder(Component.translatable(KEY),
                            b -> Minecraft.getInstance().setScreen(new ConfigScreen(optionsScreen)))
                    .width(Button.DEFAULT_WIDTH)
                    .tooltip(Tooltip.create(Component.translatable("typinganimation.description")))
                    .build();
            grid.addChild(button, row, 0, 1, 2);
        } catch (Throwable e) {
            TypingAnimationMod.LOGGER.warn("[{}] Could not add the Options screen button", TypingAnimationMod.MOD_ID,
                    e);
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
     * The screen the config screen returns to. An Options screen that was already initialized is not re-initialized
     * by {@code setScreen} (it only re-arranges its widgets), so when the "Options button" setting no longer matches
     * it, a fresh Options screen (returning to the same screen) takes its place.
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
