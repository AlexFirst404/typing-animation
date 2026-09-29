package dev.typinganimation.mixin;

import net.minecraft.client.StringSplitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Per-char float advances without allocating (StringSplitter#stringWidth allocates per call). */
@Mixin(StringSplitter.class)
public interface StringSplitterAccessor {
    @Accessor("widthProvider")
    StringSplitter.WidthProvider typinganimation$widthProvider();
}
