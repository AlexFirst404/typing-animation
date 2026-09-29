package dev.typinganimation.mc;

import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.TypingAnimationMod;

import java.nio.file.Path;

/** Common client initialisation, called by every loader entrypoint with the loader's config directory. */
public final class ClientInit {
    private static boolean initialized;

    private ClientInit() {
    }

    /** Idempotent; never throws. */
    public static synchronized void init(Path configDir) {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            ConfigManager.init(configDir);
            TypingAnimationMod.LOGGER.info("[{}] {} initialised (config: {})", TypingAnimationMod.MOD_ID,
                    TypingAnimationMod.MOD_NAME, ConfigManager.path());
            if (SelfTest.ENABLED) {
                TypingAnimationMod.LOGGER.info("[{}] self-test enabled", TypingAnimationMod.MOD_ID);
            }
        } catch (Throwable t) {
            TypingAnimationMod.LOGGER.error("[{}] Initialisation failed; using default settings",
                    TypingAnimationMod.MOD_ID, t);
        }
    }
}
