package dev.typinganimation.mixin;

import dev.typinganimation.mc.CommandBlockOverhaulAnimation;
import dev.typinganimation.mc.CommandBlockOverhaulStateHolder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional hooks use the foreign methods and explicit Forge aliases for Minecraft overrides. */
@Pseudo
@Mixin(targets="me.cyanhana.commandblockuioverhaul.ui.ModMultiLineEditBox",remap=false)
public abstract class CommandBlockOverhaulMixin implements CommandBlockOverhaulStateHolder {
    @Unique private CommandBlockOverhaulAnimation typinganimation$commandBlock;
    @Override public CommandBlockOverhaulAnimation typinganimation$commandBlockAnimation() {
        if(typinganimation$commandBlock==null) typinganimation$commandBlock=new CommandBlockOverhaulAnimation();
        return typinganimation$commandBlock;
    }
    @Inject(method={"renderWidget","m_87963_"},at=@At("HEAD"),require=0)
    private void typinganimation$begin(GuiGraphics g,int mouseX,int mouseY,float partialTick,CallbackInfo ci) {
        typinganimation$commandBlockAnimation().begin(this,g);
    }
    @Inject(method={"renderWidget","m_87963_"},at=@At("RETURN"),require=0)
    private void typinganimation$end(CallbackInfo ci) { typinganimation$commandBlockAnimation().end(this); }
    @Inject(method="renderColoredLine",at=@At("HEAD"),cancellable=true,require=0)
    private void typinganimation$line(GuiGraphics g,String text,int x,int y,int color,int index,CallbackInfo ci) {
        if(typinganimation$commandBlockAnimation().line(g,text,x,y,color,index)) ci.cancel();
    }
    @Redirect(method={"renderWidget","m_87963_"},require=0,
            at=@At(value="INVOKE",remap=true,target="Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"))
    private void typinganimation$bar(GuiGraphics g,RenderType type,int x0,int y0,int x1,int y1,int color) {
        if(!typinganimation$commandBlockAnimation().ownsCaret()) g.fill(type,x0,y0,x1,y1,color);
    }
    @Redirect(method={"renderWidget","m_87963_"},require=0,
            at=@At(value="INVOKE",remap=true,ordinal=1,target="Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int typinganimation$append(GuiGraphics g,Font font,String text,int x,int y,int color) {
        return typinganimation$commandBlockAnimation().ownsCaret() ? x+font.width(text) : g.drawString(font,text,x,y,color);
    }
    @Inject(method={"setValue","m_94144_"},at=@At("HEAD"),require=0)
    private void typinganimation$assignment(CallbackInfo ci) { typinganimation$commandBlockAnimation().assignment(); }
    @Inject(method={"keyPressed","m_7933_"},at=@At("HEAD"),require=0)
    private void typinganimation$keyBegin(CallbackInfoReturnable<Boolean> ci) { typinganimation$commandBlockAnimation().beforeInput(this); }
    @Inject(method={"keyPressed","m_7933_"},at=@At("RETURN"),require=0)
    private void typinganimation$keyEnd(CallbackInfoReturnable<Boolean> ci) { typinganimation$commandBlockAnimation().afterInput(this); }
    @Inject(method={"insertText","m_94164_","deleteTo","deleteChars","m_94180_","deleteWords","m_94176_","undo","redo"},at=@At("HEAD"),require=0)
    private void typinganimation$editBegin(CallbackInfo ci) { typinganimation$commandBlockAnimation().beforeInput(this); }
    @Inject(method={"insertText","m_94164_","deleteTo","deleteChars","m_94180_","deleteWords","m_94176_","undo","redo"},at=@At("RETURN"),require=0)
    private void typinganimation$editEnd(CallbackInfo ci) { typinganimation$commandBlockAnimation().afterInput(this); }
}
