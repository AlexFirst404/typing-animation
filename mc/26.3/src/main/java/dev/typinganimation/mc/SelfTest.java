package dev.typinganimation.mc;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import dev.typinganimation.core.AppearStyle;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.RemoveStyle;
import dev.typinganimation.core.TypingAnimationMod;
import dev.typinganimation.core.TypingConfig;
import dev.typinganimation.mixin.MultilineTextFieldAccessor;
import dev.typinganimation.mixin.StringSplitterAccessor;
import dev.typinganimation.mixin.StringViewAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Dev self-test (docs/SPEC.md section 9), driven by the client tick hook of {@code MinecraftMixin}. Enabled by
 * {@code TYPINGANIMATION_SELFTEST=1}; screenshots with {@code TYPINGANIMATION_SELFTEST_SCREENSHOTS=1}.
 * Starts once the title (or first-launch accessibility onboarding) screen is shown without an overlay and runs a
 * script of steps, one step per tick:
 * <ol>
 *   <li>force-load every class our mixins target and check the duck/accessor interfaces are applied;</li>
 *   <li>a test screen with a plain field, a chat-like CHAT field with a coloured formatter and a multi-line
 *       field: typing, paste, deletion, caret moves, scrolling, all appear/remove styles (screenshots mid-animation);
 *       the first frame after a scroll back (HOME) and after typing at the end of the scrolled field is traced:
 *       the scroll must glide, neighbouring chars must neither overlap nor leave gaps, and the text must reach the
 *       edge of the text area; a wholesale replacement of the multi-line text (book page turn) is not animated;
 *       at-rest fidelity: the same text rendered by vanilla (mod disabled), by our idle path and by the forced
 *       per-char path must give identical pixels in the text-field regions;</li>
 *   <li>Options screen -&gt; our button -&gt; ConfigScreen: layout checks (also at 320x240), cycle a button, drag a
 *       slider, arrow keys move a slider by one step, type into the preview, run the demo (its phrase fits the
 *       preview, typing into the preview stops it), Done saves; switching the Options button off/on applies when
 *       the config screen returns to the Options screen (also when it was off when that screen opened); reopen,
 *       Reset, Done;</li>
 *   <li>{@code TypingRenderer.firstError == null}; log PASS/FAIL and stop the client.</li>
 * </ol>
 */
public final class SelfTest {
    private static final String TAG = "[typinganimation] ";
    private static final Logger LOG = TypingAnimationMod.LOGGER;
    private static final int START_TIMEOUT_TICKS = 20 * 300;
    private static final int RUN_TIMEOUT_TICKS = 20 * 240;
    private static final int PLAIN_TEXT_COLOR = 0xFFE0E0E0;
    private static final int WHITE = 0xFFFFFFFF;

    private static SelfTest instance;
    private static volatile long frameCount;

    private final Minecraft mc;
    private final boolean saveScreenshots = ClientInit.flag(System.getenv("TYPINGANIMATION_SELFTEST_SCREENSHOTS"))
            || Boolean.getBoolean("typinganimation.selftest.screenshots");
    private final List<Step> steps = new ArrayList<>();
    private final AtomicInteger pendingCaptures = new AtomicInteger();
    private final Map<String, Grab> grabs = new HashMap<>();
    private final Map<String, TypingRenderer.Trace> traces = new HashMap<>();
    private volatile Throwable asyncError;

    private int ticks;
    private boolean started;
    private boolean finished;
    private int startTick;
    private int index;
    private int stepTick;
    private long stepMark;
    private int settledTicks;
    private int otherScreenTicks;
    private Screen startScreen;
    private SelfTestScreen screen;
    private OptionsScreen options;
    private ConfigScreen config;
    private TypingConfig original;
    private long statMark;

    private SelfTest(Minecraft mc) {
        this.mc = mc;
    }

    /** Client tick (end of {@code Minecraft#tick}). */
    public static void onTick(Minecraft mc) {
        if (instance == null) {
            instance = new SelfTest(mc);
        }
        instance.tick();
    }

    /** End of every rendered frame ({@code Minecraft#runTick}). */
    public static void onFrame() {
        frameCount++;
    }

    // =================================================================== driver

    private void tick() {
        if (finished) {
            return;
        }
        ticks++;
        try {
            if (!started) {
                if (!ready()) {
                    if (ticks > START_TIMEOUT_TICKS) {
                        throw new AssertionError("no title screen within " + START_TIMEOUT_TICKS + " ticks");
                    }
                    return;
                }
                start();
            }
            Throwable async = asyncError;
            if (async != null) {
                throw new AssertionError("screenshot failed", async);
            }
            if (TypingRenderer.firstError != null) {
                throw new AssertionError("render path error", TypingRenderer.firstError);
            }
            if (index >= steps.size()) {
                pass();
                return;
            }
            Step step = steps.get(index);
            if (ticks - startTick > RUN_TIMEOUT_TICKS) {
                throw new AssertionError("timed out in step '" + step.name + "'");
            }
            if (stepTick == 0) {
                stepMark = frameCount;
                LOG.debug(TAG + "selftest step {}: {}", index, step.name);
            }
            boolean done = step.body.run(stepTick);
            stepTick++;
            if (done) {
                index++;
                stepTick = 0;
            }
        } catch (Throwable t) {
            String where = index < steps.size() ? " (step " + index + " '" + steps.get(index).name + "')" : "";
            fail(t.getMessage() + where, t);
        }
    }

    /**
     * The title screen or the first-launch accessibility onboarding screen, without a loading overlay; any other
     * screen (e.g. a loader's warning screen) is accepted after it stayed for 10 s.
     */
    private boolean ready() {
        Screen s = mc.gui.screen();
        if (mc.gui.overlay() != null || s == null) {
            otherScreenTicks = 0;
            return false;
        }
        if (s instanceof TitleScreen || s instanceof AccessibilityOnboardingScreen) {
            return true;
        }
        return ++otherScreenTicks > 200;
    }

    private void start() {
        started = true;
        startTick = ticks;
        startScreen = mc.gui.screen();
        LOG.info(TAG + "SELFTEST START (screen {}, screenshots {})", startScreen.getClass().getSimpleName(),
                saveScreenshots);
        if (startScreen instanceof AccessibilityOnboardingScreen) {
            // first launch of a fresh run directory: behave as if the user confirmed the onboarding screen
            mc.options.onboardAccessibility = false;
            mc.options.save();
        }
        original = ConfigManager.get().copy();
        cfg().copyFrom(TypingConfig.defaults());
        buildScript();
    }

    private void pass() {
        finished = true;
        restoreConfig();
        LOG.info(TAG + "SELFTEST PASS");
        mc.stop();
    }

    private void fail(String reason, Throwable t) {
        finished = true;
        try {
            restoreConfig();
        } catch (Throwable ignored) {
            // best effort
        }
        LOG.error(TAG + "SELFTEST FAIL: " + reason, t);
        mc.stop();
    }

    private void restoreConfig() {
        TypingRenderer.debugForcePerChar = false;
        TypingRenderer.debugNoMerge = false;
        if (original != null) {
            cfg().copyFrom(original);
            ConfigManager.save();
        }
    }

    private static TypingConfig cfg() {
        return ConfigManager.get();
    }

    // =================================================================== script

    private void buildScript() {
        act("force-load mixin targets", this::forceLoadTargets);
        act("open test screen", () -> {
            screen = new SelfTestScreen(startScreen);
            mc.gui.setScreen(screen);
        });
        until("test screen rendered", () -> mc.gui.screen() == screen && screen.frames >= 3, 200);
        act("slow animations for screenshots", () -> {
            TypingConfig c = cfg();
            c.durationMs = 700;
            c.removeDurationMs = 700;
            c.staggerMs = 25;
            c.maxStaggerMs = 600;
            c.glideMs = 120;
        });

        // --- typing one char per tick
        String plainText = "Typing animation";
        String chatText = "/give @p diamond";
        String multiText = "Multi-line field";
        for (int i = 0; i < plainText.length(); i++) {
            int k = i;
            act("type char " + k, () -> {
                type(screen.plain, plainText.charAt(k));
                type(screen.chat, chatText.charAt(k));
                type(screen.multi, multiText.charAt(k));
            });
            if (i == 11) {
                screenshot("01-typing-slide_up");
            }
        }
        // --- paste (long text: the plain field scrolls)
        act("paste", () -> {
            screen.plain.insertText(" + pasted: the quick brown fox jumps over the lazy dog");
            screen.chat.insertText(" 64 {display:{Name:'Shiny',Lore:['Glows','Rare']},Unbreakable:1b} replace");
            screen.multi.setValue(screen.multi.getValue() + "\nSecond line, pasted at once\nand a third one");
            check(screen.plain.getValue().length() > 60, "paste into plain field failed");
        });
        waitTicks(3);
        screenshot("02-paste-stagger");
        waitTicks(12);

        // --- deleting (FALL)
        act("remove style FALL", () -> cfg().removeStyle = RemoveStyle.FALL);
        for (int i = 0; i < 6; i++) {
            act("backspace " + i, () -> {
                String before = screen.plain.getValue();
                key(screen.plain, InputConstants.KEY_BACKSPACE, InputConstants.KEYCODE_BACKSPACE);
                key(screen.chat, InputConstants.KEY_BACKSPACE, InputConstants.KEYCODE_BACKSPACE);
                key(screen.multi, InputConstants.KEY_BACKSPACE, InputConstants.KEYCODE_BACKSPACE);
                check(screen.plain.getValue().length() == before.length() - 1, "backspace did not delete");
            });
            if (i == 3) {
                screenshot("03-delete-fall");
            }
        }
        waitTicks(10);

        // --- caret moves and scrolling
        for (int i = 0; i < 5; i++) {
            act("left " + i, () -> {
                key(screen.plain, InputConstants.KEY_LEFT, InputConstants.KEYCODE_LEFT);
                key(screen.chat, InputConstants.KEY_LEFT, InputConstants.KEYCODE_LEFT);
                key(screen.multi, InputConstants.KEY_LEFT, InputConstants.KEYCODE_LEFT);
            });
        }
        act("home (scroll back)", () -> {
            check(plainScrolled(), "the plain field is not scrolled before HOME");
            traces.put("home", TypingRenderer.traceNextFrame((TypingStateHolder) screen.plain));
            check(fieldScrolled(screen.chat), "the chat field is not scrolled before HOME");
            traces.put("home-chat", TypingRenderer.traceNextFrame((TypingStateHolder) screen.chat));
            key(screen.plain, InputConstants.KEY_HOME, InputConstants.KEYCODE_HOME);
            key(screen.chat, InputConstants.KEY_HOME, InputConstants.KEYCODE_HOME);
            key(screen.multi, InputConstants.KEY_HOME, InputConstants.KEYCODE_HOME);
            check(screen.plain.getCursorPosition() == 0, "HOME did not move the caret");
        });
        waitTicks(1);
        screenshot("04-home-scroll-glide");
        until("HOME frame traced", () -> traces.get("home").done && traces.get("home-chat").done, 100);
        act("check HOME scroll glide", () -> {
            checkScrollTrace(traces.get("home"), "HOME", -1);
            checkScrollTrace(traces.get("home-chat"), "HOME in the formatted chat field", -1);
        });
        waitTicks(8);
        act("end", () -> {
            key(screen.plain, InputConstants.KEY_END, InputConstants.KEYCODE_END);
            key(screen.chat, InputConstants.KEY_END, InputConstants.KEYCODE_END);
            screen.multi.setValue(screen.multi.getValue()); // caret to the end
            // grey suggestion after the caret (like command completion): it must move with a scroll glide
            screen.plain.setSuggestion(" suggestion");
        });
        waitTicks(8);
        act("type at the end of the scrolled field", () -> {
            check(plainScrolled(), "the plain field is not scrolled while typing at its end");
            traces.put("typing", TypingRenderer.traceNextFrame((TypingStateHolder) screen.plain));
            statMark = TypingRenderer.statShiftedSuggestions;
            // wider than any free space at the right edge: the field has to scroll
            type(screen.plain, 'M');
            type(screen.plain, 'W');
        });
        until("typing frame traced", () -> traces.get("typing").done, 100);
        act("check typing scroll glide", () -> {
            checkScrollTrace(traces.get("typing"), "typing at the end", 1);
            check(TypingRenderer.statShiftedSuggestions > statMark, "the suggestion did not follow the scroll glide");
            screen.plain.setSuggestion(null);
        });
        waitTicks(8);
        // --- selection highlight during a scroll glide
        act("select back to the start (scroll glide with a selection)", () -> {
            check(plainScrolled(), "the plain field is not scrolled before selecting");
            statMark = TypingRenderer.statShiftedHighlights;
            screen.plain.moveCursorToStart(true); // what Shift+Home does
            check(!screen.plain.getHighlighted().isEmpty(), "Shift+Home did not select anything");
        });
        waitFrames(3);
        act("check selection highlight glide", () -> {
            long moved = TypingRenderer.statShiftedHighlights - statMark;
            check(moved > 0, "the selection highlight did not follow the scroll glide");
            LOG.info(TAG + "selftest: selection highlight moved with the scroll glide in {} draw calls", moved);
            screen.plain.moveCursorToEnd(false);
        });
        waitTicks(8);

        // --- every appear / remove style
        Object[][] styles = {
                {AppearStyle.POP, RemoveStyle.SHRINK, true},
                {AppearStyle.SPIN, RemoveStyle.SCATTER, true},
                {AppearStyle.SCRAMBLE, RemoveStyle.FLY_UP, true},
                {AppearStyle.DROP, RemoveStyle.FADE, true},
                {AppearStyle.WAVE, RemoveStyle.FALL, true},
                {AppearStyle.GROW, RemoveStyle.SHRINK, false},
                {AppearStyle.STRETCH, RemoveStyle.SCATTER, false},
                {AppearStyle.SLIDE_LEFT, RemoveStyle.FLY_UP, false},
                {AppearStyle.SLIDE_DOWN, RemoveStyle.FADE, false},
                {AppearStyle.FADE, RemoveStyle.NONE, false},
                {AppearStyle.NONE, RemoveStyle.FALL, false},
        };
        int shot = 5;
        for (Object[] st : styles) {
            AppearStyle appear = (AppearStyle) st[0];
            RemoveStyle remove = (RemoveStyle) st[1];
            boolean screenshot = (Boolean) st[2];
            String word = " " + appear.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            act("style " + appear + "/" + remove, () -> {
                cfg().appearStyle = appear;
                cfg().removeStyle = remove;
                screen.chat.deleteWords(-1);
                for (int k = 0; k < 4; k++) {
                    key(screen.multi, InputConstants.KEY_BACKSPACE, InputConstants.KEYCODE_BACKSPACE);
                }
            });
            for (int i = 0; i < Math.min(6, word.length()); i++) {
                int k = i;
                act("type " + appear + " " + k, () -> {
                    type(screen.plain, word.charAt(k));
                    type(screen.chat, word.charAt(k));
                    type(screen.multi, word.charAt(k));
                });
            }
            if (screenshot) {
                screenshot(String.format("%02d-style-%s-%s", shot++, lower(appear), lower(remove)));
            }
            waitTicks(4);
        }

        // --- at rest
        act("default settings", () -> {
            cfg().copyFrom(TypingConfig.defaults());
            logMultiCursor("after styles");
        });
        until("all fields settled", this::settled, 200);
        act("check carets", () -> {
            logMultiCursor("at rest");
            check(multiCursor() == screen.multi.getValue().length(), "multi-line caret moved unexpectedly");
            check(screen.plain.getCursorPosition() == screen.plain.getValue().length(), "plain caret moved unexpectedly");
        });
        screenshot("10-at-rest");

        // --- a wholesale replacement of a multi-line field (book page turn) is not animated
        act("multi-line page replace", () -> screen.multi.setValue(
                "A completely different page of text,\nlong enough to count as a page turn."));
        waitFrames(1);
        act("check page replace", () -> {
            WidgetAnimation anim = ((TypingStateHolder) screen.multi).typinganimation$animation();
            check(anim.state.ghostCount() == 0, "page replacement left " + anim.state.ghostCount() + " ghosts");
        });
        until("page replace settled", this::settled, 200);

        // --- decorated text (underline/strikethrough/italic/bold spans across pieces) typed one char per tick, with and
        //     without text shadow; then obfuscated chars typed and removed again while animating (random glyphs are
        //     never compared with vanilla)
        act("decorated fields focused", () -> {
            screen.deco.setFocused(true);
            screen.decoFlat.setFocused(true);
        });
        String decoText = SelfTestScreen.DECO_TEXT;
        for (int i = 0; i < decoText.length(); i++) {
            int k = i;
            act("type decorated char " + k, () -> {
                type(screen.deco, decoText.charAt(k));
                type(screen.decoFlat, decoText.charAt(k));
            });
            if (i == 22) {
                screenshot("10a-decorated-typing");
            }
        }
        long[] glyphMark = new long[1];
        act("type obfuscated chars", () -> {
            glyphMark[0] = TypingRenderer.statGlyphs;
            screen.deco.insertText("###");
            screen.decoFlat.insertText("###");
        });
        waitFrames(3);
        screenshot("10b-decorated-obfuscated");
        waitTicks(4);
        act("remove obfuscated chars", () -> {
            check(TypingRenderer.statGlyphs > glyphMark[0], "the obfuscated chars were not drawn animated");
            screen.deco.setValue(decoText);
            screen.decoFlat.setValue(decoText);
        });
        until("decorated fields settled", this::settled, 200);
        act("decorated caret", () -> {
            for (EditBox b : new EditBox[]{screen.deco, screen.decoFlat}) {
                check(b.getValue().equals(decoText), "decorated field value: '" + b.getValue() + "'");
                b.moveCursorTo(SelfTestScreen.DECO_CARET, false);
                b.setFocused(false); // no blinking caret in the pixel comparisons
            }
        });

        // --- at-rest fidelity: vanilla vs our idle path vs forced per-char path
        act("fidelity text", () -> {
            screen.plain.setValue("Fidelity: quick brown fox 0123 [ok]");
            screen.chat.setValue("/tp @s ~1 ~2 ~3 minecraft:overworld true");
            // setValue keeps the scroll position of the longer old value: show the text from its start
            screen.chat.moveCursorToStart(false);
            screen.chat.moveCursorToEnd(false);
            screen.multi.setFocused(false);
            screen.multi.setValue("Line one of the multi-line box\nLine two: abc XYZ 123");
            screen.multi.setScrollAmount(0.0);
        });
        until("fidelity text settled", this::settled, 200);
        act("mod disabled (vanilla)", () -> cfg().enabled = false);
        waitFrames(3);
        capture("11-fidelity-vanilla", "vanilla");
        act("mod enabled", () -> cfg().enabled = true);
        until("settled after enabling", this::settled, 200);
        waitFrames(2);
        capture("12-fidelity-idle-path", "idle");
        act("force per-char path", () -> {
            TypingRenderer.debugForcePerChar = true;
            TypingRenderer.debugNoMerge = true;
        });
        waitFrames(3);
        capture("13-fidelity-per-char-path", "perchar");
        act("force per-char path, merged runs", () -> TypingRenderer.debugNoMerge = false);
        waitFrames(3);
        capture("14-fidelity-merged-path", "merged");
        act("normal path", () -> {
            TypingRenderer.debugForcePerChar = false;
            TypingRenderer.debugNoMerge = false;
        });
        until("captures done", () -> pendingCaptures.get() == 0, 200);
        act("compare fidelity", this::compareFidelity);
        // translucent decorated text: overlapping quads blend twice, so geometry or draw-order differences show
        act("translucent decorated text, mod disabled (vanilla)", () -> {
            screen.deco.setTextColor(SelfTestScreen.TRANSLUCENT_TEXT);
            screen.decoFlat.setTextColor(SelfTestScreen.TRANSLUCENT_TEXT);
            cfg().enabled = false;
        });
        waitFrames(3);
        capture("15-fidelity-translucent-vanilla", "vanilla-t");
        act("mod enabled (translucent)", () -> cfg().enabled = true);
        until("settled after enabling (translucent)", this::settled, 200);
        act("force per-char path, merged runs (translucent)", () -> TypingRenderer.debugForcePerChar = true);
        waitFrames(3);
        capture("16-fidelity-translucent-merged-path", "merged-t");
        act("force per-char path, per glyph (translucent)", () -> TypingRenderer.debugNoMerge = true);
        waitFrames(3);
        capture("17-fidelity-translucent-per-char-path", "perchar-t");
        act("normal path, opaque decorated text", () -> {
            TypingRenderer.debugForcePerChar = false;
            TypingRenderer.debugNoMerge = false;
            screen.deco.setTextColor(SelfTestScreen.DEFAULT_TEXT);
            screen.decoFlat.setTextColor(SelfTestScreen.DEFAULT_TEXT);
        });
        until("translucent captures done", () -> pendingCaptures.get() == 0, 200);
        act("compare translucent fidelity", this::compareTranslucentFidelity);

        // --- Options screen -> our button -> ConfigScreen
        act("open options", () -> {
            options = new OptionsScreen(startScreen, mc.options);
            mc.gui.setScreen(options);
        });
        waitFrames(3);
        screenshot("20-options-screen");
        act("options layout", () -> {
            AbstractWidget button = findOptionsButton(options);
            checkLayout(options, "OptionsScreen", false);
            check(fits(button), "options button label cut off");
            int w = options.width;
            int h = options.height;
            options.resize(320, 240);
            try {
                checkLayout(options, "OptionsScreen@320x240", false);
            } finally {
                options.resize(w, h);
            }
        });
        act("press options button", () -> {
            click(options, findOptionsButton(options));
            check(mc.gui.screen() instanceof ConfigScreen, "options button did not open ConfigScreen but "
                    + mc.gui.screen());
            config = (ConfigScreen) mc.gui.screen();
        });
        until("config screen rendered", () -> config.frames() >= 3, 200);
        act("preview hint and focus", () -> {
            EditBox preview = config.preview();
            check(config.getFocused() == preview && preview.isFocused(), "the preview is not focused on open");
            String hint = Component.translatable("typinganimation.config.preview.hint").getString();
            check(preview.getValue().isEmpty() && hint.equals(suggestion(preview)),
                    "the preview hint is not shown on open (suggestion '" + suggestion(preview) + "')");
        });
        screenshot("21-config-screen");
        act("config layout", () -> {
            checkLayout(config, "ConfigScreen", true);
            check(config.options().size() == (TypingRenderer.MULTILINE_SUPPORTED ? 14 : 13),
                    "unexpected option count " + config.options().size());
            int w = config.width;
            int h = config.height;
            config.resize(320, 240);
            try {
                checkLayout(config, "ConfigScreen@320x240", true);
            } finally {
                config.resize(w, h);
            }
        });
        act("russian label widths", this::checkRussianLabels);
        act("cycle appear style", () -> {
            AppearStyle before = cfg().appearStyle;
            click(config, config.option("appearStyle"));
            check(cfg().appearStyle != before, "appear style did not change");
            check(config.getFocused() == config.preview(), "clicking a button took the focus from the preview");
        });
        act("drag duration slider", () -> {
            int before = cfg().durationMs;
            AbstractWidget slider = config.option("durationMs");
            click(config, slider.getX() + slider.getWidth() * 0.75, slider.getY() + slider.getHeight() / 2.0);
            check(cfg().durationMs != before, "duration slider did not change the config");
            check(cfg().durationMs % 10 == 0, "duration slider value not on its 10 ms step: " + cfg().durationMs);
            check(config.getFocused() == config.preview(), "releasing the slider did not give the focus back to the preview");
        });
        act("slider arrow keys", () -> {
            AbstractWidget slider = config.option("durationMs");
            config.allowKeyboardAdjust("durationMs");
            int before = cfg().durationMs;
            slider.keyPressed(new KeyEvent(InputConstants.KEY_RIGHT, InputConstants.KEYCODE_RIGHT, 0));
            check(cfg().durationMs == Math.min(before + 10, TypingConfig.DURATION_MAX_MS),
                    "right arrow moved the duration slider from " + before + " to " + cfg().durationMs);
            slider.keyPressed(new KeyEvent(InputConstants.KEY_LEFT, InputConstants.KEYCODE_LEFT, 0));
            slider.keyPressed(new KeyEvent(InputConstants.KEY_LEFT, InputConstants.KEYCODE_LEFT, 0));
            check(cfg().durationMs == Math.max(before - 10, TypingConfig.DURATION_MIN_MS),
                    "left arrow moved the duration slider to " + cfg().durationMs + " (from " + before + ")");
            slider.keyPressed(new KeyEvent(InputConstants.KEY_RIGHT, InputConstants.KEYCODE_RIGHT, 0));
            check(cfg().durationMs == before, "duration slider did not return to " + before);
            config.setFocused(null);
        });
        act("toggle enabled off", () -> {
            click(config, config.option("enabled"));
            check(!cfg().enabled, "enabled toggle did not switch off");
            check(!config.option("easing").active, "style options stay active while disabled");
        });
        act("toggle enabled on", () -> {
            click(config, config.option("enabled"));
            check(cfg().enabled, "enabled toggle did not switch on");
        });
        act("type into preview", () -> {
            click(config, config.preview());
            config.charTyped(new CharacterEvent('H'));
            config.charTyped(new CharacterEvent('i'));
            check("Hi".equals(config.preview().getValue()), "preview text is '" + config.preview().getValue() + "'");
            check(suggestion(config.preview()) == null, "the preview hint is still shown after typing");
        });
        act("start demo", () -> {
            click(config, config.demoButton());
            check(config.demoRunning(), "demo did not start");
        });
        waitTicks(50);
        screenshot("22-config-demo");
        act("stop demo", () -> {
            EditBox preview = config.preview();
            check(!"Hi".equals(preview.getValue()), "demo did not type anything");
            check(mc.font.width(preview.getValue() + "_") <= preview.getInnerWidth(),
                    "demo text does not fit the preview: '" + preview.getValue() + "'");
            click(config, config.demoButton());
            check(!config.demoRunning(), "demo did not stop");
            Map<String, String> ru = new HashMap<>();
            BuiltinTranslations.begin(List.of("en_us", "ru_ru"));
            BuiltinTranslations.addMissing(ru);
            String ruPhrase = config.fittingPhrase(ru.get("typinganimation.config.demo.text"), preview);
            check(mc.font.width(ruPhrase + "_") <= preview.getInnerWidth() && ruPhrase.length() > 10,
                    "Russian demo phrase does not fit: '" + ruPhrase + "'");
            LOG.info(TAG + "selftest: demo phrase (ru) '{}' {} px of {}", ruPhrase, mc.font.width(ruPhrase),
                    preview.getInnerWidth());
        });
        act("restart demo", () -> {
            click(config, config.demoButton());
            check(config.demoRunning(), "demo did not start again");
        });
        waitTicks(12);
        act("type during demo", () -> {
            config.setFocused(config.preview());
            check(config.charTyped(new CharacterEvent('Z')), "preview did not take a typed char");
        });
        waitFrames(2);
        act("demo stopped by typing", () -> {
            check(!config.demoRunning(), "demo kept running after the user typed");
            String v = config.preview().getValue();
            check(v.indexOf('Z') >= 0, "demo erased the user's text: '" + v + "'");
            check(Component.translatable("typinganimation.config.demo").getString()
                    .equals(config.demoButton().getMessage().getString()), "demo button label not reset");
        });
        act("done", () -> {
            click(config, config.doneButton());
            check(mc.gui.screen() == options, "Done did not return to the Options screen but " + mc.gui.screen());
            String json = Files.readString(ConfigManager.path(), StandardCharsets.UTF_8);
            check(json.contains("\"appearStyle\": \"slide_down\""), "config not saved on Done: " + json);
        });
        waitFrames(2);
        // --- the optionsButton setting applies when the config screen returns to the Options screen
        act("options button off", () -> {
            click(options, findOptionsButton(options));
            config = (ConfigScreen) mc.gui.screen();
            click(config, config.option("optionsButton"));
            check(!cfg().optionsButton, "optionsButton toggle did not switch off");
            click(config, config.doneButton());
            check(mc.gui.screen() == options, "Done did not return to the Options screen");
            AbstractWidget b = optionsButtonOrNull(options);
            check(b == null || (!b.visible && !b.active), "options button still shown after switching it off");
        });
        waitFrames(2);
        act("options button on again", () -> {
            mc.gui.setScreen(new ConfigScreen(options));
            config = (ConfigScreen) mc.gui.screen();
            click(config, config.option("optionsButton"));
            check(cfg().optionsButton, "optionsButton toggle did not switch on");
            click(config, config.doneButton());
            AbstractWidget b = findOptionsButton(options);
            check(b.visible && b.active, "options button not shown again after switching it on");
            checkLayout(options, "OptionsScreen (button on again)", false);
        });
        act("options opened with the button off", () -> {
            cfg().optionsButton = false;
            OptionsScreen second = new OptionsScreen(startScreen, mc.options);
            mc.gui.setScreen(second);
            check(optionsButtonOrNull(second) == null, "options button added although switched off");
            mc.gui.setScreen(new ConfigScreen(second));
            config = (ConfigScreen) mc.gui.screen();
            click(config, config.option("optionsButton"));
            click(config, config.doneButton());
            check(mc.gui.screen() == second, "Done did not return to the second Options screen");
            AbstractWidget b = findOptionsButton(second);
            check(b.visible && b.active, "options button not added after switching it on");
            checkLayout(second, "OptionsScreen (button added later)", false);
            mc.gui.setScreen(options);
        });
        waitFrames(2);
        act("reopen and reset", () -> {
            click(options, findOptionsButton(options));
            check(mc.gui.screen() instanceof ConfigScreen, "options button did not reopen ConfigScreen");
            config = (ConfigScreen) mc.gui.screen();
            click(config, config.resetButton());
            check(cfg().equals(TypingConfig.defaults()), "Reset did not restore defaults: " + cfg());
            click(config, config.doneButton());
            String json = Files.readString(ConfigManager.path(), StandardCharsets.UTF_8);
            check(json.contains("\"appearStyle\": \"slide_up\""), "reset config not saved: " + json);
        });
        act("back to start screen", () -> mc.gui.setScreen(startScreen));
        waitFrames(2);

        // --- final checks
        act("final checks", () -> {
            check(TypingRenderer.firstError == null, "render path error: " + TypingRenderer.firstError);
            check(TypingRenderer.statGlyphs > 0, "no animated glyph was drawn");
            check(TypingRenderer.statRuns > 0, "no settled run was drawn");
            check(TypingRenderer.statGhosts > 0, "no ghost was drawn");
            check(TypingRenderer.statCaretOffsets > 0, "the caret never glided");
            check(TypingRenderer.statIdleSegments > 0, "the idle path was never used");
            check(TypingRenderer.statScrollGlideFrames > 0, "no scroll glide frame");
            check(TypingRenderer.statRevealed > 0, "no char was revealed by a scroll glide");
            check(TypingRenderer.statFallbacks == 0, "formatter mismatch fallbacks: " + TypingRenderer.statFallbacks);
            for (Object w : new Object[]{screen.plain, screen.chat, screen.multi, screen.deco, screen.decoFlat}) {
                WidgetAnimation anim = ((TypingStateHolder) w).typinganimation$animation();
                check(anim.activeFrames() > 0 && !anim.hasFailed(), "widget " + w + " was not animated");
            }
            LOG.info(TAG + "selftest stats: glyphs={} runs={} ghosts={} caretOffsets={} idleSegments={} fallbacks={}"
                            + " scrollGlideFrames={} revealed={} shiftedHighlights={} shiftedSuggestions={}",
                    TypingRenderer.statGlyphs, TypingRenderer.statRuns, TypingRenderer.statGhosts,
                    TypingRenderer.statCaretOffsets, TypingRenderer.statIdleSegments, TypingRenderer.statFallbacks,
                    TypingRenderer.statScrollGlideFrames, TypingRenderer.statRevealed,
                    TypingRenderer.statShiftedHighlights, TypingRenderer.statShiftedSuggestions);
        });
        until("screenshots written", () -> pendingCaptures.get() == 0, 200);
    }

    // =================================================================== step helpers

    @FunctionalInterface
    private interface Body {
        boolean run(int tick) throws Exception;
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private record Step(String name, Body body) {
    }

    private void act(String name, Action action) {
        steps.add(new Step(name, t -> {
            action.run();
            return true;
        }));
    }

    private void waitTicks(int n) {
        steps.add(new Step("wait " + n + " ticks", t -> t >= n - 1));
    }

    private void waitFrames(int n) {
        steps.add(new Step("wait " + n + " frames", t -> {
            if (t > 200) {
                throw new AssertionError("no frames rendered");
            }
            return frameCount - stepMark >= n;
        }));
    }

    private void until(String name, BooleanSupplier condition, int timeoutTicks) {
        steps.add(new Step(name, t -> {
            if (condition.getAsBoolean()) {
                return true;
            }
            if (t >= timeoutTicks) {
                throw new AssertionError(name + ": timed out after " + timeoutTicks + " ticks");
            }
            return false;
        }));
    }

    private void screenshot(String name) {
        act("screenshot " + name, () -> captureNow(name, null));
    }

    private void capture(String name, String key) {
        act("capture " + name, () -> captureNow(name, key));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String lower(Enum<?> e) {
        return e.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** True once all test fields report idle frames for 5 consecutive ticks. */
    private boolean settled() {
        boolean idle = true;
        for (Object w : new Object[]{screen.plain, screen.chat, screen.multi, screen.deco, screen.decoFlat}) {
            WidgetAnimation anim = ((TypingStateHolder) w).typinganimation$animation();
            idle &= anim.lastFrameIdle();
        }
        settledTicks = idle ? settledTicks + 1 : 0;
        if (settledTicks >= 5) {
            settledTicks = 0;
            return true;
        }
        return false;
    }

    /** Caret index of the multi-line test field (dev diagnostics only: reflection on the private text field). */
    private int multiCursor() {
        try {
            java.lang.reflect.Field f = MultiLineEditBox.class.getDeclaredField("textField");
            f.setAccessible(true);
            return ((MultilineTextField) f.get(screen.multi)).cursor();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new AssertionError("can not read the multi-line caret", e);
        }
    }

    private void logMultiCursor(String when) {
        LOG.info(TAG + "selftest: multi-line caret {} of {} ({})", multiCursor(), screen.multi.getValue().length(), when);
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

    // =================================================================== input helpers

    private static void type(GuiEventListener widget, char c) {
        check(widget.charTyped(new CharacterEvent(c)), "charTyped rejected by " + widget);
    }

    private static void key(GuiEventListener widget, int key, int keycode) {
        widget.keyPressed(new KeyEvent(key, keycode, 0));
    }

    private static void click(Screen s, AbstractWidget w) {
        check(w != null, "widget to click is missing");
        click(s, w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0);
    }

    private static void click(Screen s, double x, double y) {
        MouseButtonEvent e = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        s.mouseClicked(e, false);
        s.mouseReleased(e);
    }

    // =================================================================== checks

    private void forceLoadTargets() throws Exception {
        ClassLoader loader = Minecraft.class.getClassLoader();
        String[] targets = {
                "net.minecraft.client.gui.components.EditBox",
                "net.minecraft.client.gui.components.MultiLineEditBox",
                "net.minecraft.client.gui.components.MultilineTextField",
                "net.minecraft.client.gui.components.MultilineTextField$StringView",
                "net.minecraft.client.gui.screens.ChatScreen",
                "net.minecraft.client.gui.screens.options.OptionsScreen",
                "net.minecraft.client.StringSplitter",
                "net.minecraft.client.resources.language.ClientLanguage",
                "net.minecraft.client.Minecraft",
        };
        for (String name : targets) {
            Class.forName(name, true, loader);
        }
        // translations: loaded by the loader's resource pack or by the built-in fallback (ClientLanguageMixin)
        net.minecraft.locale.Language language = net.minecraft.locale.Language.getInstance();
        for (String key : new String[]{OptionsButton.KEY, "typinganimation.title", "typinganimation.config.demo.text",
                "typinganimation.appear.slide_up", "typinganimation.config.ms"}) {
            check(language.has(key), "translation missing for " + key);
        }
        Map<String, String> en = new HashMap<>();
        BuiltinTranslations.begin(List.of("en_us"));
        BuiltinTranslations.addMissing(en);
        Map<String, String> ru = new HashMap<>();
        BuiltinTranslations.begin(List.of("en_us", "ru_ru"));
        BuiltinTranslations.addMissing(ru);
        check(en.size() > 50 && en.keySet().equals(ru.keySet()), "built-in lang files incomplete: en " + en.size()
                + " keys, ru " + ru.size() + " keys");
        check(!en.get("typinganimation.title").equals(ru.get("typinganimation.title")),
                "built-in Russian translation not loaded");
        check(TypingStateHolder.class.isAssignableFrom(EditBox.class), "EditBox mixin not applied");
        check(EditBoxAccess.class.isAssignableFrom(EditBox.class), "EditBox formatter access not applied");
        check(TypingStateHolder.class.isAssignableFrom(MultiLineEditBox.class), "MultiLineEditBox mixin not applied");
        check(StringSplitterAccessor.class.isAssignableFrom(StringSplitter.class), "StringSplitter accessor not applied");
        check(MultilineTextFieldAccessor.class.isAssignableFrom(MultilineTextField.class),
                "MultilineTextField accessor not applied");
        check(StringViewAccessor.class.isAssignableFrom(
                Class.forName("net.minecraft.client.gui.components.MultilineTextField$StringView", true, loader)),
                "StringView accessor not applied");
        LOG.info(TAG + "selftest: all {} mixin target classes loaded", targets.length);
    }

    private static AbstractWidget findOptionsButton(Screen s) {
        AbstractWidget w = optionsButtonOrNull(s);
        if (w == null) {
            throw new AssertionError("Typing Animation button missing from " + s.getClass().getSimpleName());
        }
        return w;
    }

    private static AbstractWidget optionsButtonOrNull(Screen s) {
        for (GuiEventListener child : s.children()) {
            if (OptionsButton.isOptionsButton(child)) {
                return (AbstractWidget) child;
            }
        }
        return null;
    }

    /** True when the plain test field is too narrow for its value (it shows it from a scrolled position). */
    private boolean plainScrolled() {
        return fieldScrolled(screen.plain);
    }

    private boolean fieldScrolled(EditBox box) {
        return mc.font.width(box.getValue()) > box.getInnerWidth(); // vanilla scrolls by plain widths
    }

    /**
     * Checks the chars drawn in the first frame after a scroll of the plain field: the scroll glides (the text is
     * displaced in direction {@code sign}), neighbouring untransformed chars neither overlap nor leave a gap
     * (tolerance 1 px: vanilla starts the part after the caret at an integer x), and the text reaches the edge of
     * the text area on the side the displacement uncovers.
     */
    private static void checkScrollTrace(TypingRenderer.Trace t, String what, int sign) {
        check(t.done && t.count > 0, what + ": no char drawn in the traced frame (count " + t.count + ")");
        check(t.shift * sign >= 0.5f, what + ": the scroll did not glide (offset " + t.shift + ")");
        Integer[] order = new Integer[t.count];
        for (int i = 0; i < t.count; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, java.util.Comparator.comparingInt(i -> t.index[i]));
        int pairs = 0;
        for (int k = 0; k + 1 < order.length; k++) {
            int a = order[k];
            int b = order[k + 1];
            check(t.index[b] != t.index[a], what + ": char " + t.index[a] + " drawn twice");
            if (t.index[b] - t.index[a] > 2 || t.transformed[a] || t.transformed[b]) {
                continue;
            }
            float gap = t.x[b] - (t.x[a] + t.width[a]);
            check(gap >= -1f && gap <= 1f, what + ": chars " + t.index[a] + " and " + t.index[b]
                    + (gap < 0 ? " overlap by " + -gap : " have a gap of " + gap) + " px");
            pairs++;
        }
        int first = order[0];
        int last = order[order.length - 1];
        if (sign > 0 && t.index[first] > 0) {
            check(t.x[first] <= t.textClipX0 + 0.5f, what + ": gap at the left edge (first char " + t.index[first]
                    + " at " + t.x[first] + ", text area from " + t.textClipX0 + ")");
        }
        if (sign < 0 && t.index[last] < t.valueLength - 1) {
            check(t.x[last] + t.width[last] >= t.textClipX1 - 0.5f, what + ": gap at the right edge (last char "
                    + t.index[last] + " ends at " + (t.x[last] + t.width[last]) + ", text area to " + t.textClipX1 + ")");
        }
        check(pairs >= 10, what + ": only " + pairs + " neighbouring chars checked");
        LOG.info(TAG + "selftest: {} scroll glide ok (offset {} px, {} chars drawn, chars {}..{})", what, t.shift,
                t.count, t.index[first], t.index[last]);
    }

    private boolean fits(AbstractWidget w) {
        return mc.font.width(w.getMessage()) <= w.getWidth() - 8;
    }

    /** Every visible widget inside the screen, no two overlapping, and (optionally) every button label fits. */
    private void checkLayout(Screen s, String what, boolean labels) {
        List<AbstractWidget> widgets = new ArrayList<>();
        for (GuiEventListener child : s.children()) {
            if (child instanceof AbstractWidget w && w.visible) {
                widgets.add(w);
            }
        }
        check(!widgets.isEmpty(), what + ": no widgets");
        for (AbstractWidget w : widgets) {
            check(w.getX() >= 0 && w.getY() >= 0 && w.getX() + w.getWidth() <= s.width
                            && w.getY() + w.getHeight() <= s.height,
                    what + ": " + describe(w) + " outside the " + s.width + "x" + s.height + " screen");
            if (labels && (w instanceof Button || w instanceof CycleButton<?> || w instanceof AbstractSliderButton)) {
                check(fits(w), what + ": label of " + describe(w) + " is cut off");
            }
        }
        for (int i = 0; i < widgets.size(); i++) {
            for (int j = i + 1; j < widgets.size(); j++) {
                AbstractWidget a = widgets.get(i);
                AbstractWidget b = widgets.get(j);
                boolean overlap = a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth()
                        && a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight();
                check(!overlap, what + ": " + describe(a) + " overlaps " + describe(b));
            }
        }
    }

    /**
     * The test runs in English; this measures the widest Russian label of every option with the real font and
     * logs the ones that would not fit (a too long CycleButton label scrolls, so this is a warning only).
     */
    private void checkRussianLabels() {
        Map<String, String> ru = new HashMap<>();
        BuiltinTranslations.begin(List.of("en_us", "ru_ru"));
        BuiltinTranslations.addMissing(ru);
        String on = "Вкл";
        String off = "Выкл";
        Map<String, String[]> values = new java.util.LinkedHashMap<>();
        values.put("appearStyle", keys(AppearStyle.values(), "typinganimation.appear."));
        values.put("removeStyle", keys(RemoveStyle.values(), "typinganimation.remove."));
        values.put("easing", keys(dev.typinganimation.core.Easing.values(), "typinganimation.easing."));
        int widest = 0;
        int tooLong = 0;
        for (String key : config.options().keySet()) {
            String caption = ru.get("typinganimation.config." + key);
            List<String> candidates = new ArrayList<>();
            if (values.containsKey(key)) {
                for (String k : values.get(key)) {
                    candidates.add(ru.get(k));
                }
            } else if (key.endsWith("Ms")) {
                candidates.add(ru.get(key.equals("staggerMs") ? "typinganimation.config.ms_per_char"
                        : "typinganimation.config.ms").replace("%s", "1000"));
            } else if (key.equals("intensity")) {
                candidates.add(ru.get("typinganimation.config.multiplier").replace("%s", "2.75"));
            } else {
                candidates.add(on);
                candidates.add(off);
            }
            for (String v : candidates) {
                String label = caption + ": " + v;
                int w = mc.font.width(label);
                widest = Math.max(widest, w);
                if (w > 150 - 8) {
                    tooLong++;
                    LOG.warn(TAG + "selftest: Russian label '{}' is {} px wide (button text area 142 px)", label, w);
                }
            }
        }
        LOG.info(TAG + "selftest: widest Russian option label {} px, {} too long", widest, tooLong);
    }

    private static String[] keys(Enum<?>[] values, String prefix) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = prefix + values[i].name().toLowerCase(java.util.Locale.ROOT);
        }
        return out;
    }

    private static String describe(AbstractWidget w) {
        return "'" + w.getMessage().getString() + "' [" + w.getX() + "," + w.getY() + " " + w.getWidth() + "x"
                + w.getHeight() + "]";
    }

    // =================================================================== screenshots and pixel comparison

    /** Pixels (ARGB) of one captured frame. */
    private record Grab(int width, int height, int scale, int[] argb) {
        int at(int x, int y) {
            return argb[y * width + x];
        }
    }

    private void captureNow(String name, String key) {
        if (key == null && !saveScreenshots) {
            return;
        }
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        int scale = mc.getWindow().getGuiScale();
        pendingCaptures.incrementAndGet();
        Screenshot.takeScreenshot(target, image -> {
            try {
                if (key != null) {
                    int w = image.getWidth();
                    int h = image.getHeight();
                    int[] px = new int[w * h];
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            px[y * w + x] = image.getPixel(x, y);
                        }
                    }
                    synchronized (grabs) {
                        grabs.put(key, new Grab(w, h, scale, px));
                    }
                }
                if (saveScreenshots) {
                    java.nio.file.Path dir = mc.gameDirectory.toPath().toAbsolutePath().normalize().resolve("screenshots");
                    Files.createDirectories(dir);
                    java.nio.file.Path file = dir.resolve("typinganimation-selftest-" + name + ".png");
                    image.writeToFile(file);
                    LOG.info(TAG + "SELFTEST SCREENSHOT " + file);
                }
            } catch (Throwable t) {
                asyncError = t;
            } finally {
                image.close();
                pendingCaptures.decrementAndGet();
            }
        });
    }

    private void compareFidelity() {
        Grab vanilla;
        Grab idle;
        Grab perChar;
        Grab merged;
        synchronized (grabs) {
            vanilla = grabs.get("vanilla");
            idle = grabs.get("idle");
            perChar = grabs.get("perchar");
            merged = grabs.get("merged");
        }
        check(vanilla != null && idle != null && perChar != null && merged != null, "fidelity captures missing");
        EditBox plain = screen.plain;
        EditBox chat = screen.chat;
        MultiLineEditBox multi = screen.multi;
        // text regions: up to the end of the text (the append caret "_" starts one pixel after it)
        int plainEnd = plain.getX() + 4 + mc.font.width(plain.getValue());
        int chatEnd = chat.getX() + mc.font.width(SelfTestScreen.formatChatValue(chat.getValue()));
        int[][] regions = {
                {plain.getX(), plain.getY(), plainEnd, plain.getY() + plain.getHeight(), PLAIN_TEXT_COLOR},
                {chat.getX(), chat.getY(), chatEnd, chat.getY() + chat.getHeight(), WHITE},
                {multi.getX(), multi.getY(), multi.getX() + multi.getWidth(), multi.getY() + multi.getHeight(),
                        PLAIN_TEXT_COLOR},
        };
        String[] names = {"plain field", "chat field (coloured, bold)", "multi-line field"};
        for (int i = 0; i < regions.length; i++) {
            int[] r = regions[i];
            int textPixels = count(vanilla, r, r[4]);
            check(textPixels >= 10, names[i] + ": no text found in the compared region (" + textPixels + " px)");
            compare(names[i] + " idle path vs vanilla", vanilla, idle, r);
            compare(names[i] + " per-char path vs vanilla", vanilla, perChar, r);
            compare(names[i] + " per-char path (merged runs) vs vanilla", vanilla, merged, r);
            LOG.info(TAG + "selftest fidelity: {} identical ({} text pixels)", names[i], textPixels);
        }
        // decorated fields: every path must match vanilla, except one known painter's-order difference of chars drawn
        // by their own submit (only animating chars are, at rest runs are merged): vanilla draws all underlines and
        // strikethroughs of a text after all its glyphs, so a strikethrough's shadow overhangs the next glyph's first
        // column; a char drawn on its own draws the next glyph over it. Those rows are left out for the shadowed field.
        int[] deco = decoRegion(screen.deco);
        int[] flat = decoRegion(screen.decoFlat);
        int textPixels = count(vanilla, deco, SelfTestScreen.DECO_YELLOW);
        check(textPixels >= 10 && count(vanilla, flat, SelfTestScreen.DECO_YELLOW) >= 10,
                "decorated fields: no text found in the compared region");
        compare("decorated field idle path vs vanilla", vanilla, idle, deco);
        compare("decorated field per-char path (merged runs) vs vanilla", vanilla, merged, deco);
        int band = compareOutsideStrikeShadow("decorated field per-char path (per glyph) vs vanilla", vanilla, perChar,
                deco, screen.deco);
        compare("decorated field without shadow idle path vs vanilla", vanilla, idle, flat);
        compare("decorated field without shadow per-char path (merged runs) vs vanilla", vanilla, merged, flat);
        compare("decorated field without shadow per-char path (per glyph) vs vanilla", vanilla, perChar, flat);
        LOG.info(TAG + "selftest fidelity: decorated fields identical ({} text pixels; per glyph with shadow: {} px "
                + "differ in the strikethrough-shadow rows, not compared)", textPixels, band);
    }

    /** Translucent decorated text: vanilla vs the forced per-char path (merged runs and per glyph). */
    private void compareTranslucentFidelity() {
        Grab vanilla;
        Grab vanillaT;
        Grab mergedT;
        Grab perCharT;
        synchronized (grabs) {
            vanilla = grabs.get("vanilla");
            vanillaT = grabs.get("vanilla-t");
            mergedT = grabs.get("merged-t");
            perCharT = grabs.get("perchar-t");
        }
        check(vanilla != null && vanillaT != null && mergedT != null && perCharT != null,
                "translucent fidelity captures missing");
        int[] deco = decoRegion(screen.deco);
        int[] flat = decoRegion(screen.decoFlat);
        int changed = differing(vanilla, vanillaT, deco);
        check(changed >= 10 && differing(vanilla, vanillaT, flat) >= 10,
                "decorated fields: the translucent text colour made no difference");
        compare("translucent decorated field per-char path (merged runs) vs vanilla", vanillaT, mergedT, deco);
        int band = compareOutsideStrikeShadow("translucent decorated field per-char path (per glyph) vs vanilla",
                vanillaT, perCharT, deco, screen.deco);
        compare("translucent decorated field without shadow per-char path (merged runs) vs vanilla", vanillaT, mergedT,
                flat);
        compare("translucent decorated field without shadow per-char path (per glyph) vs vanilla", vanillaT, perCharT,
                flat);
        LOG.info(TAG + "selftest fidelity: translucent decorated fields identical ({} pixels differ from opaque; per "
                + "glyph with shadow: {} px differ in the strikethrough-shadow rows, not compared)", changed, band);
    }

    /** GUI rectangle of a decorated (borderless) field: its bar, including underline and shadow rows. */
    private static int[] decoRegion(EditBox box) {
        return new int[]{box.getX() - 2, box.getY() - 2, box.getX() + box.getWidth() + 2, box.getY() + box.getHeight()};
    }

    /** Number of pixels in {@code r} that differ between two captures. */
    private static int differing(Grab a, Grab b, int[] r) {
        int s = a.scale;
        int n = 0;
        for (int y = Math.max(0, r[1] * s); y < Math.min(a.height, r[3] * s); y++) {
            for (int x = Math.max(0, r[0] * s); x < Math.min(a.width, r[2] * s); x++) {
                if (a.at(x, y) != b.at(x, y)) {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * Like {@link #compare}, but the rows of the strikethrough shadow of a borderless field's text line (GUI y
     * {@code textY + 4.5} to {@code textY + 5.5}, see compareFidelity) are left out; returns the number of pixels that
     * differ inside those rows.
     */
    private static int compareOutsideStrikeShadow(String what, Grab a, Grab b, int[] r, EditBox box) {
        check(a.width == b.width && a.height == b.height && a.scale == b.scale, what + ": capture sizes differ");
        int s = a.scale;
        int band0 = (int) Math.floor((box.getY() + 4.5) * s);
        int band1 = (int) Math.ceil((box.getY() + 5.5) * s);
        int diff = 0;
        int inBand = 0;
        int firstX = -1;
        int firstY = -1;
        for (int y = Math.max(0, r[1] * s); y < Math.min(a.height, r[3] * s); y++) {
            for (int x = Math.max(0, r[0] * s); x < Math.min(a.width, r[2] * s); x++) {
                if (a.at(x, y) != b.at(x, y)) {
                    if (y >= band0 && y < band1) {
                        inBand++;
                        continue;
                    }
                    if (diff == 0) {
                        firstX = x;
                        firstY = y;
                    }
                    diff++;
                }
            }
        }
        check(diff == 0, what + ": " + diff + " pixels differ (first at " + firstX + "," + firstY + ": "
                + (diff > 0 ? Integer.toHexString(a.at(firstX, firstY)) + " vs " + Integer.toHexString(b.at(firstX, firstY)) : "")
                + ")");
        return inBand;
    }

    private static int count(Grab g, int[] r, int color) {
        int n = 0;
        int s = g.scale;
        for (int y = Math.max(0, r[1] * s); y < Math.min(g.height, r[3] * s); y++) {
            for (int x = Math.max(0, r[0] * s); x < Math.min(g.width, r[2] * s); x++) {
                if (g.at(x, y) == color) {
                    n++;
                }
            }
        }
        return n;
    }

    private static void compare(String what, Grab a, Grab b, int[] r) {
        check(a.width == b.width && a.height == b.height && a.scale == b.scale, what + ": capture sizes differ");
        int s = a.scale;
        int diff = 0;
        int firstX = -1;
        int firstY = -1;
        for (int y = Math.max(0, r[1] * s); y < Math.min(a.height, r[3] * s); y++) {
            for (int x = Math.max(0, r[0] * s); x < Math.min(a.width, r[2] * s); x++) {
                if (a.at(x, y) != b.at(x, y)) {
                    if (diff == 0) {
                        firstX = x;
                        firstY = y;
                    }
                    diff++;
                }
            }
        }
        check(diff == 0, what + ": " + diff + " pixels differ (first at " + firstX + "," + firstY + ": "
                + (diff > 0 ? Integer.toHexString(a.at(firstX, firstY)) + " vs " + Integer.toHexString(b.at(firstX, firstY)) : "")
                + ")");
    }
}
