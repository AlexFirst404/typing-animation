package dev.typinganimation.mixin;

import dev.typinganimation.mc.ScholarAnimation;
import dev.typinganimation.mc.ScholarStateHolder;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional adapter for Scholar's independent page, title and author text widgets. */
@Pseudo
@Mixin(targets = "io.github.mortuusars.scholar.client.gui.widget.textbox.TextBox", remap = false)
public abstract class ScholarTextBoxMixin implements ScholarStateHolder {
    @Unique private ScholarAnimation typinganimation$scholar;

    @Override
    public ScholarAnimation typinganimation$scholarAnimation() {
        if (typinganimation$scholar == null) typinganimation$scholar = new ScholarAnimation();
        return typinganimation$scholar;
    }

    @Inject(method = "renderLines", at = @At("HEAD"), require = 0)
    private void typinganimation$begin(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                       List<?> lines, int color, CallbackInfo ci) {
        typinganimation$scholarAnimation().begin(this, graphics);
    }

    @Inject(method = "renderLines", at = @At("RETURN"), require = 0)
    private void typinganimation$end(CallbackInfo ci) {
        typinganimation$scholarAnimation().end(this);
    }

    @ModifyArg(method = "renderLines", index = 1, require = 0,
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"))
    private String typinganimation$line(Font font, String text, int x, int y, int color, boolean shadow) {
        return typinganimation$scholarAnimation().line(font, text, x, y, color, shadow);
    }

    @Inject(method = "renderCursor", at = @At("HEAD"), cancellable = true, require = 0)
    private void typinganimation$cursor(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                        @Coerce Object editor, @Coerce Object cursor, int color, CallbackInfo ci) {
        if (typinganimation$scholarAnimation().caret(this, color)) ci.cancel();
    }

    @Inject(method = {"charTyped", "method_25400", "m_5534_", "keyPressed", "method_25404", "m_7933_"}, at = @At("HEAD"), require = 0)
    private void typinganimation$beforeInput(CallbackInfoReturnable<Boolean> ci) {
        typinganimation$scholarAnimation().beforeInput(this);
    }

    @Inject(method = {"charTyped", "method_25400", "m_5534_"}, at = @At("RETURN"), require = 0)
    private void typinganimation$afterCharacter(CharacterEvent event, CallbackInfoReturnable<Boolean> ci) {
        typinganimation$scholarAnimation().afterInput(this, ci.getReturnValueZ(), -1, true);
    }

    @Inject(method = {"keyPressed", "method_25404", "m_7933_"}, at = @At("RETURN"), require = 0)
    private void typinganimation$afterKey(KeyEvent event,
                                          CallbackInfoReturnable<Boolean> ci) {
        typinganimation$scholarAnimation().afterInput(this, ci.getReturnValueZ(), event.key(), false);
    }
}
