package dev.typinganimation.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Local, bounded Ogg Vorbis libraries. Imported archives never become executable resource packs. */
public final class CustomSoundPackLibrary {
    public static final String NAMESPACE = "typinganimation_custom";
    public static final int MAX_PACK_BYTES = 32 * 1024 * 1024;
    public static final int MAX_LIBRARY_BYTES = 64 * 1024 * 1024;
    public static final int MAX_SAMPLE_BYTES = 8 * 1024 * 1024;
    public static final int MAX_PACKS = 16;
    private static final int MAX_ENTRIES = 80;
    private static final int MAX_MANIFEST_BYTES = 16 * 1024;
    private static final int[] OGG_CRC = crcTable();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path directory;

    public record Pack(String id, String name) { }
    /** Stable reason codes are suitable for localized UI messages. */
    public record Result(boolean success, String message, Pack pack) { }
    private record Validated(Pack pack, JsonObject manifest, Map<String, byte[]> files,
                             Map<TypingSoundAction, List<String>> sounds) { }

    public CustomSoundPackLibrary(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    public static CustomSoundPackLibrary defaultLibrary() {
        return new CustomSoundPackLibrary(ConfigManager.path().toAbsolutePath().getParent().resolve("typinganimation-sounds"));
    }
    public Path directory() { return directory; }
    public static String eventPath(String id, TypingSoundAction action) { return "custom." + id + "." + action.path(); }

    /** Bad or externally modified entries are ignored without breaking the settings screen. */
    public synchronized List<Pack> list() {
        List<Pack> result = new ArrayList<>();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return result;
        try (var paths = Files.list(directory)) {
            paths.filter(p -> validId(p.getFileName().toString())).limit(MAX_PACKS + 1L).forEach(p -> {
                try { result.add(readInstalled(p).pack()); } catch (Exception | StackOverflowError ignored) { }
            });
        } catch (IOException | SecurityException ignored) { }
        result.sort(Comparator.comparing(Pack::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Pack::id));
        return List.copyOf(result);
    }

    public synchronized Pack find(String id) {
        if (!validId(id)) return null;
        try { return readInstalled(directory.resolve(id)).pack(); }
        catch (Exception | StackOverflowError ignored) { return null; }
    }

    public synchronized Result importPack(Path archive) {
        Path staging = null;
        try {
            if (archive == null || !Files.isRegularFile(archive) || Files.size(archive) > MAX_PACK_BYTES)
                return failure("invalid_archive");
            Map<String, byte[]> entries = readZip(archive);
            Validated validated = validate(entries, null);
            String id = validated.pack().id();
            if (Files.exists(directory.resolve(id), LinkOption.NOFOLLOW_LINKS))
                return find(id) == null ? failure("invalid_manifest") : new Result(true, "already_imported", validated.pack());
            if (list().size() >= MAX_PACKS) return failure("library_full");
            int libraryBytes = resources().values().stream().mapToInt(b -> b.length).sum();
            int packBytes = validated.files().values().stream().mapToInt(b -> b.length).sum();
            if (libraryBytes + packBytes > MAX_LIBRARY_BYTES) return failure("library_full");
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory)) return failure("invalid_archive");
            staging = Files.createTempDirectory(directory, ".import-");
            Files.writeString(staging.resolve("pack.json"), GSON.toJson(validated.manifest()), StandardCharsets.UTF_8);
            for (Map.Entry<String, byte[]> file : validated.files().entrySet()) {
                Path target = staging.resolve(file.getKey()).normalize();
                if (!target.startsWith(staging)) throw new PackException("unsafe_path");
                Files.createDirectories(target.getParent());
                Files.write(target, file.getValue());
            }
            Path target = directory.resolve(id);
            try { Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(staging, target); }
            staging = null;
            return new Result(true, "imported", validated.pack());
        } catch (PackException e) { return failure(e.getMessage()); }
        catch (Exception | StackOverflowError e) { return failure("io_error"); }
        finally { if (staging != null) removeTree(staging); }
    }

    public synchronized Result deletePack(String id) {
        if (!validId(id)) return failure("not_found");
        Path target = directory.resolve(id);
        Pack pack = find(id);
        if (pack == null) return failure("not_found");
        if (!removeTree(target)) return failure("io_error");
        return new Result(true, "deleted", pack);
    }

    /** Snapshot used only during Minecraft resource reload. Keys are relative to this library's namespace. */
    public synchronized Map<String, byte[]> resources() {
        Map<String, byte[]> result = new LinkedHashMap<>();
        JsonObject events = new JsonObject();
        int total = 0;
        for (Pack pack : list()) {
            try {
                Validated data = readInstalled(directory.resolve(pack.id()));
                int bytes = data.files().values().stream().mapToInt(b -> b.length).sum();
                if (total + bytes > MAX_LIBRARY_BYTES) continue;
                total += bytes;
                Map<String, String> sampleNames = new LinkedHashMap<>();
                for (Map.Entry<String, byte[]> file : data.files().entrySet()) {
                    String sampleName = "custom/" + pack.id() + "/sample_" + sampleNames.size();
                    sampleNames.put(file.getKey(), sampleName);
                    result.put("sounds/" + sampleName + ".ogg", file.getValue());
                }
                for (TypingSoundAction action : TypingSoundAction.values()) {
                    JsonObject event = new JsonObject();
                    JsonArray samples = new JsonArray();
                    for (String path : data.sounds().get(action)) samples.add(NAMESPACE + ":" + sampleNames.get(path));
                    event.add("sounds", samples);
                    events.add(eventPath(pack.id(), action), event);
                }
            } catch (Exception | StackOverflowError ignored) { }
        }
        if (!result.isEmpty()) result.put("sounds.json", GSON.toJson(events).getBytes(StandardCharsets.UTF_8));
        return Map.copyOf(result);
    }

    private Validated readInstalled(Path path) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new PackException("not_found");
        Map<String, byte[]> files = new LinkedHashMap<>();
        Path manifest = path.resolve("pack.json");
        if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) throw new PackException("invalid_manifest");
        files.put("pack.json", readBounded(Files.newInputStream(manifest), MAX_MANIFEST_BYTES));
        JsonObject json = parse(files.get("pack.json"));
        Set<String> names = referencedNames(json);
        int total = files.get("pack.json").length;
        for (String name : names) {
            if (!safePath(name)) throw new PackException("unsafe_path");
            Path sample = path.resolve(name).normalize();
            Path cursor = path;
            for (Path part : path.relativize(sample)) {
                cursor = cursor.resolve(part);
                if (Files.isSymbolicLink(cursor)) throw new PackException("unsafe_path");
            }
            if (!Files.isRegularFile(sample, LinkOption.NOFOLLOW_LINKS)) throw new PackException("missing_sample");
            byte[] bytes = readBounded(Files.newInputStream(sample), MAX_SAMPLE_BYTES);
            total += bytes.length;
            if (total > MAX_PACK_BYTES) throw new PackException("too_large");
            files.put(name, bytes);
        }
        Validated data = validate(files, path.getFileName().toString());
        if (!data.pack().id().equals(path.getFileName().toString())) throw new PackException("invalid_manifest");
        return data;
    }

    private static Map<String, byte[]> readZip(Path archive) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        int count = 0, total = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) throw new PackException("too_large");
                String name = entry.getName();
                if (entry.isDirectory()) {
                    if (!safePath(name.substring(0, name.length() - 1))) throw new PackException("unsafe_path");
                    continue;
                }
                if (!safePath(name) || files.containsKey(name)) throw new PackException("unsafe_path");
                int limit = name.equals("pack.json") ? MAX_MANIFEST_BYTES : MAX_SAMPLE_BYTES;
                byte[] bytes = readEntry(zip, Math.min(limit, MAX_PACK_BYTES - total));
                total += bytes.length;
                files.put(name, bytes);
            }
        }
        return files;
    }

    private static Validated validate(Map<String, byte[]> input, String installedId) throws IOException {
        byte[] manifestBytes = input.get("pack.json");
        if (manifestBytes == null) throw new PackException("invalid_manifest");
        JsonObject manifest = parse(manifestBytes);
        String name;
        try {
            if (!manifest.get("format").isJsonPrimitive() || !manifest.getAsJsonPrimitive("format").isNumber()
                    || !manifest.get("format").getAsString().equals("1")) throw new PackException("invalid_manifest");
            if (!manifest.get("name").isJsonPrimitive() || !manifest.getAsJsonPrimitive("name").isString())
                throw new PackException("invalid_manifest");
            name = manifest.get("name").getAsString().strip();
            if (name.isEmpty() || name.length() > 48 || name.codePoints().anyMatch(Character::isISOControl))
                throw new PackException("invalid_manifest");
        } catch (RuntimeException e) { throw new PackException("invalid_manifest"); }
        Map<TypingSoundAction, List<String>> sounds = readSounds(manifest);
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (List<String> paths : sounds.values()) for (String path : paths) {
            if (!safePath(path) || !path.toLowerCase(Locale.ROOT).endsWith(".ogg")) throw new PackException("unsafe_path");
            byte[] bytes = input.get(path);
            if (bytes == null) throw new PackException("missing_sample");
            if (bytes.length > MAX_SAMPLE_BYTES) throw new PackException("too_large");
            validateVorbis(bytes);
            files.put(path, bytes);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            for (TypingSoundAction action : TypingSoundAction.values()) {
                digest.update((byte) action.ordinal());
                for (String path : sounds.get(action)) {
                    digest.update(path.getBytes(StandardCharsets.UTF_8));
                    digest.update(files.get(path));
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            String id = hex.substring(0, 24);
            if (installedId != null && !id.equals(installedId)) throw new PackException("invalid_manifest");
            return new Validated(new Pack(id, name), manifest, files, sounds);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }

    private static Set<String> referencedNames(JsonObject manifest) throws IOException {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (List<String> paths : readSounds(manifest).values()) result.addAll(paths);
        return result;
    }

    private static Map<TypingSoundAction, List<String>> readSounds(JsonObject manifest) throws IOException {
        try {
            JsonObject object = manifest.getAsJsonObject("sounds");
            Map<TypingSoundAction, List<String>> result = new EnumMap<>(TypingSoundAction.class);
            for (TypingSoundAction action : TypingSoundAction.values()) {
                JsonElement value = object.get(action.path());
                if (value == null && action != TypingSoundAction.KEY) continue;
                List<String> paths = new ArrayList<>();
                if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) paths.add(value.getAsString());
                else if (value != null && value.isJsonArray()) {
                    for (JsonElement path : value.getAsJsonArray()) {
                        if (!path.isJsonPrimitive() || !path.getAsJsonPrimitive().isString()) throw new PackException("invalid_manifest");
                        paths.add(path.getAsString());
                    }
                } else throw new PackException("invalid_manifest");
                if (paths.isEmpty() || paths.size() > 16) throw new PackException("invalid_manifest");
                result.put(action, List.copyOf(paths));
            }
            for (TypingSoundAction action : TypingSoundAction.values()) result.putIfAbsent(action, result.get(TypingSoundAction.KEY));
            return result;
        } catch (RuntimeException e) { throw new PackException("invalid_manifest"); }
    }

    private static JsonObject parse(byte[] bytes) throws IOException {
        try {
            if (bytes.length > MAX_MANIFEST_BYTES) throw new PackException("too_large");
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException | StackOverflowError e) { throw new PackException("invalid_manifest"); }
    }

    /** Ogg framing, Vorbis identification and a ten-second granule limit; decoding stays in Minecraft. */
    private static void validateVorbis(byte[] bytes) throws IOException {
        if (bytes.length < 58) throw new PackException("invalid_audio");
        int position = 0, rate = 0, serial = 0, pages = 0;
        long greatestGranule = 0;
        boolean identified = false, ended = false;
        while (position < bytes.length) {
            if (position + 27 > bytes.length || bytes[position] != 'O' || bytes[position + 1] != 'g'
                    || bytes[position + 2] != 'g' || bytes[position + 3] != 'S' || bytes[position + 4] != 0)
                throw new PackException("invalid_audio");
            int segments = bytes[position + 26] & 255, size = 0;
            if (position + 27 + segments > bytes.length) throw new PackException("invalid_audio");
            for (int i = 0; i < segments; i++) size += bytes[position + 27 + i] & 255;
            int body = position + 27 + segments;
            if (body + size > bytes.length) throw new PackException("invalid_audio");
            int crc = 0;
            for (int i = position; i < body + size; i++) {
                int value = i >= position + 22 && i < position + 26 ? 0 : bytes[i] & 255;
                crc = (crc << 8) ^ OGG_CRC[((crc >>> 24) ^ value) & 255];
            }
            if (crc != littleInt(bytes, position + 22) || ended) throw new PackException("invalid_audio");
            int thisSerial = littleInt(bytes, position + 14);
            if (pages++ == 0) {
                if ((bytes[position + 5] & 2) == 0) throw new PackException("invalid_audio");
                serial = thisSerial;
            }
            else if (serial != thisSerial) throw new PackException("invalid_audio");
            long granule = 0;
            for (int i = 0; i < 8; i++) granule |= (bytes[position + 6 + i] & 255L) << (i * 8);
            if (granule >= 0) greatestGranule = Math.max(greatestGranule, granule);
            if (!identified) {
                if (size < 30 || bytes[body] != 1 || !new String(bytes, body + 1, 6, StandardCharsets.US_ASCII).equals("vorbis"))
                    throw new PackException("invalid_audio");
                int channels = bytes[body + 11] & 255;
                rate = littleInt(bytes, body + 12);
                if (littleInt(bytes, body + 7) != 0 || channels < 1 || channels > 2 || rate < 8000 || rate > 192000
                        || (bytes[body + 29] & 1) == 0) throw new PackException("invalid_audio");
                identified = true;
            }
            if ((bytes[position + 5] & 4) != 0) ended = true;
            position = body + size;
        }
        if (!ended || greatestGranule <= 0 || greatestGranule > rate * 10L) throw new PackException("invalid_audio");
    }

    private static int[] crcTable() {
        int[] table = new int[256];
        for (int i = 0; i < table.length; i++) {
            int crc = i << 24;
            for (int bit = 0; bit < 8; bit++) crc = (crc << 1) ^ (crc < 0 ? 0x04c11db7 : 0);
            table[i] = crc;
        }
        return table;
    }

    private static int littleInt(byte[] bytes, int p) {
        return (bytes[p] & 255) | ((bytes[p + 1] & 255) << 8) | ((bytes[p + 2] & 255) << 16) | ((bytes[p + 3] & 255) << 24);
    }
    private static boolean validId(String id) { return id != null && id.matches("[0-9a-f]{24}"); }
    private static boolean safePath(String name) {
        if (name == null || name.length() > 160 || name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":")) return false;
        for (String part : name.split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals("..") || !part.matches("[A-Za-z0-9_. -]+")) return false;
        return true;
    }
    private static byte[] readBounded(InputStream input, int limit) throws IOException {
        try (input) { return readEntry(input, limit); }
    }
    private static byte[] readEntry(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] block = new byte[8192];
        int count;
        while ((count = input.read(block)) != -1) {
            if (out.size() + count > limit) throw new PackException("too_large");
            out.write(block, 0, count);
        }
        return out.toByteArray();
    }
    private static Result failure(String reason) { return new Result(false, reason, null); }
    private static boolean removeTree(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true;
        try (var files = Files.walk(path)) {
            for (Path child : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(child);
            return true;
        } catch (IOException | SecurityException ignored) { return false; }
    }
    private static final class PackException extends IOException { PackException(String code) { super(code); } }
}
