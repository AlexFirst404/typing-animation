package dev.typinganimation.mc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The "Typing Animation..." button of the vanilla Options screen (see OptionsScreenMixin, which also applies the
 * {@code optionsButton} setting).
 */
public final class OptionsButton {
    public static final String KEY = "typinganimation.options.button";

    private OptionsButton() {
    }

    /** Adds the button as a new full row (spanning both columns, centred) below the options grid. */
    public static Button addToGrid(GridLayout grid, Screen screen) {
        int[] children = {0};
        grid.visitChildren(child -> children[0]++);
        int row = (children[0] + 1) / 2; // the options grid has two columns
        return grid.addChild(create(screen).width(Button.DEFAULT_WIDTH).build(), row, 0, 1, 2);
    }

    /** Compact top-left fallback (x=6, y=6, text width + 16). */
    public static Button createCorner(Screen screen, Font font) {
        Component message = Component.translatable(KEY);
        return create(screen).bounds(6, 6, font.width(message) + 16, Button.DEFAULT_HEIGHT).build();
    }

    private static Button.Builder create(Screen screen) {
        return Button.builder(Component.translatable(KEY), button -> Minecraft.getInstance().gui.setScreen(new ConfigScreen(screen)))
                .tooltip(Tooltip.create(Component.translatable("typinganimation.description")));
    }

    /** True for the button created here (used by the self-test). */
    public static boolean isOptionsButton(Object widget) {
        return widget instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents tc
                && KEY.equals(tc.getKey());
    }
}
