package dev.typinganimation.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Dev self-test access to real vanilla controls, not a replacement editor. */
@Mixin(BookEditScreen.class)
public interface BookTestAccessor {
    @Accessor("pages") List<String> typinganimation$pages();
    @Accessor("pageEdit") TextFieldHelper typinganimation$pageEdit();
    @Accessor("currentPage") int typinganimation$page();
    @Accessor("title") String typinganimation$title();
    @Accessor("signButton") Button typinganimation$signButton();
    @Accessor("cancelButton") Button typinganimation$cancelButton();
}
