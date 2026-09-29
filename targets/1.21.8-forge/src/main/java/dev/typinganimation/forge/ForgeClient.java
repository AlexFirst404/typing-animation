package dev.typinganimation.forge;

import dev.typinganimation.mc.ClientInit;
import dev.typinganimation.mc.ConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/** Client-side registration (only loaded on the client dist). */
final class ForgeClient {
    private ForgeClient() {
    }

    static void init(FMLJavaModLoadingContext context) {
        ClientInit.init(FMLPaths.CONFIGDIR.get());
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent)));
    }
}
