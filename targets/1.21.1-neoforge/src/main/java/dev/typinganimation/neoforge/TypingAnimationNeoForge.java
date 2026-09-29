package dev.typinganimation.neoforge;

import dev.typinganimation.core.TypingAnimationMod;
import dev.typinganimation.mc.ClientInit;
import dev.typinganimation.mc.ConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * NeoForge entrypoint. {@code dist = Dist.CLIENT}: never constructed on a dedicated server. Mixins are registered
 * through {@code [[mixins]]} in neoforge.mods.toml.
 */
@Mod(value = TypingAnimationMod.MOD_ID, dist = Dist.CLIENT)
public final class TypingAnimationNeoForge {
    public TypingAnimationNeoForge(ModContainer container) {
        ClientInit.init(FMLPaths.CONFIGDIR.get());
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (modContainer, parent) -> new ConfigScreen(parent));
    }
}
