package dev.typinganimation.fabric;

import dev.typinganimation.mc.ClientInit;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric client entrypoint (fabric.mod.json: "environment": "client"; no Fabric API needed). Mixins are listed in
 * fabric.mod.json.
 */
public final class TypingAnimationFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientInit.init(FabricLoader.getInstance().getConfigDir());
	}
}
