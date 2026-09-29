package dev.typinganimation.mixin;

import dev.typinganimation.mc.SelfTest;
import dev.typinganimation.mc.WidgetAnimator;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Loader-independent client hooks: the frame counter (a text widget that was not rendered in the previous frame, e.g.
 * because it was hidden, snaps to its value instead of animating what changed meanwhile) and the client tick, which
 * only does something while the dev self-test is enabled.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "runTick(Z)V", at = @At("HEAD"))
    private void typinganimation$frame(boolean renderLevel, CallbackInfo ci) {
        WidgetAnimator.nextFrame();
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void typinganimation$tick(CallbackInfo ci) {
        if (SelfTest.ENABLED) {
            SelfTest.onClientTick((Minecraft) (Object) this);
        }
    }
}
