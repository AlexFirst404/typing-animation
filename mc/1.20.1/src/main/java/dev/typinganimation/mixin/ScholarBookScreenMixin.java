package dev.typinganimation.mixin;

import dev.typinganimation.mc.ScholarAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A spread change is navigation, including when the destination has identical page text. */
@Pseudo
@Mixin(targets = "io.github.mortuusars.scholar.client.gui.screen.edit.SpreadBookEditScreen", remap = false)
public abstract class ScholarBookScreenMixin {
    @Inject(method = "setTextBoxes(Z)V", at = @At("RETURN"), require = 0)
    private void typinganimation$pageChanged(CallbackInfo ci) {
        ScholarAnimation.resetPages(this);
    }
}
