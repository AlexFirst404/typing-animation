package dev.typinganimation.mixin;

import dev.typinganimation.mc.ClientInit;
import dev.typinganimation.mc.SelfTest;
import dev.typinganimation.mc.Showcase;
import dev.typinganimation.mc.TypingRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Loader-independent client tick / frame hooks: the end of every rendered frame advances the renderer's frame
 * counter (an increment); everything else only drives the dev tools and does nothing unless one is enabled
 * (self-test: {@code TYPINGANIMATION_SELFTEST=1} or {@code -Dtypinganimation.selftest=true}; showcase recorder:
 * {@code TYPINGANIMATION_SHOWCASE=1}).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void typinganimation$tick(CallbackInfo ci) {
        if (ClientInit.SELFTEST) {
            SelfTest.onTick((Minecraft) (Object) this);
        } else if (ClientInit.SHOWCASE) {
            Showcase.onTick((Minecraft) (Object) this);
        }
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void typinganimation$frame(boolean advanceGameTime, CallbackInfo ci) {
        TypingRenderer.onFrameEnd();
        if (ClientInit.SELFTEST) {
            SelfTest.onFrame();
        } else if (ClientInit.SHOWCASE) {
            Showcase.onFrame((Minecraft) (Object) this);
        }
    }
}
