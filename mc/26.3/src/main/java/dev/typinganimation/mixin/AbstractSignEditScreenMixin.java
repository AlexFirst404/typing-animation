package dev.typinganimation.mixin;

import dev.typinganimation.mc.SignAnimation;
import dev.typinganimation.mc.SignStateHolder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    @Inject(method = "extractSignText", at = @At("HEAD"))
    private void typinganimation$begin(GuiGraphicsExtractor graphics, org.joml.Vector2f cursorOutput, CallbackInfo ci) {
        typinganimation$signAnimation().begin(this, graphics, font, messages, sign.getTextLineHeight(), line,
                signField.getCursorPos(), signField.getCursorPos() != signField.getSelectionPos());
    }

    @ModifyArg(method = "extractSignText", index = 1, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"))
    private String typinganimation$line(Font f, String text, int x, int y, int color, boolean shadow) {
        return typinganimation$signAnimation().line(f, text, x, y, color, shadow);
    }

    @Redirect(method = "extractSignText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractAppendCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZ)V"))
    private void typinganimation$append(GuiGraphicsExtractor graphics, Font f, int x, int y, int color, boolean shadow) {
        if (!typinganimation$signAnimation().drawCaret(graphics, f, x, y, color, sign.getTextLineHeight(), false)) {
            net.minecraft.client.gui.components.TextCursorUtils.extractAppendCursor(graphics, f, x, y, color, shadow);
        }
    }

    @Redirect(method = "extractSignText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractInsertCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V"))
    private void typinganimation$insert(GuiGraphicsExtractor graphics, int x, int y, int color, int height) {
        if (!typinganimation$signAnimation().drawCaret(graphics, font, x, y, color, height, true)) {
            net.minecraft.client.gui.components.TextCursorUtils.extractInsertCursor(graphics, x, y, color, height);
        }
    }

    @Inject(method = "extractSignText", at = @At("TAIL"))
    private void typinganimation$end(GuiGraphicsExtractor graphics, org.joml.Vector2f cursorOutput, CallbackInfo ci) {
        typinganimation$signAnimation().end(graphics, font);
    }
}
