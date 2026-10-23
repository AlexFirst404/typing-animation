package dev.typinganimation.core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Audio resources exposed to Minecraft even when no loader resource-pack API is installed. */
public final class BundledSoundResources {
    public static final String NAMESPACE = "typinganimation";
    public static final List<String> PATHS = paths();

    private BundledSoundResources() {}

    private static List<String> paths() {
        List<String> result = new ArrayList<>();
        result.add("sounds.json");
        for (SoundPreset preset : SoundPreset.values()) {
            String family = preset.name().toLowerCase(Locale.ROOT);
            for (TypingSoundAction action : TypingSoundAction.values()) {
                int variants = switch (action) {
                    case KEY -> 4;
                    case SPACE, BACKSPACE -> 2;
                    case ENTER -> 1;
                };
                for (int variant = 1; variant <= variants; variant++) {
                    result.add("sounds/typing/" + family + "/"
                            + action.name().toLowerCase(Locale.ROOT) + "_" + variant + ".ogg");
                }
            }
        }
        return List.copyOf(result);
    }

    /** Each consumer owns a fresh stream; paths outside the bundled audio set are never exposed. */
    public static InputStream open(String path) throws IOException {
        if (!PATHS.contains(path)) throw new FileNotFoundException(path);
        InputStream stream = BundledSoundResources.class.getResourceAsStream("/assets/" + NAMESPACE + "/" + path);
        if (stream == null) throw new FileNotFoundException("Bundled sound resource: " + path);
        return stream;
    }
}
