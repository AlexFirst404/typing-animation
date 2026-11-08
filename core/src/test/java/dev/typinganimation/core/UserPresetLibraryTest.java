package dev.typinganimation.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static dev.typinganimation.core.UserPresetLibrary.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class UserPresetLibraryTest {
    @TempDir Path directory;

    @Test
    void saveAndApplyCapturesStyleAndSoundWithoutPersonalPreferences() throws Exception {
        UserPresetLibrary library = new UserPresetLibrary(directory.resolve("library"));
        TypingConfig style = new TypingConfig();
        style.appearStyle = AppearStyle.SPIRAL;
        style.removeStyle = RemoveStyle.SCATTER;
        style.typingSounds = true;
        style.soundPreset = SoundPreset.TYPEWRITER;
        style.soundVolume = .6f;
        style.cursorCustomColor = true;
        style.cursorHexColor = "#112233";
        style.cursorBlinkMs = 1300;
        style.cursorSoftBlink = true;
        style.cursorKeepVisibleWhileTyping = true;
        style.adaptiveTyping = true;
        style.customSoundPack = "0123456789abcdef01234567";
        style.enabled = false;
        style.optionsButton = false;
        style.animateChat = false;
        style.settingsModeChosen = true;
        style.advancedSettings = true;
        var saved = library.save("Моя машинка", style);
        assertTrue(saved.ok());
        assertEquals(java.util.List.of("Моя машинка"), library.list());
        JsonObject settings = JsonParser.parseString(Files.readString(saved.path())).getAsJsonObject().getAsJsonObject("settings");
        for (String key : new String[]{"enabled", "optionsButton", "animateChat", "animateSigns", "onlyWhenFocused",
                "advancedSettings", "settingsModeChosen"}) assertFalse(settings.has(key), key);
        TypingConfig target = new TypingConfig();
        target.animateOtherFields = false;
        target.onlyWhenFocused = false;
        assertTrue(library.apply("Моя машинка", target).ok());
        assertEquals(AppearStyle.SPIRAL, target.appearStyle);
        assertEquals(RemoveStyle.SCATTER, target.removeStyle);
        assertEquals("#112233", target.cursorHexColor);
        assertEquals(1300, target.cursorBlinkMs);
        assertTrue(target.cursorCustomColor);
        assertTrue(target.cursorSoftBlink);
        assertTrue(target.cursorKeepVisibleWhileTyping);
        assertTrue(target.adaptiveTyping);
        assertTrue(target.typingSounds);
        assertEquals(SoundPreset.TYPEWRITER, target.soundPreset);
        assertEquals(.6f, target.soundVolume);
        assertEquals("0123456789abcdef01234567", target.customSoundPack);
        assertTrue(target.enabled);
        assertTrue(target.optionsButton);
        assertTrue(target.animateChat);
        assertFalse(target.animateOtherFields);
        assertFalse(target.onlyWhenFocused);
        assertFalse(target.settingsModeChosen);
        assertFalse(target.advancedSettings);
        assertEquals(ALREADY_EXISTS, library.save("моя машинка", style).status());
    }

    @Test
    void exportImportDuplicateRenameAndDeleteLeaveExistingEntriesIntact() throws Exception {
        UserPresetLibrary library = new UserPresetLibrary(directory.resolve("a"));
        TypingConfig source = new TypingConfig();
        source.soundPitch = 1.5f;
        assertTrue(library.save("Original", source).ok());
        assertTrue(library.duplicate("Original", "Copy").ok());
        assertTrue(library.rename("Copy", "Renamed").ok());
        assertEquals(NOT_FOUND, library.apply("Copy", new TypingConfig()).status());
        assertEquals(ALREADY_EXISTS, library.rename("Renamed", "Original").status());
        Path exported = directory.resolve("out/style.json");
        assertTrue(library.exportFile("Original", exported).ok());
        assertEquals(ALREADY_EXISTS, library.exportFile("Original", exported).status());
        var imported = library.importFile(exported);
        assertTrue(imported.ok());
        assertEquals("Original (2)", imported.name());
        TypingConfig target = new TypingConfig();
        assertTrue(library.apply(imported.name(), target).ok());
        assertEquals(1.5f, target.soundPitch);
        assertTrue(library.delete("Renamed").ok());
        assertEquals(NOT_FOUND, library.delete("Renamed").status());
        assertEquals(java.util.List.of("Original", "Original (2)"), library.list());
        assertTrue(Files.isRegularFile(exported));
    }

    @Test
    void hostileOrDamagedImportsDoNotChangeTargetOrExistingFiles() throws Exception {
        UserPresetLibrary library = new UserPresetLibrary(directory.resolve("library"));
        TypingConfig target = new TypingConfig();
        target.durationMs = 420;
        assertTrue(library.save("Good", target).ok());
        String before = Files.readString(library.exportFile("Good", directory.resolve("good.json")).path());
        Path bad = directory.resolve("bad.json");
        Files.writeString(bad, "[");
        assertEquals(INVALID_FILE, library.importFile(bad).status());
        Files.writeString(bad, before.replace("\"version\": 1", "\"version\": 200"));
        assertEquals(UNSUPPORTED_VERSION, library.importFile(bad).status());
        Files.writeString(bad, before.replace("\"name\": \"Good\"", "\"name\": \"../escape\""));
        assertEquals(INVALID_NAME, library.importFile(bad).status());
        Files.writeString(bad, "x".repeat(UserPresetLibrary.MAX_BYTES + 1));
        assertEquals(TOO_LARGE, library.importFile(bad).status());
        assertEquals(java.util.List.of("Good"), library.list());
        assertEquals(INVALID_NAME, library.save("../escape", target).status());
        assertEquals(INVALID_NAME, library.save("bad:name", target).status());
        assertEquals(INVALID_NAME, library.save(" ", target).status());
        assertEquals(INVALID_NAME, library.save("x".repeat(41), target).status());
        assertEquals(NOT_FOUND, library.apply("Missing", target).status());
        assertEquals(420, target.durationMs);
        assertEquals(before, Files.readString(directory.resolve("good.json")));
    }

    @Test
    void importCannotOverrideNavigationAndFieldPreferences() throws Exception {
        UserPresetLibrary library = new UserPresetLibrary(directory.resolve("library"));
        assertTrue(library.save("Source", new TypingConfig()).ok());
        Path exported = library.exportFile("Source", directory.resolve("source.json")).path();
        JsonObject object = JsonParser.parseString(Files.readString(exported)).getAsJsonObject();
        JsonObject settings = object.getAsJsonObject("settings");
        settings.addProperty("enabled", false);
        settings.addProperty("optionsButton", false);
        settings.addProperty("advancedSettings", true);
        settings.addProperty("settingsModeChosen", true);
        settings.addProperty("animateSigns", false);
        settings.addProperty("durationMs", 999999);
        Files.writeString(exported, object.toString());
        var imported = library.importFile(exported);
        TypingConfig target = new TypingConfig();
        assertTrue(library.apply(imported.name(), target).ok());
        assertTrue(target.enabled);
        assertTrue(target.optionsButton);
        assertTrue(target.animateSigns);
        assertFalse(target.advancedSettings);
        assertFalse(target.settingsModeChosen);
        assertEquals(1000, target.durationMs);
    }
}
