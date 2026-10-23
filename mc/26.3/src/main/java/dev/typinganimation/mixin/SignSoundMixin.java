package dev.typinganimation.mixin;

import dev.typinganimation.core.FieldKind;
import dev.typinganimation.core.TypingSoundAction;
import dev.typinganimation.mc.TypingSounds;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSignEditScreen.class)
public abstract class SignSoundMixin {
    @Shadow @Final private String[] messages;
    @Shadow private int line;
    @Unique private String typinganimation$soundBefore;
    @Unique private int typinganimation$soundLine;

    @Inject(method = {"charTyped", "keyPressed"}, at = @At("HEAD"))
    private void typinganimation$beforeInput(CallbackInfoReturnable<Boolean> ci) {
        typinganimation$soundLine = line;
        typinganimation$soundBefore = messages[line];
    }

    @Inject(method = "charTyped", at = @At("RETURN"))
    private void typinganimation$afterChar(CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ() && line == typinganimation$soundLine)
            TypingSounds.edited(typinganimation$soundBefore, messages[line], FieldKind.SIGN, false);
        typinganimation$soundBefore = null;
    }

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void typinganimation$afterKey(KeyEvent event, CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ()) {
            if (event.isConfirmation() && line != typinganimation$soundLine)
                TypingSounds.action(TypingSoundAction.ENTER, FieldKind.SIGN, false);
            else if (line == typinganimation$soundLine)
                TypingSounds.edited(typinganimation$soundBefore, messages[line], FieldKind.SIGN, false);
        }
        typinganimation$soundBefore = null;
    }
}
