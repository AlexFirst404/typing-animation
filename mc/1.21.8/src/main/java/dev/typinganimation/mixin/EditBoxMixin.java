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
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiFunction;

/**
 * Animated text for {@link EditBox} (Minecraft 1.21.6 - 1.21.8, identical in vanilla, NeoForge and Forge). Only
 * argument modifiers and injections are used (no redirects), so other mods hooking the same calls keep working.
 * All hooks are in {@code renderWidget(GuiGraphics, int, int, float)}:
 * <ul>
 *   <li>HEAD: begin the frame;</li>
 *   <li>{@code formatter.apply(String, Integer)} ({@code BiFunction#apply}, ordinals 0/1): remember each segment's
 *       plain text and start index;</li>
 *   <li>{@code GuiGraphics#drawString(Font, FormattedCharSequence, int, int, int, boolean)} (ordinals 0/1 =
 *       before/after the caret): the renderer lays the chars out and either lets vanilla draw (idle) or draws
 *       them itself and hands vanilla an empty sequence;</li>
 *   <li>{@code drawString(Font, String, ...)} ordinal 0 (the grey suggestion) and the selection highlight: x moved
 *       with the text while a scroll glides. The highlight is {@code GuiGraphics#textHighlight(IIII)} in 1.21.7+
 *       and the private {@code EditBox#renderHighlight(GuiGraphics, IIII)} in 1.21.6 (Fabric production:
 *       intermediary {@code method_1886}; see the note at the highlight handlers);</li>
 *   <li>caret: {@code fill(IIIII)} (insert bar, the only plain fill here) and {@code drawString(Font, String, ...)}
 *       ordinal 1 ({@code "_"}): when the caret glides, vanilla gets a transparent colour and the renderer draws
 *       the moved caret right after the call;</li>
 *   <li>TAIL: chars revealed by a scroll glide, ghosts, end of frame.</li>
 * </ul>
 */
@Mixin(EditBox.class)
public abstract class EditBoxMixin implements TypingStateHolder, EditBoxAccess {
    @Unique
    private static final String DRAW_SEQ =
            "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V";
    @Unique
    private static final String DRAW_STR =
            "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V";
    @Unique
    private static final String FORMAT = "Ljava/util/function/BiFunction;apply(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
    @Unique
    private static final String FILL = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V";
    /** 1.21.7+. */
    @Unique
    private static final String TEXT_HIGHLIGHT = "Lnet/minecraft/client/gui/GuiGraphics;textHighlight(IIII)V";
    /** 1.21.6 (Mojang names: NeoForge, Forge, Fabric dev). */
    @Unique
    private static final String RENDER_HIGHLIGHT =
            "Lnet/minecraft/client/gui/components/EditBox;renderHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V";
    /**
     * 1.21.6, Fabric production (intermediary name of {@code renderHighlight}; Loom can not remap a method that
     * does not exist in the 1.21.8 mappings it builds against, so the intermediary name is given literally).
     */
    @Unique
    private static final String RENDER_HIGHLIGHT_INTERMEDIARY =
            "Lnet/minecraft/client/gui/components/EditBox;method_1886(Lnet/minecraft/client/gui/GuiGraphics;IIII)V";

    @Shadow @Final private Font font;
    @Shadow private String value;
    @Shadow private int displayPos;
    @Shadow private boolean textShadow;
    @Shadow private boolean isEditable;
    @Shadow private int textColor;
    @Shadow private int textColorUneditable;
    @Shadow private int textX;
    @Shadow private int textY;
    @Shadow private BiFunction<String, Integer, FormattedCharSequence> formatter;

    @Shadow public abstract boolean isVisible();

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
        return formatter.apply(text, offset);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void typinganimation$begin(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        WidgetAnimation anim = typinganimation$animation();
        if (isVisible()) {
            TypingRenderer.beginEditBox(anim, (EditBox) (Object) this, graphics, font, textX, textY, displayPos, value,
                    isEditable ? textColor : textColorUneditable);
        }
    }

    // ------------------------------------------------------------------ segments

    @ModifyArg(method = "renderWidget", index = 0, at = @At(value = "INVOKE", ordinal = 0, target = FORMAT))
    private Object typinganimation$captureBefore(Object text, Object offset) {
        typinganimation$capture(0, text, offset);
        return text;
    }

    @ModifyArg(method = "renderWidget", index = 0, at = @At(value = "INVOKE", ordinal = 1, target = FORMAT))
    private Object typinganimation$captureAfter(Object text, Object offset) {
        typinganimation$capture(1, text, offset);
        return text;
    }

    @Unique
    private void typinganimation$capture(int slot, Object text, Object offset) {
        if (text instanceof String s && offset instanceof Integer i) {
            TypingRenderer.captureSegment(typinganimation$animation(), slot, s, i);
        }
    }

    @ModifyArg(method = "renderWidget", index = 1, at = @At(value = "INVOKE", ordinal = 0, target = DRAW_SEQ))
    private FormattedCharSequence typinganimation$textBefore(Font font, FormattedCharSequence seq, int x, int y, int color,
                                                             boolean shadow) {
        return TypingRenderer.editBoxSegment(typinganimation$animation(), this, 0, font, seq, x, y, color, shadow);
    }

    @ModifyArg(method = "renderWidget", index = 1, at = @At(value = "INVOKE", ordinal = 1, target = DRAW_SEQ))
    private FormattedCharSequence typinganimation$textAfter(Font font, FormattedCharSequence seq, int x, int y, int color,
                                                            boolean shadow) {
        return TypingRenderer.editBoxSegment(typinganimation$animation(), this, 1, font, seq, x, y, color, shadow);
    }

    // ------------------------------------------------------------------ suggestion + selection (scroll glide)

    @ModifyArg(method = "renderWidget", index = 2, at = @At(value = "INVOKE", ordinal = 0, target = DRAW_STR))
    private int typinganimation$suggestionX(int x) {
        return TypingRenderer.shiftedX(typinganimation$animation(), x, false);
    }

    // Selection highlight x0/x1: one of three alternatives exists per version/runtime; each group needs one.

    @Group(name = "typinganimation$highlightX0", min = 1)
    @ModifyArg(method = "renderWidget", index = 0, require = 0, at = @At(value = "INVOKE", target = TEXT_HIGHLIGHT))
    private int typinganimation$highlightX0(int x) {
        return TypingRenderer.shiftedX(typinganimation$animation(), x, true);
    }

    @Group(name = "typinganimation$highlightX1", min = 1)
    @ModifyArg(method = "renderWidget", index = 2, require = 0, at = @At(value = "INVOKE", target = TEXT_HIGHLIGHT))
    private int typinganimation$highlightX1(int x) {
        return TypingRenderer.shiftedX(typinganimation$animation(), x, true);
    }

    @Group(name = "typinganimation$highlightX0", min = 1)
    @ModifyArg(method = "renderWidget", index = 1, require = 0, at = @At(value = "INVOKE", target = RENDER_HIGHLIGHT))
    private int typinganimation$highlightX0Old(int x) {
        return TypingRenderer.shiftedX(typinganimation$animation(), x, true);
    }

    @Group(name = "typinganimation$highlightX1", min = 1)
    @ModifyArg(method = "renderWidget", index = 3, require = 0, at = @At(value = "INVOKE", target = RENDER_HIGHLIGHT))
    private int typinganimation$highlightX1Old(int x) {
        return TypingRenderer.shiftedX(typinganimation$animation(), x, true);
    }

    @Group(name = "typinganimation$highlightX0", min = 1)
    @ModifyArg(method = "renderWidget", index = 1, require = 0,
            at = @At(value = "INVOKE", target = RENDER_HIGHLIGHT_INTERMEDIARY))
    private int typinganimation$highlightX0Intermediary(int x) {
        return TypingRenderer.shiftedX(typinganimation$animation(), x, true);
    }

    @Group(name = "typinganimation$highlightX1", min = 1)
    @ModifyArg(method = "renderWidget", index = 3, require = 0,
            at = @At(value = "INVOKE", target = RENDER_HIGHLIGHT_INTERMEDIARY))
    private int typinganimation$highlightX1Intermediary(int x) {
        return TypingRenderer.shiftedX(typinganimation$animation(), x, true);
    }

    // ------------------------------------------------------------------ caret

    @ModifyArg(method = "renderWidget", index = 4, at = @At(value = "INVOKE", target = FILL))
    private int typinganimation$insertCaret(int x0, int y0, int x1, int y1, int color) {
        return TypingRenderer.editBoxCaret(typinganimation$animation(), this, WidgetAnimation.CARET_INSERT, null, x0, y0,
                x1, y1, color, false);
    }

    @Inject(method = "renderWidget", at = @At(value = "INVOKE", target = FILL, shift = At.Shift.AFTER))
    private void typinganimation$insertCaretDone(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                                 CallbackInfo ci) {
        TypingRenderer.caretDrawn(typinganimation$animation());
    }

    @ModifyArg(method = "renderWidget", index = 4, at = @At(value = "INVOKE", ordinal = 1, target = DRAW_STR))
    private int typinganimation$appendCaret(Font font, String str, int x, int y, int color, boolean shadow) {
        if (!"_".equals(str)) {
            return color; // not the append caret (another mod changed the method): leave it alone
        }
        return TypingRenderer.editBoxCaret(typinganimation$animation(), this, WidgetAnimation.CARET_APPEND, font, x, y,
                0, 0, color, shadow);
    }

    @Inject(method = "renderWidget", at = @At(value = "INVOKE", ordinal = 1, target = DRAW_STR, shift = At.Shift.AFTER))
    private void typinganimation$appendCaretDone(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                                 CallbackInfo ci) {
        TypingRenderer.caretDrawn(typinganimation$animation());
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void typinganimation$end(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TypingRenderer.endEditBox(typinganimation$animation(), this, font, textShadow);
    }
}
