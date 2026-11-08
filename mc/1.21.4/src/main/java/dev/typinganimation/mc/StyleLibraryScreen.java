package dev.typinganimation.mc;

import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.CustomSoundPackLibrary;
import dev.typinganimation.core.UserPresetLibrary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Local library manager. Files are read only after an explicit Import action. */
public final class StyleLibraryScreen extends Screen {
    private static final String PREFIX = "typinganimation.config.library.";
    private final ConfigScreen parent;
    private final boolean sounds;
    private final UserPresetLibrary presets = UserPresetLibrary.current();
    private final CustomSoundPackLibrary soundPacks = CustomSoundPackLibrary.defaultLibrary();
    private final List<String> names = new ArrayList<>();
    private final List<String> soundIds = new ArrayList<>();
    private final Map<String, Button> actions = new LinkedHashMap<>();
    private String selected = "";
    private String enteredName = "";
    private String enteredPath = "";
    private EditBox nameInput, pathInput;
    private Button selector;
    private Button backButton;
    private Component status = Component.empty();
    private boolean reloading;
    private boolean deleteArmed;
    private int top;

    public StyleLibraryScreen(ConfigScreen parent, boolean sounds) {
        super(Component.translatable(PREFIX + (sounds ? "sounds" : "presets")));
        this.parent = parent;
        this.sounds = sounds;
        if (sounds) selected = ConfigManager.get().customSoundPack;
    }

    @Override
    protected void init() {
        int left = width / 2 - 155;
        top = Math.max(0, (height - 236) / 2);
        refreshNames();
        selector = addRenderableWidget(Button.builder(selectionLabel(), b -> cycle())
                .bounds(left, top + 20, 310, 20).build());
        nameInput = new EditBox(font, left, top + 56, 310, 20,
                Component.translatable(PREFIX + "name"));
        nameInput.setMaxLength(40);
        nameInput.setValue(enteredName);
        nameInput.setResponder(value -> enteredName = value);
        nameInput.visible = !sounds;
        addRenderableWidget(nameInput);
        pathInput = new EditBox(font, left, top + 92, 310, 20,
                Component.translatable(PREFIX + "path"));
        pathInput.setMaxLength(2048);
        pathInput.setValue(enteredPath);
        pathInput.setResponder(value -> enteredPath = value);
        pathInput.setTooltip(Tooltip.create(Component.translatable(PREFIX + (sounds ? "soundPathHint" : "pathHint"))));
        addRenderableWidget(pathInput);
        actions.clear();
        if (sounds) {
            action("use", 0, 0, this::useSound);
            action("preview", 1, 0, this::previewSound);
            action("import", 2, 0, this::importSound);
            action("delete", 0, 1, this::deleteSound);
        } else {
            action("save", 0, 0, () -> report(presets.save(enteredName, ConfigManager.get())));
            action("apply", 1, 0, this::applyPreset);
            action("duplicate", 2, 0, () -> report(presets.duplicate(selected, enteredName)));
            action("rename", 0, 1, () -> report(presets.rename(selected, enteredName)));
            action("import", 1, 1, this::importPreset);
            action("export", 2, 1, this::exportPreset);
            action("delete", 0, 2, this::deletePreset);
        }
        backButton = addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + 204, top + 166, 106, 20).build());
        if (sounds) setInitialFocus(pathInput); else setInitialFocus(nameInput);
    }

    private void action(String key, int column, int row, Runnable run) {
        Button button = Button.builder(Component.translatable(PREFIX + key), b -> {
            if (!reloading) {
                if (!key.equals("delete")) deleteArmed = false;
                try { run.run(); }
                catch (RuntimeException e) { status = Component.translatable(PREFIX + "invalidPath"); }
            }
        }).bounds(width / 2 - 155 + column * 102, top + 118 + row * 24, 98, 20).build();
        button.active = !reloading;
        actions.put(key, addRenderableWidget(button));
    }

    private void refreshNames() {
        names.clear();
        soundIds.clear();
        if (sounds) {
            names.add(Component.translatable(PREFIX + "builtIn").getString());
            soundIds.add("");
            for (CustomSoundPackLibrary.Pack pack : soundPacks.list()) {
                names.add(pack.name());
                soundIds.add(pack.id());
            }
            if (!soundIds.contains(selected)) selected = "";
        } else {
            names.addAll(presets.list());
            if (!names.contains(selected)) selected = names.isEmpty() ? "" : names.get(0);
        }
        if (selector != null) selector.setMessage(selectionLabel());
    }

    private Component selectionLabel() {
        if (sounds) {
            int index = soundIds.indexOf(selected);
            return Component.translatable(PREFIX + "selected", names.isEmpty() ? "" : names.get(Math.max(0, index)));
        }
        return selected.isEmpty() ? Component.translatable(PREFIX + "empty")
                : Component.translatable(PREFIX + "selected", selected);
    }

    private void cycle() {
        if (reloading || names.isEmpty()) return;
        deleteArmed = false;
        if (sounds) selected = soundIds.get((soundIds.indexOf(selected) + 1) % soundIds.size());
        else {
            selected = names.get((names.indexOf(selected) + 1) % names.size());
            enteredName = selected;
            nameInput.setValue(enteredName);
        }
        selector.setMessage(selectionLabel());
    }

    private void report(UserPresetLibrary.Result result) {
        status = Component.translatable(PREFIX + "result." + result.status().name().toLowerCase(Locale.ROOT));
        if (result.ok() && result.name() != null && !result.name().isEmpty()) {
            selected = result.name();
            enteredName = selected;
            nameInput.setValue(selected);
        }
        refreshNames();
    }

    private void applyPreset() {
        UserPresetLibrary.Result result = presets.apply(selected, ConfigManager.get());
        report(result);
        if (result.ok()) {
            ConfigAccess.ensureReachable();
            ConfigManager.save();
            onClose();
        }
    }

    private Path chosenPath() {
        String value = enteredPath.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
            value = value.substring(1, value.length() - 1);
        return Path.of(value);
    }

    private void importPreset() {
        if (enteredPath.isBlank()) { status = Component.translatable(PREFIX + "pathRequired"); return; }
        report(presets.importFile(chosenPath()));
    }

    private void exportPreset() {
        Path destination = enteredPath.isBlank()
                ? presets.directory().resolve(selected.replaceAll("[^\\p{L}\\p{N} ._-]", "_") + ".json")
                : chosenPath();
        UserPresetLibrary.Result result = presets.exportFile(selected, destination);
        report(result);
        if (result.ok() && result.path() != null) {
            enteredPath = result.path().toString();
            pathInput.setValue(enteredPath);
        }
    }

    private void useSound() {
        ConfigManager.get().customSoundPack = selected;
        ConfigManager.save();
        status = Component.translatable(PREFIX + "used");
    }

    private void previewSound() {
        TypingSounds.previewCustom(selected);
        status = Component.translatable(PREFIX + "previewing");
    }

    private void importSound() {
        if (enteredPath.isBlank()) { status = Component.translatable(PREFIX + "pathRequired"); return; }
        CustomSoundPackLibrary.Result result = soundPacks.importPack(chosenPath());
        if (result.success()) {
            if (result.pack() != null) selected = result.pack().id();
            refreshNames();
            reloadSounds();
        } else status = Component.translatable(PREFIX + "soundResult." + result.message());
    }

    private void deletePreset() {
        if (!confirmDelete()) return;
        report(presets.delete(selected));
    }

    private boolean confirmDelete() {
        if (!deleteArmed) {
            deleteArmed = true;
            status = Component.translatable(PREFIX + "confirmDelete");
            return false;
        }
        deleteArmed = false;
        return true;
    }

    private void deleteSound() {
        if (selected.isEmpty()) { status = Component.translatable(PREFIX + "builtInProtected"); return; }
        if (!confirmDelete()) return;
        CustomSoundPackLibrary.Result result = soundPacks.deletePack(selected);
        if (result.success()) {
            if (selected.equals(ConfigManager.get().customSoundPack)) {
                ConfigManager.get().customSoundPack = "";
                ConfigManager.save();
            }
            selected = "";
            refreshNames();
            reloadSounds();
        } else status = Component.translatable(PREFIX + "soundResult." + result.message());
    }

    private void reloadSounds() {
        reloading = true;
        status = Component.translatable(PREFIX + "reloading");
        selector.active = false;
        backButton.active = false;
        for (Button action : actions.values()) action.active = false;
        TypingSounds.reloadCustomPacks().whenComplete((nothing, failure) -> minecraft.execute(() -> {
            reloading = false;
            selector.active = true;
            backButton.active = true;
            for (Map.Entry<String, Button> action : actions.entrySet())
                action.getValue().active = failure == null
                        || (!action.getKey().equals("use") && !action.getKey().equals("preview"));
            status = Component.translatable(PREFIX + (failure == null ? "ready" : "reloadFailed"));
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, top + 6, 0xFFFFFFFF);
        if (!sounds) graphics.drawString(font, Component.translatable(PREFIX + "name"), width / 2 - 155, top + 45, 0xFFCCCCCC);
        else graphics.drawCenteredString(font, Component.translatable(PREFIX + "soundHint"), width / 2, top + 56, 0xFFCCCCCC);
        graphics.drawString(font, Component.translatable(PREFIX + "path"), width / 2 - 155, top + 81, 0xFFCCCCCC);
        int lineY = top + 196;
        for (var line : font.split(status, 310)) {
            if (lineY > top + 226) break;
            graphics.drawString(font, line, width / 2 - 155, lineY, 0xFFD5C6FF);
            lineY += 10;
        }
    }

    @Override
    public void onClose() { if (reloading) return; minecraft.setScreen(parent); parent.refreshFromLibrary(); }


    // Runtime verification hooks; production interaction still goes through the ordinary widgets.
    Button actionButton(String key) { return actions.get(key); }
    Button selectorButton() { return selector; }
    EditBox nameInput() { return nameInput; }
    EditBox pathInput() { return pathInput; }
    String selectedName() { return selected; }

    @Override
    public void onFilesDrop(List<Path> files) {
        if (reloading) return;
        deleteArmed = false;
        String extension = sounds ? ".zip" : ".json";
        if (files.size() != 1 || files.get(0).getFileName() == null
                || !files.get(0).getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension)) {
            status = Component.translatable(PREFIX + "dropExpected", extension);
            return;
        }
        pathInput.setValue(files.get(0).toAbsolutePath().normalize().toString());
        status = Component.translatable(PREFIX + "dropReady");
    }

    @Override
    public void removed() { ConfigManager.save(); }
}
