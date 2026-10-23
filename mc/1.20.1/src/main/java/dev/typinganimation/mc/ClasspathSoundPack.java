package dev.typinganimation.mc;

import dev.typinganimation.core.BundledSoundResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Lowest-priority audio fallback for Fabric/Quilt without a mod resource loader. */
public final class ClasspathSoundPack implements PackResources {

    public static List<PackResources> includeIfMissing(PackType type, List<PackResources> packs) {
        if (type != PackType.CLIENT_RESOURCES) return packs;
        for (PackResources pack : packs) {
            // A partial user override must not suppress the rest of the bundled samples.
            boolean complete = true;
            for (String path : BundledSoundResources.PATHS) {
                if (pack.getResource(type, id(path)) == null) {
                    complete = false;
                    break;
                }
            }
            if (complete) return packs;
        }
        List<PackResources> withFallback = new ArrayList<>(packs.size() + 1);
        withFallback.add(new ClasspathSoundPack());
        withFallback.addAll(packs);
        return withFallback;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(BundledSoundResources.NAMESPACE, path);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        if (type != PackType.CLIENT_RESOURCES || !BundledSoundResources.NAMESPACE.equals(id.getNamespace())
                || !BundledSoundResources.PATHS.contains(id.getPath())) return null;
        return () -> BundledSoundResources.open(id.getPath());
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !BundledSoundResources.NAMESPACE.equals(namespace)) return;
        String prefix = directory.isEmpty() ? "" : directory + "/";
        for (String path : BundledSoundResources.PATHS) {
            if (path.startsWith(prefix)) output.accept(id(path), () -> BundledSoundResources.open(path));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(BundledSoundResources.NAMESPACE) : Set.of();
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) { return null; }

    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> type) { return null; }

    @Override
    public String packId() { return "typinganimation:bundled_sounds"; }

    @Override
    public void close() { /* Streams belong to their callers, not to this stateless pack. */ }
}

