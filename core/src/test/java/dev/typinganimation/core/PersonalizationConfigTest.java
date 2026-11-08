package dev.typinganimation.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PersonalizationConfigTest {
    @Test
    void preferencesRoundTripAndCopy() {
        TypingConfig config = TypingConfig.defaults();
        config.adaptiveTyping = true;
        config.cursorCustomColor = true;
        config.cursorHexColor = "#123ABC";
        config.cursorBlinkMs = 1700;
        config.cursorSoftBlink = true;
        config.cursorKeepVisibleWhileTyping = true;
        config.customSoundPack = "0123456789abcdef01234567";
        config.advancedSettings = true;
        config.settingsModeChosen = true;
        TypingConfig loaded = TypingConfig.defaults();
        ConfigManager.readInto(JsonParser.parseString(ConfigManager.toJson(config)).getAsJsonObject(), loaded);
        loaded.clamp();
        assertEquals(config, loaded);
        assertEquals(config.hashCode(), loaded.hashCode());
        assertEquals(config, config.copy());
        loaded.cursorBlinkMs++;
        assertNotEquals(config, loaded);
    }

    @Test
    void oldFilesKeepBehaviourAndAskForModeOnce() {
        TypingConfig config = TypingConfig.defaults();
        ConfigManager.readInto(JsonParser.parseString("{\"durationMs\":310,\"typingSounds\":true}").getAsJsonObject(), config);
        config.clamp();
        assertFalse(config.adaptiveTyping);
        assertFalse(config.cursorCustomColor);
        assertFalse(config.cursorSoftBlink);
        assertFalse(config.cursorKeepVisibleWhileTyping);
        assertEquals(600, config.cursorBlinkMs);
        assertEquals("", config.customSoundPack);
        assertFalse(config.settingsModeChosen);
        assertFalse(config.advancedSettings);
        assertEquals(310, config.durationMs);
        assertTrue(config.typingSounds);
    }

    @Test
    void invalidColourAndPackIdentifiersCannotBecomePaths() {
        TypingConfig config = TypingConfig.defaults();
        config.cursorHexColor = " aa22bF ";
        config.customSoundPack = "../../somewhere";
        config.cursorBlinkMs = Integer.MIN_VALUE;
        config.clamp();
        assertEquals("#AA22BF", config.cursorHexColor);
        assertEquals("", config.customSoundPack);
        assertEquals(200, config.cursorBlinkMs);
        config.cursorHexColor = "#ABC";
        config.cursorBlinkMs = Integer.MAX_VALUE;
        config.clamp();
        assertEquals("#C4B5FD", config.cursorHexColor);
        assertEquals(2000, config.cursorBlinkMs);
        config.cursorHexColor = null;
        config.customSoundPack = null;
        assertDoesNotThrow(config::clamp);
        assertEquals("#C4B5FD", config.cursorHexColor);
        assertEquals("", config.customSoundPack);
    }

    @Test
    void wrongJsonTypesUseDefaults() {
        TypingConfig config = TypingConfig.defaults();
        ConfigManager.readInto(JsonParser.parseString("{\"cursorHexColor\":123,\"customSoundPack\":[],"
                + "\"cursorBlinkMs\":\"NaN\",\"advancedSettings\":{},\"settingsModeChosen\":null}").getAsJsonObject(), config);
        config.clamp();
        assertEquals(TypingConfig.defaults(), config);
    }
}
