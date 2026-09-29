package dev.typinganimation.mixin;

import dev.typinganimation.core.FieldKind;
import dev.typinganimation.mc.EditBoxHooks;
import dev.typinganimation.mc.TypingStateHolder;
import dev.typinganimation.mc.WidgetAnimator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Animated text for {@link EditBox}. Vanilla, Fabric and Forge draw the text segments and the append caret with the
 * 5-arg {@code GuiGraphics#drawString}; NeoForge 21.5.x patches {@code renderWidget} to the 6-arg overload with
 * its {@code textShadow} field (all five text draws). Both descriptors are redirected with {@code require = 0};
 * each {@link Group} needs at least one injection, so a total miss fails loudly while another mod taking over one
 * of the calls only costs that call its animation.
 */
@Mixin(EditBox.class)
public abstract class EditBoxMixin implements TypingStateHolder {
    @Shadow @Final private Font font;
    @Shadow private String value;
    @Shadow private int displayPos;
    @Shadow private int cursorPos;
    @Shadow private int highlightPos;
    @Shadow private int maxLength;

    @Unique
    private WidgetAnimator typinganimation$animatorField;

    @Override
    public WidgetAnimator typinganimation$animator() {
        WidgetAnimator a = typinganimation$animatorField;
        if (a == null) {
            a = new WidgetAnimator(FieldKind.OTHER);
            typinganimation$animatorField = a;
        }
        return a;
    }

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void typinganimation$begin(GuiGraphics gg, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        EditBoxHooks.begin((EditBox) (Object) this, typinganimation$animator(), font, value, displayPos, cursorPos,
                highlightPos);
    }

    /** The visible substring (one call per frame): lets the hooks split the segments without recomputing it. */
    @Redirect(method = "renderWidget", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;plainSubstrByWidth(Ljava/lang/String;I)Ljava/lang/String;"))
    private String typinganimation$visible(Font f, String text, int maxWidth) {
        String s = f.plainSubstrByWidth(text, maxWidth);
        EditBoxHooks.visible(typinganimation$animator(), s);
        return s;
    }

    // ---------------------------------------------------------------- text segments (before / after the cursor)

    @Group(name = "typinganimation_editbox_text", min = 1)
    @Redirect(method = "renderWidget", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int typinganimation$text(GuiGraphics gg, Font f, FormattedCharSequence text, int x, int y, int color) {
        return EditBoxHooks.drawText((EditBox) (Object) this, typinganimation$animator(), gg, f, text, x, y, color,
                true, false);
    }

    @Group(name = "typinganimation_editbox_text", min = 1)
    @Redirect(method = "renderWidget", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I"))
    private int typinganimation$textShadowArg(GuiGraphics gg, Font f, FormattedCharSequence text, int x, int y,
                                              int color, boolean shadow) {
        return EditBoxHooks.drawText((EditBox) (Object) this, typinganimation$animator(), gg, f, text, x, y, color,
                shadow, true);
    }

    // ------------------------------- caret (String draws: 0 = suggestion, 1 = "_"; insert-mode bar = RenderType fill)

    @Group(name = "typinganimation_editbox_caret", min = 1)
    @Redirect(method = "renderWidget", require = 0, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int typinganimation$caret(GuiGraphics gg, Font f, String text, int x, int y, int color) {
        return EditBoxHooks.drawCaretString((EditBox) (Object) this, typinganimation$animator(), gg, f, text, x, y,
                color, true, false);
    }

    @Group(name = "typinganimation_editbox_caret", min = 1)
    @Redirect(method = "renderWidget", require = 0, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"))
    private int typinganimation$caretShadowArg(GuiGraphics gg, Font f, String text, int x, int y, int color,
                                               boolean shadow) {
        return EditBoxHooks.drawCaretString((EditBox) (Object) this, typinganimation$animator(), gg, f, text, x, y,
                color, shadow, true);
    }

    @Group(name = "typinganimation_editbox_caret", min = 1)
    @Redirect(method = "renderWidget", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"))
    private void typinganimation$caretBar(GuiGraphics gg, RenderType type, int x1, int y1, int x2, int y2, int color) {
        EditBoxHooks.fillCaret((EditBox) (Object) this, typinganimation$animator(), gg, type, x1, y1, x2, y2, color);
    }

    @Inject(method = "renderWidget", at = @At("RETURN"))
    private void typinganimation$end(GuiGraphics gg, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        EditBoxHooks.end((EditBox) (Object) this, typinganimation$animator(), gg, value, cursorPos, maxLength);
    }
}
