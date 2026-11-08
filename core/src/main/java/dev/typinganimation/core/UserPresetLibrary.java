package dev.typinganimation.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Local, versioned, portable named settings. Loading a preset never changes field or navigation preferences. */
public final class UserPresetLibrary {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_BYTES = 64 * 1024;
    public static final int MAX_NAME_LENGTH = 40;
    private static final String FORMAT = "typinganimation-preset";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String[] PERSONAL_KEYS = {"enabled", "animateChat", "animateOtherFields", "animateMultiline",
            "animateSigns", "onlyWhenFocused", "optionsButton", "advancedSettings", "settingsModeChosen"};
    private final Path directory;

    public enum Status { OK, INVALID_NAME, ALREADY_EXISTS, NOT_FOUND, INVALID_FILE, UNSUPPORTED_VERSION, TOO_LARGE, IO_ERROR }

    public record Result(Status status, String name, Path path) {
        public boolean ok() { return status == Status.OK; }
    }

    /** Uses the current game instance's config directory; no global or server settings are used. */
    public static UserPresetLibrary current() {
        return new UserPresetLibrary(ConfigManager.path().resolveSibling("typinganimation-presets"));
    }

    public UserPresetLibrary(Path directory) {
        if (directory == null) throw new IllegalArgumentException("directory");
        this.directory = directory.toAbsolutePath().normalize();
    }

    public Path directory() { return directory; }

    /** Only valid entries are shown. A damaged file is retained on disk for recovery. */
    public synchronized List<String> list() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(directory)) return names;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).limit(1024).toList()) {
                try {
                    Preset preset = read(file);
                    if (file.equals(pathFor(preset.name)) && !names.contains(preset.name)) names.add(preset.name);
                } catch (PresetException | IOException | RuntimeException | StackOverflowError ignored) {
                    // Do not let a manually edited preset prevent the settings screen from opening.
                }
            }
        } catch (IOException | RuntimeException e) {
            log("list", e);
        }
        names.sort(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT)));
        return List.copyOf(names);
    }

    /** Saves a new name. Existing entries are never silently replaced. */
    public synchronized Result save(String name, TypingConfig config) {
        String clean = validName(name);
        if (clean == null) return result(Status.INVALID_NAME, name, null);
        if (config == null) return result(Status.INVALID_FILE, clean, null);
        Path file = pathFor(clean);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return result(Status.ALREADY_EXISTS, clean, file);
        TypingConfig copy = config.copy();
        copy.clamp();
        try {
            writeNew(file, encode(new Preset(clean, copy)));
            return result(Status.OK, clean, file);
        } catch (IOException | RuntimeException e) {
            log("save", e);
            return result(Status.IO_ERROR, clean, file);
        }
    }

    public synchronized Result apply(String name, TypingConfig target) {
        if (validName(name) == null) return result(Status.INVALID_NAME, name, null);
        if (target == null) return result(Status.INVALID_FILE, name, null);
        Path file = pathFor(name);
        try {
            Preset preset = read(file);
            // Start with the user's current preferences; the portable settings omit every personal key.
            ConfigManager.readInto(settings(preset.config), target);
            target.clamp();
            return result(Status.OK, preset.name, file);
        } catch (PresetException e) {
            return result(e.status, name, file);
        } catch (IOException | RuntimeException | StackOverflowError e) {
            log("apply", e);
            return result(Status.IO_ERROR, name, file);
        }
    }

    public synchronized Result duplicate(String source, String newName) {
        if (validName(source) == null) return result(Status.INVALID_NAME, source, null);
        try {
            return save(newName, read(pathFor(source)).config);
        } catch (PresetException e) {
            return result(e.status, source, pathFor(source));
        } catch (IOException | RuntimeException | StackOverflowError e) {
            log("duplicate", e);
            return result(Status.IO_ERROR, source, pathFor(source));
        }
    }

    public synchronized Result rename(String source, String newName) {
        String from = validName(source), to = validName(newName);
        if (from == null || to == null) return result(Status.INVALID_NAME, newName, null);
        Path oldFile = pathFor(from), newFile = pathFor(to);
        if (oldFile.equals(newFile)) {
            // Case-only renames would otherwise overwrite the source; keep it as an explicit no-op.
            return Files.isRegularFile(oldFile, LinkOption.NOFOLLOW_LINKS)
                    ? result(Status.OK, from, oldFile) : result(Status.NOT_FOUND, from, oldFile);
        }
        try {
            Preset original = read(oldFile);
            Result saved = save(to, original.config);
            if (!saved.ok()) return saved;
            try {
                Files.delete(oldFile);
            } catch (IOException e) {
                Files.deleteIfExists(newFile);
                throw e;
            }
            return saved;
        } catch (PresetException e) {
            return result(e.status, from, oldFile);
        } catch (IOException | RuntimeException | StackOverflowError e) {
            log("rename", e);
            return result(Status.IO_ERROR, from, oldFile);
        }
    }

    public synchronized Result delete(String name) {
        String clean = validName(name);
        if (clean == null) return result(Status.INVALID_NAME, name, null);
        Path file = pathFor(clean);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return result(Status.NOT_FOUND, clean, file);
            Files.delete(file);
            return result(Status.OK, clean, file);
        } catch (IOException | RuntimeException e) {
            log("delete", e);
            return result(Status.IO_ERROR, clean, file);
        }
    }

    /** Imports a standalone preset JSON. A collision gets a new name rather than replacing existing work. */
    public synchronized Result importFile(Path source) {
        try {
            Preset preset = read(source);
            String name = preset.name;
            for (int number = 2; Files.exists(pathFor(name), LinkOption.NOFOLLOW_LINKS); number++) {
                if (number > 1000) return result(Status.ALREADY_EXISTS, preset.name, null);
                String suffix = " (" + number + ")";
                name = preset.name.substring(0, Math.min(preset.name.length(), MAX_NAME_LENGTH - suffix.length())).trim() + suffix;
            }
            return save(name, preset.config);
        } catch (PresetException e) {
            return result(e.status, "", source);
        } catch (IOException | RuntimeException | StackOverflowError e) {
            log("import", e);
            return result(Status.IO_ERROR, "", source);
        }
    }

    /** Exports to an exact user-chosen file. Existing files are left intact. */
    public synchronized Result exportFile(String name, Path destination) {
        String clean = validName(name);
        if (clean == null) return result(Status.INVALID_NAME, name, null);
        if (destination == null) return result(Status.INVALID_FILE, clean, null);
        try {
            Preset preset = read(pathFor(clean));
            Path file = destination.toAbsolutePath().normalize();
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return result(Status.ALREADY_EXISTS, clean, file);
            writeNew(file, encode(preset));
            return result(Status.OK, preset.name, file);
        } catch (PresetException e) {
            return result(e.status, clean, destination);
        } catch (IOException | RuntimeException | StackOverflowError e) {
            log("export", e);
            return result(Status.IO_ERROR, clean, destination);
        }
    }

    private static JsonObject settings(TypingConfig config) {
        JsonObject object = JsonParser.parseString(ConfigManager.toJson(config)).getAsJsonObject();
        for (String key : PERSONAL_KEYS) object.remove(key);
        return object;
    }

    private static String encode(Preset preset) {
        JsonObject object = new JsonObject();
        object.addProperty("format", FORMAT);
        object.addProperty("version", FORMAT_VERSION);
        object.addProperty("name", preset.name);
        object.add("settings", settings(preset.config));
        return GSON.toJson(object) + System.lineSeparator();
    }

    private static Preset read(Path file) throws IOException, PresetException {
        if (file == null || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new PresetException(Status.NOT_FOUND);
        if (Files.size(file) > MAX_BYTES) throw new PresetException(Status.TOO_LARGE);
        byte[] bytes;
        try (InputStream input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new PresetException(Status.TOO_LARGE);
        try {
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) throw new PresetException(Status.INVALID_FILE);
            JsonObject object = root.getAsJsonObject();
            if (!object.has("format") || !object.get("format").isJsonPrimitive()
                    || !object.getAsJsonPrimitive("format").isString()
                    || !FORMAT.equals(object.get("format").getAsString())) throw new PresetException(Status.INVALID_FILE);
            if (!object.has("version") || !object.get("version").isJsonPrimitive()
                    || !object.getAsJsonPrimitive("version").isNumber()) throw new PresetException(Status.INVALID_FILE);
            if (!Integer.toString(FORMAT_VERSION).equals(object.get("version").getAsString()))
                throw new PresetException(Status.UNSUPPORTED_VERSION);
            if (!object.has("name") || !object.get("name").isJsonPrimitive()
                    || !object.getAsJsonPrimitive("name").isString()) throw new PresetException(Status.INVALID_NAME);
            String name = validName(object.get("name").getAsString());
            if (name == null) throw new PresetException(Status.INVALID_NAME);
            if (!object.has("settings") || !object.get("settings").isJsonObject()) throw new PresetException(Status.INVALID_FILE);
            JsonObject settings = object.getAsJsonObject("settings").deepCopy();
            for (String key : PERSONAL_KEYS) settings.remove(key);
            if (settings.size() == 0) throw new PresetException(Status.INVALID_FILE);
            TypingConfig config = TypingConfig.defaults();
            ConfigManager.readInto(settings, config);
            config.clamp();
            return new Preset(name, config);
        } catch (RuntimeException | StackOverflowError e) {
            throw new PresetException(Status.INVALID_FILE);
        }
    }

    private Path pathFor(String name) {
        String clean = validName(name);
        if (clean == null) throw new IllegalArgumentException("Invalid preset name");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(clean.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(digest) + ".json");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String validName(String name) {
        if (name == null) return null;
        String clean = Normalizer.normalize(name.trim(), Normalizer.Form.NFC);
        if (clean.isEmpty() || clean.length() > MAX_NAME_LENGTH || clean.equals(".") || clean.equals("..")) return null;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (Character.isISOControl(c) || Character.isSurrogate(c) || "/\\:*?\"<>|".indexOf(c) >= 0) return null;
        }
        return clean;
    }

    private static void writeNew(Path file, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Preset exceeds size limit");
        Path parent = file.getParent();
        if (parent == null) throw new IOException("Missing destination directory");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".typinganimation-preset-", ".tmp");
        try {
            Files.write(temporary, bytes);
            // Recheck after writing the temporary file to avoid accidental replacement by ordinary UI actions.
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Destination already exists");
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file); }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Result result(Status status, String name, Path path) { return new Result(status, name, path); }
    private static void log(String operation, Throwable error) {
        TypingAnimationMod.LOGGER.warn("[{}] Could not {} a user preset: {}", TypingAnimationMod.MOD_ID, operation, error.toString());
    }
    private record Preset(String name, TypingConfig config) { }
    private static final class PresetException extends Exception {
        private static final long serialVersionUID = 1L;
        private final Status status;
        private PresetException(Status status) { this.status = status; }
    }
}
