package dev.typinganimation.mixin;

import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * The laid-out lines of a multi-line text field. The element type ({@code MultilineTextField.StringView}) is
 * protected, so it is exposed as {@code List<?>} and read through {@link StringViewAccessor}.
 */
@Mixin(MultilineTextField.class)
public interface MultilineTextFieldAccessor {
    @Accessor("displayLines")
    List<?> typinganimation$displayLines();
}
