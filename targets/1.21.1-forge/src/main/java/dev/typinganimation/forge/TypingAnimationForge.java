package dev.typinganimation.forge;

import dev.typinganimation.core.TypingAnimationMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint. Forge's @Mod has no dist filter, so everything client-side is behind the FMLEnvironment.dist
 * guard and lives in {@link ForgeClient} (never loaded on a dedicated server; mods.toml also sets
 * clientSideOnly=true and displayTest=IGNORE_SERVER_VERSION). Keep the NO-ARG constructor: Forge 51 (MC 1.21,
 * inside the declared range) only calls a no-arg constructor. Mixins are registered by the MixinConfigs manifest
 * attribute (build.gradle).
 */
@Mod(TypingAnimationMod.MOD_ID)
public final class TypingAnimationForge {
    public TypingAnimationForge() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            TypingAnimationMod.LOGGER.warn("[{}] client-only mod loaded on a dedicated server; doing nothing",
                    TypingAnimationMod.MOD_ID);
            return;
        }
        ForgeClient.init();
    }
}
