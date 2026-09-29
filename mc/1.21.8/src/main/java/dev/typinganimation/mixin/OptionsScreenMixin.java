package dev.typinganimation.mixin;

import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.TypingAnimationMod;
import dev.typinganimation.mc.OptionsButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the "Typing Animation..." button to the vanilla Options screen: as a new row of the options grid (the
 * grid is handed to {@code HeaderAndFooterLayout#addToContents}), or, if the contents are not a grid (another mod
 * changed the screen), as a compact button in the top-left corner.
 *
 * <p>The {@code optionsButton} setting is applied again in {@code repositionElements}, which runs at the end of
 * {@code init} and whenever the (already initialised) screen is shown again, e.g. when the config screen returns
 * to it: the button is added when it was switched on, and hidden/disabled when it was switched off (its grid row
 * stays empty until the screen is opened again; the grid has no way to remove a child).
 *
 * <p>Both hooks run inside vanilla's {@code init}: a failure is logged once and the screen is left as vanilla built it.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
    @Unique
    private static boolean typinganimation$failureLogged;
    @Unique
    private GridLayout typinganimation$grid;
    @Unique
    private Button typinganimation$button;

    private OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void typinganimation$initStart(CallbackInfo ci) {
        typinganimation$grid = null;
        typinganimation$button = null;
    }

    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToContents(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
    private LayoutElement typinganimation$addToGrid(LayoutElement contents) {
        try {
            if (contents instanceof GridLayout grid) {
                typinganimation$grid = grid;
                if (ConfigManager.get().optionsButton) {
                    // added before vanilla registers the grid's widgets, so vanilla registers it too
                    typinganimation$button = OptionsButton.addToGrid(grid, this);
                }
            }
        } catch (Throwable t) {
            typinganimation$failed(t);
        }
        return contents;
    }

    @Inject(method = "repositionElements", at = @At("HEAD"))
    private void typinganimation$syncButton(CallbackInfo ci) {
        try {
            boolean wanted = ConfigManager.get().optionsButton;
            Button button = typinganimation$button;
            if (button == null) {
                if (!wanted) {
                    return;
                }
                GridLayout grid = typinganimation$grid;
                button = grid != null ? OptionsButton.addToGrid(grid, this) : OptionsButton.createCorner(this, font);
                typinganimation$button = button;
                addRenderableWidget(button); // a new grid row is placed by the arrangeElements() that follows
            } else if (button.visible != wanted) {
                button.visible = wanted;
                button.active = wanted;
                if (!wanted && getFocused() == button) {
                    setFocused(null);
                }
            }
        } catch (Throwable t) {
            typinganimation$failed(t);
        }
    }

    @Unique
    private static void typinganimation$failed(Throwable t) {
        if (!typinganimation$failureLogged) {
            typinganimation$failureLogged = true;
            TypingAnimationMod.LOGGER.warn("[{}] Could not add the Options screen button", TypingAnimationMod.MOD_ID, t);
        }
    }
}
