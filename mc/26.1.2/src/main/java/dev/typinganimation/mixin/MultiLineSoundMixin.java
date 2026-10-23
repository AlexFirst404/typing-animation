package dev.typinganimation.mixin;

import dev.typinganimation.mc.TypingSounds;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe actual input, so initialization, history, redraws and rejected input stay silent. */
@Mixin(MultiLineEditBox.class)
public abstract class MultiLineSoundMixin {
    @Shadow @Final private MultilineTextField textField;
    @Unique private String typinganimation$soundBefore;

    @Inject(method = {"charTyped", "keyPressed"}, at = @At("HEAD"))
    private void typinganimation$beforeInput(CallbackInfoReturnable<Boolean> ci) {
        typinganimation$soundBefore = textField.value();
    }

    @Inject(method = {"charTyped", "keyPressed"}, at = @At("RETURN"))
    private void typinganimation$afterInput(CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ()) TypingSounds.widgetEdited(this, typinganimation$soundBefore, textField.value());
        typinganimation$soundBefore = null;
    }
}
