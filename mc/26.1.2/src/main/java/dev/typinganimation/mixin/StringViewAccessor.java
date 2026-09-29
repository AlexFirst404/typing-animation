package dev.typinganimation.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads {@code MultilineTextField.StringView} (a protected nested record that shared code can not name) without
 * access wideners / access transformers, which differ per loader.
 */
@Mixin(targets = "net.minecraft.client.gui.components.MultilineTextField$StringView")
public interface StringViewAccessor {
    @Accessor("beginIndex")
    int typinganimation$beginIndex();

    @Accessor("endIndex")
    int typinganimation$endIndex();
}
