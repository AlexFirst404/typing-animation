package dev.typinganimation.neoforge;

import dev.typinganimation.mc.ClientInit;
import dev.typinganimation.mc.ConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * NeoForge 26.2 entrypoint (client only: FML does not construct this class on a dedicated server because of
 * {@code dist = Dist.CLIENT}): config + mods-list config screen. Rendering hooks are mixins.
 */
@Mod(value = "typinganimation", dist = Dist.CLIENT)
public final class TypingAnimationNeoForge {
    public TypingAnimationNeoForge(ModContainer container) {
        ClientInit.init(FMLPaths.CONFIGDIR.get());
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (mod, parent) -> new ConfigScreen(parent));
    }
}
