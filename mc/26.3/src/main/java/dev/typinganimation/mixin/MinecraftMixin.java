package dev.typinganimation.mixin;

import dev.typinganimation.mc.ClientInit;
import dev.typinganimation.mc.SelfTest;
import dev.typinganimation.mc.TypingRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Loader-independent client tick / frame hooks: the end of every rendered frame advances the renderer's frame
 * counter (an increment); everything else only drives the dev self-test and does nothing unless it is enabled
 * ({@code TYPINGANIMATION_SELFTEST=1} or {@code -Dtypinganimation.selftest=true}).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void typinganimation$tick(CallbackInfo ci) {
        if (ClientInit.SELFTEST) {
            SelfTest.onTick((Minecraft) (Object) this);
        }
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void typinganimation$frame(boolean advanceGameTime, CallbackInfo ci) {
        TypingRenderer.onFrameEnd();
        if (ClientInit.SELFTEST) {
            SelfTest.onFrame();
        }
    }
}
