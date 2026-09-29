package dev.typinganimation.mc;

import dev.typinganimation.core.AppearStyle;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.Easing;
import dev.typinganimation.core.RemoveStyle;
import dev.typinganimation.core.TypingConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

/**
 * Settings screen: a live preview field with an auto-typing demo, every option as a button/slider with a tooltip
 * (two columns, 150 px wide, 22 px rows), Reset and Done. Changes apply immediately and are saved when the screen
 * closes. The whole layout is 232 px tall, so it fits the smallest (240 px) scaled screen.
 */
public class ConfigScreen extends Screen {
    private static final String PREFIX = "typinganimation.config.";
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_STEP = 22;
    private static final int COLUMN_GAP = 10;
    private static final int CONTENT_WIDTH = 2 * BUTTON_WIDTH + COLUMN_GAP;
    private static final int DEMO_WIDTH = 60;
    /** Title (top+7) .. bottom row end (top+228) plus margins. */
    private static final int CONTENT_HEIGHT = 232;
    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private final Screen parent;
    private final Demo demo = new Demo();
    /** Option widgets by config field name (also used by the self-test). */
    private final Map<String, AbstractWidget> options = new LinkedHashMap<>();
    private EditBox preview;
    private Button demoButton;
    private Button resetButton;
    private Button doneButton;
    private String previewText = "";
    private boolean refocusPreview;
    private int titleY;
    private long frames;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("typinganimation.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        options.clear();
        TypingConfig cfg = ConfigManager.get();
        int left = width / 2 - CONTENT_WIDTH / 2;
        int right = left + BUTTON_WIDTH + COLUMN_GAP;
        int top = Math.max(0, (height - CONTENT_HEIGHT) / 2);
        titleY = top + 7;

        // live preview + demo
        int previewY = top + 20;
        Component hint = Component.translatable(PREFIX + "preview.hint");
        preview = new EditBox(font, left, previewY, CONTENT_WIDTH - DEMO_WIDTH - 6, BUTTON_HEIGHT, hint);
        preview.setMaxLength(256);
        // The preview keeps the focus, and vanilla draws a hint only in unfocused boxes: show the hint as the grey
        // suggestion instead (drawn right after the caret while the box is empty).
        String hintText = hint.getString();
        preview.setValue(previewText);
        preview.setSuggestion(previewText.isEmpty() ? hintText : null);
        preview.setResponder(text -> {
            previewText = text;
            preview.setSuggestion(text.isEmpty() ? hintText : null);
        });
        ((TypingStateHolder) preview).typinganimation$setForceAnimate(true);
        addRenderableWidget(preview);
        demoButton = addRenderableWidget(Button.builder(demoLabel(), button -> toggleDemo())
                .bounds(left + CONTENT_WIDTH - DEMO_WIDTH, previewY, DEMO_WIDTH, BUTTON_HEIGHT).build());

        // options grid
        List<AbstractWidget> grid = new ArrayList<>();
        grid.add(toggle("enabled", cfg.enabled, v -> {
            cfg.enabled = v;
            refreshActive();
        }));
        grid.add(cycle("appearStyle", AppearStyle.values(), cfg.appearStyle, AppearStyle::translationKey,
                v -> cfg.appearStyle = v));
        grid.add(cycle("removeStyle", RemoveStyle.values(), cfg.removeStyle, RemoveStyle::translationKey,
                v -> cfg.removeStyle = v));
        grid.add(cycle("easing", Easing.values(), cfg.easing, Easing::translationKey, v -> cfg.easing = v));
        grid.add(slider("durationMs", TypingConfig.DURATION_MIN_MS, TypingConfig.DURATION_MAX_MS, 10,
                () -> cfg.durationMs, v -> cfg.durationMs = (int) Math.round(v),
                v -> Component.translatable(PREFIX + "ms", (int) Math.round(v))));
        grid.add(slider("intensity", TypingConfig.INTENSITY_MIN, TypingConfig.INTENSITY_MAX, 0.05,
                () -> cfg.intensity, v -> cfg.intensity = (float) v,
                v -> Component.translatable(PREFIX + "multiplier", decimal(v))));
        grid.add(slider("staggerMs", TypingConfig.STAGGER_MIN_MS, TypingConfig.STAGGER_MAX_MS, 1,
                () -> cfg.staggerMs, v -> cfg.staggerMs = (int) Math.round(v),
                v -> Component.translatable(PREFIX + "ms_per_char", (int) Math.round(v))));
        grid.add(slider("glideMs", TypingConfig.GLIDE_MIN_MS, TypingConfig.GLIDE_MAX_MS, 5,
                () -> cfg.glideMs, v -> cfg.glideMs = (int) Math.round(v),
                v -> Component.translatable(PREFIX + "ms", (int) Math.round(v))));
        grid.add(toggle("smoothReflow", cfg.smoothReflow, v -> cfg.smoothReflow = v));
        grid.add(toggle("smoothCursor", cfg.smoothCursor, v -> cfg.smoothCursor = v));
        grid.add(toggle("animateChat", cfg.animateChat, v -> cfg.animateChat = v));
        grid.add(toggle("animateOtherFields", cfg.animateOtherFields, v -> cfg.animateOtherFields = v));
        if (TypingRenderer.MULTILINE_SUPPORTED) {
            grid.add(toggle("animateMultiline", cfg.animateMultiline, v -> cfg.animateMultiline = v));
        }
        grid.add(toggle("optionsButton", cfg.optionsButton, v -> cfg.optionsButton = v));

        int gridY = top + 48;
        for (int i = 0; i < grid.size(); i++) {
            AbstractWidget w = grid.get(i);
            w.setPosition(i % 2 == 0 ? left : right, gridY + (i / 2) * ROW_STEP);
            addRenderableWidget(w);
        }
        int rows = (grid.size() + 1) / 2;
        int bottomY = gridY + rows * ROW_STEP + 6;
        resetButton = addRenderableWidget(Button.builder(Component.translatable(PREFIX + "reset"), button -> resetAll())
                .bounds(left, bottomY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable(PREFIX + "done"), button -> onClose())
                .bounds(right, bottomY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        refreshActive();
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(preview);
    }

    /** Keeps the caret in the preview after a click on a button; a slider keeps the focus until it is released. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);
        if (!(getFocused() instanceof AbstractSliderButton)) {
            focusPreview();
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = super.mouseReleased(event);
        if (getFocused() instanceof AbstractSliderButton) {
            focusPreview();
        }
        return handled;
    }

    private void focusPreview() {
        if (preview != null && getFocused() != preview && minecraft != null && minecraft.gui.screen() == this) {
            setFocused(preview);
        }
    }

    @Override
    public void tick() {
        if (refocusPreview && preview != null) {
            refocusPreview = false;
            setFocused(preview);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        frames++;
        demo.update(AnimationClock.nowMs());
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(font, title, width / 2, titleY, TITLE_COLOR);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        demo.stop();
        ConfigManager.save();
    }

    // ------------------------------------------------------------------ actions

    private void resetAll() {
        ConfigManager.reset();
        rebuildWidgets();
    }

    private void toggleDemo() {
        if (demo.running) {
            demo.stop();
        } else {
            demo.start(AnimationClock.nowMs());
            refocusPreview = true; // show the caret while the demo types
        }
        demoButton.setMessage(demoLabel());
    }

    private Component demoLabel() {
        return Component.translatable(PREFIX + (demo.running ? "demo.stop" : "demo"));
    }

    /** Style options only matter while animations are enabled. */
    private void refreshActive() {
        boolean enabled = ConfigManager.get().enabled;
        for (Map.Entry<String, AbstractWidget> e : options.entrySet()) {
            String key = e.getKey();
            if (!key.equals("enabled") && !key.equals("optionsButton")) {
                e.getValue().active = enabled;
            }
        }
    }

    // ------------------------------------------------------------------ widget factories

    private AbstractWidget toggle(String key, boolean value, Consumer<Boolean> setter) {
        Tooltip tooltip = tooltip(key);
        CycleButton<Boolean> button = CycleButton.onOffBuilder(value)
                .withTooltip(v -> tooltip)
                .create(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable(PREFIX + key),
                        (b, v) -> setter.accept(v));
        options.put(key, button);
        return button;
    }

    private <E> AbstractWidget cycle(String key, E[] values, E value, Function<E, String> translationKey,
                                     Consumer<E> setter) {
        Tooltip tooltip = tooltip(key);
        CycleButton<E> button = CycleButton.<E>builder(v -> Component.translatable(translationKey.apply(v)), value)
                .withValues(Arrays.asList(values))
                .withTooltip(v -> tooltip)
                .create(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable(PREFIX + key),
                        (b, v) -> setter.accept(v));
        options.put(key, button);
        return button;
    }

    private AbstractWidget slider(String key, double min, double max, double step, DoubleSupplier getter,
                                  DoubleConsumer setter, DoubleFunction<Component> label) {
        ValueSlider slider = new ValueSlider(key, min, max, step, getter, setter, label);
        options.put(key, slider);
        return slider;
    }

    private static Tooltip tooltip(String key) {
        return Tooltip.create(Component.translatable(PREFIX + key + ".tooltip"));
    }

    /** "1", "1.5", "1.25" (no trailing zeros). */
    static String decimal(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.indexOf('.') >= 0) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }

    // ------------------------------------------------------------------ self-test hooks

    /** The option widget for a config field name (e.g. "appearStyle"), or null. */
    AbstractWidget option(String key) {
        return options.get(key);
    }

    Map<String, AbstractWidget> options() {
        return options;
    }

    EditBox preview() {
        return preview;
    }

    Button demoButton() {
        return demoButton;
    }

    Button resetButton() {
        return resetButton;
    }

    Button doneButton() {
        return doneButton;
    }

    boolean demoRunning() {
        return demo.running;
    }

    long frames() {
        return frames;
    }

    Screen parent() {
        return parent;
    }

    // ------------------------------------------------------------------ slider

    /** Self-test: lets the slider of {@code key} take arrow keys (as after pressing Enter on it). */
    void allowKeyboardAdjust(String key) {
        if (options.get(key) instanceof ValueSlider slider) {
            slider.allowKeys();
        }
    }

    /**
     * Slider over [min, max] snapped to {@code step} (the handle too); label "Caption: value". Arrow keys move by
     * exactly one step.
     */
    private final class ValueSlider extends AbstractSliderButton {
        private final String key;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleConsumer setter;
        private final DoubleFunction<Component> label;

        ValueSlider(String key, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter,
                    DoubleFunction<Component> label) {
            super(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, Component.empty(), clamp01((getter.getAsDouble() - min) / (max - min)));
            this.key = key;
            this.min = min;
            this.max = max;
            this.step = step;
            this.setter = setter;
            this.label = label;
            setTooltip(tooltip(key));
            updateMessage();
        }

        private double current() {
            double v = min + value * (max - min);
            v = Math.round(v / step) * step;
            return Math.max(min, Math.min(max, v));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("options.generic_value", Component.translatable(PREFIX + key),
                    label.apply(current())));
        }

        void allowKeys() {
            canChangeValue = true;
        }

        private double normalized(double v) {
            return clamp01((v - min) / (max - min));
        }

        @Override
        protected void applyValue() {
            double v = current();
            value = normalized(v); // the handle sits on the step grid, matching the label
            setter.accept(v);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (canChangeValue && (event.isLeft() || event.isRight())) {
                // vanilla moves by 1/(width-8) of the range, which is not a step: use exactly one step
                setValue(normalized(current() + (event.isLeft() ? -step : step)));
                return true;
            }
            return super.keyPressed(event);
        }

        private static double clamp01(double v) {
            return Double.isFinite(v) ? Math.max(0.0, Math.min(1.0, v)) : 0.0;
        }
    }

    // ------------------------------------------------------------------ demo

    /**
     * The sample phrase shortened at a word boundary so that it fits the preview field (with the caret): the whole
     * phrase stays visible instead of scrolling. Falls back to the phrase cut at the width.
     */
    String fittingPhrase(String phrase, EditBox box) {
        int max = box.getInnerWidth() - font.width("_") - 1;
        if (font.width(phrase) <= max) {
            return phrase;
        }
        int cut = phrase.length();
        while (true) {
            int space = phrase.lastIndexOf(' ', cut - 1);
            if (space <= 0) {
                return font.plainSubstrByWidth(phrase, max);
            }
            cut = space;
            String head = phrase.substring(0, cut);
            while (!head.isEmpty() && ",;:-".indexOf(head.charAt(head.length() - 1)) >= 0) {
                head = head.substring(0, head.length() - 1);
            }
            if (font.width(head) <= max) {
                return head;
            }
        }
    }

    /**
     * Types the localized sample phrase into the preview with human-like timing, pauses, erases the last words
     * (every third round everything, sometimes re-inserting the erased part at once like a paste) and loops.
     * Stops when the user edits the preview text.
     */
    private final class Demo {
        private static final int ERASE_ALL = 0, TYPING = 1, PAUSE_FULL = 2, ERASING = 3, PAUSE_ERASED = 4;
        private final Random random = new Random();
        boolean running;
        private int phase;
        private long nextAt;
        private String phrase = "";
        /** The preview text as the demo left it (anything else = the user edited it). */
        private String lastValue = "";
        private int eraseTo;
        private int round;
        private boolean pasteNext;

        void start(long now) {
            phrase = Component.translatable(PREFIX + "demo.text").getString();
            if (preview != null) {
                phrase = fittingPhrase(phrase, preview);
                lastValue = preview.getValue();
            }
            running = true;
            round = 0;
            pasteNext = false;
            phase = ERASE_ALL;
            nextAt = now + 150;
        }

        void stop() {
            running = false;
        }

        void update(long now) {
            EditBox box = preview;
            if (!running || box == null) {
                return;
            }
            if (!box.getValue().equals(lastValue)) {
                // the user typed into the preview: hand it over instead of erasing their text
                stop();
                if (demoButton != null) {
                    demoButton.setMessage(demoLabel());
                }
                return;
            }
            for (int guard = 0; running && now >= nextAt && guard < 6; guard++) {
                step(box, now);
                lastValue = box.getValue();
            }
        }

        private void step(EditBox box, long now) {
            String cur = box.getValue();
            if (box.getCursorPosition() != cur.length()) {
                box.moveCursorToEnd(false);
            }
            switch (phase) {
                case ERASE_ALL -> {
                    if (cur.isEmpty() || phrase.startsWith(cur)) {
                        phase = TYPING;
                        nextAt = now + 350;
                    } else {
                        box.deleteChars(-1);
                        nextAt = now + 16;
                    }
                }
                case TYPING -> {
                    if (!phrase.startsWith(cur)) {
                        phase = ERASE_ALL;
                        nextAt = now;
                    } else if (cur.length() >= phrase.length()) {
                        phase = PAUSE_FULL;
                        nextAt = now + 1400;
                    } else if (pasteNext) {
                        pasteNext = false;
                        box.insertText(phrase.substring(cur.length()));
                        nextAt = now + 300;
                    } else {
                        int cp = phrase.codePointAt(cur.length());
                        box.insertText(Character.toString(cp));
                        nextAt = now + typingDelay(cp);
                    }
                }
                case PAUSE_FULL -> {
                    round++;
                    if (round % 3 == 0) {
                        eraseTo = 0;
                    } else {
                        int words = 1 + random.nextInt(3);
                        int p = cur.length();
                        for (int i = 0; i < words && p > 0; i++) {
                            int space = phrase.lastIndexOf(' ', Math.max(0, p - 2));
                            p = space < 0 ? 0 : space + 1;
                        }
                        eraseTo = p;
                    }
                    phase = ERASING;
                    nextAt = now;
                }
                case ERASING -> {
                    if (cur.length() <= eraseTo) {
                        phase = PAUSE_ERASED;
                        pasteNext = round % 3 == 2;
                        nextAt = now + 550;
                    } else {
                        box.deleteChars(-1);
                        nextAt = now + 30 + random.nextInt(30);
                    }
                }
                default -> {
                    phase = TYPING;
                    nextAt = now;
                }
            }
        }

        /** Delay after typing {@code cp}: quick keys, longer after spaces/punctuation, the occasional hesitation. */
        private long typingDelay(int cp) {
            long d = 45 + random.nextInt(85);
            if (cp == ' ') {
                d += 30 + random.nextInt(120);
            } else if (",.!?;:".indexOf(cp) >= 0) {
                d += 150 + random.nextInt(200);
            }
            if (random.nextInt(20) == 0) {
                d += 200 + random.nextInt(250);
            }
            return d;
        }
    }
}
