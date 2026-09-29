package dev.typinganimation.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Checks the shared lang files in ../common-resources (skipped when core is built on its own). */
class LangFilesTest {
    private static final Path LANG = Path.of("..", "common-resources", "assets", "typinganimation", "lang");
    private static final String[] UI_FIELDS = {"enabled", "appearStyle", "removeStyle", "easing", "durationMs",
            "intensity", "staggerMs", "glideMs", "smoothReflow", "smoothCursor", "animateChat", "animateOtherFields",
            "animateMultiline", "optionsButton"};

    private static List<String> requiredKeys() {
        List<String> keys = new ArrayList<>(List.of("typinganimation.title", "typinganimation.description",
                "typinganimation.options.button", "typinganimation.config.preview.hint",
                "typinganimation.config.demo", "typinganimation.config.demo.stop",
                "typinganimation.config.demo.text", "typinganimation.config.reset", "typinganimation.config.done",
                "typinganimation.config.ms", "typinganimation.config.ms_per_char",
                "typinganimation.config.multiplier"));
        for (String f : UI_FIELDS) {
            keys.add("typinganimation.config." + f);
            keys.add("typinganimation.config." + f + ".tooltip");
        }
        for (AppearStyle s : AppearStyle.values()) keys.add(s.translationKey());
        for (RemoveStyle s : RemoveStyle.values()) keys.add(s.translationKey());
        for (Easing e : Easing.values()) keys.add(e.translationKey());
        return keys;
    }

    private static JsonObject load(String name) throws IOException {
        Path file = LANG.resolve(name);
        assumeTrue(Files.isRegularFile(file), "lang dir not present: " + file.toAbsolutePath());
        byte[] bytes = Files.readAllBytes(file);
        assertFalse(bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF, name + " must not start with a BOM");
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertFalse(text.contains("�"), name + " must be valid UTF-8");
        // strict syntax + no duplicate keys
        JsonReader strict = new JsonReader(new StringReader(text));
        strict.setLenient(false);
        java.util.Set<String> seen = new java.util.HashSet<>();
        strict.beginObject();
        while (strict.hasNext()) {
            String key = strict.nextName();
            assertTrue(seen.add(key), name + " duplicate key " + key);
            strict.nextString();
        }
        strict.endObject();
        assertEquals(com.google.gson.stream.JsonToken.END_DOCUMENT, strict.peek());
        JsonElement root = JsonParser.parseString(text);
        assertTrue(root.isJsonObject());
        return root.getAsJsonObject();
    }

    @Test
    void bothLanguagesAreCompleteAndConsistent() throws IOException {
        JsonObject en = load("en_us.json");
        JsonObject ru = load("ru_ru.json");
        List<String> required = requiredKeys();
        for (String key : required) {
            assertTrue(en.has(key), "en_us missing " + key);
            assertTrue(ru.has(key), "ru_ru missing " + key);
        }
        // no dead keys: every translation is used by the UI (json-only config fields have none)
        for (String key : en.keySet()) {
            assertTrue(required.contains(key), "en_us has a key that nothing uses: " + key);
        }
        assertEquals(en.keySet(), ru.keySet(), "both files must define the same keys");
        for (Map.Entry<String, JsonElement> e : en.entrySet()) {
            assertTrue(e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString(), e.getKey());
            assertFalse(e.getValue().getAsString().isBlank(), e.getKey());
            String ruValue = ru.get(e.getKey()).getAsString();
            assertFalse(ruValue.isBlank(), e.getKey());
            assertEquals(count(e.getValue().getAsString(), "%s"), count(ruValue, "%s"), "placeholders of " + e.getKey());
        }
        assertTrue(en.get("typinganimation.config.ms").getAsString().contains("%s"));
        assertTrue(ru.get("typinganimation.config.demo.text").getAsString().matches(".*[а-яё].*"),
                "Russian demo text must be Russian");
    }

    private static int count(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + 1)) n++;
        return n;
    }
}
