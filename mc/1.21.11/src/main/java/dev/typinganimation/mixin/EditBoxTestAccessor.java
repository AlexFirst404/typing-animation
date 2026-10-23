package dev.typinganimation.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Mapping-safe read access used only by the opt-in self-test. */
@Mixin(EditBox.class)
public interface EditBoxTestAccessor {
    @Accessor("suggestion")
    String typinganimation$getSuggestion();
}
