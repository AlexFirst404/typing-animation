package dev.typinganimation.mixin;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Mapping-safe read access used only by the opt-in self-test. */
@Mixin(MultiLineEditBox.class)
public interface MultiLineTestAccessor {
    @Accessor("textField")
    MultilineTextField typinganimation$getTextField();
}
