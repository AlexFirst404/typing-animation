package dev.typinganimation.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VisualPresetTest {
    private static final Set<String> VISUAL_FIELDS = Set.of("appearStyle", "removeStyle", "easing", "durationMs",
            "removeDurationMs", "intensity", "removeIntensity", "staggerMs", "maxStaggerMs", "smoothReflow",
            "smoothCursor", "glideMs", "cursorGlideMs", "cursorStyle", "cursorColor", "cursorWidth");

    @Test
    void eachPresetIsDistinctValidAndRecognisedAfterAnyPreviousPreset() {
        assertEquals(VisualPreset.DEFAULT, VisualPreset.match(TypingConfig.defaults()));
        Set<TypingConfig> unique = new HashSet<>();
        for (VisualPreset preset : VisualPreset.values()) {
            TypingConfig config = new TypingConfig();
            preset.applyTo(config);
            assertTrue(unique.add(config.copy()), preset + " must offer a distinct combination");
            TypingConfig clamped = config.copy();
            clamped.clamp();
            assertEquals(config, clamped, preset + " must already fit the UI's allowed ranges");
            assertEquals(preset, VisualPreset.match(config));
            for (VisualPreset next : VisualPreset.values()) {
                TypingConfig changed = config.copy();
                next.applyTo(changed);
                assertEquals(next, VisualPreset.match(changed), "applying " + next + " after " + preset);
            }
        }
        assertNull(VisualPreset.match(null));
    }

    @Test
    void visualChangesPreserveAudioAndWhereToAnimateChoices() throws IllegalAccessException {
        TypingConfig original = new TypingConfig();
        original.enabled = false;
        original.typingSounds = true;
        original.soundPreset = SoundPreset.TYPEWRITER;
        original.soundVolume = 0.72f;
        original.soundPitch = 1.3f;
        original.soundVariation = false;
        original.soundOnDelete = false;
        original.soundOnEnter = false;
        original.animateChat = false;
        original.animateOtherFields = false;
        original.animateMultiline = false;
        original.animateSigns = false;
        original.onlyWhenFocused = false;
        original.optionsButton = false;
        for (VisualPreset preset : VisualPreset.values()) {
            TypingConfig changed = original.copy();
            preset.applyTo(changed);
            for (Field field : TypingConfig.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) || VISUAL_FIELDS.contains(field.getName())) continue;
                assertEquals(field.get(original), field.get(changed), preset + " must preserve " + field.getName());
            }
            assertEquals(preset, VisualPreset.match(changed), "unrelated settings cannot turn a preset into Custom");
        }
    }

    @Test
    void anyManualVisualAdjustmentBecomesCustom() throws IllegalAccessException {
        for (VisualPreset preset : VisualPreset.values()) {
            for (Field field : TypingConfig.class.getFields()) {
                if (!VISUAL_FIELDS.contains(field.getName())) continue;
                TypingConfig changed = new TypingConfig();
                preset.applyTo(changed);
                if (field.getType() == int.class) field.setInt(changed, field.getInt(changed) + 1);
                else if (field.getType() == float.class) field.setFloat(changed, field.getFloat(changed) + 0.01f);
                else if (field.getType() == boolean.class) field.setBoolean(changed, !field.getBoolean(changed));
                else {
                    Object current = field.get(changed);
                    Object[] choices = field.getType().getEnumConstants();
                    field.set(changed, choices[0] == current ? choices[1] : choices[0]);
                }
                assertNull(VisualPreset.match(changed), preset + ": editing " + field.getName() + " must show Custom");
            }
        }
    }

    @Test
    void presetSurvivesSavingAndManualEditsCannotMutateItsTemplate() {
        for (VisualPreset preset : VisualPreset.values()) {
            TypingConfig config = new TypingConfig();
            config.typingSounds = true;
            config.soundPreset = SoundPreset.LAPTOP;
            preset.applyTo(config);
            TypingConfig restored = new TypingConfig();
            ConfigManager.readInto(JsonParser.parseString(ConfigManager.toJson(config)).getAsJsonObject(), restored);
            assertEquals(config, restored);
            assertEquals(preset, VisualPreset.match(restored));
            config.durationMs = 999;
            config.cursorColor = CursorColor.RED;
            preset.applyTo(config);
            assertEquals(restored, config, "reapplying must restore the preset, not a mutated shared template");
        }
    }

    @Test
    void minimalSoftAndPlayfulProfilesHaveDifferentMotionIntent() {
        TypingConfig minimal = new TypingConfig(), soft = new TypingConfig(), bouncy = new TypingConfig();
        VisualPreset.MINIMAL.applyTo(minimal);
        VisualPreset.SOFT.applyTo(soft);
        VisualPreset.BOUNCY.applyTo(bouncy);
        assertEquals(AppearStyle.FADE, minimal.appearStyle);
        assertEquals(0, minimal.staggerMs, "minimal typing should not queue a paste animation");
        assertTrue(minimal.durationMs < soft.durationMs);
        assertTrue(soft.intensity < bouncy.intensity);
        assertTrue(soft.cursorGlideMs > minimal.cursorGlideMs);
        assertEquals(Easing.BACK_OUT, bouncy.easing.resolve(bouncy.appearStyle.defaultEasing()),
                "bouncy profile should actually use an overshooting appearance curve");
    }
}
