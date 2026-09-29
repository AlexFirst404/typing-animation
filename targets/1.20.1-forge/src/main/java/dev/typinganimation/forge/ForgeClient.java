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

    static void init() {
        ClientInit.init(FMLPaths.CONFIGDIR.get());
        // The (BiFunction<Minecraft, Screen, Screen>) constructor is the one present in Forge 46, 47 and NeoForge
        // 47.1 (the Function<Screen, Screen> one exists only in Forge 47.4.x).
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ConfigScreen(parent)));
    }
}
