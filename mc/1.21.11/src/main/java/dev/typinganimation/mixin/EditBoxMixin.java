package dev.typinganimation.mixin;

import dev.typinganimation.core.FieldKind;
import dev.typinganimation.mc.EditBoxAccess;
import dev.typinganimation.mc.TypingRenderer;
import dev.typinganimation.mc.TypingStateHolder;
import dev.typinganimation.mc.WidgetAnimation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
 * Animated text for {@link EditBox} ({@code renderWidget}; identical bytecode on vanilla/Fabric, NeoForge 21.11 and
 * Forge 61). Only argument modifiers and injections are used (no redirects), so other mods hooking the same calls
 * keep working:
 * <ul>
 *   <li>HEAD: begin the frame;</li>
 *   <li>{@code applyFormat(String,int)} (ordinals 0/1): remember each segment's plain text and start index;</li>
 *   <li>{@code GuiGraphics#drawString(Font, FormattedCharSequence, int, int, int, boolean)} (ordinals 0/1 =
 *       before/after the caret): the renderer lays the chars out and either lets vanilla draw (idle) or draws
 *       them itself and hands vanilla an empty sequence;</li>
 *   <li>{@code drawString(Font, String, int, int, int, boolean)} ordinal 0 (the grey suggestion) and
 *       {@code textHighlight} (selection): x moved with the text while a scroll glides;</li>
 *   <li>the caret: {@code fill(IIIII)} (insert caret, the only fill call) and {@code drawString(Font, String, ...)}
 *       ordinal 1 (the append caret "_"): when the caret glides, vanilla gets a transparent colour and the
 *       renderer draws the moved caret right after the call;</li>
 *   <li>TAIL: chars revealed by a scroll glide, ghosts, end of frame.</li>
 * </ul>
 */
@Mixin(EditBox.class)
public abstract class EditBoxMixin implements TypingStateHolder, EditBoxAccess {
    @Unique
    private static final String DRAW_SEQUENCE =
            "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V";
    @Unique
    private static final String DRAW_STRING =
            "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V";
    @Unique
    private static final String FILL = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V";
    @Unique
    private static final String APPLY_FORMAT =
            "Lnet/minecraft/client/gui/components/EditBox;applyFormat(Ljava/lang/String;I)Lnet/minecraft/util/FormattedCharSequence;";
    @Unique
    private static final String HIGHLIGHT = "Lnet/minecraft/client/gui/GuiGraphics;textHighlight(IIIIZ)V";

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

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void typinganimation$begin(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                       CallbackInfo ci) {
        WidgetAnimation anim = typinganimation$animation();
        if (isVisible()) {
            TypingRenderer.beginEditBox(anim, (EditBox) (Object) this, graphics, font, textX, textY, displayPos, value,
                    isEditable ? textColor : textColorUneditable);
        }
    }

    @ModifyArg(method = "renderWidget", index = 0, at = @At(value = "INVOKE", ordinal = 0, target = APPLY_FORMAT))
    private String typinganimation$captureBefore(String text, int offset) {
        TypingRenderer.captureSegment(typinganimation$animation(), 0, text, offset);
        return text;
    }

    @ModifyArg(method = "renderWidget", index = 0, at = @At(value = "INVOKE", ordinal = 1, target = APPLY_FORMAT))
    private String typinganimation$captureAfter(String text, int offset) {
        TypingRenderer.captureSegment(typinganimation$animation(), 1, text, offset);
        return text;
    }

    @ModifyArg(method = "renderWidget", index = 1, at = @At(value = "INVOKE", ordinal = 0, target = DRAW_SEQUENCE))
    private FormattedCharSequence typinganimation$textBefore(Font font, FormattedCharSequence seq, int x, int y,
                                                             int color, boolean shadow) {
        return TypingRenderer.editBoxSegment(typinganimation$animation(), this, 0, font, seq, x, y, color, shadow);
    }

    @ModifyArg(method = "renderWidget", index = 1, at = @At(value = "INVOKE", ordinal = 1, target = DRAW_SEQUENCE))
    private FormattedCharSequence typinganimation$textAfter(Font font, FormattedCharSequence seq, int x, int y,
                                                            int color, boolean shadow) {
        return TypingRenderer.editBoxSegment(typinganimation$animation(), this, 1, font, seq, x, y, color, shadow);
    }

    /** The grey suggestion (drawn at the caret while it is at the end of the text). */
    @ModifyArg(method = "renderWidget", index = 2, at = @At(value = "INVOKE", ordinal = 0, target = DRAW_STRING))
    private int typinganimation$suggestionX(int x) {
        return TypingRenderer.shiftedX(typinganimation$animation(), x, false);
    }

    @ModifyArg(method = "renderWidget", index = 0, at = @At(value = "INVOKE", target = HIGHLIGHT))
    private int typinganimation$highlightX0(int x) {
        return TypingRenderer.shiftedX(typinganimation$animation(), x, true);
    }

    @ModifyArg(method = "renderWidget", index = 2, at = @At(value = "INVOKE", target = HIGHLIGHT))
    private int typinganimation$highlightX1(int x) {
        return TypingRenderer.shiftedX(typinganimation$animation(), x, true);
    }

    /** Insert caret: {@code fill(caretX, textY - 1, caretX + 1, textY + 10, color)}; argument 4 = colour. */
    @ModifyArg(method = "renderWidget", index = 4, at = @At(value = "INVOKE", target = FILL))
    private int typinganimation$insertCaret(int x0, int y0, int x1, int y1, int color) {
        return TypingRenderer.editBoxInsertCaret(typinganimation$animation(), this, x0, y0, x1, y1, color);
    }

    @Inject(method = "renderWidget", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = FILL))
    private void typinganimation$insertCaretDone(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                                 CallbackInfo ci) {
        TypingRenderer.caretDrawn(typinganimation$animation());
    }

    /** Append caret: {@code drawString(font, "_", caretX, textY, color, shadow)}; argument 4 = colour. */
    @ModifyArg(method = "renderWidget", index = 4, at = @At(value = "INVOKE", ordinal = 1, target = DRAW_STRING))
    private int typinganimation$appendCaret(Font font, String text, int x, int y, int color, boolean shadow) {
        return TypingRenderer.editBoxAppendCaret(typinganimation$animation(), this, font, text, x, y, color, shadow);
    }

    @Inject(method = "renderWidget", at = @At(value = "INVOKE", ordinal = 1, shift = At.Shift.AFTER,
            target = DRAW_STRING))
    private void typinganimation$appendCaretDone(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                                 CallbackInfo ci) {
        TypingRenderer.caretDrawn(typinganimation$animation());
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void typinganimation$end(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                     CallbackInfo ci) {
        TypingRenderer.endEditBox(typinganimation$animation(), this, font, textShadow);
    }
}
