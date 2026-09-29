package dev.typinganimation.mixin;

import dev.typinganimation.mc.OptionsButton;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Adds our button as a centred row under the vanilla options grid: the grid is handed to
 * {@code HeaderAndFooterLayout#addToContents} before the layout's widgets are registered, so the button is arranged
 * and added like every vanilla one (6 rows: 61 + 6 * 24 + 33 = 238 px, fits a 240 px tall screen). Also exposes the
 * screen's parent, so the config screen can return to a fresh Options screen when the button setting changed.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin implements OptionsButton.ParentAccess {
    @Shadow @Final private Screen lastScreen;

    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addToContents(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
    private LayoutElement typinganimation$addButton(LayoutElement contents) {
        OptionsButton.addTo(contents, (Screen) (Object) this);
        return contents;
    }

    @Override
    public Screen typinganimation$lastScreen() {
        return lastScreen;
    }
}
