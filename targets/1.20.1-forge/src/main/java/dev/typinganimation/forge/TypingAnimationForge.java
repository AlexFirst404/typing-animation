package dev.typinganimation.forge;

import dev.typinganimation.core.TypingAnimationMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge / NeoForge 1.20.x entrypoint. Forge's @Mod has no dist filter and only Forge 47.4.x understands the
 * mods.toml clientSideOnly flag (Forge 46 and NeoForge 47.1 ignore it), so everything client-side is behind the
 * FMLEnvironment.dist guard and lives in {@link ForgeClient} (never loaded on a dedicated server). Keep the
 * NO-ARG constructor: constructor injection of FMLJavaModLoadingContext exists only in Forge 47.4.x, not in Forge 46
 * (MC 1.20) or NeoForge 47.1 (MC 1.20.1). Mixins are registered by the MixinConfigs manifest attribute (build.gradle).
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
