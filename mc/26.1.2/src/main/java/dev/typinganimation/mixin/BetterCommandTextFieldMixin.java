package dev.typinganimation.mixin;

import dev.typinganimation.mc.BetterCommandAnimation;
import dev.typinganimation.mc.BetterCommandStateHolder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The optional editor overrides EditBox drawing and input instead of calling the vanilla implementation. */
@Pseudo
@Mixin(targets="bettercommandblockui.main.ui.MultiLineTextFieldWidget",remap=false)
public abstract class BetterCommandTextFieldMixin implements BetterCommandStateHolder {
    @Unique private BetterCommandAnimation typinganimation$betterCommand;
    @Override public BetterCommandAnimation typinganimation$betterCommandAnimation() {
        if (typinganimation$betterCommand==null) typinganimation$betterCommand=new BetterCommandAnimation();
        return typinganimation$betterCommand;
    }

    // Both development Mojang names and the published Fabric override names are explicit.
    @Inject(method={"extractWidgetRenderState","renderWidget","render","method_48579","method_25394"},at=@At("HEAD"),require=0)
    private void typinganimation$begin(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick,CallbackInfo ci) {
        typinganimation$betterCommandAnimation().begin(this,graphics);
    }
    @Inject(method={"extractWidgetRenderState","renderWidget","render","method_48579","method_25394"},at=@At("RETURN"),require=0)
    private void typinganimation$end(CallbackInfo ci) { typinganimation$betterCommandAnimation().end(this); }

    @Inject(method="drawColoredLine",at=@At("HEAD"),require=0)
    private void typinganimation$coloredLine(GuiGraphicsExtractor graphics,String text,int x,int y,int index,CallbackInfo ci) {
        typinganimation$betterCommandAnimation().coloredLine(index);
    }
    @Inject(method="drawRawText",at=@At("HEAD"),require=0)
    private void typinganimation$rawLine(CallbackInfo ci) { typinganimation$betterCommandAnimation().rawLine(); }
    @ModifyArg(method={"drawColoredLine","drawRawText"},index=1,require=0,
            at=@At(value="INVOKE",remap=true,target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private String typinganimation$segment(Font font,String text,int x,int y,int color) {
        return typinganimation$betterCommandAnimation().segment(font,text,x,y,color);
    }
    @ModifyArg(method={"extractWidgetRenderState","renderWidget","render","method_48579","method_25394"},index=1,require=0,
            at=@At(value="INVOKE",remap=true,target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private String typinganimation$nativeCursor(String text) {
        return typinganimation$betterCommandAnimation().nativeCursor(text);
    }
    @ModifyArg(method={"extractWidgetRenderState","renderWidget","render","method_48579","method_25394"},index=4,require=0,
            at=@At(value="INVOKE",remap=true,target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private int typinganimation$nativeFill(int x0,int y0,int x1,int y1,int color) {
        return typinganimation$betterCommandAnimation().nativeFill(x0,y0,x1,y1,color);
    }

    @Inject(method={"setValue","method_1852","setRawText"},at=@At("HEAD"),require=0)
    private void typinganimation$assignment(CallbackInfo ci) { typinganimation$betterCommandAnimation().assignment(); }
    @Inject(method={"charTyped","keyPressed","method_25400","method_25404"},at=@At("HEAD"),require=0)
    private void typinganimation$beforeInput(CallbackInfoReturnable<Boolean> ci) {
        typinganimation$betterCommandAnimation().beforeInput(this);
    }
    @Inject(method={"charTyped","keyPressed","method_25400","method_25404"},at=@At("RETURN"),require=0)
    private void typinganimation$afterInput(CallbackInfoReturnable<Boolean> ci) {
        typinganimation$betterCommandAnimation().afterInput(this,ci.getReturnValueZ());
    }
    @Inject(method={"insertText","method_1867","deleteChars","method_1878","deleteWords","method_1877"},at=@At("HEAD"),require=0)
    private void typinganimation$beforeEdit(CallbackInfo ci) { typinganimation$betterCommandAnimation().beforeInput(this); }
    @Inject(method={"insertText","method_1867","deleteChars","method_1878","deleteWords","method_1877"},at=@At("RETURN"),require=0)
    private void typinganimation$afterEdit(CallbackInfo ci) { typinganimation$betterCommandAnimation().afterInput(this,true); }
}
