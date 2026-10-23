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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Checks the shared lang files in ../common-resources (skipped when core is built on its own). */
class LangFilesTest {
    private static final Path LANG = Path.of("..", "common-resources", "assets", "typinganimation", "lang");
    private static final String[] UI_FIELDS = {"enabled", "appearStyle", "removeStyle", "easing", "durationMs",
            "intensity", "staggerMs", "glideMs", "smoothReflow", "smoothCursor", "animateChat", "animateOtherFields",
            "animateMultiline", "optionsButton", "removeDurationMs", "removeIntensity", "maxStaggerMs",
            "cursorGlideMs", "cursorStyle", "cursorColor", "cursorWidth", "animateSigns", "onlyWhenFocused",
            "typingSounds", "soundPreset", "soundVolume", "soundPitch", "soundVariation", "soundOnDelete", "soundOnEnter"};

    private static List<String> requiredKeys() {
        List<String> keys = new ArrayList<>(List.of("typinganimation.title", "typinganimation.description",
                "typinganimation.options.button", "typinganimation.config.preview.hint",
                "typinganimation.config.demo", "typinganimation.config.demo.stop",
                "typinganimation.config.demo.text", "typinganimation.config.reset", "typinganimation.config.done",
                "typinganimation.config.ms", "typinganimation.config.ms_per_char",
                "typinganimation.config.multiplier", "typinganimation.config.pixels",
                "typinganimation.config.page.animations", "typinganimation.config.page.cursor",
                "typinganimation.config.page.behaviour", "typinganimation.config.page.sound",
                "typinganimation.config.visualPreset", "typinganimation.config.visualPreset.custom",
                "typinganimation.config.visualPreset.tooltip", "typinganimation.config.applyPreset",
                "typinganimation.config.applyPreset.tooltip", "typinganimation.config.soundTest",
                "typinganimation.config.soundTest.tooltip", "typinganimation.config.percent"));
        for (String f : UI_FIELDS) {
            keys.add("typinganimation.config." + f);
            keys.add("typinganimation.config." + f + ".tooltip");
        }
        for (AppearStyle s : AppearStyle.values()) keys.add(s.translationKey());
        for (RemoveStyle s : RemoveStyle.values()) keys.add(s.translationKey());
        for (Easing e : Easing.values()) keys.add(e.translationKey());
        for (CursorStyle s : CursorStyle.values()) keys.add(s.translationKey());
        for (CursorColor c : CursorColor.values()) keys.add(c.translationKey());
        for (SoundPreset s : SoundPreset.values()) {
            keys.add(s.translationKey());
            for (TypingSoundAction action : TypingSoundAction.values()) {
                keys.add(s.translationKey() + "." + action.path());
            }
        }
        for (VisualPreset p : VisualPreset.values()) keys.add(p.translationKey());
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

    @Test
    void everySoundEventHasTranslatedSubtitlesAndAllOriginalSamples() throws IOException {
        Path assets = LANG.getParent().toAbsolutePath().normalize();
        assumeTrue(Files.isDirectory(assets), "shared resources not present: " + assets);
        Path manifest = assets.resolve("sounds.json");
        assertTrue(Files.isRegularFile(manifest), "typing sound manifest is packaged");
        JsonObject events = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject en = load("en_us.json"), ru = load("ru_ru.json");
        assertEquals(16, events.size(), "four keyboard families each expose four input actions");
        Path sounds = assets.resolve("sounds");
        Set<Path> samples = new HashSet<>();
        for (SoundPreset preset : SoundPreset.values()) {
            for (TypingSoundAction action : TypingSoundAction.values()) {
                String eventId = "typing." + preset.name().toLowerCase(Locale.ROOT) + "." + action.path();
                assertTrue(events.has(eventId), "missing sound event " + eventId);
                JsonObject event = events.getAsJsonObject(eventId);
                String subtitle = preset.translationKey() + "." + action.path();
                assertEquals(subtitle, event.get("subtitle").getAsString(), eventId);
                assertTrue(en.has(subtitle) && ru.has(subtitle), "subtitle must be translated: " + subtitle);
                assertFalse(en.get(subtitle).getAsString().isBlank());
                assertFalse(ru.get(subtitle).getAsString().isBlank());
                int count = switch (action) {
                    case KEY -> 4;
                    case SPACE, BACKSPACE -> 2;
                    case ENTER -> 1;
                };
                var references = event.getAsJsonArray("sounds");
                assertEquals(count, references.size(), eventId + " sample variants");
                for (JsonElement reference : references) {
                    String resource = reference.getAsString();
                    String prefix = "typinganimation:";
                    assertTrue(resource.startsWith(prefix), "sample must use this mod's namespace: " + resource);
                    String relative = resource.substring(prefix.length());
                    Path sample = sounds.resolve(relative + ".ogg").normalize();
                    assertTrue(sample.startsWith(sounds), "sample cannot escape its namespace: " + resource);
                    assertTrue(Files.isRegularFile(sample), "missing packaged sample " + resource);
                    assertTrue(samples.add(sample), "each variant references a distinct original sample: " + resource);
                    byte[] bytes = Files.readAllBytes(sample);
                    assertTrue(bytes.length > 4, "empty sound sample " + resource);
                    assertEquals("OggS", new String(bytes, 0, 4, StandardCharsets.US_ASCII), resource);
                }
            }
        }
        assertEquals(36, samples.size(), "all original samples are reachable through sound events");
        Set<String> fallbackPaths = new HashSet<>();
        fallbackPaths.add("sounds.json");
        for (Path sample : samples) fallbackPaths.add(assets.relativize(sample).toString().replace('\\', '/'));
        assertEquals(fallbackPaths, new HashSet<>(BundledSoundResources.PATHS),
                "bare-loader fallback exposes exactly the manifest and every referenced sample");
        assertEquals(fallbackPaths.size(), BundledSoundResources.PATHS.size(), "fallback contains no duplicate paths");
        try (var files = Files.walk(sounds)) {
            assertEquals(samples, files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".ogg")).collect(java.util.stream.Collectors.toSet()),
                    "no unused or accidentally omitted Ogg files");
        }
    }
}
