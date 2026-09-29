package dev.typinganimation.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The GUI buffer source every {@code GuiGraphics#drawString} batches into. 1.21.4 has no public getter any more
 * (1.21.1 had {@code bufferSource()}); glyphs must go through {@code Font#drawInBatch} with a float x and the
 * unmodified pose to land on exactly the pixels vanilla uses, which needs the buffer source itself.
 */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {
    @Accessor("bufferSource")
    MultiBufferSource.BufferSource typinganimation$bufferSource();
}
