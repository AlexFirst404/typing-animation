package dev.typinganimation.mc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.typinganimation.core.TypingAnimationMod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Makes our lang files work without a loader resource pack for mod assets (Fabric without Fabric API does not
 * register mod assets as resource packs). While the client language is built (ClientLanguageMixin), our
 * {@code assets/typinganimation/lang/<code>.json} entries are added from the mod jar with {@code putIfAbsent}: keys
 * that resource packs (or the loader) already provided win, so on NeoForge/Forge this is a no-op.
 */
public final class BuiltinTranslations {
    private static final String ROOT = "/assets/" + TypingAnimationMod.MOD_ID + "/lang/";
    private static final ThreadLocal<List<String>> STACK = new ThreadLocal<>();

    private BuiltinTranslations() {
    }

    /** Start of {@code ClientLanguage#loadFrom}: remembers the language stack (lowest priority first). */
    public static void begin(List<String> languageStack) {
        STACK.set(languageStack);
    }

    /** The translation map is complete (before it is frozen): adds our missing keys. Never throws. */
    public static void addMissing(Map<String, String> translations) {
        List<String> stack = STACK.get();
        STACK.remove();
        try {
            if (stack == null || stack.isEmpty()) {
                stack = List.of("en_us");
            }
            // highest priority (last) first, putIfAbsent: the selected language wins over en_us
            for (int i = stack.size() - 1; i >= 0; i--) {
                load(stack.get(i), translations);
            }
        } catch (Throwable t) {
            TypingAnimationMod.LOGGER.warn("[{}] Could not add built-in translations", TypingAnimationMod.MOD_ID, t);
        }
    }

    private static void load(String code, Map<String, String> out) throws Exception {
        if (code == null) {
            return;
        }
        String path = ROOT + code.toLowerCase(Locale.ROOT) + ".json";
        try (InputStream in = BuiltinTranslations.class.getResourceAsStream(path)) {
            if (in == null) {
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) {
                    return;
                }
                JsonObject obj = root.getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        out.putIfAbsent(e.getKey(), e.getValue().getAsString());
                    }
                }
            }
        }
    }
}
