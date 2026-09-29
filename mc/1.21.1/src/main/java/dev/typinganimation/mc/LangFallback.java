package dev.typinganimation.mc;

import dev.typinganimation.core.TypingAnimationMod;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Makes our bundled translations available when no resource pack provides them. NeoForge and Forge load mod assets
 * as resource packs, and so does Fabric with Fabric API; plain Fabric Loader (this mod does not require Fabric API)
 * does not, which would leave every label as its raw key. Only in that case the language that the game installs is
 * wrapped: keys the game knows are answered by it, our keys from the jar's lang files (en_us layered with the
 * selected language). Resource packs that translate our keys therefore still win.
 */
public final class LangFallback {
    /** A key that is present whenever our assets were loaded by the game. */
    private static final String PROBE_KEY = "typinganimation.title";
    private static Language lastDelegate;
    private static Language lastResult;

    private LangFallback() {
    }

    /** The language to install instead of {@code language} (itself when our keys are already there). */
    public static synchronized Language wrapIfMissing(Language language, String selectedCode) {
        if (language == null || language instanceof Wrapper) {
            return language;
        }
        if (language == lastDelegate) {
            return lastResult; // I18n and Language.inject receive the same instance
        }
        Language result = language;
        try {
            if (!language.has(PROBE_KEY)) {
                Map<String, String> ours = new HashMap<>();
                load("en_us", ours);
                if (selectedCode != null && !"en_us".equals(selectedCode)) {
                    load(selectedCode.toLowerCase(Locale.ROOT), ours);
                }
                if (!ours.isEmpty()) {
                    result = new Wrapper(language, ours);
                }
            }
        } catch (RuntimeException e) {
            TypingAnimationMod.LOGGER.warn("[{}] Could not load bundled translations", TypingAnimationMod.MOD_ID, e);
        }
        lastDelegate = language;
        lastResult = result;
        return result;
    }

    private static void load(String code, Map<String, String> into) {
        String path = "/assets/" + TypingAnimationMod.MOD_ID + "/lang/" + code + ".json";
        try (InputStream in = LangFallback.class.getResourceAsStream(path)) {
            if (in != null) {
                Language.loadFromJson(in, into::put);
            }
        } catch (Exception e) {
            TypingAnimationMod.LOGGER.warn("[{}] Could not read {}", TypingAnimationMod.MOD_ID, path, e);
        }
    }

    /** Delegating language with our translations as a fallback. */
    private static final class Wrapper extends Language {
        private final Language delegate;
        private final Map<String, String> fallback;

        Wrapper(Language delegate, Map<String, String> fallback) {
            this.delegate = delegate;
            this.fallback = fallback;
        }

        @Override
        public String getOrDefault(String key, String defaultValue) {
            if (!delegate.has(key)) {
                String v = fallback.get(key);
                if (v != null) {
                    return v;
                }
            }
            return delegate.getOrDefault(key, defaultValue);
        }

        @Override
        public boolean has(String key) {
            return delegate.has(key) || fallback.containsKey(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return delegate.isDefaultRightToLeft();
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return delegate.getVisualOrder(text);
        }
    }
}
