package dev.typinganimation.mc;

import dev.typinganimation.core.BundledSoundResources;
import dev.typinganimation.core.CustomSoundPackLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.repository.PackSource;
import java.util.Optional;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Lowest-priority audio fallback for Fabric/Quilt without a mod resource loader. */
public final class ClasspathSoundPack implements PackResources {
    private final Map<String, byte[]> customResources = CustomSoundPackLibrary.defaultLibrary().resources();
    private boolean includeBundled = true;
    private boolean includeCustom = true;
    private static volatile Set<String> loadedCustomIds = Set.of();

    public static boolean customAvailable(String id) { return loadedCustomIds.contains(id); }

    private void recordCustomIds() {
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (String path : customResources.keySet()) {
            if (path.startsWith("sounds/custom/")) ids.add(path.substring("sounds/custom/".length()).split("/")[0]);
        }
        loadedCustomIds = Set.copyOf(ids);
    }

    private static final PackLocationInfo LOCATION = new PackLocationInfo(
            "typinganimation:bundled_sounds", Component.literal("Typing Animation sounds"),
            PackSource.BUILT_IN, Optional.empty());

    public static List<PackResources> includeIfMissing(PackType type, List<PackResources> packs) {
        if (type != PackType.CLIENT_RESOURCES) return packs;
        boolean missing = true;
        for (PackResources pack : packs) {
            // A partial user override must not suppress the rest of the bundled samples.
            boolean complete = true;
            for (String path : BundledSoundResources.PATHS) {
                if (pack.getResource(type, id(path)) == null) {
                    complete = false;
                    break;
                }
            }
            if (complete) { missing = false; break; }
        }
        ClasspathSoundPack fallback = new ClasspathSoundPack();
        fallback.includeBundled = missing;
        fallback.recordCustomIds();
        fallback.includeCustom = !fallback.customResources.isEmpty();
        if (fallback.includeCustom) {
            for (PackResources pack : packs) {
                boolean complete = true;
                for (String path : fallback.customResources.keySet()) {
                    if (pack.getResource(type, ResourceLocation.fromNamespaceAndPath(CustomSoundPackLibrary.NAMESPACE, path)) == null) { complete = false; break; }
                }
                if (complete) { fallback.includeCustom = false; break; }
            }
        }
        if (!missing && !fallback.includeCustom) return packs;
        List<PackResources> withFallback = new ArrayList<>(packs.size() + 1);
        withFallback.add(fallback);
        withFallback.addAll(packs);
        return withFallback;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BundledSoundResources.NAMESPACE, path);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        if (type != PackType.CLIENT_RESOURCES) return null;
        if (CustomSoundPackLibrary.NAMESPACE.equals(id.getNamespace())) {
            if (!includeCustom) return null;
            byte[] bytes = customResources.get(id.getPath());
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }
        if (!includeBundled || !BundledSoundResources.NAMESPACE.equals(id.getNamespace())
                || !BundledSoundResources.PATHS.contains(id.getPath())) return null;
        return () -> BundledSoundResources.open(id.getPath());
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) return;
        String prefix = directory.isEmpty() ? "" : directory + "/";
        if (CustomSoundPackLibrary.NAMESPACE.equals(namespace)) {
            if (!includeCustom) return;
            customResources.forEach((path, bytes) -> {
                if (path.startsWith(prefix)) output.accept(ResourceLocation.fromNamespaceAndPath(CustomSoundPackLibrary.NAMESPACE, path), () -> new ByteArrayInputStream(bytes));
            });
            return;
        }
        if (!includeBundled || !BundledSoundResources.NAMESPACE.equals(namespace)) return;
        for (String path : BundledSoundResources.PATHS) {
            if (path.startsWith(prefix)) output.accept(id(path), () -> BundledSoundResources.open(path));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.CLIENT_RESOURCES) return Set.of();
        if (includeCustom && !customResources.isEmpty()) return includeBundled
                ? Set.of(BundledSoundResources.NAMESPACE, CustomSoundPackLibrary.NAMESPACE)
                : Set.of(CustomSoundPackLibrary.NAMESPACE);
        return includeBundled ? Set.of(BundledSoundResources.NAMESPACE) : Set.of();
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) { return null; }

    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> type) { return null; }

    @Override
    public PackLocationInfo location() { return LOCATION; }

    @Override
    public void close() { /* Streams belong to their callers, not to this stateless pack. */ }
}

