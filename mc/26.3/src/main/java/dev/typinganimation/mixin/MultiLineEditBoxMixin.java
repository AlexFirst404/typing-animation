package dev.typinganimation.mixin;

import dev.typinganimation.core.FieldKind;
import dev.typinganimation.mc.TypingRenderer;
import dev.typinganimation.mc.TypingStateHolder;
import dev.typinganimation.mc.WidgetAnimation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Animated text for {@link MultiLineEditBox} (book editor, report comments, dialog inputs). Its lines are drawn
 * with {@code GuiGraphicsExtractor#text(Font, String, int, int, int, boolean)}: ordinal 0 = the caret line
 * before the caret, 1 = the caret line after the caret, 2 = every other line. The absolute start index of a
 * line is looked up from its y (lines are {@code getInnerTop() + 9 * lineIndex}) in the text field's line
 * list; {@code MultilineTextField.StringView} is protected, so it is read through accessor mixins only.
 * A gliding caret: vanilla's caret call gets a transparent colour and the renderer draws the moved caret right
 * after it.
 * The widget applies the scroll translation and a scissor around {@code extractContents}, so ghosts drawn at
 * its TAIL scroll and clip with the text.
 */
@Mixin(MultiLineEditBox.class)
public abstract class MultiLineEditBoxMixin extends AbstractTextAreaWidget implements TypingStateHolder {
    @Shadow @Final private Font font;
    @Shadow @Final private MultilineTextField textField;
    @Shadow @Final private boolean textShadow;

    @Unique
    private WidgetAnimation typinganimation$anim;

    private MultiLineEditBoxMixin(int x, int y, int width, int height, Component narration) {
        super(x, y, width, height, narration, null);
    }

    @Override
    public WidgetAnimation typinganimation$animation() {
        WidgetAnimation a = typinganimation$anim;
        if (a == null) {
            a = new WidgetAnimation(FieldKind.MULTILINE);
            typinganimation$anim = a;
        }
        return a;
    }

    @Inject(method = "extractContents", at = @At("HEAD"))
    private void typinganimation$begin(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        TypingRenderer.beginMultiline(typinganimation$animation(), this, graphics, getInnerLeft(), getInnerTop(),
                scrollAmount(), textField.value(), isFocused());
    }

    /** Absolute index of the first char of the line drawn at {@code y}, or -1 when it can not be determined. */
    @Unique
    private int typinganimation$lineStart(int y, String str) {
        int rel = y - getInnerTop();
        if (rel < 0 || rel % 9 != 0) {
            return -1;
        }
        List<?> lines = ((MultilineTextFieldAccessor) (Object) textField).typinganimation$displayLines();
        int line = rel / 9;
        if (line >= lines.size()) {
            return -1;
        }
        int begin = ((StringViewAccessor) lines.get(line)).typinganimation$beginIndex();
        return textField.value().startsWith(str, begin) ? begin : -1;
    }

    @ModifyArg(method = "extractContents", index = 1, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"))
    private String typinganimation$lineBeforeCaret(Font font, String str, int x, int y, int color, boolean shadow) {
        WidgetAnimation anim = typinganimation$animation();
        int start = str == null ? -1 : typinganimation$lineStart(y, str);
        return TypingRenderer.multilineSegment(anim, this, start, font, str, x, y, color, shadow);
    }

    @ModifyArg(method = "extractContents", index = 1, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"))
    private String typinganimation$lineAfterCaret(Font font, String str, int x, int y, int color, boolean shadow) {
        WidgetAnimation anim = typinganimation$animation();
        int cursor = textField.cursor();
        int start = str != null && textField.value().startsWith(str, cursor) ? cursor : -1;
        return TypingRenderer.multilineSegment(anim, this, start, font, str, x, y, color, shadow);
    }

    @ModifyArg(method = "extractContents", index = 1, at = @At(value = "INVOKE", ordinal = 2,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"))
    private String typinganimation$line(Font font, String str, int x, int y, int color, boolean shadow) {
        WidgetAnimation anim = typinganimation$animation();
        int start = str == null ? -1 : typinganimation$lineStart(y, str);
        return TypingRenderer.multilineSegment(anim, this, start, font, str, x, y, color, shadow);
    }

    @ModifyArg(method = "extractContents", index = 3, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractInsertCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V"))
    private int typinganimation$insertCaret(GuiGraphicsExtractor graphics, int x, int y, int color, int lineHeight) {
        return TypingRenderer.multilineCaret(typinganimation$animation(), this, getInnerLeft(),
                WidgetAnimation.CARET_INSERT, null, x, y, color, lineHeight, false);
    }

    @Inject(method = "extractContents", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractInsertCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V"))
    private void typinganimation$insertCaretDone(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a,
                                                 CallbackInfo ci) {
        TypingRenderer.caretDrawn(typinganimation$animation());
    }

    @ModifyArg(method = "extractContents", index = 4, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractAppendCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZ)V"))
    private int typinganimation$appendCaret(GuiGraphicsExtractor graphics, Font font, int x, int y, int color,
                                            boolean shadow) {
        return TypingRenderer.multilineCaret(typinganimation$animation(), this, getInnerLeft(),
                WidgetAnimation.CARET_APPEND, font, x, y, color, 0, shadow);
    }

    @Inject(method = "extractContents", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractAppendCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZ)V"))
    private void typinganimation$appendCaretDone(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a,
                                                 CallbackInfo ci) {
        TypingRenderer.caretDrawn(typinganimation$animation());
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void typinganimation$end(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        TypingRenderer.end(typinganimation$animation(), this, font, textShadow);
    }
}
