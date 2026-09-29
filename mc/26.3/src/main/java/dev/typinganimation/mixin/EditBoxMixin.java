package dev.typinganimation.mixin;

import dev.typinganimation.core.FieldKind;
import dev.typinganimation.mc.EditBoxAccess;
import dev.typinganimation.mc.TypingRenderer;
import dev.typinganimation.mc.TypingStateHolder;
import dev.typinganimation.mc.WidgetAnimation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Animated text for {@link EditBox}. Only argument modifiers and injections are used (no redirects), so other
 * mods hooking the same calls keep working:
 * <ul>
 *   <li>HEAD: begin the frame;</li>
 *   <li>{@code applyFormat(String,int)} (ordinals 0/1): remember each segment's plain text and start index;</li>
 *   <li>{@code GuiGraphicsExtractor#text(Font, FormattedCharSequence, int, int, int, boolean)} (ordinals 0/1 =
 *       before/after the caret): the renderer lays the chars out and either lets vanilla draw (idle) or draws
 *       them itself and hands vanilla an empty sequence;</li>
 *   <li>{@code text(Font, String, ...)} (the grey suggestion) and {@code textHighlight} (selection): x moved with
 *       the text while a scroll glides;</li>
 *   <li>{@code TextCursorUtils#extractInsertCursor / extractAppendCursor}: when the caret glides, vanilla gets a
 *       transparent colour and the renderer draws the moved caret right after the call;</li>
 *   <li>TAIL: chars revealed by a scroll glide, ghosts, end of frame.</li>
 * </ul>
 */
@Mixin(EditBox.class)
public abstract class EditBoxMixin implements TypingStateHolder, EditBoxAccess {
    @Shadow @Final private Font font;
    @Shadow private String value;
    @Shadow private int displayPos;
    @Shadow private boolean textShadow;
    @Shadow private boolean isEditable;
    @Shadow private int textColor;
    @Shadow private int textColorUneditable;
    @Shadow private int textX;
    @Shadow private int textY;

    @Shadow public abstract boolean isVisible();

    @Shadow
    private FormattedCharSequence applyFormat(String text, int offset) {
        throw new AssertionError("mixin shadow");
    }

    @Unique
    private WidgetAnimation typinganimation$anim;

    @Override
    public WidgetAnimation typinganimation$animation() {
        WidgetAnimation a = typinganimation$anim;
        if (a == null) {
            a = new WidgetAnimation(FieldKind.OTHER);
            typinganimation$anim = a;
        }
        return a;
    }

    @Override
    public FormattedCharSequence typinganimation$format(String text, int offset) {
        return applyFormat(text, offset);
    }

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
    private void typinganimation$begin(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        WidgetAnimation anim = typinganimation$animation();
        if (isVisible()) {
            TypingRenderer.beginEditBox(anim, (EditBox) (Object) this, graphics, font, textX, textY, displayPos, value,
                    isEditable ? textColor : textColorUneditable);
        }
    }

    @ModifyArg(method = "extractWidgetRenderState", index = 0, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/components/EditBox;applyFormat(Ljava/lang/String;I)Lnet/minecraft/util/FormattedCharSequence;"))
    private String typinganimation$captureBefore(String text, int offset) {
        TypingRenderer.captureSegment(typinganimation$animation(), 0, text, offset);
        return text;
    }

    @ModifyArg(method = "extractWidgetRenderState", index = 0, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/components/EditBox;applyFormat(Ljava/lang/String;I)Lnet/minecraft/util/FormattedCharSequence;"))
    private String typinganimation$captureAfter(String text, int offset) {
        TypingRenderer.captureSegment(typinganimation$animation(), 1, text, offset);
        return text;
    }

    @ModifyArg(method = "extractWidgetRenderState", index = 1, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"))
    private FormattedCharSequence typinganimation$textBefore(Font font, FormattedCharSequence seq, int x, int y, int color,
                                                             boolean shadow) {
        return TypingRenderer.editBoxSegment(typinganimation$animation(), this, 0, font, seq, x, y, color, shadow);
    }

    @ModifyArg(method = "extractWidgetRenderState", index = 1, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"))
    private FormattedCharSequence typinganimation$textAfter(Font font, FormattedCharSequence seq, int x, int y, int color,
                                                            boolean shadow) {
        return TypingRenderer.editBoxSegment(typinganimation$animation(), this, 1, font, seq, x, y, color, shadow);
    }

    @ModifyArg(method = "extractWidgetRenderState", index = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"))
    private int typinganimation$suggestionX(int x) {
        return TypingRenderer.shiftedX(typinganimation$animation(), x, false);
    }

    @ModifyArg(method = "extractWidgetRenderState", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textHighlight(IIIIZ)V"))
    private int typinganimation$highlightX0(int x) {
        return TypingRenderer.shiftedX(typinganimation$animation(), x, true);
    }

    @ModifyArg(method = "extractWidgetRenderState", index = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textHighlight(IIIIZ)V"))
    private int typinganimation$highlightX1(int x) {
        return TypingRenderer.shiftedX(typinganimation$animation(), x, true);
    }

    @ModifyArg(method = "extractWidgetRenderState", index = 3, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractInsertCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V"))
    private int typinganimation$insertCaret(GuiGraphicsExtractor graphics, int x, int y, int color, int lineHeight) {
        return TypingRenderer.editBoxCaret(typinganimation$animation(), this, WidgetAnimation.CARET_INSERT, null, x, y,
                color, lineHeight, false);
    }

    @Inject(method = "extractWidgetRenderState", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractInsertCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V"))
    private void typinganimation$insertCaretDone(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a,
                                                 CallbackInfo ci) {
        TypingRenderer.caretDrawn(typinganimation$animation());
    }

    @ModifyArg(method = "extractWidgetRenderState", index = 4, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractAppendCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZ)V"))
    private int typinganimation$appendCaret(GuiGraphicsExtractor graphics, Font font, int x, int y, int color,
                                            boolean shadow) {
        return TypingRenderer.editBoxCaret(typinganimation$animation(), this, WidgetAnimation.CARET_APPEND, font, x, y,
                color, 0, shadow);
    }

    @Inject(method = "extractWidgetRenderState", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractAppendCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZ)V"))
    private void typinganimation$appendCaretDone(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a,
                                                 CallbackInfo ci) {
        TypingRenderer.caretDrawn(typinganimation$animation());
    }

    @Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
    private void typinganimation$end(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        TypingRenderer.endEditBox(typinganimation$animation(), this, font, textShadow);
    }
}
