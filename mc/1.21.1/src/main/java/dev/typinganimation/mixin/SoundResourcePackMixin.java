package dev.typinganimation.mixin;

import dev.typinganimation.mc.ClasspathSoundPack;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/** Client mixin: keeps audio available with bare Fabric/Quilt and preserves resource-pack priority. */
@Mixin(MultiPackResourceManager.class)
public abstract class SoundResourcePackMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static List<PackResources> typinganimation$bundledSounds(
            List<PackResources> packs, PackType type, List<PackResources> originalPacks) {
        return ClasspathSoundPack.includeIfMissing(type, packs);
    }
}

