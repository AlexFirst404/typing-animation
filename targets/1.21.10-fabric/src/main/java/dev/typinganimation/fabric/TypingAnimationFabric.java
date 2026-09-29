package dev.typinganimation.fabric;

import dev.typinganimation.mc.ClientInit;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric client entrypoint (the mod is client-only: "environment": "client"). Rendering hooks are mixins. */
public final class TypingAnimationFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientInit.init(FabricLoader.getInstance().getConfigDir());
    }
}
