package dev.typinganimation.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint. Forge's {@code @Mod} has no dist attribute: everything client-side is behind the
 * {@link FMLEnvironment#dist} guard in a separate class, so nothing client-only loads on a dedicated server
 * (mods.toml also marks the mod clientSideOnly). Rendering hooks are mixins (jar manifest MixinConfigs).
 */
@Mod("typinganimation")
public final class TypingAnimationForge {
    public TypingAnimationForge(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist.isClient()) {
            ForgeClient.init(context);
        }
    }
}
