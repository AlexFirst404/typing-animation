package dev.typinganimation.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import static dev.typinganimation.core.TypingAnimationMod.LOGGER;

/**
 * Loads/saves {@code <configDir>/typinganimation.json} (Gson, pretty printed). Keys are the field names of
 * {@link TypingConfig}, enums are written as lower-case names. Missing or invalid values fall back to defaults,
 * then {@link TypingConfig#clamp()} is applied. There is exactly one live {@link TypingConfig} instance
 * ({@link #get()}); loading and resetting update it in place, so references to it stay valid.
 * No method ever throws; IO problems are logged.
 */
public final class ConfigManager {
    public static final String FILE_NAME = "typinganimation.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final TypingConfig CONFIG = new TypingConfig();
    private static volatile Path path;
    private static boolean initialized;

    private ConfigManager() {
    }

    /** Idempotent; loads the file or writes defaults. Never throws. A null directory is logged and ignored. */
    public static synchronized void init(Path configDir) {
        if (initialized) {
            return;
        }
        if (configDir == null) {
            LOGGER.warn("[{}] No config directory given; using default settings", TypingAnimationMod.MOD_ID);
            return;
        }
        try {
            path = configDir.resolve(FILE_NAME);
        } catch (RuntimeException e) {
            LOGGER.warn("[{}] Invalid config directory {}; using default settings", TypingAnimationMod.MOD_ID,
                    configDir, e);
            return;
        }
        initialized = true;
        reload();
    }

    /** Never null (defaults before init). Always the same instance. */
    public static TypingConfig get() {
        return CONFIG;
    }

    /** Clamps and writes the current config. Never throws (logs). No-op before a successful init. */
    public static synchronized void save() {
        Path p = path;
        if (p == null) {
            LOGGER.debug("[{}] save() before init; ignored", TypingAnimationMod.MOD_ID);
            return;
        }
        try {
            CONFIG.clamp();
            write(p, toJson(CONFIG));
        } catch (RuntimeException e) {
            LOGGER.warn("[{}] Could not save config to {}", TypingAnimationMod.MOD_ID, p, e);
        }
    }

    /** Restores defaults (in place) and saves. */
    public static synchronized void reset() {
        CONFIG.copyFrom(TypingConfig.defaults());
        save();
    }

    /** The config file; before a successful init the conventional relative path {@code config/typinganimation.json}. */
    public static Path path() {
        Path p = path;
        return p != null ? p : Path.of("config", FILE_NAME);
    }

    /**
     * Extra helper: re-reads the file into the live config (defaults for anything missing/invalid). A missing
     * file is created with defaults; an unparseable one is backed up to {@code typinganimation.json.bak} and
     * rewritten; a valid file is rewritten only if normalisation changed it. Never throws.
     */
    public static synchronized void reload() {
        Path p = path;
        if (p == null) {
            return;
        }
        TypingConfig loaded = TypingConfig.defaults();
        String original = null;
        boolean broken = false;
        try {
            if (Files.isRegularFile(p)) {
                original = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                String text = original.startsWith("﻿") ? original.substring(1) : original;
                JsonElement root = JsonParser.parseString(text);
                if (root != null && root.isJsonObject()) {
                    readInto(root.getAsJsonObject(), loaded);
                } else {
                    broken = true;
                    LOGGER.warn("[{}] Config {} is not a JSON object; using defaults", TypingAnimationMod.MOD_ID, p);
                }
            } else if (Files.exists(p)) {
                LOGGER.warn("[{}] Config path {} is not a regular file; using defaults", TypingAnimationMod.MOD_ID, p);
                CONFIG.copyFrom(loaded);
                return;
            }
        } catch (Exception | StackOverflowError e) {
            broken = original != null;
            LOGGER.warn("[{}] Could not read config {}; using defaults", TypingAnimationMod.MOD_ID, p, e);
            if (original == null) {
                // unreadable: keep defaults in memory, do not overwrite what we could not read
                CONFIG.copyFrom(loaded);
                return;
            }
            loaded = TypingConfig.defaults();
        }
        loaded.clamp();
        CONFIG.copyFrom(loaded);
        try {
            String json = toJson(loaded);
            if (broken) {
                backup(p);
            }
            if (original == null || broken || !json.trim().equals(original.trim())) {
                write(p, json);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("[{}] Could not write config {}", TypingAnimationMod.MOD_ID, p, e);
        }
    }

    /** Package-private for tests: forget init state and restore defaults in memory. */
    static synchronized void resetForTests() {
        initialized = false;
        path = null;
        CONFIG.copyFrom(TypingConfig.defaults());
    }

    // ---------------------------------------------------------------- JSON mapping

    static String toJson(TypingConfig c) {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", c.enabled);
        o.addProperty("appearStyle", enumName(c.appearStyle, AppearStyle.SLIDE_UP));
        o.addProperty("removeStyle", enumName(c.removeStyle, RemoveStyle.FADE));
        o.addProperty("easing", enumName(c.easing, Easing.AUTO));
        o.addProperty("durationMs", c.durationMs);
        o.addProperty("removeDurationMs", c.removeDurationMs);
        o.addProperty("intensity", c.intensity);
        o.addProperty("staggerMs", c.staggerMs);
        o.addProperty("maxStaggerMs", c.maxStaggerMs);
        o.addProperty("smoothReflow", c.smoothReflow);
        o.addProperty("smoothCursor", c.smoothCursor);
        o.addProperty("glideMs", c.glideMs);
        o.addProperty("animateChat", c.animateChat);
        o.addProperty("animateOtherFields", c.animateOtherFields);
        o.addProperty("animateMultiline", c.animateMultiline);
        o.addProperty("onlyWhenFocused", c.onlyWhenFocused);
        o.addProperty("optionsButton", c.optionsButton);
        return GSON.toJson(o) + System.lineSeparator();
    }

    /** Reads every known key of {@code o} into {@code c}; missing/invalid entries keep the value already in c. */
    static void readInto(JsonObject o, TypingConfig c) {
        c.enabled = readBool(o, "enabled", c.enabled);
        c.appearStyle = readEnum(o, "appearStyle", AppearStyle.class, c.appearStyle);
        c.removeStyle = readEnum(o, "removeStyle", RemoveStyle.class, c.removeStyle);
        c.easing = readEnum(o, "easing", Easing.class, c.easing);
        c.durationMs = readInt(o, "durationMs", c.durationMs);
        c.removeDurationMs = readInt(o, "removeDurationMs", c.removeDurationMs);
        c.intensity = readFloat(o, "intensity", c.intensity);
        c.staggerMs = readInt(o, "staggerMs", c.staggerMs);
        c.maxStaggerMs = readInt(o, "maxStaggerMs", c.maxStaggerMs);
        c.smoothReflow = readBool(o, "smoothReflow", c.smoothReflow);
        c.smoothCursor = readBool(o, "smoothCursor", c.smoothCursor);
        c.glideMs = readInt(o, "glideMs", c.glideMs);
        c.animateChat = readBool(o, "animateChat", c.animateChat);
        c.animateOtherFields = readBool(o, "animateOtherFields", c.animateOtherFields);
        c.animateMultiline = readBool(o, "animateMultiline", c.animateMultiline);
        c.onlyWhenFocused = readBool(o, "onlyWhenFocused", c.onlyWhenFocused);
        c.optionsButton = readBool(o, "optionsButton", c.optionsButton);
    }

    private static String enumName(Enum<?> e, Enum<?> fallback) {
        return (e != null ? e : fallback).name().toLowerCase(Locale.ROOT);
    }

    private static JsonPrimitive primitive(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsJsonPrimitive() : null;
    }

    private static boolean readBool(JsonObject o, String key, boolean def) {
        try {
            JsonPrimitive p = primitive(o, key);
            if (p == null) return def;
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isString()) {
                String s = p.getAsString().trim();
                if (s.equalsIgnoreCase("true")) return true;
                if (s.equalsIgnoreCase("false")) return false;
            }
        } catch (RuntimeException ignored) {
            // fall through to default
        }
        return def;
    }

    private static double readNumber(JsonObject o, String key) {
        try {
            JsonPrimitive p = primitive(o, key);
            if (p == null) return Double.NaN;
            if (p.isNumber()) return p.getAsDouble();
            if (p.isString()) return Double.parseDouble(p.getAsString().trim());
        } catch (RuntimeException ignored) {
            // fall through
        }
        return Double.NaN;
    }

    private static int readInt(JsonObject o, String key, int def) {
        double d = readNumber(o, key);
        if (!Double.isFinite(d)) return def;
        long r = Math.round(d);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, r));
    }

    private static float readFloat(JsonObject o, String key, float def) {
        double d = readNumber(o, key);
        if (!Double.isFinite(d)) return def;
        float f = (float) d;
        return Float.isFinite(f) ? f : def;
    }

    private static <E extends Enum<E>> E readEnum(JsonObject o, String key, Class<E> type, E def) {
        try {
            JsonPrimitive p = primitive(o, key);
            if (p == null || !p.isString()) return def;
            String s = p.getAsString().trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
            return Enum.valueOf(type, s);
        } catch (RuntimeException ignored) {
            return def;
        }
    }

    // ---------------------------------------------------------------- IO

    private static boolean write(Path p, String json) {
        Path tmp = null;
        try {
            if (Files.isDirectory(p)) {
                LOGGER.warn("[{}] Config path {} is a directory; not saving", TypingAnimationMod.MOD_ID, p);
                return false;
            }
            Path dir = p.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            tmp = p.resolveSibling(FILE_NAME + ".tmp");
            Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("[{}] Could not save config to {}", TypingAnimationMod.MOD_ID, p, e);
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                    // best effort
                }
            }
            return false;
        }
    }

    private static void backup(Path p) {
        try {
            Path bak = p.resolveSibling(FILE_NAME + ".bak");
            Files.copy(p, bak, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("[{}] Unreadable config backed up to {}", TypingAnimationMod.MOD_ID, bak);
        } catch (Exception e) {
            LOGGER.warn("[{}] Could not back up broken config {}", TypingAnimationMod.MOD_ID, p, e);
        }
    }
}
