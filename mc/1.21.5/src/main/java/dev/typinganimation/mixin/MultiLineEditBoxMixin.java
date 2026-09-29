package dev.typinganimation.mixin;

import dev.typinganimation.core.FieldKind;
import dev.typinganimation.mc.MultiLineHooks;
import dev.typinganimation.mc.TypingStateHolder;
import dev.typinganimation.mc.WidgetAnimator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Animated text for {@link MultiLineEditBox} (same call shapes on every loader: 5-arg String drawString, 5-int fill;
 * the selection highlight is a separate {@code fill(RenderType, ...)} in {@code renderHighlight}). Since 1.21.4 the
 * box extends {@link AbstractTextAreaWidget} (scroll area with {@code innerPadding()} and {@code scrollAmount()}).
 * The protected {@code MultilineTextField.StringView} record is never named: the line ranges are read from the
 * {@code String#substring(begin, end)} calls that produce each drawn line.
 */
@Mixin(MultiLineEditBox.class)
public abstract class MultiLineEditBoxMixin extends AbstractTextAreaWidget implements TypingStateHolder {
    @Shadow @Final private Font font;
    @Shadow @Final private MultilineTextField textField;

    @Unique
    private WidgetAnimator typinganimation$animatorField;

    private MultiLineEditBoxMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Override
    public WidgetAnimator typinganimation$animator() {
        WidgetAnimator a = typinganimation$animatorField;
        if (a == null) {
            a = new WidgetAnimator(FieldKind.MULTILINE);
            typinganimation$animatorField = a;
        }
        return a;
    }

    @Inject(method = "renderContents", at = @At("HEAD"))
    private void typinganimation$begin(GuiGraphics gg, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        MultiLineHooks.begin((MultiLineEditBox) (Object) this, typinganimation$animator(), font, textField.value(),
                textField.cursor(), textField.hasSelection(), getX() + innerPadding(), getY() + innerPadding(),
                scrollAmount());
    }

    @ModifyArg(method = "renderContents", index = 0, at = @At(value = "INVOKE",
            target = "Ljava/lang/String;substring(II)Ljava/lang/String;"))
    private int typinganimation$lineRange(int beginIndex, int endIndex) {
        MultiLineHooks.substring(typinganimation$animator(), beginIndex, endIndex);
        return beginIndex;
    }

    @Redirect(method = "renderContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int typinganimation$line(GuiGraphics gg, Font f, String text, int x, int y, int color) {
        return MultiLineHooks.drawString((MultiLineEditBox) (Object) this, typinganimation$animator(), gg, f, text, x,
                y, color);
    }

    @Redirect(method = "renderContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void typinganimation$caretBar(GuiGraphics gg, int x1, int y1, int x2, int y2, int color) {
        MultiLineHooks.fill((MultiLineEditBox) (Object) this, typinganimation$animator(), gg, x1, y1, x2, y2, color);
    }

    @Inject(method = "renderContents", at = @At("RETURN"))
    private void typinganimation$end(GuiGraphics gg, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        MultiLineHooks.end((MultiLineEditBox) (Object) this, typinganimation$animator(), gg);
    }
}
