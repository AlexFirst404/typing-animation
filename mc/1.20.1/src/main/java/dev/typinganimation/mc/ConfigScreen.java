package dev.typinganimation.mc;

import dev.typinganimation.core.AppearStyle;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.Easing;
import dev.typinganimation.core.RemoveStyle;
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
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Settings screen (docs/SPEC.md section 6): title, live preview field + Demo, a two-column grid of every option
 * (150 px wide, 22 px row step), Reset + Done. Changes apply to the live config immediately and are saved when the
 * screen closes. Fits a 240 px tall GUI.
 */
public class ConfigScreen extends Screen {
    private static final int COL_W = 150;
    private static final int COL_GAP = 10;
    private static final int ROW_H = 20;
    private static final int ROW_STEP = 22;
    private static final int DEMO_W = 60;

    private final Screen parent;
    private final DemoTyper demo = new DemoTyper();
    private TypingConfig savedSnapshot;
    private String previewText = "";
    private int titleY;
    /** Every option except "enabled" and "optionsButton": only meaningful while animations are enabled. */
    private final List<AbstractWidget> styleOptions = new ArrayList<>();

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

    public ConfigScreen(Screen parent) {
        super(Component.translatable("typinganimation.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final TypingConfig cfg = ConfigManager.get();
        if (savedSnapshot == null) {
            savedSnapshot = cfg.copy();
        }
        final int total = COL_W * 2 + COL_GAP;
        final int left = (this.width - total) / 2;
        final int rows = 7;
        final int blockH = 13 + 26 + rows * ROW_STEP + 4 + ROW_H;
        final int top = Math.max(4, (this.height - blockH) / 2);
        titleY = top;
        final int previewY = top + 13;
        final int gridY = previewY + 26;
        final int bottomY = gridY + rows * ROW_STEP + 4;

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

        List<AbstractWidget> grid = new ArrayList<>();
        enabledButton = onOff("enabled", cfg.enabled, v -> {
            cfg.enabled = v;
            refreshActive();
        });
        grid.add(enabledButton);
        appearButton = CycleButton.<AppearStyle>builder(s -> Component.translatable(s.translationKey()))
                .withValues(AppearStyle.values())
                .withInitialValue(cfg.appearStyle)
                .withTooltip(v -> tooltip("appearStyle"))
                .create(0, 0, COL_W, ROW_H, label("appearStyle"), (b, v) -> cfg.appearStyle = v);
        grid.add(appearButton);
        grid.add(CycleButton.<RemoveStyle>builder(s -> Component.translatable(s.translationKey()))
                .withValues(RemoveStyle.values())
                .withInitialValue(cfg.removeStyle)
                .withTooltip(v -> tooltip("removeStyle"))
                .create(0, 0, COL_W, ROW_H, label("removeStyle"), (b, v) -> cfg.removeStyle = v));
        grid.add(CycleButton.<Easing>builder(s -> Component.translatable(s.translationKey()))
                .withValues(Easing.values())
                .withInitialValue(cfg.easing)
                .withTooltip(v -> tooltip("easing"))
                .create(0, 0, COL_W, ROW_H, label("easing"), (b, v) -> cfg.easing = v));
        durationSlider = new ConfigSlider(0, 0, COL_W, ROW_H, key("durationMs"),
                TypingConfig.DURATION_MIN_MS, TypingConfig.DURATION_MAX_MS, 10,
                () -> cfg.durationMs, v -> cfg.durationMs = (int) Math.round(v), ConfigScreen::ms);
        grid.add(durationSlider);
        grid.add(new ConfigSlider(0, 0, COL_W, ROW_H, key("intensity"),
                TypingConfig.INTENSITY_MIN, TypingConfig.INTENSITY_MAX, 0.05,
                () -> cfg.intensity, v -> cfg.intensity = (float) v, ConfigScreen::multiplier));
        grid.add(new ConfigSlider(0, 0, COL_W, ROW_H, key("staggerMs"),
                TypingConfig.STAGGER_MIN_MS, TypingConfig.STAGGER_MAX_MS, 1,
                () -> cfg.staggerMs, v -> cfg.staggerMs = (int) Math.round(v),
                v -> Component.translatable("typinganimation.config.ms_per_char", (int) Math.round(v))));
        grid.add(new ConfigSlider(0, 0, COL_W, ROW_H, key("glideMs"),
                TypingConfig.GLIDE_MIN_MS, TypingConfig.GLIDE_MAX_MS, 5,
                () -> cfg.glideMs, v -> cfg.glideMs = (int) Math.round(v), ConfigScreen::ms));
        grid.add(onOff("smoothReflow", cfg.smoothReflow, v -> cfg.smoothReflow = v));
        smoothCursorButton = onOff("smoothCursor", cfg.smoothCursor, v -> cfg.smoothCursor = v);
        grid.add(smoothCursorButton);
        grid.add(onOff("animateChat", cfg.animateChat, v -> cfg.animateChat = v));
        grid.add(onOff("animateOtherFields", cfg.animateOtherFields, v -> cfg.animateOtherFields = v));
        grid.add(onOff("animateMultiline", cfg.animateMultiline, v -> cfg.animateMultiline = v));
        optionsButtonToggle = onOff("optionsButton", cfg.optionsButton, v -> cfg.optionsButton = v);
        grid.add(optionsButtonToggle);
        for (int i = 0; i < grid.size(); i++) {
            AbstractWidget w = grid.get(i);
            w.setPosition(left + (i % 2) * (COL_W + COL_GAP), gridY + (i / 2) * ROW_STEP);
            addRenderableWidget(w);
        }
        styleOptions.clear();
        for (AbstractWidget w : grid) {
            if (w != enabledButton && w != optionsButtonToggle) {
                styleOptions.add(w);
            }
        }
        refreshActive();

        resetButton = addRenderableWidget(Button.builder(Component.translatable("typinganimation.config.reset"),
                b -> resetToDefaults()).bounds(left, bottomY, COL_W, ROW_H).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("typinganimation.config.done"),
                b -> onClose()).bounds(left + COL_W + COL_GAP, bottomY, COL_W, ROW_H).build());

        setInitialFocus(preview);
    }

    /** Style options only matter while animations are enabled. */
    private void refreshActive() {
        boolean enabled = ConfigManager.get().enabled;
        for (AbstractWidget w : styleOptions) {
            w.active = enabled;
        }
    }

    private CycleButton<Boolean> onOff(String field, boolean initial, Consumer<Boolean> setter) {
        return CycleButton.onOffBuilder(initial)
                .withTooltip(v -> tooltip(field))
                .create(0, 0, COL_W, ROW_H, label(field), (b, v) -> setter.accept(v));
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
        ConfigManager.reset();
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
        renderBackground(gg); // 1.20.x: Screen#render does not draw the background
        super.render(gg, mouseX, mouseY, partialTick);
        gg.drawCenteredString(this.font, this.title, this.width / 2, titleY, 0xFFFFFF);
    }

    @Override
    public void tick() {
        super.tick();
        if (preview != null) {
            preview.tick(); // 1.20.x: the caret blink is driven by EditBox#tick
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // keep the caret in the preview after clicking a button (sliders keep focus while dragging)
        if (preview != null && getFocused() != preview && !(getFocused() instanceof AbstractSliderButton)) {
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
