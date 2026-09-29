package dev.typinganimation.mc;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.typinganimation.core.AppearStyle;
import dev.typinganimation.core.CharTransform;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.FieldAnimationState;
import dev.typinganimation.core.RemoveStyle;
import dev.typinganimation.core.TypingAnimationMod;
import dev.typinganimation.core.TypingConfig;
import dev.typinganimation.mixin.GuiGraphicsAccessor;
import dev.typinganimation.mixin.StringSplitterAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * Dev self-test (docs/SPEC.md section 9), enabled by {@code TYPINGANIMATION_SELFTEST=1} or
 * {@code -Dtypinganimation.selftest=true}; screenshots with {@code TYPINGANIMATION_SELFTEST_SCREENSHOTS=1}. Driven
 * by the client tick ({@code MinecraftMixin}); runs a script of steps, then logs exactly
 * {@code [typinganimation] SELFTEST PASS} or {@code [typinganimation] SELFTEST FAIL: <reason>} and stops the client.
 */
public final class SelfTest {
    public static final boolean ENABLED = flag("TYPINGANIMATION_SELFTEST", "typinganimation.selftest");
    static final boolean SCREENSHOTS = flag("TYPINGANIMATION_SELFTEST_SCREENSHOTS",
            "typinganimation.selftest.screenshots");
    private static final String TAG = "[typinganimation] ";
    private static final int TIMEOUT_TICKS = 20 * 300;
    private static SelfTest instance;
    private static int screenshotCounter;

    private final List<Step> steps = new ArrayList<>();
    private Minecraft mc;
    private boolean started, finished;
    private int ticks, stableTicks;
    private int stepIndex, stepTicks;
    private TypingConfig original;
    private SelfTestScreen screen;
    private String asyncFailure;
    private boolean asyncDone;
    private Button optionsButton;
    private ConfigScreen configScreen;
    private String originalLanguage;
    private CompletableFuture<Void> reload;
    private long multiCaretGlidesMark;

    private SelfTest() {
    }

    private static boolean flag(String env, String property) {
        String v = System.getenv(env);
        if (v == null) {
            v = System.getProperty(property);
        }
        return v != null && (v.equals("1") || v.equalsIgnoreCase("true"));
    }

    public static void onClientTick(Minecraft mc) {
        if (instance == null) {
            instance = new SelfTest();
        }
        instance.tick(mc);
    }

    // ================================================================ driver

    private void tick(Minecraft mc) {
        if (finished) {
            return;
        }
        this.mc = mc;
        ticks++;
        try {
            if (ticks > TIMEOUT_TICKS) {
                throw new AssertionError("timed out (step " + currentStepName() + ", screen " + mc.screen + ")");
            }
            if (!started) {
                boolean ready = mc.getOverlay() == null
                        && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen);
                stableTicks = ready ? stableTicks + 1 : 0;
                if (stableTicks >= 20) {
                    started = true;
                    log("started on " + mc.screen.getClass().getSimpleName());
                    buildScript();
                }
                return;
            }
            stepTicks++;
            // Steps without a delay run in the same tick as the step before them (e.g. a screenshot request right
            // after a keystroke).
            while (stepIndex < steps.size() && !finished) {
                Step step = steps.get(stepIndex);
                if (stepTicks < step.delay) {
                    return; // stepTicks = ticks since the previous step completed
                }
                if (!step.action.getAsBoolean()) {
                    if (stepTicks - step.delay > step.timeout) {
                        throw new AssertionError("step '" + step.name + "' did not complete within "
                                + step.timeout + " ticks");
                    }
                    return;
                }
                stepIndex++;
                stepTicks = 0;
            }
        } catch (Throwable t) {
            fail(t.getMessage() == null ? t.toString() : t.getMessage(), t);
        }
    }

    private String currentStepName() {
        return stepIndex < steps.size() ? steps.get(stepIndex).name : "-";
    }

    private record Step(String name, int delay, int timeout, BooleanSupplier action) {
    }

    /** Runs {@code action} once after {@code delay} ticks. */
    private void run(int delay, String name, Runnable action) {
        steps.add(new Step(name, delay, 0, () -> {
            action.run();
            return true;
        }));
    }

    /** Polls {@code condition} every tick after {@code delay} ticks until true, failing after {@code timeout}. */
    private void waitFor(int delay, String name, int timeout, BooleanSupplier condition) {
        steps.add(new Step(name, delay, timeout, condition));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // ================================================================ script

    private void buildScript() {
        run(0, "force-load mixin targets", this::forceLoadMixinTargets);
        run(0, "open test screen", () -> {
            original = ConfigManager.get().copy();
            ConfigManager.get().copyFrom(TypingConfig.defaults());
            cleanScreenshotDir();
            screen = new SelfTestScreen();
            mc.setScreen(screen);
        });
        waitFor(0, "test screen shown", 40, () -> mc.screen == screen && screen.plain != null);
        run(2, "focus fields", () -> {
            screen.plain.setFocused(true);
            screen.chat.setFocused(true);
            style(AppearStyle.SLIDE_UP, RemoveStyle.FADE, 220, 160);
        });

        // 1. typing one char per tick (default style), chat-like command with coloured formatter
        String typed = "Typing test, hello!";
        String command = "/give @p minecraft:diamond 64";
        for (int i = 0; i < Math.max(typed.length(), command.length()); i++) {
            final int k = i;
            run(1, "type " + i, () -> {
                if (k < typed.length()) {
                    screen.plain.charTyped(typed.charAt(k), 0);
                }
                if (k < command.length()) {
                    screen.chat.charTyped(command.charAt(k), 0);
                }
            });
            if (i == 12) {
                capture("typing-slide_up", 25);
            }
        }
        // 2. paste a long string (stagger + scrolling)
        run(4, "paste", () -> screen.plain.insertText(" Pasted: the quick brown fox jumps over the lazy dog"));
        capture("paste-stagger-scroll", 240);
        // 3. delete chars one by one
        for (int i = 0; i < 8; i++) {
            run(i == 0 ? 12 : 1, "backspace " + i, () -> {
                backspace(screen.plain);
                backspace(screen.chat);
            });
            if (i == 5) {
                capture("delete-fade", 30);
            }
        }
        // 4. caret movement: Home on a long text is a big scroll jump (snaps), the caret then glides to the right
        run(8, "home", () -> key(screen.plain, 268));
        for (int i = 0; i < 4; i++) {
            run(2, "right " + i, () -> key(screen.plain, 262));
        }
        run(2, "insert mid", () -> screen.plain.insertText("[mid]"));
        capture("insert-mid-reflow", 30);
        run(6, "end", () -> key(screen.plain, 269));
        run(6, "left", () -> {
            key(screen.chat, 263);
            key(screen.chat, 263);
            key(screen.chat, 263);
        });
        run(2, "insert in chat", () -> screen.chat.insertText("0"));

        // 5. more styles, slower so the screenshots catch them mid-flight
        styleRound("pop-fall", AppearStyle.POP, RemoveStyle.FALL, " Pop!");
        styleRound("spin-scatter", AppearStyle.SPIN, RemoveStyle.SCATTER, " Spin!");
        styleRound("scramble-shrink", AppearStyle.SCRAMBLE, RemoveStyle.SHRINK, " Scramble");
        styleRound("drop-flyup", AppearStyle.DROP, RemoveStyle.FLY_UP, " Drop!");
        styleRound(null, AppearStyle.WAVE, RemoveStyle.FADE, " wave");
        styleRound(null, AppearStyle.GROW, RemoveStyle.FALL, " grow");
        styleRound(null, AppearStyle.STRETCH, RemoveStyle.SHRINK, " stretch");
        styleRound(null, AppearStyle.SLIDE_LEFT, RemoveStyle.SCATTER, " left");
        styleRound(null, AppearStyle.SLIDE_DOWN, RemoveStyle.FLY_UP, " down");
        styleRound(null, AppearStyle.FADE, RemoveStyle.FADE, " fade");

        // 5b. an edit made while a field is not rendered (hidden, like the creative search box across a tab switch)
        // must not animate when it shows again: no ghosts of the old text, no appear animation of the new one
        final String[] savedPlain = new String[1];
        run(16, "hide plain", () -> screen.plain.setVisible(false));
        run(3, "edit while hidden", () -> {
            savedPlain[0] = screen.plain.getValue();
            screen.plain.setValue("edited while hidden");
        });
        run(3, "show plain", () -> screen.plain.setVisible(true));
        run(2, "no animation after hidden edit", () -> checkSettled(screen.plain, "an edit while hidden"));
        run(1, "restore while hidden", () -> {
            screen.plain.setVisible(false);
            screen.plain.setValue(savedPlain[0]);
        });
        run(3, "show plain again", () -> screen.plain.setVisible(true));
        run(2, "no animation after hidden restore", () -> checkSettled(screen.plain, "a restore while hidden"));

        // 6. multi-line
        run(4, "multi-line focus", () -> {
            style(AppearStyle.SLIDE_UP, RemoveStyle.FADE, 300, 250);
            screen.plain.setFocused(false);
            screen.chat.setFocused(false);
            screen.multi.setFocused(true);
        });
        String lineOne = "Multi-line box";
        for (int i = 0; i < lineOne.length(); i++) {
            final char c = lineOne.charAt(i);
            run(1, "multi type " + i, () -> screen.multi.charTyped(c, 0));
        }
        run(1, "multi enter", () -> {
            key(screen.multi, 257);
            multiCaretGlidesMark = TypingRenderer.STATS.multilineCaretGlides;
        });
        // typing right after a line change (faster than the glide settles): the caret must glide on the new line
        String lineTwo = "Line two: ";
        for (int i = 0; i < lineTwo.length(); i++) {
            final char c = lineTwo.charAt(i);
            run(1, "multi type line two " + i, () -> screen.multi.charTyped(c, 0));
        }
        run(1, "multi caret glides on the new line", () -> check(
                TypingRenderer.STATS.multilineCaretGlides > multiCaretGlidesMark,
                "the multi-line caret did not glide after a line change"));
        run(1, "multi paste", () -> screen.multi.setValue(screen.multi.getValue()
                + "second line, long enough to wrap around the edge of the box"));
        capture("multiline-paste", 200);
        for (int i = 0; i < 5; i++) {
            run(i == 0 ? 10 : 1, "multi backspace " + i, () -> key(screen.multi, 259));
        }
        capture("multiline-delete", 40);

        // 7. at rest
        run(30, "at rest", () -> style(AppearStyle.SLIDE_UP, RemoveStyle.FADE, 220, 160));
        capture("at-rest", 0);
        waitFor(0, "at-rest screenshot", 40, () -> !screen.capturePending());

        // 8. at-rest fidelity: identical input into both columns, left drawn vanilla, right animated
        run(1, "fidelity focus", () -> {
            log("fidelity plain pair text shadow: " + (screen.textShadowOff ? "off (NeoForge setTextShadow)"
                    : "default (no loader switch)"));
            screen.multi.setFocused(false);
            screen.fidelityInput(b -> b.setFocused(true), b -> b.setFocused(true), b -> b.setFocused(true));
            screen.decoInput(b -> b.setFocused(true));
        });
        String fidPlain = "Fidelity: quick brown fox 0123 ÄÖÜß Привет, мир! {}[]";
        String fidChat = "/tp @a ~ ~10 ~ facing entity:minecraft @s";
        String fidMulti = "First line\nSecond line wraps around the box edge nicely\nÜmlaut ẞ ё";
        String fidDeco = SelfTestScreen.DECO_TEXT;
        int n = Math.max(Math.max(fidPlain.length(), fidDeco.length()), Math.max(fidChat.length(), fidMulti.length()));
        for (int i = 0; i < n; i++) {
            final int k = i;
            if (k < fidDeco.length()) {
                run(0, "fidelity type decorated " + i, () -> screen.decoInput(b -> b.charTyped(fidDeco.charAt(k), 0)));
            }
            run(1, "fidelity type " + i, () -> screen.fidelityInput(
                    k < fidPlain.length() ? b -> b.charTyped(fidPlain.charAt(k), 0) : null,
                    k < fidChat.length() ? b -> b.charTyped(fidChat.charAt(k), 0) : null,
                    k < fidMulti.length() ? b -> {
                        char c = fidMulti.charAt(k);
                        if (c == '\n') {
                            key(b, 257);
                        } else {
                            b.charTyped(c, 0);
                        }
                    } : null));
        }
        // Obfuscated (random glyph) chars through the animated path of the decorated pair: typed, animated in (the
        // right column draws them per char), then removed again (ghosts) before the pixel checks.
        final long[] transformedMark = new long[1];
        run(1, "fidelity obfuscated type", () -> {
            transformedMark[0] = TypingRenderer.STATS.transformedGlyphs;
            screen.decoInput(b -> b.insertText("###"));
        });
        capture("fidelity-obfuscated", 60);
        run(8, "fidelity obfuscated remove", () -> {
            check(TypingRenderer.STATS.transformedGlyphs > transformedMark[0],
                    "the obfuscated chars of the decorated pair were not drawn animated");
            screen.decoInput(b -> {
                backspace(b);
                backspace(b);
                backspace(b);
            });
        });
        run(1, "fidelity decorated caret", () -> screen.decoInput(b -> {
            check(b.getValue().equals(SelfTestScreen.DECO_TEXT), "decorated pair value: '" + b.getValue() + "'");
            while (b.getCursorPosition() > SelfTestScreen.DECO_CARET) {
                key(b, 263);
            }
        }));
        run(1, "fidelity caret + selection", () -> screen.fidelityInput(b -> {
            for (int i = 0; i < 6; i++) {
                key(b, 263);
            }
            b.setHighlightPos(b.getCursorPosition() - 4);
        }, b -> {
            key(b, 263);
            key(b, 263);
        }, null));
        run(30, "fidelity check (idle path)", () -> fidelity(1));
        waitFor(0, "fidelity result (idle path)", 60, () -> asyncDone);
        run(0, "fidelity verdict (idle path)", this::checkAsync);
        run(1, "fidelity check (per-char path)", () -> fidelity(2));
        waitFor(0, "fidelity result (per-char path)", 60, () -> asyncDone);
        run(0, "fidelity verdict (per-char path)", this::checkAsync);
        // Translucent chat and decorated text (alpha 0xE0) through the per-char path: overlapping quads blend twice
        // here, so this catches e.g. an underline/strikethrough drawn 1 px left of a mid-piece char, or a different
        // draw order of overlapping effects, that opaque text hides.
        run(1, "fidelity translucent text", () -> {
            screen.fidelityInput(null, b -> b.setTextColor(SelfTestScreen.FID_TRANSLUCENT_TEXT), null);
            screen.decoInput(b -> b.setTextColor(SelfTestScreen.FID_TRANSLUCENT_TEXT));
        });
        run(2, "fidelity check (per-char path, translucent)", () -> fidelity(3));
        waitFor(0, "fidelity result (per-char path, translucent)", 60, () -> asyncDone);
        run(0, "fidelity verdict (per-char path, translucent)", this::checkAsync);
        run(1, "fidelity opaque text", () -> {
            screen.fidelityInput(null, b -> b.setTextColor(SelfTestScreen.DEFAULT_TEXT), null);
            screen.decoInput(b -> b.setTextColor(SelfTestScreen.DEFAULT_TEXT));
        });
        run(1, "fidelity screenshot", () -> {
            screen.fidelityMode = 2;
            screen.requestCapture("fidelity-at-rest", 0, null);
        });
        waitFor(0, "fidelity screenshot", 40, () -> !screen.capturePending());

        // 9. Options screen -> our button -> ConfigScreen -> interact -> Done
        run(1, "open options", () -> mc.setScreen(new OptionsScreen(new TitleScreen(), mc.options)));
        waitFor(0, "options screen shown", 40, () -> mc.screen instanceof OptionsScreen);
        run(4, "find options button", this::findOptionsButton);
        run(0, "screenshot options", () -> grab("options-screen"));
        run(0, "press options button", () -> optionsButton.onPress());
        waitFor(0, "config screen shown", 20, () -> mc.screen instanceof ConfigScreen);
        run(4, "config layout", this::checkConfigLayout);
        run(0, "preview hint and focus", () -> {
            EditBox preview = configScreen.preview;
            check(configScreen.getFocused() == preview && preview.isFocused(), "the preview is not focused on open");
            String hint = Component.translatable("typinganimation.config.preview.hint").getString();
            check(preview.getValue().isEmpty() && hint.equals(suggestion(preview)),
                    "the preview hint is not shown on open (suggestion '" + suggestion(preview) + "')");
        });
        run(0, "screenshot config", () -> grab("config-screen"));
        run(1, "cycle appear style", () -> {
            AppearStyle before = ConfigManager.get().appearStyle;
            configScreen.appearButton.onPress();
            check(ConfigManager.get().appearStyle != before, "appear style button did not change the config");
        });
        run(1, "move duration slider", () -> {
            int before = ConfigManager.get().durationMs;
            ConfigSlider s = configScreen.durationSlider;
            s.onClick(s.getX() + s.getWidth() * 0.8, s.getY() + 10);
            check(ConfigManager.get().durationMs != before, "duration slider did not change the config");
        });
        run(1, "toggle smooth cursor", () -> {
            boolean before = ConfigManager.get().smoothCursor;
            configScreen.smoothCursorButton.onPress();
            check(ConfigManager.get().smoothCursor != before, "smooth cursor button did not change the config");
            configScreen.smoothCursorButton.onPress();
        });
        run(1, "slider arrow keys", () -> {
            ConfigSlider s = configScreen.durationSlider;
            s.allowKeys();
            int before = ConfigManager.get().durationMs;
            s.keyPressed(InputConstants.KEY_RIGHT, 0, 0);
            check(ConfigManager.get().durationMs == Math.min(before + 10, TypingConfig.DURATION_MAX_MS),
                    "right arrow moved the duration slider from " + before + " to " + ConfigManager.get().durationMs);
            s.keyPressed(InputConstants.KEY_LEFT, 0, 0);
            s.keyPressed(InputConstants.KEY_LEFT, 0, 0);
            check(ConfigManager.get().durationMs == Math.max(before - 10, TypingConfig.DURATION_MIN_MS),
                    "left arrow moved the duration slider to " + ConfigManager.get().durationMs + " (from " + before + ")");
            s.keyPressed(InputConstants.KEY_RIGHT, 0, 0);
            check(ConfigManager.get().durationMs == before, "duration slider did not return to " + before);
        });
        run(1, "clicks keep the preview focused", () -> {
            boolean before = ConfigManager.get().smoothCursor;
            click(configScreen, configScreen.smoothCursorButton);
            check(ConfigManager.get().smoothCursor != before, "clicking smooth cursor did not change the config");
            check(configScreen.getFocused() == configScreen.preview, "clicking a button took the focus from the preview");
            click(configScreen, configScreen.smoothCursorButton);
            ConfigSlider s = configScreen.durationSlider;
            double x = s.getX() + s.getWidth() / 2.0;
            double y = s.getY() + s.getHeight() / 2.0;
            configScreen.mouseClicked(x, y, 0);
            check(configScreen.getFocused() == s, "the slider did not keep the focus while pressed");
            configScreen.mouseReleased(x, y, 0);
            check(configScreen.getFocused() == configScreen.preview,
                    "releasing the slider did not give the focus back to the preview");
        });
        run(1, "toggle enabled off", () -> {
            configScreen.enabledButton.onPress();
            check(!ConfigManager.get().enabled, "enabled toggle did not switch off");
            check(!configScreen.appearButton.active && !configScreen.durationSlider.active,
                    "style options stay active while disabled");
            check(configScreen.optionsButtonToggle.active, "the options button toggle was greyed out");
        });
        run(1, "toggle enabled on", () -> {
            configScreen.enabledButton.onPress();
            check(ConfigManager.get().enabled, "enabled toggle did not switch on");
            check(configScreen.appearButton.active && configScreen.durationSlider.active,
                    "style options stay inactive after enabling");
        });
        run(1, "start demo", () -> {
            configScreen.demoButton.onPress();
            check(configScreen.demoRunning(), "demo did not start");
        });
        run(34, "screenshot config demo", () -> grab("config-demo"));
        run(1, "demo stopped by typing", () -> {
            check(configScreen.demoRunning(), "demo is not running");
            configScreen.setFocused(configScreen.preview);
            check(configScreen.charTyped('Z', 0), "preview did not take a typed char");
            check(!configScreen.demoRunning(), "demo kept running after the user typed");
            String v = configScreen.preview.getValue();
            check(v.indexOf('Z') >= 0, "demo erased the user's text: '" + v + "'");
            check(suggestion(configScreen.preview) == null, "the preview hint is still shown with text");
            check(Component.translatable("typinganimation.config.demo").getString()
                    .equals(configScreen.demoButton.getMessage().getString()), "demo button label not reset");
        });
        // turning the Options button off (and on again) takes effect on the Options screen we return to
        run(1, "options button off", () -> {
            configScreen.optionsButtonToggle.onPress();
            check(!ConfigManager.get().optionsButton, "options button toggle did not change the config");
        });
        run(1, "press done", () -> configScreen.doneButton.onPress());
        waitFor(0, "back on options screen", 20, () -> mc.screen instanceof OptionsScreen);
        run(2, "options button gone", () -> check(OptionsButton.find(mc.screen) == null,
                "the Options screen still shows the button after turning it off"));
        run(1, "open config again", () -> mc.setScreen(new ConfigScreen(mc.screen)));
        run(3, "options button on", () -> {
            configScreen = (ConfigScreen) mc.screen;
            configScreen.optionsButtonToggle.onPress();
            check(ConfigManager.get().optionsButton, "options button toggle did not change the config");
            configScreen.doneButton.onPress();
        });
        waitFor(0, "back on options screen (2)", 20, () -> mc.screen instanceof OptionsScreen);
        run(2, "options button back", () -> check(OptionsButton.find(mc.screen) != null,
                "the Options screen lacks the button after turning it on again"));
        run(1, "options opened with the button off", () -> {
            Screen back = mc.screen;
            ConfigManager.get().optionsButton = false;
            try {
                OptionsScreen second = new OptionsScreen(new TitleScreen(), mc.options);
                mc.setScreen(second);
                check(OptionsButton.find(second) == null, "options button added although switched off");
            } finally {
                ConfigManager.get().optionsButton = true;
            }
            mc.setScreen(back);
        });
        run(1, "reset", () -> {
            mc.setScreen(new ConfigScreen(mc.screen));
            configScreen = (ConfigScreen) mc.screen;
            configScreen.resetButton.onPress();
            check(ConfigManager.get().equals(TypingConfig.defaults()), "Reset did not restore defaults: " + ConfigManager.get());
            String json = readConfigFile();
            check(json.contains("\"appearStyle\": \"slide_up\""), "reset config not saved: " + json);
            configScreen.doneButton.onPress();
            check(mc.screen instanceof OptionsScreen, "Done after Reset did not return to the Options screen");
        });
        run(1, "close options", () -> mc.setScreen(new TitleScreen()));

        // 10. the config screen in Russian (Cyrillic labels must be translated and fit), then back to English
        run(1, "switch to ru_ru", () -> switchLanguage("ru_ru"));
        waitFor(0, "ru_ru reload", 1200, this::reloadDone);
        run(2, "open config (ru_ru)", () -> mc.setScreen(new ConfigScreen(new TitleScreen())));
        run(4, "config layout (ru_ru)", () -> {
            checkConfigLayout();
            String title = configScreen.getTitle().getString();
            check(title.chars().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC),
                    "config title not translated to Russian: " + title);
        });
        run(0, "screenshot config (ru_ru)", () -> grab("config-screen-ru_ru"));
        run(1, "switch back to " + "en_us", () -> {
            mc.setScreen(new TitleScreen());
            switchLanguage(originalLanguage);
        });
        waitFor(0, "language restore reload", 1200, this::reloadDone);
        run(2, "finish", this::finish);
    }

    private void switchLanguage(String code) {
        if (originalLanguage == null) {
            originalLanguage = mc.getLanguageManager().getSelected();
        }
        mc.getLanguageManager().setSelected(code);
        reload = mc.reloadResourcePacks();
    }

    private boolean reloadDone() {
        return reload != null && reload.isDone() && mc.getOverlay() == null;
    }

    private void styleRound(String shot, AppearStyle appear, RemoveStyle remove, String text) {
        // slower than the defaults so the screenshots show several chars in different phases
        run(16, "style " + appear + "/" + remove, () -> style(appear, remove, 700, 700));
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            run(1, appear + " type " + i, () -> {
                screen.chat.charTyped(c, 0);
                screen.plain.charTyped(c, 0);
            });
        }
        if (shot != null) {
            capture(shot + "-appear", 60);
        }
        for (int i = 0; i < 3; i++) {
            run(i == 0 ? 16 : 2, remove + " delete " + i, () -> {
                backspace(screen.chat);
                backspace(screen.plain);
            });
        }
        if (shot != null) {
            capture(shot + "-remove", 60);
        }
    }

    private void style(AppearStyle appear, RemoveStyle remove, int durationMs, int removeMs) {
        TypingConfig cfg = ConfigManager.get();
        cfg.appearStyle = appear;
        cfg.removeStyle = remove;
        cfg.durationMs = durationMs;
        cfg.removeDurationMs = removeMs;
        if (screen != null) {
            screen.statusLine = String.format(Locale.ROOT, "appear %s (%d ms), remove %s (%d ms)",
                    appear.name().toLowerCase(Locale.ROOT), durationMs, remove.name().toLowerCase(Locale.ROOT),
                    removeMs);
        }
    }

    /** Adds a step requesting a render-time screenshot {@code delayMs} after the step runs. */
    private void capture(String name, long delayMs) {
        run(0, "capture " + name, () -> screen.requestCapture(name, delayMs, null));
    }

    private static void key(Object widget, int keyCode) {
        if (widget instanceof EditBox b) {
            b.keyPressed(keyCode, 0, 0);
        } else if (widget instanceof MultiLineEditBox m) {
            m.keyPressed(keyCode, 0, 0);
        }
    }

    private static void backspace(EditBox box) {
        box.keyPressed(259, 0, 0);
    }

    private void fidelity(int mode) {
        asyncDone = false;
        asyncFailure = null;
        final String label = mode == 1 ? "idle path" : mode == 2 ? "per-char path" : "per-char path, translucent";
        screen.requestFidelity(mode, diff -> {
            asyncFailure = diff.isEmpty() ? null : "at-rest fidelity mismatch (" + label + "): " + diff;
            asyncDone = true;
            log("fidelity " + label + ": " + (diff.isEmpty() ? "pixel-identical to vanilla" : diff));
        });
    }

    private void checkAsync() {
        check(asyncFailure == null, asyncFailure);
    }

    /** The field shows its value without any animation (no ghosts, no appearing chars). */
    private static void checkSettled(EditBox box, String after) {
        FieldAnimationState st = ((TypingStateHolder) box).typinganimation$state();
        check(st.ghostCount() == 0, st.ghostCount() + " ghost(s) animating after " + after);
        CharTransform tf = new CharTransform();
        TypingConfig cfg = ConfigManager.get();
        for (int i = 0; i < box.getValue().length(); i++) {
            check(!st.appearTransform(i, cfg, tf), "char " + i + " animates in after " + after);
        }
    }

    // ================================================================ checks

    private void forceLoadMixinTargets() {
        ClassLoader loader = SelfTest.class.getClassLoader();
        Class<?>[] targets = {EditBox.class, MultiLineEditBox.class, ChatScreen.class, OptionsScreen.class,
                Minecraft.class, StringSplitter.class, LanguageManager.class, GuiGraphics.class};
        for (Class<?> c : targets) {
            try {
                Class.forName(c.getName(), true, loader);
            } catch (Throwable t) {
                throw new AssertionError("could not load mixin target " + c.getName(), t);
            }
        }
        check(TypingStateHolder.class.isAssignableFrom(EditBox.class), "EditBox mixin not applied");
        check(TypingStateHolder.class.isAssignableFrom(MultiLineEditBox.class), "MultiLineEditBox mixin not applied");
        check(StringSplitterAccessor.class.isAssignableFrom(StringSplitter.class), "StringSplitter accessor not applied");
        check(mc.font.getSplitter() instanceof StringSplitterAccessor, "StringSplitter accessor not applied");
        check(GuiGraphicsAccessor.class.isAssignableFrom(GuiGraphics.class), "GuiGraphics accessor not applied");
        for (String key : new String[]{"typinganimation.title", OptionsButton.KEY, "typinganimation.config.demo",
                "typinganimation.appear.slide_up"}) {
            check(Language.getInstance().has(key), "translation missing: " + key);
        }
        log("mixin targets loaded, translations present");
    }

    /** Left click (press + release) at the centre of {@code w}, through the screen like real input. */
    private static void click(Screen s, AbstractWidget w) {
        double x = w.getX() + w.getWidth() / 2.0;
        double y = w.getY() + w.getHeight() / 2.0;
        s.mouseClicked(x, y, 0);
        s.mouseReleased(x, y, 0);
    }

    /** The grey suggestion of an EditBox (diagnostics only: reflection on the private field, Mojang name in dev). */
    private static String suggestion(EditBox box) {
        try {
            java.lang.reflect.Field f = EditBox.class.getDeclaredField("suggestion");
            f.setAccessible(true);
            return (String) f.get(box);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new AssertionError("can not read the EditBox suggestion", e);
        }
    }

    private static String readConfigFile() {
        try {
            return Files.readString(ConfigManager.path(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("can not read " + ConfigManager.path(), e);
        }
    }

    private void findOptionsButton() {
        Screen s = mc.screen;
        optionsButton = null;
        Button done = null;
        for (GuiEventListener l : s.children()) {
            if (l instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents tc) {
                if (OptionsButton.KEY.equals(tc.getKey())) {
                    optionsButton = b;
                } else if ("gui.done".equals(tc.getKey())) {
                    done = b;
                }
            }
        }
        check(optionsButton != null, "Typing Animation button missing from the Options screen");
        check(!optionsButton.getMessage().getString().equals(OptionsButton.KEY), "options button label untranslated");
        check(optionsButton.getY() >= 0 && optionsButton.getY() + optionsButton.getHeight() <= s.height,
                "options button outside the screen");
        if (done != null) {
            check(optionsButton.getY() + optionsButton.getHeight() <= done.getY(),
                    "options button overlaps the Done button");
        }
        for (GuiEventListener l : s.children()) {
            if (l instanceof AbstractWidget w && w != optionsButton && overlaps(w, optionsButton)) {
                throw new AssertionError("options button overlaps " + w.getMessage().getString());
            }
        }
    }

    private void checkConfigLayout() {
        configScreen = (ConfigScreen) mc.screen;
        List<AbstractWidget> widgets = new ArrayList<>();
        for (GuiEventListener l : configScreen.children()) {
            if (l instanceof AbstractWidget w) {
                check(w.getX() >= 0 && w.getY() >= 0 && w.getX() + w.getWidth() <= configScreen.width
                                && w.getY() + w.getHeight() <= configScreen.height,
                        "config widget outside the screen: " + w.getMessage().getString());
                for (AbstractWidget o : widgets) {
                    check(!overlaps(w, o), "config widgets overlap: " + w.getMessage().getString() + " / "
                            + o.getMessage().getString());
                }
                widgets.add(w);
            }
        }
        check(widgets.size() >= 18, "config screen has only " + widgets.size() + " widgets");
        log("config screen " + configScreen.width + "x" + configScreen.height + ": " + widgets.size()
                + " widgets, no overlaps");
    }

    private static boolean overlaps(AbstractWidget a, AbstractWidget b) {
        return a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth()
                && a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight();
    }

    private void finish() {
        ConfigManager.get().copyFrom(original);
        ConfigManager.save();
        if (TypingRenderer.firstError != null) {
            throw new AssertionError("renderer error: " + TypingRenderer.firstError, TypingRenderer.firstError);
        }
        TypingRenderer.Stats s = TypingRenderer.STATS;
        log("render stats: " + s);
        check(s.transformedGlyphs > 0, "no animated glyph was drawn");
        check(s.ghostGlyphs > 0, "no removed-char ghost was drawn");
        check(s.glidingGlyphs > 0, "no reflow glide happened");
        check(s.caretGlides > 0, "the caret never glided");
        check(s.chatGlyphs > 0, "the CHAT-kind field was never drawn per char");
        check(s.multilineGlyphs > 0, "the multi-line field was never drawn per char");
        check(s.idleSegments > 0, "the idle fast path was never used");
        check(s.mismatches == 0, "formatter length mismatches: " + s.mismatches);
        check(s.multilineCaretGlides > 0, "the multi-line caret never glided");
        check(s.visibleCaptures > 0, "the EditBox visible-substring capture never ran");
        finished = true;
        TypingAnimationMod.LOGGER.info(TAG + "SELFTEST PASS");
        mc.stop();
    }

    private void fail(String reason, Throwable t) {
        if (finished) {
            return;
        }
        finished = true;
        try {
            if (original != null) {
                ConfigManager.get().copyFrom(original);
                ConfigManager.save();
            }
        } catch (Throwable ignored) {
            // best effort
        }
        TypingAnimationMod.LOGGER.error(TAG + "SELFTEST FAIL: " + reason, t);
        if (mc != null) {
            mc.stop();
        }
    }

    private static void log(String message) {
        TypingAnimationMod.LOGGER.info(TAG + "selftest: " + message);
    }

    // ================================================================ screenshots

    private static Path screenshotDir() {
        return Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize().resolve("screenshots")
                .resolve("typinganimation-selftest");
    }

    private static void cleanScreenshotDir() {
        if (!SCREENSHOTS) {
            return;
        }
        Path dir = screenshotDir();
        try (Stream<Path> files = Files.exists(dir) ? Files.list(dir) : Stream.empty()) {
            files.filter(p -> p.toString().endsWith(".png")).forEach(p -> p.toFile().delete());
        } catch (Exception e) {
            log("could not clean " + dir + ": " + e);
        }
    }

    /** Tick-time screenshot of the last rendered frame. */
    private static void grab(String name) {
        writeScreenshot(name);
    }

    /** Reads back the main render target (render thread) and writes a PNG; returns its absolute path. */
    static String writeScreenshot(String name) {
        if (!SCREENSHOTS) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        File file = screenshotDir().resolve(String.format(Locale.ROOT, "%02d-%s.png", ++screenshotCounter, name))
                .toFile();
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(file.toPath().getParent());
            img.writeToFile(file);
            String path = file.getAbsolutePath();
            TypingAnimationMod.LOGGER.info(TAG + "SELFTEST SCREENSHOT " + path);
            return path;
        } catch (Exception e) {
            log("screenshot " + name + " failed: " + e);
            return null;
        }
    }
}
