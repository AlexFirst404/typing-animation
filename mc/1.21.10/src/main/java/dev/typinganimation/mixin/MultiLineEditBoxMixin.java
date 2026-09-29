package dev.typinganimation.mixin;

import dev.typinganimation.core.FieldKind;
import dev.typinganimation.mc.TypingRenderer;
import dev.typinganimation.mc.TypingStateHolder;
import dev.typinganimation.mc.WidgetAnimation;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Animated text for {@link MultiLineEditBox} (book editor, report comments, dialog inputs) in
 * {@code renderContents(GuiGraphics, int, int, float)} (identical bytecode on vanilla/Fabric, NeoForge and Forge).
 * Its lines are drawn with {@code GuiGraphics#drawString(Font, String, int, int, int, boolean)}: ordinal 0 = the
 * caret line before the caret, 1 = the caret line after the caret, 2 = every other line, 3 = the append caret
 * "_"; the insert caret is the only {@code fill(IIIII)}. The absolute start index of a line is looked up from its y
 * (lines are {@code getInnerTop() + 9 * lineIndex}) in the text field's line list;
 * {@code MultilineTextField.StringView} is protected, so it is read through accessor mixins only.
 * A gliding caret: vanilla's caret call gets a transparent colour and the renderer repeats it, moved, right after.
 * The widget applies the scroll translation and a scissor around {@code renderContents}, so ghosts drawn at its
 * TAIL scroll and clip with the text.
 */
@Mixin(MultiLineEditBox.class)
public abstract class MultiLineEditBoxMixin extends AbstractTextAreaWidget implements TypingStateHolder {
    @Shadow @Final private Font font;
    @Shadow @Final private MultilineTextField textField;
    @Shadow @Final private boolean textShadow;

    @Unique
    private WidgetAnimation typinganimation$anim;

    private MultiLineEditBoxMixin(int x, int y, int width, int height, Component narration) {
        super(x, y, width, height, narration);
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

    @Inject(method = "renderContents", at = @At("HEAD"))
    private void typinganimation$begin(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                       CallbackInfo ci) {
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

    @ModifyArg(method = "renderContents", index = 1, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"))
    private String typinganimation$lineBeforeCaret(Font font, String str, int x, int y, int color, boolean shadow) {
        WidgetAnimation anim = typinganimation$animation();
        int start = str == null ? -1 : typinganimation$lineStart(y, str);
        return TypingRenderer.multilineSegment(anim, this, start, font, str, x, y, color, shadow);
    }

    @ModifyArg(method = "renderContents", index = 1, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"))
    private String typinganimation$lineAfterCaret(Font font, String str, int x, int y, int color, boolean shadow) {
        WidgetAnimation anim = typinganimation$animation();
        int cursor = textField.cursor();
        int start = str != null && textField.value().startsWith(str, cursor) ? cursor : -1;
        return TypingRenderer.multilineSegment(anim, this, start, font, str, x, y, color, shadow);
    }

    @ModifyArg(method = "renderContents", index = 1, at = @At(value = "INVOKE", ordinal = 2,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"))
    private String typinganimation$line(Font font, String str, int x, int y, int color, boolean shadow) {
        WidgetAnimation anim = typinganimation$animation();
        int start = str == null ? -1 : typinganimation$lineStart(y, str);
        return TypingRenderer.multilineSegment(anim, this, start, font, str, x, y, color, shadow);
    }

    /** Insert caret: {@code fill(x, lineY - 1, x + 1, lineY + 10, cursorColor)}, the only fill of the method. */
    @ModifyArg(method = "renderContents", index = 4, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private int typinganimation$insertCaret(int x0, int y0, int x1, int y1, int color) {
        return TypingRenderer.multilineInsertCaret(typinganimation$animation(), this, getInnerLeft(), x0, y0, x1, y1,
                color);
    }

    @Inject(method = "renderContents", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private void typinganimation$insertCaretDone(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                                 CallbackInfo ci) {
        TypingRenderer.caretDrawn(typinganimation$animation());
    }

    /** Append caret: {@code drawString(font, "_", x, lineY, cursorColor, textShadow)} after the line loop. */
    @ModifyArg(method = "renderContents", index = 4, at = @At(value = "INVOKE", ordinal = 3,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"))
    private int typinganimation$appendCaret(Font font, String text, int x, int y, int color, boolean shadow) {
        return TypingRenderer.multilineAppendCaret(typinganimation$animation(), this, getInnerLeft(), font, text, x, y,
                color, shadow);
    }

    @Inject(method = "renderContents", at = @At(value = "INVOKE", ordinal = 3, shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"))
    private void typinganimation$appendCaretDone(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                                 CallbackInfo ci) {
        TypingRenderer.caretDrawn(typinganimation$animation());
    }

    @Inject(method = "renderContents", at = @At("TAIL"))
    private void typinganimation$end(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                     CallbackInfo ci) {
        TypingRenderer.end(typinganimation$animation(), this, font, textShadow);
    }
}
