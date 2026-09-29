package dev.typinganimation.forge;

import dev.typinganimation.mc.ClientInit;
import dev.typinganimation.mc.ConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/** Client-only half of the Forge entrypoint: config, and the config screen in the mods list. */
final class ForgeClient {
    private ForgeClient() {
    }

    // ModLoadingContext.get() is deprecated in Forge 52, but Forge 51 (MC 1.21) only offers it (no-arg @Mod
    // constructor, no FMLJavaModLoadingContext parameter), so it is the one call that works across the range.
    @SuppressWarnings("removal")
    static void init() {
        ClientInit.init(FMLPaths.CONFIGDIR.get());
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ConfigScreen(parent)));
    }
}
