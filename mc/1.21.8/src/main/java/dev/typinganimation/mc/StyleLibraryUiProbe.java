package dev.typinganimation.mc;

import dev.typinganimation.core.AppearStyle;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.SoundPreset;
import dev.typinganimation.core.TypingConfig;
import dev.typinganimation.core.UserPresetLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Exercises the real named-preset manager without touching pre-existing presets or files. */
public final class StyleLibraryUiProbe {
    private static Minecraft client;
    private static Screen previousScreen;
    private static TypingConfig previousConfig;
    private static ConfigScreen configScreen;
    private static StyleLibraryScreen libraryScreen;
    private static UserPresetLibrary presets;
    private static Path directory, exportedFile;
    private static String originalName, copiedName, renamedName, importedName;
    private static final Set<String> ownedNames = new LinkedHashSet<>();

    private StyleLibraryUiProbe() { }

    /** Call on the client thread after resource-reload overlays have disappeared. */
    public static void prepare() throws IOException {
        require(previousConfig == null, "Style library probe already prepared");
        client = Minecraft.getInstance();
        previousScreen = client.screen;
        previousConfig = ConfigManager.get().copy();
        presets = UserPresetLibrary.current();
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        originalName = "My style " + unique;
        copiedName = "Copy " + unique;
        renamedName = "Cozy keys " + unique;
        importedName = renamedName + " (2)";
        for (String name : List.of(originalName, copiedName, renamedName, importedName))
            require(!presets.list().contains(name), "Probe name unexpectedly exists");
        directory = Files.createTempDirectory("typinganimation-preset-ui-");
        exportedFile = directory.resolve("shared-style.json");
        TypingConfig cfg = ConfigManager.get();
        cfg.settingsModeChosen = true;
        cfg.advancedSettings = true;
        cfg.enabled = true;
        cfg.appearStyle = AppearStyle.POP;
        cfg.soundPreset = SoundPreset.LAPTOP;
        cfg.soundVolume = .23f;
        cfg.customSoundPack = "";
        cfg.adaptiveTyping = true;
        cfg.cursorCustomColor = true;
        cfg.cursorHexColor = "#12ABEF";
        configScreen = new ConfigScreen(previousScreen);
        client.setScreen(configScreen);
        openLibrary();
    }

    /** Drives actual widget click handlers; leaves the manager visible for a screenshot. */
    public static void verifyActions() throws IOException {
        require(previousConfig != null && client.screen == libraryScreen, "Preset manager is not open");
        verifyLayoutAt320();
        libraryScreen.nameInput().setValue(originalName);
        click("save");
        require(presets.list().contains(originalName), "Save current did not create the named preset");
        ownedNames.add(originalName);

        libraryScreen.nameInput().setValue(copiedName);
        click("duplicate");
        require(presets.list().contains(copiedName) && presets.list().contains(originalName),
                "Duplicate changed the source or failed");
        ownedNames.add(copiedName);

        libraryScreen.nameInput().setValue(renamedName);
        click("rename");
        require(presets.list().contains(renamedName) && !presets.list().contains(copiedName), "Rename did not move the preset");
        ownedNames.add(renamedName);

        libraryScreen.pathInput().setValue(exportedFile.toString());
        click("export");
        require(Files.isRegularFile(exportedFile), "Export did not create the chosen file");
        require(Files.readString(exportedFile).contains(renamedName), "Exported preset lost its name");
        int countBeforeDrop = presets.list().size();
        libraryScreen.pathInput().setValue("");
        libraryScreen.onFilesDrop(List.of(exportedFile));
        require(libraryScreen.pathInput().getValue().equals(exportedFile.toAbsolutePath().normalize().toString()),
                "Dropping JSON did not fill the import path");
        require(presets.list().size() == countBeforeDrop, "File drop imported before an explicit Import action");
        libraryScreen.onFilesDrop(List.of(exportedFile, exportedFile));
        require(libraryScreen.pathInput().getValue().equals(exportedFile.toAbsolutePath().normalize().toString()),
                "Invalid multi-file drop changed the path");
        click("import");
        require(presets.list().contains(importedName) && presets.list().contains(renamedName),
                "Import collision overwrote the original or failed to pick a new name");
        ownedNames.add(importedName);

        TypingConfig cfg = ConfigManager.get();
        cfg.appearStyle = AppearStyle.NONE;
        cfg.soundPreset = SoundPreset.MECHANICAL;
        cfg.soundVolume = .91f;
        cfg.adaptiveTyping = false;
        cfg.cursorCustomColor = false;
        cfg.cursorHexColor = "#FFFFFF";
        cfg.animateSigns = false;
        click("apply");
        require(client.screen == configScreen, "Apply did not return to live settings");
        require(cfg.appearStyle == AppearStyle.POP && cfg.soundPreset == SoundPreset.LAPTOP
                        && Math.abs(cfg.soundVolume - .23f) < .001f && cfg.adaptiveTyping
                        && cfg.cursorCustomColor && cfg.cursorHexColor.equals("#12ABEF"),
                "Apply did not restore the saved visual, cursor and sound values");
        require(!cfg.animateSigns && cfg.advancedSettings && cfg.settingsModeChosen,
                "Applying a shared preset changed personal field or interface preferences");

        openLibrary();
        select(importedName);
        click("delete");
        require(presets.list().contains(importedName), "First Delete press removed a preset without confirmation");
        click("delete");
        require(!presets.list().contains(importedName) && presets.list().contains(renamedName),
                "Confirmed Delete failed or removed another preset");
        select(renamedName);
        verifyLayoutAt320();
    }

    private static void openLibrary() {
        libraryScreen = new StyleLibraryScreen(configScreen, false);
        client.setScreen(libraryScreen);
    }

    private static void select(String name) {
        int limit = presets.list().size() + 1;
        for (int i = 0; i < limit && !name.equals(libraryScreen.selectedName()); i++)
            click(libraryScreen.selectorButton());
        require(name.equals(libraryScreen.selectedName()), "Saved preset is unreachable in the selector");
    }

    private static void click(String key) {
        AbstractWidget widget = libraryScreen.actionButton(key);
        require(widget != null && widget.active && widget.visible, "Library action is unavailable: " + key);
        click(widget);
    }

    private static void click(AbstractWidget widget) {
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        boolean accepted = libraryScreen.mouseClicked(x, y, 0);
        libraryScreen.mouseReleased(x, y, 0);
        require(accepted, "Library widget rejected mouse click: " + widget.getMessage().getString());
    }

    /** Checks both real-size and 320 x 240 layouts, then restores the actual screen size. */
    public static void verifyLayoutAt320() {
        int width = libraryScreen.width, height = libraryScreen.height;
        libraryScreen.resize(client, 320, 240);
        try { verifyLayout(); }
        finally { libraryScreen.resize(client, width, height); }
        verifyLayout();
    }

    private static void verifyLayout() {
        List<AbstractWidget> widgets = new ArrayList<>();
        for (var child : libraryScreen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) continue;
            require(widget.getX() >= 0 && widget.getY() >= 0
                            && widget.getX() + widget.getWidth() <= libraryScreen.width
                            && widget.getY() + widget.getHeight() <= libraryScreen.height,
                    "Preset library widget lies outside the screen");
            for (AbstractWidget other : widgets) {
                boolean overlaps = widget.getX() < other.getX() + other.getWidth()
                        && other.getX() < widget.getX() + widget.getWidth()
                        && widget.getY() < other.getY() + other.getHeight()
                        && other.getY() < widget.getY() + widget.getHeight();
                require(!overlaps, "Preset library widgets overlap");
            }
            widgets.add(widget);
        }
        require(widgets.size() == 11, "Preset library is missing controls: " + widgets.size());
    }

    /** Restores the exact config and prior screen. Deletes only this run's names and known temporary files. */
    public static void cleanup() throws IOException {
        if (previousConfig == null) return;
        IOException cleanupFailure = null;
        for (String name : ownedNames) {
            UserPresetLibrary.Result result = presets.delete(name);
            if (!result.ok() && result.status() != UserPresetLibrary.Status.NOT_FOUND)
                cleanupFailure = new IOException("Could not remove the probe preset: " + result.status());
        }
        ownedNames.clear();
        try {
            if (exportedFile != null) Files.deleteIfExists(exportedFile);
            if (directory != null) Files.deleteIfExists(directory);
        } catch (IOException e) { cleanupFailure = e; }
        ConfigManager.get().copyFrom(previousConfig);
        ConfigManager.save();
        previousConfig = null;
        client.setScreen(previousScreen);
        libraryScreen = null;
        configScreen = null;
        previousScreen = null;
        exportedFile = directory = null;
        if (cleanupFailure != null) throw cleanupFailure;
    }

    /** Best effort for a failing self-test; never touches unrelated presets. */
    public static void abort() {
        try { cleanup(); }
        catch (Exception ignored) { }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
