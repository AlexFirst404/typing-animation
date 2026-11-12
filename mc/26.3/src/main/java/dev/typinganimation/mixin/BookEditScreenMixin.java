package dev.typinganimation.mixin;

import dev.typinganimation.mc.BookPageHooks;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Page navigation must not look like insertion/deletion in the page being left. */
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin {
    @Shadow private MultiLineEditBox page;

    @Inject(method = "updatePageContent", at = @At("TAIL"))
    private void typinganimation$pageChanged(CallbackInfo ci) {
        BookPageHooks.pageChanged(page);
    }
}
