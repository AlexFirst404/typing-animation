package dev.typinganimation.mixin;

import dev.typinganimation.core.FieldKind;
import dev.typinganimation.mc.TypingStateHolder;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the chat / command input as {@link FieldKind#CHAT} (the "Chat" toggle applies to it). */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow protected EditBox input;

    @Inject(method = "init", at = @At("TAIL"))
    private void typinganimation$markChatInput(CallbackInfo ci) {
        if (input instanceof TypingStateHolder holder) {
            holder.typinganimation$setFieldKind(FieldKind.CHAT);
        }
    }
}
