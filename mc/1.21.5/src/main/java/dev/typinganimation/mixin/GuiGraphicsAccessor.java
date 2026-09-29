package dev.typinganimation.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The buffer source a {@link GuiGraphics} draws into. 1.21.4/1.21.5 no longer have the public
 * {@code bufferSource()} getter of 1.21.1; glyphs must still go into this exact buffer (not a global one) so they
 * batch and flush in order with the vanilla draws of the same GuiGraphics.
 */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {
    @Accessor("bufferSource")
    MultiBufferSource.BufferSource typinganimation$bufferSource();
}
