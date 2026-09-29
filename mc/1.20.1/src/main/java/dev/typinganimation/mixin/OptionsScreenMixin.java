package dev.typinganimation.mixin;

import dev.typinganimation.mc.OptionsButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds our button to the vanilla Options screen. The 1.20.x options grid already fills a 240 px tall screen
 * (height / 6 - 12 = 28 + 204 px = 232), so the button goes to the top-left corner (docs/SPEC.md section 6 fallback),
 * added after {@code init} like any other widget (re-added by every re-init, e.g. on resize or when the screen is
 * shown again). Also exposes the screen's parent, so the config screen can return to a fresh Options screen when the
 * button setting changed.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen implements OptionsButton.ParentAccess {
    @Shadow @Final private Screen lastScreen;

    private OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void typinganimation$addButton(CallbackInfo ci) {
        Button button = OptionsButton.create((Screen) (Object) this, this.font);
        if (button != null) {
            addRenderableWidget(button);
        }
    }

    @Override
    public Screen typinganimation$lastScreen() {
        return lastScreen;
    }
}
