package dev.typinganimation.mixin;

import dev.typinganimation.core.FieldKind;
import dev.typinganimation.mc.BookAnimation;
import dev.typinganimation.mc.BookStateHolder;
import dev.typinganimation.mc.TypingSounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin extends Screen implements BookStateHolder {
    @Shadow private boolean isSigning;
    @Shadow private int currentPage;
    @Shadow private int frameTick;
    @Shadow private String title;
    @Shadow @Final private TextFieldHelper pageEdit;
    @Shadow @Final private TextFieldHelper titleEdit;
    @Shadow private String getCurrentPageText() { throw new AssertionError(); }
    @Unique private BookAnimation typinganimation$book;
    @Unique private String typinganimation$soundBefore;
    @Unique private int typinganimation$soundPage;
    @Unique private boolean typinganimation$soundSigning;

    private BookEditScreenMixin(Component title) { super(title); }
    @Override public BookAnimation typinganimation$bookAnimation() {
        if (typinganimation$book == null) typinganimation$book = new BookAnimation();
        return typinganimation$book;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void typinganimation$begin(GuiGraphics graphics, int x, int y, float partial, CallbackInfo ci) {
        TextFieldHelper edit = isSigning ? titleEdit : pageEdit;
        typinganimation$bookAnimation().begin(this, font, isSigning ? title : getCurrentPageText(), currentPage,
                isSigning, edit.getCursorPos(), edit.getCursorPos() != edit.getSelectionPos(), width, frameTick);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", ordinal = 3,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"))
    private int typinganimation$line(GuiGraphics graphics, Font font, Component text, int x, int y, int color, boolean shadow) {
        return typinganimation$bookAnimation().line(graphics, font, text, x, y, color, shadow);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I"))
    private int typinganimation$title(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        return typinganimation$bookAnimation().title(graphics, font, text, x, y, color, shadow);
    }

    @Inject(method = "renderCursor", at = @At("HEAD"), cancellable = true)
    private void typinganimation$cursor(GuiGraphics graphics, @Coerce Object pos, boolean end, CallbackInfo ci) {
        BookPosAccessor point = (BookPosAccessor) pos;
        if (typinganimation$bookAnimation().caret(graphics, font, point.typinganimation$x(), point.typinganimation$y(), end)) ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void typinganimation$end(GuiGraphics graphics, int x, int y, float partial, CallbackInfo ci) {
        typinganimation$bookAnimation().end(graphics);
    }

    @Inject(method = {"charTyped", "keyPressed"}, at = @At("HEAD"))
    private void typinganimation$beforeInput(CallbackInfoReturnable<Boolean> ci) {
        typinganimation$soundPage = currentPage;
        typinganimation$soundSigning = isSigning;
        typinganimation$soundBefore = isSigning ? title : getCurrentPageText();
    }

    @Inject(method = {"charTyped", "keyPressed"}, at = @At("RETURN"))
    private void typinganimation$afterInput(CallbackInfoReturnable<Boolean> ci) {
        if (ci.getReturnValueZ() && currentPage == typinganimation$soundPage && isSigning == typinganimation$soundSigning)
            TypingSounds.edited(typinganimation$soundBefore, isSigning ? title : getCurrentPageText(), FieldKind.MULTILINE, false);
        typinganimation$soundBefore = null;
    }
}
