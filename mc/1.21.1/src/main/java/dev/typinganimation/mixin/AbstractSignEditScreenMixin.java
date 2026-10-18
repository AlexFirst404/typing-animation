package dev.typinganimation.mixin;

import dev.typinganimation.mc.SignAnimation;
import dev.typinganimation.mc.SignStateHolder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks the shared normal/hanging sign editor without replacing input, sign updates or selection rendering. */
@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin extends Screen implements SignStateHolder {
    @Shadow @Final private SignBlockEntity sign;
    @Shadow @Final private String[] messages;
    @Shadow private int line;
    @Shadow private TextFieldHelper signField;
    @Unique private SignAnimation typinganimation$sign;

    private AbstractSignEditScreenMixin(Component title) { super(title); }

    @Override
    public SignAnimation typinganimation$signAnimation() {
        if (typinganimation$sign == null) typinganimation$sign = new SignAnimation();
        return typinganimation$sign;
    }

    @Override public String typinganimation$signMessage(int line) { return messages[line]; }
    @Override public int typinganimation$signLine() { return line; }

    @Inject(method = "renderSignText", at = @At("HEAD"))
    private void typinganimation$begin(GuiGraphics graphics, CallbackInfo ci) {
        typinganimation$signAnimation().begin(this, graphics, font, messages, sign.getTextLineHeight(), line,
                signField.getCursorPos(), signField.getCursorPos() != signField.getSelectionPos());
    }

    @ModifyArg(method = "renderSignText", index = 1, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"))
    private String typinganimation$line(Font f, String text, int x, int y, int color, boolean shadow) {
        return typinganimation$signAnimation().line(f, text, x, y, color, shadow);
    }

    @Redirect(method = "renderSignText", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"))
    private int typinganimation$append(GuiGraphics graphics, Font f, String text, int x, int y, int color, boolean shadow) {
        if (typinganimation$signAnimation().drawCaret(graphics, f, x, y, color, sign.getTextLineHeight(), false)) {
            return x + f.width(text);
        }
        return graphics.drawString(f, text, x, y, color, shadow);
    }

    @Redirect(method = "renderSignText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void typinganimation$insert(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        if (!typinganimation$signAnimation().drawCaret(graphics, font, x0, y0 + 1, color, y1 - y0 - 1, true)) {
            graphics.fill(x0, y0, x1, y1, color);
        }
    }

    @Inject(method = "renderSignText", at = @At("TAIL"))
    private void typinganimation$end(GuiGraphics graphics, CallbackInfo ci) {
        typinganimation$signAnimation().end(graphics, font);
    }
}
