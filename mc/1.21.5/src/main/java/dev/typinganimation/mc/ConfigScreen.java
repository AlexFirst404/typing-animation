package dev.typinganimation.mc;

import dev.typinganimation.core.AppearStyle;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.CursorColor;
import dev.typinganimation.core.CursorStyle;
import dev.typinganimation.core.Easing;
import dev.typinganimation.core.RemoveStyle;
import dev.typinganimation.core.SoundPreset;
import dev.typinganimation.core.VisualPreset;
import dev.typinganimation.core.TypingConfig;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

/**
 * Four compact settings pages with a persistent live preview. Each option has a tooltip, changes apply
 * immediately, and Reset / Done stay in the same place. The layout fits a 320 x 240 scaled screen.
 */
public class ConfigScreen extends Screen {
    private static final int COL_W = 150;
    private static final int COL_GAP = 10;
    private static final int ROW_H = 20;
    private static final int ROW_STEP = 22;
    private static final int DEMO_W = 60;

    private static final int PAGE_COUNT = 4;

    private final Screen parent;
    private final DemoTyper demo = new DemoTyper();
    private TypingConfig savedSnapshot;
    private String previewText = "";
    private int titleY;
    private final Map<String, AbstractWidget> options = new LinkedHashMap<>();
    private final Map<AbstractWidget, Integer> optionPages = new LinkedHashMap<>();
    private final List<Button> pageButtons = new ArrayList<>();
    private int selectedPage;
    private VisualPreset selectedPreset;
    private String cachedCustomSoundId;
    private String cachedCustomSoundName;
    private Button presetButton;
    private Button applyPresetButton;
    private Button soundTestButton;
    private int accentX;
    private int accentY;

    // package-private for the self-test
    EditBox preview;
    Button demoButton;
    Button resetButton;
    Button doneButton;
    CycleButton<Boolean> enabledButton;
    CycleButton<AppearStyle> appearButton;
    CycleButton<Boolean> smoothCursorButton;
    CycleButton<Boolean> optionsButtonToggle;
    ConfigSlider durationSlider;


    private static final int VISIBLE_OPTIONS = 8;
    private int optionOffset;
    private int gridLeft, gridRight, gridTop;
    private Button previousOptions, nextOptions, optionCounter;
    private boolean choosingMode;

    private void chooseMode(boolean detailed) {
        TypingConfig cfg = ConfigManager.get();
        cfg.advancedSettings = detailed;
        cfg.settingsModeChosen = true;
        ConfigManager.save();
        optionOffset = 0;
        rebuildWidgets();
    }

    private void addModeChoice() {
        int left = width / 2 - 155;
        int y = Math.max(40, height / 2 - 20);
        addRenderableWidget(Button.builder(Component.translatable("typinganimation.config.mode.basic"),
                b -> chooseMode(false)).bounds(left, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("typinganimation.config.mode.detailed"),
                b -> chooseMode(true)).bounds(left + 160, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + 80, y + 56, 150, 20).build());
    }

    private void addManagementControls(int left, int y) {
        previousOptions = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            optionOffset = Math.max(0, optionOffset - VISIBLE_OPTIONS);
            refreshOptionVisibility();
        }).bounds(left, y - 28, 40, 20).build());
        optionCounter = addRenderableWidget(Button.builder(Component.empty(), b -> {})
                .bounds(left + 44, y - 28, 222, 20).build());
        optionCounter.active = false;
        nextOptions = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            optionOffset += VISIBLE_OPTIONS;
            refreshOptionVisibility();
        }).bounds(left + 270, y - 28, 40, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("typinganimation.config.library.presets"),
                b -> openLibrary(false)).bounds(left, y, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("typinganimation.config.library.sounds"),
                b -> openLibrary(true)).bounds(left + 102, y, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("typinganimation.config.mode.current", Component.translatable(ConfigManager.get().advancedSettings
                ? "typinganimation.config.mode.detailed" : "typinganimation.config.mode.basic")),
                b -> chooseMode(!ConfigManager.get().advancedSettings)).bounds(left + 204, y, 106, 20).build());
    }

    private void openLibrary(boolean sounds) {
        minecraft.setScreen(new StyleLibraryScreen(this, sounds));
    }

    private void resetConfigPreservingMode() {
        boolean detailed = ConfigManager.get().advancedSettings;
        ConfigManager.reset();
        ConfigManager.get().advancedSettings = detailed;
        ConfigManager.get().settingsModeChosen = true;
        ConfigAccess.ensureReachable();
        ConfigManager.save();
    }

    private boolean basicOption(AbstractWidget widget) {
        for (Map.Entry<String, AbstractWidget> entry : options.entrySet()) {
            if (entry.getValue() != widget) continue;
            return switch (entry.getKey()) {
                case "enabled", "appearStyle", "removeStyle", "adaptiveTyping", "visualPreset", "applyPreset",
                     "smoothCursor", "cursorStyle", "cursorColor", "cursorKeepVisibleWhileTyping",
                     "animateChat", "animateOtherFields", "animateMultiline", "animateSigns", "pauseOptionsButton", "optionsButton",
                     "typingSounds", "soundPreset", "soundVolume", "soundTest" -> true;
                default -> false;
            };
        }
        return false;
    }

    private List<AbstractWidget> currentOptions() {
        List<AbstractWidget> visible = new ArrayList<>();
        for (Map.Entry<AbstractWidget, Integer> entry : optionPages.entrySet()) {
            if (entry.getValue() == selectedPage
                    && (ConfigManager.get().advancedSettings || basicOption(entry.getKey()))) {
                visible.add(entry.getKey());
            }
        }
        return visible;
    }

    private void refreshOptionVisibility() {
        List<AbstractWidget> visible = currentOptions();
        optionOffset = Math.max(0, Math.min(optionOffset,
                Math.max(0, (visible.size() - 1) / VISIBLE_OPTIONS * VISIBLE_OPTIONS)));
        for (AbstractWidget widget : optionPages.keySet()) widget.visible = false;
        for (int i = optionOffset; i < Math.min(visible.size(), optionOffset + VISIBLE_OPTIONS); i++) {
            AbstractWidget widget = visible.get(i);
            int cell = i - optionOffset;
            widget.setPosition(cell % 2 == 0 ? gridLeft : gridRight, gridTop + cell / 2 * 22);
            widget.visible = true;
        }
        if (previousOptions != null) {
            previousOptions.visible = nextOptions.visible = optionCounter.visible = visible.size() > VISIBLE_OPTIONS;
            previousOptions.active = optionOffset > 0;
            nextOptions.active = optionOffset + VISIBLE_OPTIONS < visible.size();
            optionCounter.setMessage(Component.translatable("typinganimation.config.optionPage",
                    optionOffset / VISIBLE_OPTIONS + 1, Math.max(1, (visible.size() + VISIBLE_OPTIONS - 1) / VISIBLE_OPTIONS)));
        }
    }

    private AbstractWidget hexColorControl(TypingConfig cfg) {
        EditBox input = new EditBox(font, 0, 0, 150, 20,
                Component.translatable("typinganimation.config.cursorHexColor"));
        input.setMaxLength(7);
        input.setValue(cfg.cursorHexColor);
        input.setTooltip(tooltip("cursorHexColor"));
        input.setResponder(value -> {
            if (value.matches("#[0-9a-fA-F]{6}")) {
                cfg.cursorHexColor = value.toUpperCase(Locale.ROOT);
                input.setTextColor(0xFFE0E0E0);
            } else input.setTextColor(0xFFFF7777);
        });
        options.put("cursorHexColor", input);
        return input;
    }

    /** Runtime UI verification hooks. */
    boolean choosingMode() { return choosingMode; }
    void chooseSettingsMode(boolean detailed) { chooseMode(detailed); }
    int optionPageOffset() { return optionOffset; }
    void refreshFromLibrary() { cachedCustomSoundId = null; rebuildWidgets(); }

    public ConfigScreen(Screen parent) {
        super(Component.translatable("typinganimation.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        options.clear();
        preview = null;
        optionPages.clear();
        pageButtons.clear();
        final TypingConfig cfg = ConfigManager.get();
        ConfigAccess.ensureReachable();
        choosingMode = !cfg.settingsModeChosen;
        if (choosingMode) {
            titleY = Math.max(12, height / 2 - 76);
            addModeChoice();
            return;
        }
        if (savedSnapshot == null) {
            savedSnapshot = cfg.copy();
        }
        final int total = COL_W * 2 + COL_GAP;
        final int left = (this.width - total) / 2;
        final int right = left + COL_W + COL_GAP;
        final int top = Math.max(4, (this.height - 230) / 2);
        titleY = top;
        final int previewY = top + 13;
        final int tabsY = top + 39;
        final int gridY = top + 65;
        final int bottomY = top + 205;
        gridLeft = left;
        gridRight = right;
        gridTop = gridY;
        accentX = left;
        accentY = tabsY + ROW_H;

        preview = new EditBox(this.font, left, previewY, total - DEMO_W - 4, ROW_H,
                Component.translatable("typinganimation.config.preview.hint"));
        preview.setMaxLength(256);
        // The preview keeps the focus, and vanilla draws a hint only in unfocused boxes: show the hint as the grey
        // suggestion instead (drawn right after the caret while the box is empty).
        final String hint = Component.translatable("typinganimation.config.preview.hint").getString();
        preview.setValue(previewText);
        preview.setSuggestion(previewText.isEmpty() ? hint : null);
        preview.setResponder(s -> {
            previewText = s;
            preview.setSuggestion(s.isEmpty() ? hint : null);
        });
        ((TypingStateHolder) preview).typinganimation$setPreview(true);
        addRenderableWidget(preview);

        demoButton = addRenderableWidget(Button.builder(demoLabel(), b -> toggleDemo())
                .bounds(left + total - DEMO_W, previewY, DEMO_W, ROW_H).build());

        String[] pageNames = {"animations", "cursor", "behaviour", "sound"};
        int tabWidth = (total - (PAGE_COUNT - 1) * 4) / PAGE_COUNT;
        for (int i = 0; i < pageNames.length; i++) {
            final int page = i;
            pageButtons.add(addRenderableWidget(Button.builder(label("page." + pageNames[i]), b -> showPage(page))
                    .bounds(left + i * (tabWidth + 4), tabsY, tabWidth, ROW_H).build()));
        }

        List<AbstractWidget> animations = new ArrayList<>();
        enabledButton = onOff("enabled", cfg.enabled, v -> {
            cfg.enabled = v;
            refreshActive();
        });
        animations.add(enabledButton);
        animations.add(cycle("easing", Easing.values(), cfg.easing, Easing::translationKey, v -> cfg.easing = v));
        appearButton = cycle("appearStyle", AppearStyle.values(), cfg.appearStyle, AppearStyle::translationKey, v -> cfg.appearStyle = v);
        animations.add(appearButton);
        animations.add(cycle("removeStyle", RemoveStyle.values(), cfg.removeStyle, RemoveStyle::translationKey, v -> cfg.removeStyle = v));
        durationSlider = slider("durationMs", TypingConfig.DURATION_MIN_MS, TypingConfig.DURATION_MAX_MS, 10,
                () -> cfg.durationMs, v -> cfg.durationMs = (int) Math.round(v), ConfigScreen::ms);
        animations.add(durationSlider);
        animations.add(slider("removeDurationMs", TypingConfig.DURATION_MIN_MS, TypingConfig.DURATION_MAX_MS, 10,
                () -> cfg.removeDurationMs, v -> cfg.removeDurationMs = (int) Math.round(v), ConfigScreen::ms));
        animations.add(slider("intensity", TypingConfig.INTENSITY_MIN, TypingConfig.INTENSITY_MAX, 0.05,
                () -> cfg.intensity, v -> cfg.intensity = (float) v, ConfigScreen::multiplier));
        animations.add(slider("removeIntensity", TypingConfig.INTENSITY_MIN, TypingConfig.INTENSITY_MAX, 0.05,
                () -> cfg.removeIntensity, v -> cfg.removeIntensity = (float) v, ConfigScreen::multiplier));
        animations.add(slider("staggerMs", TypingConfig.STAGGER_MIN_MS, TypingConfig.STAGGER_MAX_MS, 1,
                () -> cfg.staggerMs, v -> cfg.staggerMs = (int) Math.round(v), v -> Component.translatable("typinganimation.config.ms_per_char", (int) Math.round(v))));
        animations.add(slider("maxStaggerMs", TypingConfig.MAX_STAGGER_MIN_MS, TypingConfig.MAX_STAGGER_MAX_MS, 25,
                () -> cfg.maxStaggerMs, v -> cfg.maxStaggerMs = (int) Math.round(v), ConfigScreen::ms));
        animations.add(onOff("adaptiveTyping", cfg.adaptiveTyping, v -> cfg.adaptiveTyping = v));
        addPage(animations, 0, left, right, gridY);
        addPresetControls(left, top + 181, total);

        List<AbstractWidget> cursor = new ArrayList<>();
        smoothCursorButton = onOff("smoothCursor", cfg.smoothCursor, v -> cfg.smoothCursor = v);
        cursor.add(smoothCursorButton);
        cursor.add(slider("cursorGlideMs", TypingConfig.CURSOR_GLIDE_MIN_MS, TypingConfig.CURSOR_GLIDE_MAX_MS, 5,
                () -> cfg.cursorGlideMs, v -> cfg.cursorGlideMs = (int) Math.round(v), ConfigScreen::ms));
        cursor.add(cycle("cursorStyle", CursorStyle.values(), cfg.cursorStyle, CursorStyle::translationKey, v -> cfg.cursorStyle = v));
        cursor.add(cycle("cursorColor", CursorColor.values(), cfg.cursorColor, CursorColor::translationKey, v -> { cfg.cursorColor = v; cfg.cursorCustomColor = false; }));
        cursor.add(slider("cursorWidth", TypingConfig.CURSOR_WIDTH_MIN, TypingConfig.CURSOR_WIDTH_MAX, 1,
                () -> cfg.cursorWidth, v -> cfg.cursorWidth = (int) Math.round(v), v -> Component.translatable("typinganimation.config.pixels", (int) Math.round(v))));
        cursor.add(onOff("smoothReflow", cfg.smoothReflow, v -> cfg.smoothReflow = v));
        cursor.add(slider("glideMs", TypingConfig.GLIDE_MIN_MS, TypingConfig.GLIDE_MAX_MS, 5,
                () -> cfg.glideMs, v -> cfg.glideMs = (int) Math.round(v), ConfigScreen::ms));
        cursor.add(onOff("cursorCustomColor", cfg.cursorCustomColor, v -> cfg.cursorCustomColor = v));
        cursor.add(hexColorControl(cfg));
        cursor.add(slider("cursorBlinkMs", 200, 2000, 50, () -> cfg.cursorBlinkMs,
                v -> cfg.cursorBlinkMs = (int) Math.round(v),
                v -> Component.translatable("typinganimation.config.ms", (int) Math.round(v))));
        cursor.add(onOff("cursorSoftBlink", cfg.cursorSoftBlink, v -> cfg.cursorSoftBlink = v));
        cursor.add(onOff("cursorKeepVisibleWhileTyping", cfg.cursorKeepVisibleWhileTyping,
                v -> cfg.cursorKeepVisibleWhileTyping = v));
        addPage(cursor, 1, left, right, gridY);

        List<AbstractWidget> behaviour = new ArrayList<>();
        behaviour.add(onOff("animateChat", cfg.animateChat, v -> cfg.animateChat = v));
        behaviour.add(onOff("animateOtherFields", cfg.animateOtherFields, v -> cfg.animateOtherFields = v));
        behaviour.add(onOff("animateSigns", cfg.animateSigns, v -> cfg.animateSigns = v));
        behaviour.add(onOff("animateMultiline", cfg.animateMultiline, v -> cfg.animateMultiline = v));
        behaviour.add(onOff("onlyWhenFocused", cfg.onlyWhenFocused, v -> cfg.onlyWhenFocused = v));
        behaviour.add(onOff("pauseOptionsButton", cfg.pauseOptionsButton, v -> cfg.pauseOptionsButton = v));
        optionsButtonToggle = onOff("optionsButton", cfg.optionsButton, v -> cfg.optionsButton = v);
        behaviour.add(optionsButtonToggle);
        addPage(behaviour, 2, left, right, gridY);

        List<AbstractWidget> sound = new ArrayList<>();
        sound.add(onOff("typingSounds", cfg.typingSounds, v -> cfg.typingSounds = v));
        sound.add(cycle("soundPreset", SoundPreset.values(), cfg.soundPreset, SoundPreset::translationKey, v -> { cfg.soundPreset = v; cfg.customSoundPack = ""; }));
        sound.add(slider("soundVolume", TypingConfig.SOUND_VOLUME_MIN, TypingConfig.SOUND_VOLUME_MAX, 0.05,
                () -> cfg.soundVolume, v -> cfg.soundVolume = (float) v,
                v -> Component.translatable("typinganimation.config.percent", (int) Math.round(v * 100))));
        sound.add(slider("soundPitch", TypingConfig.SOUND_PITCH_MIN, TypingConfig.SOUND_PITCH_MAX, 0.05,
                () -> cfg.soundPitch, v -> cfg.soundPitch = (float) v, ConfigScreen::multiplier));
        sound.add(onOff("soundVariation", cfg.soundVariation, v -> cfg.soundVariation = v));
        sound.add(onOff("soundOnDelete", cfg.soundOnDelete, v -> cfg.soundOnDelete = v));
        sound.add(onOff("soundOnEnter", cfg.soundOnEnter, v -> cfg.soundOnEnter = v));
        soundTestButton = Button.builder(Component.translatable("typinganimation.config.soundTest"),
                button -> TypingSounds.preview(ConfigManager.get().soundPreset)).bounds(0, 0, 150, 20).build();
        soundTestButton.setTooltip(tooltip("soundTest"));
        options.put("soundTest", soundTestButton);
        sound.add(soundTestButton);
        addPage(sound, 3, left, right, gridY);
        addManagementControls(left, top + 181);
        showPage(selectedPage);
        refreshActive();

        resetButton = addRenderableWidget(Button.builder(Component.translatable("typinganimation.config.reset"),
                b -> resetToDefaults()).bounds(left, bottomY, COL_W, ROW_H).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("typinganimation.config.done"),
                b -> onClose()).bounds(left + COL_W + COL_GAP, bottomY, COL_W, ROW_H).build());

        if (preview != null) setInitialFocus(preview);
    }

    /** Style options only matter while animations are enabled. */
    private void refreshActive() {
        refreshSoundChoiceLabel();
        boolean enabled = ConfigManager.get().enabled;
        for (Map.Entry<String, AbstractWidget> entry : options.entrySet()) {
            if (!independentControl(entry.getKey())) {
                entry.getValue().active = enabled;
            }
        }
        AbstractWidget navigation = options.get("optionsButton");
        if (navigation != null) {
            navigation.active = ConfigAccess.canHideOptionsButton();
            if (!navigation.active) navigation.setTooltip(Tooltip.create(
                    Component.translatable("typinganimation.config.optionsButton.requiresModMenu")));
        }
    }

    private void addPresetControls(int left, int y, int total) {
        if (selectedPreset == null) {
            selectedPreset = VisualPreset.match(ConfigManager.get());
            if (selectedPreset == null) selectedPreset = VisualPreset.DEFAULT;
        }
        presetButton = Button.builder(presetLabel(), button -> {
            VisualPreset[] presets = VisualPreset.values();
            selectedPreset = presets[(selectedPreset.ordinal() + 1) % presets.length];
            refreshPresetControls();
        }).bounds(left, y, 150, 20).build();
        presetButton.setTooltip(tooltip("visualPreset"));
        applyPresetButton = Button.builder(Component.translatable("typinganimation.config.applyPreset"), button -> {
            selectedPreset.applyTo(ConfigManager.get());
            // Rebuild the visual controls from the chosen values while retaining the preview text and demo.
            rebuildWidgets();
        }).bounds(left + 160, y, 150, 20).build();
        applyPresetButton.setTooltip(tooltip("applyPreset"));
        options.put("visualPreset", presetButton);
        options.put("applyPreset", applyPresetButton);
        optionPages.put(presetButton, 0);
        optionPages.put(applyPresetButton, 0);
        addRenderableWidget(presetButton);
        addRenderableWidget(applyPresetButton);
        refreshPresetControls();
    }

    private Component presetLabel() {
        String key = VisualPreset.match(ConfigManager.get()) == null
                ? "typinganimation.config.visualPreset.custom" : "typinganimation.config.visualPreset";
        return Component.translatable(key, Component.translatable(selectedPreset.translationKey()));
    }

    private void refreshSoundChoiceLabel() {
        TypingConfig cfg = ConfigManager.get();
        AbstractWidget soundChoice = options.get("soundPreset");
        if (!cfg.customSoundPack.equals(cachedCustomSoundId)) {
            cachedCustomSoundId = cfg.customSoundPack;
            cachedCustomSoundName = "";
            if (!cachedCustomSoundId.isEmpty()) {
                var pack = dev.typinganimation.core.CustomSoundPackLibrary.defaultLibrary().find(cachedCustomSoundId);
                cachedCustomSoundName = pack == null ? "?" : pack.name();
            }
        }
        if (soundChoice != null && !cfg.customSoundPack.isEmpty()) {
            soundChoice.setMessage(Component.translatable("typinganimation.config.customSoundChoice", cachedCustomSoundName));
        }
    }

    private void refreshPresetControls() {
        refreshSoundChoiceLabel();
        if (presetButton == null || applyPresetButton == null || selectedPreset == null) return;
        presetButton.setMessage(presetLabel());
        applyPresetButton.active = VisualPreset.match(ConfigManager.get()) != selectedPreset;
    }

    private static boolean independentControl(String key) {
        return key.equals("enabled") || key.equals("optionsButton") || key.equals("pauseOptionsButton") || key.equals("typingSounds")
                || key.startsWith("sound") || key.equals("visualPreset") || key.equals("applyPreset");
    }

    /** Changes pages without rebuilding the preview, so a running demo keeps its animation state. */
    void showPage(int page) {
        if (selectedPage != page) optionOffset = 0;
        selectedPage = Math.max(0, Math.min(PAGE_COUNT - 1, page));
        refreshOptionVisibility();
        for (int i = 0; i < pageButtons.size(); i++) pageButtons.get(i).active = i != selectedPage;
        if (preview != null) setFocused(preview);
    }

    /** Self-test and keyboard navigation helper: reveal the page containing an option. */
    void showOption(String key) {
        AbstractWidget widget = options.get(key);
        Integer page = optionPages.get(widget);
        if (page != null) {
            showPage(page);
            int index = currentOptions().indexOf(widget);
            if (index >= 0) optionOffset = index / VISIBLE_OPTIONS * VISIBLE_OPTIONS;
            refreshOptionVisibility();
        }
    }

    private void addPage(List<AbstractWidget> widgets, int page, int left, int right, int gridY) {
        for (int i = 0; i < widgets.size(); i++) {
            AbstractWidget widget = widgets.get(i);
            widget.setPosition(i % 2 == 0 ? left : right, gridY + (i / 2) * ROW_STEP);
            optionPages.put(widget, page);
            addRenderableWidget(widget);
        }
    }

    private CycleButton<Boolean> onOff(String field, boolean initial, Consumer<Boolean> setter) {
        CycleButton<Boolean> widget = CycleButton.onOffBuilder(initial)
                .withTooltip(v -> tooltip(field))
                .create(0, 0, COL_W, ROW_H, label(field), (b, v) -> { setter.accept(v); refreshPresetControls(); });
        options.put(field, widget);
        return widget;
    }

    private <E> CycleButton<E> cycle(String field, E[] values, E initial, Function<E, String> translation,
                                     Consumer<E> setter) {
        CycleButton<E> widget = CycleButton.<E>builder(v -> Component.translatable(translation.apply(v)))
                .withValues(values).withInitialValue(initial).withTooltip(v -> tooltip(field))
                .create(0, 0, COL_W, ROW_H, label(field), (b, v) -> { setter.accept(v); refreshPresetControls(); });
        options.put(field, widget);
        return widget;
    }

    private ConfigSlider slider(String field, double min, double max, double step, DoubleSupplier getter,
                                DoubleConsumer setter, DoubleFunction<Component> format) {
        ConfigSlider widget = new ConfigSlider(0, 0, COL_W, ROW_H, key(field), min, max, step, getter, v -> { setter.accept(v); refreshPresetControls(); }, format);
        options.put(field, widget);
        return widget;
    }

    Map<String, AbstractWidget> options() {
        return options;
    }

    private static String key(String field) {
        return "typinganimation.config." + field;
    }

    private static Component label(String field) {
        return Component.translatable(key(field));
    }

    private static Tooltip tooltip(String field) {
        return Tooltip.create(Component.translatable(key(field) + ".tooltip"));
    }

    private static Component ms(double v) {
        return Component.translatable("typinganimation.config.ms", (int) Math.round(v));
    }

    /** "1x", "1.5x", "1.25x" (no trailing zeros). */
    private static Component multiplier(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.indexOf('.') >= 0) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return Component.translatable("typinganimation.config.multiplier", s);
    }

    private Component demoLabel() {
        return Component.translatable(demo.running() ? "typinganimation.config.demo.stop"
                : "typinganimation.config.demo");
    }

    void toggleDemo() {
        if (demo.running()) {
            demo.stop();
        } else if (preview != null) {
            demo.start(preview, Component.translatable("typinganimation.config.demo.text").getString(),
                    Util.getMillis());
            setFocused(preview);
        }
        if (demoButton != null) {
            demoButton.setMessage(demoLabel());
        }
    }

    boolean demoRunning() {
        return demo.running();
    }

    private void resetToDefaults() {
        resetConfigPreservingMode();
        selectedPreset = VisualPreset.DEFAULT;
        savedSnapshot = ConfigManager.get().copy();
        rebuildWidgets();
    }

    private void saveIfChanged() {
        TypingConfig cfg = ConfigManager.get();
        if (savedSnapshot == null || !cfg.equals(savedSnapshot)) {
            ConfigManager.save();
            savedSnapshot = cfg.copy();
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        demo.update(preview, Util.getMillis());
        super.render(gg, mouseX, mouseY, partialTick);
        gg.drawCenteredString(this.font, this.title, this.width / 2, titleY, 0xFFFFFF);
        if (choosingMode) {
            gg.drawCenteredString(font, Component.translatable("typinganimation.config.mode.question"), width / 2, titleY + 18, 0xFFE0E0E0);
            gg.drawCenteredString(font, Component.translatable("typinganimation.config.mode.hint"), width / 2, titleY + 32, 0xFFAAAAAA);
            return;
        }
        int tabWidth = (COL_W * 2 + COL_GAP - (PAGE_COUNT - 1) * 4) / PAGE_COUNT;
        int tabX = accentX + selectedPage * (tabWidth + 4);
        gg.fill(tabX, accentY, tabX + tabWidth, accentY + 2, 0xFFB39DFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // keep the caret in the preview after clicking a button (sliders keep focus while dragging)
        if (preview != null && getFocused() != preview && !(getFocused() instanceof AbstractSliderButton) && !(getFocused() instanceof EditBox)) {
            setFocused(preview);
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        if (preview != null && getFocused() instanceof AbstractSliderButton) {
            setFocused(preview);
        }
        return handled;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        stopDemoOnUserInput();
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        // Only keys the preview acted on (editing, caret movement, paste...) stop the demo; modifiers, function keys,
        // Alt+Tab and the like do not.
        if (handled && keyCode != 256 && keyCode != 258) { // Escape, Tab
            stopDemoOnUserInput();
        }
        return handled;
    }

    private void stopDemoOnUserInput() {
        if (demo.running() && getFocused() == preview) {
            toggleDemo();
        }
    }

    @Override
    public void onClose() {
        demo.stop();
        saveIfChanged();
        if (this.minecraft != null) {
            this.minecraft.setScreen(OptionsButton.returnTarget(parent));
        }
    }

    @Override
    public void removed() {
        demo.stop();
        saveIfChanged();
        super.removed();
    }
}
