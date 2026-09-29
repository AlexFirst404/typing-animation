package dev.typinganimation.mc;

import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.TypingAnimationMod;

import java.nio.file.Path;
import java.util.Locale;

/** Common client initialisation, called by every loader entrypoint with the loader's config directory. */
public final class ClientInit {
    /** Dev self-test switch: env {@code TYPINGANIMATION_SELFTEST=1} or {@code -Dtypinganimation.selftest=true}. */
    public static final boolean SELFTEST = flag(System.getenv("TYPINGANIMATION_SELFTEST"))
            || Boolean.getBoolean("typinganimation.selftest");
    /** Dev showcase recorder (gallery media): env {@code TYPINGANIMATION_SHOWCASE=1}; never together with the self-test. */
    public static final boolean SHOWCASE = !SELFTEST && flag(System.getenv("TYPINGANIMATION_SHOWCASE"));

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
            if (SELFTEST) {
                TypingAnimationMod.LOGGER.info("[{}] self-test enabled", TypingAnimationMod.MOD_ID);
            }
            if (SHOWCASE) {
                TypingAnimationMod.LOGGER.info("[{}] showcase recorder enabled", TypingAnimationMod.MOD_ID);
            }
        } catch (Throwable t) {
            TypingAnimationMod.LOGGER.error("[{}] Initialisation failed; using default settings",
                    TypingAnimationMod.MOD_ID, t);
        }
    }

    static boolean flag(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("1") || v.equals("true") || v.equals("yes");
    }
}
