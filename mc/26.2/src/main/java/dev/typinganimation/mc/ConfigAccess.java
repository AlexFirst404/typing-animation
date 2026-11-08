package dev.typinganimation.mc;

import dev.typinganimation.core.ConfigManager;

/** Keeps a reachable settings entry when a config moves between mod loaders. */
public final class ConfigAccess {
    private static boolean modMenuAvailable;
    private ConfigAccess() { }
    public static void setModMenuAvailable(boolean available) { modMenuAvailable = available; }
    public static boolean canHideOptionsButton() { return modMenuAvailable; }
    public static boolean showOptionsButton() { return !modMenuAvailable || ConfigManager.get().optionsButton; }
    public static void ensureReachable() { if (!modMenuAvailable) ConfigManager.get().optionsButton = true; }
}
