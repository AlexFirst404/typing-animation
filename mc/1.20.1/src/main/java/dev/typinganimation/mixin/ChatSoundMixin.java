package dev.typinganimation.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.typinganimation.core.FieldKind;
import dev.typinganimation.core.TypingSoundAction;
import dev.typinganimation.mc.TypingSounds;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatSoundMixin {
    @Shadow protected EditBox input;
    @Unique private String typinganimation$soundBefore;
    @Unique private long typinganimation$inputSerial;
    @Unique private boolean typinganimation$submitted;

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void typinganimation$beforeKey(CallbackInfoReturnable<Boolean> ci) {
        typinganimation$soundBefore = input == null ? null : input.getValue();
        typinganimation$inputSerial = TypingSounds.inputSerial();
        typinganimation$submitted = false;
    }

    @Inject(method = "keyPressed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/ChatScreen;handleChatInput(Ljava/lang/String;Z)Z"))
    private void typinganimation$submit(CallbackInfoReturnable<Boolean> ci) {
        typinganimation$submitted = true;
        if (input != null && !input.getValue().trim().isEmpty())
            TypingSounds.action(TypingSoundAction.ENTER, FieldKind.CHAT, false);
    }

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void typinganimation$afterKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> ci) {
        // Chat also edits values outside EditBox: Tab completion and saved-draft deletion. History stays silent.
        if (!typinganimation$submitted && ci.getReturnValueZ() && (keyCode == 258 || keyCode == 257 || keyCode == 335 || keyCode == 259 || keyCode == 261) && input != null
                && TypingSounds.inputSerial() == typinganimation$inputSerial)
            TypingSounds.edited(typinganimation$soundBefore, input.getValue(), FieldKind.CHAT, false);
        typinganimation$soundBefore = null;
    }
}
