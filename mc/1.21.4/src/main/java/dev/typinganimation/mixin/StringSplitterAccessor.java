package dev.typinganimation.mixin;

import net.minecraft.client.StringSplitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exact float advance of one code point in one style, allocation-free (the same provider the Font draws with);
 * {@code StringSplitter#stringWidth} would allocate a sink per call.
 */
@Mixin(StringSplitter.class)
public interface StringSplitterAccessor {
    @Accessor("widthProvider")
    StringSplitter.WidthProvider typinganimation$widthProvider();
}
