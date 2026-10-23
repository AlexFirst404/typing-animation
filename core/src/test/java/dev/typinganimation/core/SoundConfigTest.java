package dev.typinganimation.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SoundConfigTest {
    @TempDir Path directory;

    @BeforeEach
    @AfterEach
    void clearLiveConfig() {
        ConfigManager.resetForTests();
    }

    @Test
    void upgradingAnOldConfigStaysSilentAndKeepsItsVisualSettings() throws IOException {
        Path file = directory.resolve(ConfigManager.FILE_NAME);
        Files.writeString(file, "{\"appearStyle\":\"wave\",\"durationMs\":440,\"intensity\":0.75,"
                + "\"animateSigns\":false,\"cursorGlideMs\":140}");
        ConfigManager.init(directory);
        TypingConfig config = ConfigManager.get();
        assertFalse(config.typingSounds);
        assertEquals(SoundPreset.MECHANICAL, config.soundPreset);
        assertEquals(AppearStyle.WAVE, config.appearStyle);
        assertEquals(440, config.durationMs);
        assertEquals(0.75f, config.intensity);
        assertEquals(140, config.cursorGlideMs);
        assertFalse(config.animateSigns);
        JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertFalse(saved.get("typingSounds").getAsBoolean(), "migration writes an explicit opt-out");
        assertEquals("mechanical", saved.get("soundPreset").getAsString());
    }

    @Test
    void everySoundFamilyAndUserChoiceSurviveSavingAndReloading() {
        ConfigManager.init(directory);
        for (SoundPreset preset : SoundPreset.values()) {
            TypingConfig config = ConfigManager.get();
            config.typingSounds = true;
            config.soundPreset = preset;
            config.soundVolume = 0.72f;
            config.soundPitch = 1.3f;
            config.soundVariation = false;
            config.soundOnDelete = false;
            config.soundOnEnter = false;
            TypingConfig expected = config.copy();
            ConfigManager.save();
            ConfigManager.resetForTests();
            ConfigManager.init(directory);
            assertEquals(expected, ConfigManager.get());
        }
    }

    @Test
    void malformedSoundSettingsFallBackWithoutEnablingAudio() {
        for (String badPreset : new String[]{"\"unknown\"", "null", "true", "12", "{}", "[]"}) {
            TypingConfig config = new TypingConfig();
            ConfigManager.readInto(JsonParser.parseString("{\"typingSounds\":\"maybe\",\"soundPreset\":"
                    + badPreset + ",\"soundVolume\":\"NaN\",\"soundPitch\":\"Infinity\","
                    + "\"soundVariation\":[],\"soundOnDelete\":null,\"soundOnEnter\":{}}").getAsJsonObject(), config);
            config.clamp();
            assertEquals(TypingConfig.defaults(), config, badPreset);
        }
    }

    @Test
    void finiteOutOfRangeJsonNumbersAreClampedAndReadableEnumsAreAccepted() {
        TypingConfig config = new TypingConfig();
        ConfigManager.readInto(JsonParser.parseString("{\"soundPreset\":\"  LaPtOp  \",\"soundVolume\":-5,"
                + "\"soundPitch\":9,\"typingSounds\":\"true\"}").getAsJsonObject(), config);
        config.clamp();
        assertEquals(SoundPreset.LAPTOP, config.soundPreset);
        assertTrue(config.typingSounds);
        assertEquals(0f, config.soundVolume);
        assertEquals(1.6f, config.soundPitch);
    }

    @Test
    void inMemoryInvalidAudioValuesRecoverAndBoundariesRemainAvailable() {
        TypingConfig config = new TypingConfig();
        config.soundPreset = null;
        config.soundVolume = Float.NaN;
        config.soundPitch = Float.NaN;
        config.clamp();
        assertEquals(SoundPreset.MECHANICAL, config.soundPreset);
        assertEquals(0.35f, config.soundVolume);
        assertEquals(1f, config.soundPitch);
        config.soundVolume = Float.POSITIVE_INFINITY;
        config.soundPitch = Float.NEGATIVE_INFINITY;
        config.clamp();
        assertEquals(1f, config.soundVolume);
        assertEquals(0.6f, config.soundPitch);
        config.soundVolume = Float.NEGATIVE_INFINITY;
        config.soundPitch = Float.POSITIVE_INFINITY;
        config.clamp();
        assertEquals(0f, config.soundVolume);
        assertEquals(1.6f, config.soundPitch);
        TypingConfig atBounds = config.copy();
        config.clamp();
        assertEquals(atBounds, config);
    }
}
