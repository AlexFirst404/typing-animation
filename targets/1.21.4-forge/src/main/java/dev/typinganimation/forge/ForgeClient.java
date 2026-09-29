package dev.typinganimation.forge;

import dev.typinganimation.mc.ClientInit;
import dev.typinganimation.mc.ConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/** Client-only half of the Forge entrypoint: config, and the config screen in the mods list. */
final class ForgeClient {
    private ForgeClient() {
    }

    static void init(FMLJavaModLoadingContext context) {
        ClientInit.init(FMLPaths.CONFIGDIR.get());
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ConfigScreen(parent)));
    }
}
