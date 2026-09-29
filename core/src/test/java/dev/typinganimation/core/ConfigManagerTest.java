package dev.typinganimation.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    @TempDir
    Path dir;

    @BeforeEach
    @AfterEach
    void clean() {
        ConfigManager.resetForTests();
    }

    private Path file() {
        return dir.resolve("typinganimation.json");
    }

    private void writeFile(String content) throws IOException {
        Files.write(file(), content.getBytes(StandardCharsets.UTF_8));
    }

    private String readFile() throws IOException {
        return new String(Files.readAllBytes(file()), StandardCharsets.UTF_8);
    }

    private static TypingConfig reinit(Path dir) {
        ConfigManager.resetForTests();
        ConfigManager.init(dir);
        return ConfigManager.get();
    }

    @Test
    void getIsNeverNullBeforeInit() {
        assertNotNull(ConfigManager.get());
        assertEquals(TypingConfig.defaults(), ConfigManager.get());
        assertEquals(Path.of("config", "typinganimation.json"), ConfigManager.path());
        assertDoesNotThrow(ConfigManager::save);
        assertDoesNotThrow(ConfigManager::reset);
    }

    @Test
    void initWritesDefaultsWhenMissing() throws IOException {
        Path nested = dir.resolve("a").resolve("b");
        ConfigManager.init(nested);
        Path f = nested.resolve("typinganimation.json");
        assertEquals(f, ConfigManager.path());
        assertTrue(Files.isRegularFile(f));
        assertEquals(TypingConfig.defaults(), ConfigManager.get());
        String json = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        assertTrue(json.contains("\n  \"enabled\": true"), "pretty printed: " + json);
        assertTrue(json.contains("\"appearStyle\": \"slide_up\""), json);
        assertTrue(json.contains("\"removeStyle\": \"fade\""), json);
        assertTrue(json.contains("\"easing\": \"auto\""), json);
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        for (String key : new String[]{"enabled", "appearStyle", "removeStyle", "easing", "durationMs",
                "removeDurationMs", "intensity", "staggerMs", "maxStaggerMs", "smoothReflow", "smoothCursor",
                "glideMs", "animateChat", "animateOtherFields", "animateMultiline", "onlyWhenFocused",
                "optionsButton"}) {
            assertTrue(o.has(key), key);
        }
        assertEquals(17, o.size());
    }

    @Test
    void roundTrip() {
        ConfigManager.init(dir);
        TypingConfig c = ConfigManager.get();
        c.enabled = false;
        c.appearStyle = AppearStyle.SCRAMBLE;
        c.removeStyle = RemoveStyle.SCATTER;
        c.easing = Easing.ELASTIC_OUT;
        c.durationMs = 777;
        c.removeDurationMs = 41;
        c.intensity = 2.75f;
        c.staggerMs = 3;
        c.maxStaggerMs = 1999;
        c.smoothReflow = false;
        c.smoothCursor = false;
        c.glideMs = 0;
        c.animateChat = false;
        c.animateOtherFields = false;
        c.animateMultiline = false;
        c.onlyWhenFocused = false;
        c.optionsButton = false;
        TypingConfig expected = c.copy();
        ConfigManager.save();

        TypingConfig loaded = reinit(dir);
        assertSame(c, loaded, "the live instance is updated in place");
        assertEquals(expected, loaded);

        for (Easing e : Easing.values()) {
            loaded.easing = e;
            ConfigManager.save();
            assertEquals(e, reinit(dir).easing);
        }
        for (AppearStyle s : AppearStyle.values()) {
            loaded.appearStyle = s;
            ConfigManager.save();
            assertEquals(s, reinit(dir).appearStyle);
        }
        for (RemoveStyle s : RemoveStyle.values()) {
            loaded.removeStyle = s;
            ConfigManager.save();
            assertEquals(s, reinit(dir).removeStyle);
        }
    }

    @Test
    void initIsIdempotent() throws IOException {
        ConfigManager.init(dir);
        ConfigManager.get().durationMs = 999;
        Path other = dir.resolve("other");
        ConfigManager.init(other);
        assertEquals(file(), ConfigManager.path());
        assertEquals(999, ConfigManager.get().durationMs, "second init must not reload");
        assertFalse(Files.exists(other.resolve("typinganimation.json")));
    }

    @Test
    void missingKeysUseDefaults() throws IOException {
        writeFile("{ \"durationMs\": 500, \"appearStyle\": \"pop\" }");
        TypingConfig c = reinit(dir);
        TypingConfig expected = TypingConfig.defaults();
        expected.durationMs = 500;
        expected.appearStyle = AppearStyle.POP;
        assertEquals(expected, c);
        // normalised file now has every key
        JsonObject o = JsonParser.parseString(readFile()).getAsJsonObject();
        assertEquals(17, o.size());
        assertEquals(500, o.get("durationMs").getAsInt());
        assertEquals("pop", o.get("appearStyle").getAsString());
    }

    @Test
    void invalidValuesUseDefaults() throws IOException {
        writeFile("{\n"
                + "  \"enabled\": \"maybe\",\n"
                + "  \"appearStyle\": \"teleport\",\n"
                + "  \"removeStyle\": 5,\n"
                + "  \"easing\": null,\n"
                + "  \"durationMs\": \"soon\",\n"
                + "  \"removeDurationMs\": [1,2],\n"
                + "  \"intensity\": {\"a\": 1},\n"
                + "  \"staggerMs\": true,\n"
                + "  \"glideMs\": \"NaN\",\n"
                + "  \"smoothReflow\": 1,\n"
                + "  \"animateChat\": \"FALSE\",\n"
                + "  \"optionsButton\": \" true \"\n"
                + "}");
        TypingConfig c = reinit(dir);
        TypingConfig expected = TypingConfig.defaults();
        expected.animateChat = false; // lenient string boolean
        assertEquals(expected, c);
    }

    @Test
    void unknownKeysAreIgnoredAndLenientFormsAccepted() throws IOException {
        writeFile("{ \"futureOption\": 1, \"nested\": {\"x\": [1,2,3]}, "
                + "\"easing\": \"BACK_OUT\", \"appearStyle\": \"Slide-Left\", \"removeStyle\": \" fly up \", "
                + "\"durationMs\": 300.6, \"intensity\": \"1.5\", \"glideMs\": \"120\", \"enabled\": false }");
        TypingConfig c = reinit(dir);
        assertEquals(Easing.BACK_OUT, c.easing);
        assertEquals(AppearStyle.SLIDE_LEFT, c.appearStyle);
        assertEquals(RemoveStyle.FLY_UP, c.removeStyle);
        assertEquals(301, c.durationMs);
        assertEquals(1.5f, c.intensity);
        assertEquals(120, c.glideMs);
        assertFalse(c.enabled);
        String json = readFile();
        assertFalse(json.contains("futureOption"), "normalised file drops unknown keys");
        assertTrue(json.contains("\"easing\": \"back_out\""));
    }

    @Test
    void outOfRangeValuesAreClamped() throws IOException {
        writeFile("{ \"durationMs\": 1, \"removeDurationMs\": 1e12, \"intensity\": 99, \"staggerMs\": -50, "
                + "\"maxStaggerMs\": 1e300, \"glideMs\": -1e300 }");
        TypingConfig c = reinit(dir);
        assertEquals(40, c.durationMs);
        assertEquals(1000, c.removeDurationMs);
        assertEquals(3f, c.intensity);
        assertEquals(0, c.staggerMs);
        assertEquals(2000, c.maxStaggerMs);
        assertEquals(0, c.glideMs);
    }

    @Test
    void brokenJsonIsBackedUpAndReplacedWithDefaults() throws IOException {
        String broken = "{ \"durationMs\": 500, ";
        writeFile(broken);
        TypingConfig c = assertDoesNotThrow(() -> reinit(dir));
        assertEquals(TypingConfig.defaults(), c);
        Path bak = dir.resolve("typinganimation.json.bak");
        assertTrue(Files.exists(bak));
        assertEquals(broken, new String(Files.readAllBytes(bak), StandardCharsets.UTF_8));
        assertTrue(JsonParser.parseString(readFile()).isJsonObject());
    }

    @Test
    void nonObjectRootsGiveDefaults() throws IOException {
        for (String content : new String[]{"", "   ", "[1,2,3]", "\"text\"", "42", "null", "garbage garbage"}) {
            writeFile(content);
            TypingConfig c = assertDoesNotThrow(() -> reinit(dir), content);
            assertEquals(TypingConfig.defaults(), c, content);
        }
    }

    @Test
    void deeplyNestedJsonDoesNotThrow() throws IOException {
        StringBuilder sb = new StringBuilder("{\"a\":");
        for (int i = 0; i < 100_000; i++) sb.append('[');
        writeFile(sb.toString());
        TypingConfig c = assertDoesNotThrow(() -> reinit(dir));
        assertEquals(TypingConfig.defaults(), c);
    }

    @Test
    void utf8BomIsAccepted() throws IOException {
        writeFile("﻿{ \"durationMs\": 333 }");
        assertEquals(333, reinit(dir).durationMs);
    }

    @Test
    void validNormalisedFileIsNotRewritten() throws IOException {
        ConfigManager.init(dir);
        Files.setLastModifiedTime(file(), java.nio.file.attribute.FileTime.fromMillis(1_000_000L));
        reinit(dir);
        assertEquals(1_000_000L, Files.getLastModifiedTime(file()).toMillis());
    }

    @Test
    void resetRestoresDefaultsInPlaceAndSaves() throws IOException {
        ConfigManager.init(dir);
        TypingConfig live = ConfigManager.get();
        live.durationMs = 900;
        live.appearStyle = AppearStyle.WAVE;
        ConfigManager.save();
        ConfigManager.reset();
        assertSame(live, ConfigManager.get());
        assertEquals(TypingConfig.defaults(), live);
        assertEquals(TypingConfig.defaults(), reinit(dir));
    }

    @Test
    void saveClampsTheLiveConfig() {
        ConfigManager.init(dir);
        ConfigManager.get().glideMs = 5000;
        ConfigManager.save();
        assertEquals(300, ConfigManager.get().glideMs);
        assertEquals(300, reinit(dir).glideMs);
    }

    @Test
    void configDirIsARegularFile() throws IOException {
        Path notADir = dir.resolve("iam-a-file");
        Files.write(notADir, new byte[]{1, 2, 3});
        assertDoesNotThrow(() -> ConfigManager.init(notADir));
        assertEquals(TypingConfig.defaults(), ConfigManager.get());
        ConfigManager.get().durationMs = 500;
        assertDoesNotThrow(ConfigManager::save);
        assertDoesNotThrow(ConfigManager::reset);
        assertDoesNotThrow(ConfigManager::reload);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(notADir));
    }

    @Test
    void configFileIsADirectory() throws IOException {
        Files.createDirectories(file());
        assertDoesNotThrow(() -> ConfigManager.init(dir));
        assertEquals(TypingConfig.defaults(), ConfigManager.get());
        ConfigManager.get().staggerMs = 7;
        assertDoesNotThrow(ConfigManager::save);
        assertTrue(Files.isDirectory(file()));
        assertFalse(Files.exists(dir.resolve("typinganimation.json.tmp")), "temp file cleaned up");
    }

    @Test
    void nullDirectoryIsIgnored() {
        assertDoesNotThrow(() -> ConfigManager.init(null));
        assertNotNull(ConfigManager.get());
        assertDoesNotThrow(ConfigManager::save);
        // a later valid init still works
        ConfigManager.init(dir);
        assertEquals(file(), ConfigManager.path());
        assertTrue(Files.exists(file()));
    }

    @Test
    void reloadPicksUpExternalEdits() throws IOException {
        ConfigManager.init(dir);
        TypingConfig live = ConfigManager.get();
        writeFile("{ \"appearStyle\": \"drop\", \"durationMs\": 400 }");
        ConfigManager.reload();
        assertSame(live, ConfigManager.get());
        assertEquals(AppearStyle.DROP, live.appearStyle);
        assertEquals(400, live.durationMs);
    }

    @Test
    void toJsonAndReadIntoAreInverse() {
        TypingConfig c = new TypingConfig();
        c.easing = Easing.SINE_IN_OUT;
        c.intensity = 0.3f;
        c.staggerMs = 59;
        TypingConfig back = TypingConfig.defaults();
        ConfigManager.readInto(JsonParser.parseString(ConfigManager.toJson(c)).getAsJsonObject(), back);
        assertEquals(c, back);
    }
}
