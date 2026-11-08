package dev.typinganimation.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CustomSoundPackLibraryTest {
    @TempDir Path temp;
    private CustomSoundPackLibrary library() { return new CustomSoundPackLibrary(temp.resolve("library")); }
    private static byte[] sample() throws IOException {
        return Files.readAllBytes(Path.of("..", "common-resources", "assets", "typinganimation", "sounds", "typing", "soft", "key_1.ogg"));
    }
    private Path archive(Map<String, byte[]> entries) throws IOException {
        Path zip = temp.resolve("pack-" + System.nanoTime() + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return zip;
    }
    private static byte[] manifest(String body) { return body.getBytes(StandardCharsets.UTF_8); }
    private static String minimalManifest() { return "{\"format\":1,\"name\":\"My keyboard\",\"sounds\":{\"key\":[\"samples/key.ogg\"]}}"; }
    private Map<String, byte[]> minimal() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("pack.json", manifest(minimalManifest()));
        entries.put("samples/key.ogg", sample());
        return entries;
    }

    @Test void importsRealVorbisAndBuildsFourEventFallbacks() throws Exception {
        CustomSoundPackLibrary library = library();
        var imported = library.importPack(archive(minimal()));
        assertTrue(imported.success(), imported.message());
        assertEquals("My keyboard", imported.pack().name());
        assertTrue(imported.pack().id().matches("[a-f0-9]{24}"));
        assertEquals(imported.pack(), library.find(imported.pack().id()));
        Map<String, byte[]> resources = library.resources();
        assertEquals(2, resources.size(), "one sample plus sounds.json");
        JsonObject sounds = JsonParser.parseString(new String(resources.get("sounds.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(4, sounds.size());
        for (TypingSoundAction action : TypingSoundAction.values()) {
            String resource = sounds.getAsJsonObject(CustomSoundPackLibrary.eventPath(imported.pack().id(), action))
                    .getAsJsonArray("sounds").get(0).getAsString();
            assertTrue(resource.startsWith(CustomSoundPackLibrary.NAMESPACE + ":custom/"));
            assertArrayEquals(sample(), resources.get("sounds/" + resource.substring(resource.indexOf(':') + 1) + ".ogg"));
        }
    }

    @Test void independentLibraryInstancesPersistAndDeletePacks() throws Exception {
        var added = library().importPack(archive(minimal()));
        CustomSoundPackLibrary reopened = library();
        assertEquals(1, reopened.list().size());
        assertEquals(added.pack(), reopened.list().get(0));
        assertTrue(reopened.deletePack(added.pack().id()).success());
        assertTrue(reopened.list().isEmpty());
        assertTrue(reopened.resources().isEmpty());
    }

    @Test void repeatedImportIsIdempotentAndStableAcrossZipOrder() throws Exception {
        var entries = minimal();
        var first = library().importPack(archive(entries));
        Map<String, byte[]> reordered = new LinkedHashMap<>();
        reordered.put("samples/key.ogg", entries.get("samples/key.ogg"));
        reordered.put("pack.json", entries.get("pack.json"));
        var second = library().importPack(archive(reordered));
        assertTrue(first.success());
        assertTrue(second.success());
        assertEquals("already_imported", second.message());
        assertEquals(first.pack(), second.pack());
        assertEquals(1, library().list().size());
    }

    @Test void importsSeparateActionSamplesAndUnicodeDisplayName() throws Exception {
        var entries = minimal();
        entries.put("pack.json", manifest("{\"format\":1,\"name\":\"Клавиатура ✦\",\"sounds\":{\"key\":\"samples/key.ogg\",\"space\":[\"space.ogg\"],\"backspace\":[\"delete.ogg\"],\"enter\":\"enter.ogg\"}}"));
        entries.put("space.ogg", sample());
        entries.put("delete.ogg", sample());
        entries.put("enter.ogg", sample());
        var added = library().importPack(archive(entries));
        assertTrue(added.success(), added.message());
        assertEquals("Клавиатура ✦", added.pack().name());
        assertEquals(5, library().resources().size());
    }

    @Test void refusesZipSlipEvenWhenEntryIsNotReferenced() throws Exception {
        var entries = minimal();
        entries.put("../outside.txt", new byte[]{1});
        var result = library().importPack(archive(entries));
        assertFalse(result.success());
        assertEquals("unsafe_path", result.message());
        assertFalse(Files.exists(temp.resolve("outside.txt")));
        assertTrue(library().list().isEmpty());
    }

    @Test void refusesAbsoluteWindowsAndBackslashPaths() throws Exception {
        for (String path : new String[]{"/x.ogg", "C:/x.ogg", "folder\\x.ogg", "a/../../x.ogg"}) {
            var entries = minimal();
            entries.put(path, sample());
            assertEquals("unsafe_path", library().importPack(archive(entries)).message(), path);
        }
    }

    @Test void malformedAndUnsupportedManifestsDoNotLeavePartialLibraryEntries() throws Exception {
        for (String json : new String[]{"[]", "{", "{\"format\":2,\"name\":\"X\",\"sounds\":{\"key\":\"samples/key.ogg\"}}",
                "{\"format\":1,\"name\":\"\",\"sounds\":{\"key\":\"samples/key.ogg\"}}",
                "{\"format\":1,\"name\":\"X\",\"sounds\":{\"space\":\"samples/key.ogg\"}}"}) {
            var entries = minimal();
            entries.put("pack.json", manifest(json));
            var result = library().importPack(archive(entries));
            assertFalse(result.success(), json);
            assertEquals("invalid_manifest", result.message());
            assertTrue(library().list().isEmpty());
        }
    }

    @Test void refusesMissingOrNonVorbisSamples() throws Exception {
        var entries = minimal();
        entries.remove("samples/key.ogg");
        assertEquals("missing_sample", library().importPack(archive(entries)).message());
        entries.put("samples/key.ogg", "not an ogg file".getBytes(StandardCharsets.UTF_8));
        assertEquals("invalid_audio", library().importPack(archive(entries)).message());
    }

    @Test void refusesTruncatedOggAndOverlongGranules() throws Exception {
        var entries = minimal();
        byte[] bytes = sample();
        entries.put("samples/key.ogg", java.util.Arrays.copyOf(bytes, bytes.length - 1));
        assertEquals("invalid_audio", library().importPack(archive(entries)).message());
        bytes = sample();
        // A very large positive granule would permit minutes of audio from a single keystroke.
        bytes[6] = 0; bytes[7] = 0; bytes[8] = 0; bytes[9] = 0x40;
        entries.put("samples/key.ogg", bytes);
        assertEquals("invalid_audio", library().importPack(archive(entries)).message());
    }

    @Test void rejectsOversizedEntriesBeforeWritingThem() throws Exception {
        var entries = minimal();
        entries.put("large.ogg", new byte[CustomSoundPackLibrary.MAX_SAMPLE_BYTES + 1]);
        assertEquals("too_large", library().importPack(archive(entries)).message());
        assertTrue(library().list().isEmpty());
    }

    @Test void externallyChangedOrMissingAudioIsIgnoredAndCannotBePlayed() throws Exception {
        var imported = library().importPack(archive(minimal()));
        assertTrue(imported.success());
        Files.writeString(library().directory().resolve(imported.pack().id()).resolve("samples/key.ogg"), "corrupt");
        assertNull(library().find(imported.pack().id()));
        assertTrue(library().list().isEmpty());
        assertTrue(library().resources().isEmpty());
    }

    @Test void invalidIdsNeverAccessPathsOutsideLibrary() throws Exception {
        Path outside = temp.resolve("outside");
        Files.writeString(outside, "keep");
        assertNull(library().find("../outside"));
        assertFalse(library().deletePack("../outside").success());
        assertEquals("keep", Files.readString(outside));
    }
}
