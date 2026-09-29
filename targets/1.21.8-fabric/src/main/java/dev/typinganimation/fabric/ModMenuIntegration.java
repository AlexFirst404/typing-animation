package dev.typinganimation.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.typinganimation.mc.ConfigScreen;

/** Optional Mod Menu integration (Mod Menu is compile-only; this entrypoint is only used when it is installed). */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::new;
	}
}
