package dev.typinganimation.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.typinganimation.mc.ConfigScreen;

/** Optional Mod Menu integration (compileOnly; only loaded when Mod Menu is installed). */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::new;
	}
}
