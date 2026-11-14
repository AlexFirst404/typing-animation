package dev.typinganimation.mc;

import dev.typinganimation.core.ConfigManager;

/** Keeps the main-menu settings entry reachable while allowing a separate in-game preference. */
public final class ConfigAccess {
    private static boolean modMenuAvailable;
    private static boolean nativeSettingsAvailable;

    private ConfigAccess() { }

    public static void setModMenuAvailable(boolean available) { modMenuAvailable = available; }

    /** Reported only after a Forge/NeoForge mods-list config factory has been registered. */
    public static void setNativeSettingsAvailable(boolean available) { nativeSettingsAvailable = available; }

    public static boolean canHideOptionsButton() { return modMenuAvailable || nativeSettingsAvailable; }

    /** Main-menu policy, also used by callers that do not have an Options screen context. */
    public static boolean showOptionsButton() { return showOptionsButton(false); }

    public static boolean showOptionsButton(boolean inGame) {
        return (!canHideOptionsButton() || ConfigManager.get().optionsButton)
                && (!inGame || ConfigManager.get().pauseOptionsButton);
    }

    /** Moving a config to a loader without another settings entry never hides its main-menu button. */
    public static void ensureReachable() {
        if (!canHideOptionsButton()) ConfigManager.get().optionsButton = true;
    }
}
