package dev.typinganimation.mc;

import com.mojang.blaze3d.platform.InputConstants;
import dev.typinganimation.core.AppearStyle;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.FieldKind;
import dev.typinganimation.core.RemoveStyle;
import dev.typinganimation.core.TypingAnimationMod;
import dev.typinganimation.core.TypingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Dev-only showcase recorder for the gallery media (enabled by {@code TYPINGANIMATION_SHOWCASE=1}, nothing happens
 * otherwise). Driven by the client tick and frame hooks of {@code MinecraftMixin}: it (re)creates a superflat creative
 * world, builds a small scene, and then uses the real chat through the real input path - the chat key opens the
 * {@link ChatScreen}, text arrives through {@code KeyboardHandler#charTyped} and editing keys through
 * {@code KeyboardHandler#keyPress} with SDL key codes - while {@link ShowcaseRecorder} saves every frame of a clip
 * with a deterministic animation clock. Clips: chat typing (reflow, deletion, paste wave, sending), command typing
 * with highlighting and a completion, several appear styles; stills: the options button and the settings screen in
 * English and Russian. It asserts that the real chat input is {@link FieldKind#CHAT}, that animated glyphs were drawn
 * in the chat screen and that the renderer never fell back, then logs {@code SHOWCASE DONE} or
 * {@code SHOWCASE FAIL: ...} and stops the client. Frames go to {@code TYPINGANIMATION_SHOWCASE_OUT} (default: the
 * system temp directory), see scripts/make-gallery.sh.
 */
public final class Showcase {
    private static final String TAG = "[typinganimation] ";
    private static final Logger LOG = TypingAnimationMod.LOGGER;
    private static final String LEVEL_ID = "typinganimation-showcase";
    private static final long WORLD_SEED = 20260930L;
    /** Window of the chat and command clips: the whole 400x240 GUI (chat, input, hotbar) fits an 800 px GIF. */
    private static final int SMALL_WIDTH = 800;
    private static final int SMALL_HEIGHT = 480;
    /** Window of the full-frame still, the styles close-up and the settings stills. */
    private static final int LARGE_WIDTH = 1280;
    private static final int LARGE_HEIGHT = 720;
    /** GUI scale in the world (400x240 and 640x360 GUI) and of the settings stills (426x240). */
    private static final int WORLD_GUI_SCALE = 2;
    private static final int UI_GUI_SCALE = 3;
    /** The styles GIF shows the input left of the hotbar: phrases up to this width (GUI px). */
    private static final int STYLES_MAX_WIDTH = 190;
    private static final String CASTLE = "Anyone up for a big castle tonight? I'll bring the torches!";
    private static final Object[][] STYLES = {
            {AppearStyle.POP, RemoveStyle.SHRINK, "Pop! Letters bounce in"},
            {AppearStyle.SCRAMBLE, RemoveStyle.FLY_UP, "Scramble decodes text"},
            {AppearStyle.WAVE, RemoveStyle.SCATTER, "Wave makes it ripple"},
            {AppearStyle.DROP, RemoveStyle.FALL, "Drop lands softly"},
    };
    private static final long START_TIMEOUT_MS = 300_000L;
    private static final long RUN_TIMEOUT_MS = 1_200_000L;
    private static final int KEYCODE_ESCAPE = 27;
    /** Pointer position (window px) outside the window, see {@link #parkMouse}. */
    private static final double PARKED_MOUSE = -100.0;

    private static Showcase instance;

    private final Minecraft mc;
    private final ShowcaseRecorder rec;
    /** Seeded: the typing rhythm is the same in every run. */
    private final Random rhythm = new Random(7L);
    private final List<Step> steps = new ArrayList<>();
    private final Map<String, Integer> clipFrames = new LinkedHashMap<>();
    private final Map<String, String> clipSizes = new LinkedHashMap<>();

    private boolean started;
    private boolean finished;
    private int readyTicks;
    private int otherScreenTicks;
    private long runStart;
    private int index;
    private boolean stepStarted;
    private long stepStart;
    private Screen titleScreen;
    private TypingConfig originalConfig;
    private int originalGuiScale;
    private boolean originalPauseOnLostFocus;
    private TutorialSteps originalTutorial;
    private String originalLanguage;
    private String clip;

    // assertions
    private int chatOpens;
    private long lastGlyphs;
    private long lastGhosts;
    private long chatGlyphs;
    private long chatGhosts;
    private long chatFrames;
    private TypingRenderer.Trace chatTrace;
    private int markLength;

    private Showcase(Minecraft mc) {
        this.mc = mc;
        String dir = System.getenv("TYPINGANIMATION_SHOWCASE_OUT");
        Path out = dir != null && !dir.isBlank() ? Path.of(dir)
                : Path.of(System.getProperty("java.io.tmpdir"), "typinganimation-showcase");
        this.rec = new ShowcaseRecorder(mc, out.toAbsolutePath().normalize());
    }

    /** Client tick (end of {@code Minecraft#tick}). */
    public static void onTick(Minecraft mc) {
        if (instance == null) {
            instance = new Showcase(mc);
        }
        instance.tick();
    }

    /** End of every rendered frame ({@code Minecraft#runTick}). */
    public static void onFrame(Minecraft mc) {
        if (instance != null) {
            instance.frame();
        }
    }

    // =================================================================== driver

    private void tick() {
        if (finished) {
            return;
        }
        try {
            if (!started) {
                if (++readyTicks * 50L > START_TIMEOUT_MS) {
                    throw new AssertionError("no title screen within " + START_TIMEOUT_MS / 1000 + " s");
                }
                if (!ready()) {
                    return;
                }
                start();
            }
            mc.gui.toastManager().clear(); // no tutorial/advancement/recipe toasts in the media
            parkMouse();
            if (!rec.capturing()) {
                advance();
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void frame() {
        if (!started || finished) {
            return;
        }
        try {
            long glyphs = TypingRenderer.statGlyphs;
            long ghosts = TypingRenderer.statGhosts;
            if (mc.gui.screen() instanceof ChatScreen) {
                chatGlyphs += glyphs - lastGlyphs;
                chatGhosts += ghosts - lastGhosts;
                chatFrames++;
            }
            lastGlyphs = glyphs;
            lastGhosts = ghosts;
            parkMouse();
            if (rec.capturing()) {
                rec.onFrame();
                advance();
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Runs the script: every step that is due now (in the recorder's clock), until one has to wait. */
    private void advance() throws Throwable {
        for (int guard = 0; guard < 64 && !finished; guard++) {
            if (rec.error() != null) {
                throw new AssertionError("frame capture failed", rec.error());
            }
            if (TypingRenderer.firstError != null) {
                throw new AssertionError("render path error", TypingRenderer.firstError);
            }
            if (index >= steps.size()) {
                done();
                return;
            }
            if (rec.now() - runStart > RUN_TIMEOUT_MS) {
                throw new AssertionError("timed out in step " + index + " '" + steps.get(index).name + "'");
            }
            Step step = steps.get(index);
            if (!stepStarted) {
                stepStarted = true;
                stepStart = rec.now();
            }
            boolean capturing = rec.capturing();
            boolean complete;
            try {
                complete = step.body.run(rec.now() - stepStart);
            } catch (Throwable t) {
                throw new AssertionError(t.getMessage() + " (step " + index + " '" + step.name + "')", t);
            }
            if (!complete) {
                return;
            }
            index++;
            stepStarted = false;
            if (rec.capturing() != capturing) {
                return; // the other hook drives the next steps (frames while recording, ticks otherwise)
            }
        }
    }

    /** The title (or first-launch onboarding) screen without an overlay; any other screen after 10 s. */
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
        runStart = rec.now();
        titleScreen = mc.gui.screen();
        LOG.info(TAG + "SHOWCASE START (output {})", rec.out());
        AnimationClock.setOverride(rec::now);
        buildScript();
    }

    private void done() throws IOException {
        finished = true;
        String summary = String.format(java.util.Locale.ROOT,
                "clips %s; chat opened %d times, input kind CHAT; %d animated glyphs and %d ghosts drawn in %d chat"
                        + " screen frames; renderer fallbacks %d; frames in %s",
                clipFrames, chatOpens, chatGlyphs, chatGhosts, chatFrames, TypingRenderer.statFallbacks, rec.out());
        AnimationClock.setOverride(null);
        rec.shutdown();
        LOG.info(TAG + "SHOWCASE DONE: " + summary);
        mc.stop();
    }

    private void fail(Throwable t) {
        if (finished) {
            return;
        }
        finished = true;
        LOG.error(TAG + "SHOWCASE FAIL: " + t.getMessage(), t);
        try {
            AnimationClock.setOverride(null);
            restoreSettings();
            if (mc.level != null) {
                mc.disconnectFromWorld(Component.literal("Showcase failed"));
            }
        } catch (Throwable ignored) {
            // best effort
        }
        rec.shutdown();
        mc.stop();
    }

    // =================================================================== script

    private void buildScript() {
        act("prepare", this::prepare);
        act("create world", this::createWorld);
        until("in the world", () -> mc.player != null && mc.level != null && mc.gui.screen() == null
                && mc.gui.overlay() == null, 180_000);
        act("build the scene", this::buildScene);
        waitMs(3000);
        until("chunks rendered", () -> mc.levelRenderer.hasRenderedAllSections(), 120_000);
        waitMs(3000);
        act("check the frame size", () -> checkFrameSize(SMALL_WIDTH, SMALL_HEIGHT, WORLD_GUI_SCALE));
        // hotbar slot 2: the item /give adds goes to slot 1, the (invisible, empty) hand stays out of the picture
        press("hotbar slot 2", InputConstants.KEY_2, '2', 0);
        act("clear the chat", () -> chat().clearMessages(true));
        waitMs(500);
        // one earlier message, so the chat history is not empty (typed at once, not recorded)
        openChat();
        act("type the first message", () -> "Good morning! Lovely weather for building.".codePoints()
                .forEach(this::typeChar));
        waitMs(300);
        press("send the first message", InputConstants.KEY_RETURN, InputConstants.KEYCODE_RETURN, 0);
        waitMs(1500);

        // --- clip "chat" (800x480): default Slide Up, a mid-line insertion, deletions (Fall), a paste, sending
        act("remove style Fall", () -> cfg().removeStyle = RemoveStyle.FALL);
        startClip("chat");
        waitMs(700);
        openChat();
        waitMs(500);
        type("Anyone up for a castle later?", 12);
        waitMs(700);
        pressRepeated("caret back to 'castle'", 13, InputConstants.KEY_LEFT, InputConstants.KEYCODE_LEFT, 0);
        waitMs(350);
        type("big ", -1);
        waitMs(600);
        press("end", InputConstants.KEY_END, InputConstants.KEYCODE_END, 0);
        waitMs(500);
        pressRepeated("delete 'later?'", 6, InputConstants.KEY_BACKSPACE, InputConstants.KEYCODE_BACKSPACE, 0);
        waitMs(500);
        paste("tonight? I'll bring the torches!");
        waitMs(1400);
        act("check the message", () -> check(CASTLE.equals(chatInput().getValue()),
                "unexpected chat input '" + chatInput().getValue() + "'"));
        press("send", InputConstants.KEY_RETURN, InputConstants.KEYCODE_RETURN, 0);
        until("chat closed", () -> mc.gui.screen() == null, 3000);
        act("check the sent message", () -> check(chat().getRecentChat().contains(CASTLE), "the message was not sent"));
        waitMs(1800);
        endClip();
        waitMs(800);

        // --- clip "command" (800x480): syntax highlighting, a Tab completion, running the command
        act("default settings", () -> cfg().copyFrom(TypingConfig.defaults()));
        startClip("command");
        waitMs(500);
        openChat();
        waitMs(450);
        type("/gi", -1);
        waitMs(450);
        act("complete with Tab", () -> {
            check(chatInput().getValue().equals("/gi"), "unexpected chat input '" + chatInput().getValue() + "'");
            pressKey(InputConstants.KEY_TAB, InputConstants.KEYCODE_TAB, 0);
        });
        until("completion applied", () -> {
            if (chatInput().getValue().equals("/give")) {
                return true;
            }
            pressKey(InputConstants.KEY_TAB, InputConstants.KEYCODE_TAB, 0); // the list was not shown yet
            return false;
        }, 3000);
        waitMs(500);
        type(" @p diamond_sword 1", -1);
        waitMs(1500);
        press("run the command", InputConstants.KEY_RETURN, InputConstants.KEYCODE_RETURN, 0);
        until("command ran (sword in the inventory)", this::hasSword, 5000);
        waitMs(2000);
        endClip();

        // --- 1280x720 from here: more of the world in the still, room left of the hotbar for the styles close-up
        act("window 1280x720", () -> mc.getWindow().setWindowed(LARGE_WIDTH, LARGE_HEIGHT));
        waitMs(1500);
        act("check the frame size", () -> checkFrameSize(LARGE_WIDTH, LARGE_HEIGHT, WORLD_GUI_SCALE));
        until("chunks rendered", () -> mc.levelRenderer.hasRenderedAllSections(), 60_000);
        waitMs(500);

        // --- clip "world" (1280x720): only for the full-frame still, mid paste wave
        startClip("world");
        waitMs(300);
        openChat();
        waitMs(300);
        type("What a view! ", -1);
        waitMs(250);
        paste("Cherry blossoms by the pond.");
        waitMs(Math.round(9 * ShowcaseRecorder.FRAME_MS));
        act("still", () -> rec.markStill("chat-in-world"));
        waitMs(1200);
        press("select all", InputConstants.KEY_A, 'a', InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER);
        press("delete all", InputConstants.KEY_BACKSPACE, InputConstants.KEYCODE_BACKSPACE, 0);
        waitMs(500);
        press("close the chat", InputConstants.KEY_ESCAPE, KEYCODE_ESCAPE, 0);
        until("chat closed", () -> mc.gui.screen() == null, 3000);
        endClip();
        waitMs(500);

        // --- clip "styles" (1280x720, the GIF is a close-up of the input): other appear and remove styles,
        //     switched in the config between the phrases (the chat lines, wider than the close-up, are hidden)
        act("hide the chat lines", () -> chat().clearMessages(false));
        startClip("styles");
        waitMs(400);
        openChat();
        waitMs(400);
        for (Object[] s : STYLES) {
            AppearStyle appear = (AppearStyle) s[0];
            RemoveStyle remove = (RemoveStyle) s[1];
            String phrase = (String) s[2];
            act("style " + appear, () -> {
                check(mc.font.width(phrase) <= STYLES_MAX_WIDTH, "phrase too wide for the styles close-up: "
                        + phrase + " (" + mc.font.width(phrase) + " px)");
                cfg().appearStyle = appear;
                cfg().removeStyle = remove;
            });
            type(phrase, -1);
            waitMs(650);
            press("select all", InputConstants.KEY_A, 'a', InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER);
            waitMs(150);
            press("delete all", InputConstants.KEY_BACKSPACE, InputConstants.KEYCODE_BACKSPACE, 0);
            waitMs(450);
        }
        press("close the chat", InputConstants.KEY_ESCAPE, KEYCODE_ESCAPE, 0);
        until("chat closed", () -> mc.gui.screen() == null, 3000);
        endClip();

        // --- stills: the Options button and the settings screen (English, Russian)
        act("default settings, UI scale", () -> {
            cfg().copyFrom(TypingConfig.defaults());
            setGuiScale(UI_GUI_SCALE);
        });
        settingsStills("en_us", "config-screen-en", true);
        act("Russian", () -> setLanguage("ru_ru"));
        settingsStills("ru_ru", "config-screen-ru", false);
        act("English", () -> setLanguage("en_us"));

        // --- checks, cleanup
        act("final checks", this::finalChecks);
        until("frames written", () -> rec.pending() == 0, 60_000);
        act("write the manifest", this::writeManifest);
        act("restore settings", this::restoreSettings);
        act("leave the world", () -> mc.disconnectFromWorld(Component.literal("Showcase done")));
        until("back on the title screen", () -> mc.level == null && mc.gui.screen() instanceof TitleScreen, 120_000);
    }

    /** Options screen (still "options-button" when {@code optionsStill}), our button, the settings demo mid-typing. */
    private void settingsStills(String language, String still, boolean optionsStill) {
        OptionsScreen[] options = new OptionsScreen[1];
        ConfigScreen[] config = new ConfigScreen[1];
        startClip("ui-" + language);
        act("open options", () -> {
            options[0] = new OptionsScreen(new PauseScreen(true), mc.options);
            mc.gui.setScreen(options[0]);
        });
        waitMs(700);
        if (optionsStill) {
            act("options still", () -> {
                check(findOptionsButton(options[0]) != null, "our button is missing from the Options screen");
                rec.markStill("options-button");
            });
            waitMs(200);
        }
        act("press our button", () -> {
            click(options[0], findOptionsButton(options[0]));
            check(mc.gui.screen() instanceof ConfigScreen, "our button did not open the settings screen");
            config[0] = (ConfigScreen) mc.gui.screen();
        });
        waitMs(600);
        act("start the demo", () -> {
            click(config[0], config[0].demoButton());
            check(config[0].demoRunning(), "the demo did not start");
        });
        until("demo typed most of its phrase", () -> {
            String phrase = config[0].fittingPhrase(Component.translatable("typinganimation.config.demo.text")
                    .getString(), config[0].preview());
            markLength = config[0].preview().getValue().length();
            return markLength >= phrase.length() * 3 / 4;
        }, 20_000);
        until("the demo typed another char", () -> config[0].preview().getValue().length() > markLength, 5000);
        waitMs(Math.round(2 * ShowcaseRecorder.FRAME_MS));
        act("settings still", () -> rec.markStill(still));
        waitMs(300);
        act("done", () -> click(config[0], config[0].doneButton()));
        waitMs(200);
        act("back to the game", () -> mc.gui.setScreen(null));
        waitMs(300);
        endClip();
    }

    // =================================================================== script building blocks

    @FunctionalInterface
    private interface Body {
        boolean run(long elapsedMs) throws Exception;
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

    private void waitMs(long ms) {
        steps.add(new Step("wait " + ms + " ms", t -> t >= ms));
    }

    private void until(String name, BooleanSupplier condition, long timeoutMs) {
        steps.add(new Step(name, t -> {
            if (condition.getAsBoolean()) {
                return true;
            }
            if (t > timeoutMs) {
                throw new AssertionError(name + ": timed out after " + timeoutMs + " ms");
            }
            return false;
        }));
    }

    private void startClip(String name) {
        act("start clip " + name, () -> {
            rec.start(name);
            clip = name;
            LOG.info(TAG + "SHOWCASE clip {} recording", name);
        });
    }

    private void endClip() {
        act("end clip", () -> {
            int frames = rec.stop();
            var target = mc.gameRenderer.mainRenderTarget();
            clipFrames.put(clip, frames);
            clipSizes.put(clip, target.width + "x" + target.height);
            LOG.info(TAG + "SHOWCASE clip {}: {} frames", clip, frames);
        });
    }

    /**
     * Types {@code text} char by char with a human rhythm (seeded, so every run is the same); {@code traceAt} &gt;= 0
     * arms a draw trace of the chat input right after that char.
     */
    private void type(String text, int traceAt) {
        int[] cps = text.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            int cp = cps[i];
            act("type '" + Character.toString(cp) + "'", () -> typeChar(cp));
            if (i == traceAt) {
                act("trace the chat input", () -> chatTrace = TypingRenderer.traceNextFrame(
                        (TypingStateHolder) chatInput()));
            }
            waitMs(typingDelay(cp));
        }
    }

    /** Delay after typing {@code cp}: quick keys, longer after spaces and punctuation, the occasional hesitation. */
    private long typingDelay(int cp) {
        long d = 55 + rhythm.nextInt(70);
        if (cp == ' ') {
            d += 25 + rhythm.nextInt(90);
        } else if (",.!?;:".indexOf(cp) >= 0) {
            d += 140 + rhythm.nextInt(160);
        }
        if (rhythm.nextInt(18) == 0) {
            d += 150 + rhythm.nextInt(200);
        }
        return d;
    }

    private void press(String name, int key, int keycode, int mods) {
        act(name, () -> pressKey(key, keycode, mods));
    }

    private void pressRepeated(String name, int count, int key, int keycode, int mods) {
        for (int i = 0; i < count; i++) {
            press(name + " " + i, key, keycode, mods);
            waitMs(45 + rhythm.nextInt(40));
        }
    }

    /**
     * Inserts {@code text} at the caret like Ctrl+V does ({@code EditBox#insertText}, through the chat screen), without
     * touching the system clipboard.
     */
    private void paste(String text) {
        act("paste", () -> chatScreen().insertText(text, false));
    }

    /** Presses the chat key (T) in the world, waits for the chat screen and checks its input. */
    private void openChat() {
        act("press the chat key", () -> {
            check(mc.gui.screen() == null, "a screen is open: " + mc.gui.screen());
            pressKey(InputConstants.KEY_T, 't', 0);
        });
        until("chat screen open", () -> mc.gui.screen() instanceof ChatScreen, 3000);
        act("check the chat input", () -> {
            EditBox input = chatInput();
            FieldKind kind = ((TypingStateHolder) input).typinganimation$getFieldKind();
            check(kind == FieldKind.CHAT, "the chat input is marked " + kind + ", not CHAT");
            check(input.isFocused() && input.getValue().isEmpty(), "the chat input is not focused and empty");
            chatOpens++;
        });
    }

    // =================================================================== input (the real paths)

    /**
     * Keeps the pointer outside the window (a mouse move event, the camera does not turn): opening a screen centres it,
     * and a resize reads the real cursor, so it could hover a button or a suggestion and show a tooltip in the media.
     */
    private void parkMouse() {
        if (mc.mouseHandler.xpos() != PARKED_MOUSE || mc.mouseHandler.ypos() != PARKED_MOUSE) {
            mc.mouseHandler.onMove(mc.getWindow().handle(), PARKED_MOUSE, PARKED_MOUSE, 0.0, 0.0);
        }
    }

    /** A key press and release through {@code KeyboardHandler#keyPress} (SDL scancode + keycode). */
    private void pressKey(int key, int keycode, int mods) {
        long window = mc.getWindow().handle();
        KeyEvent event = new KeyEvent(key, keycode, mods);
        mc.keyboardHandler.keyPress(window, InputConstants.PRESS, event);
        mc.keyboardHandler.keyPress(window, InputConstants.RELEASE, event);
    }

    /** A typed char through {@code KeyboardHandler#charTyped}. */
    private void typeChar(int codepoint) {
        mc.keyboardHandler.charTyped(mc.getWindow().handle(), new CharacterEvent(codepoint));
    }

    private static void click(Screen screen, AbstractWidget w) {
        check(w != null, "widget to click is missing");
        MouseButtonEvent e = new MouseButtonEvent(w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(e, false);
        screen.mouseReleased(e);
    }

    // =================================================================== setup

    private void prepare() {
        originalConfig = cfg().copy();
        cfg().copyFrom(TypingConfig.defaults());
        originalGuiScale = mc.options.guiScale().get();
        originalPauseOnLostFocus = mc.options.pauseOnLostFocus;
        originalTutorial = mc.options.tutorialStep;
        originalLanguage = mc.options.languageCode;
        if (titleScreen instanceof AccessibilityOnboardingScreen) {
            mc.options.onboardAccessibility = false; // as if the user confirmed the first-launch screen
        }
        mc.options.pauseOnLostFocus = false; // keep running while the window is in the background
        mc.options.tutorialStep = TutorialSteps.NONE;
        if (!"en_us".equals(originalLanguage)) {
            setLanguage("en_us");
        }
        mc.getWindow().setWindowed(SMALL_WIDTH, SMALL_HEIGHT);
        setGuiScale(WORLD_GUI_SCALE);
    }

    private void createWorld() throws IOException {
        LevelStorageSource source = mc.getLevelSource();
        if (source.levelExists(LEVEL_ID)) {
            try (LevelStorageSource.LevelStorageAccess access = source.createAccess(LEVEL_ID)) {
                access.deleteLevel();
            }
        }
        LevelSettings settings = new LevelSettings("Typing Animation Showcase", GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(LEVEL_ID, settings, new WorldOptions(WORLD_SEED, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions(), titleScreen);
    }

    /** Noon, clear and frozen; no mobs; a few trees, flowers and a pond in front of the player. */
    private void buildScene() {
        IntegratedServer server = mc.getSingleplayerServer();
        check(server != null, "no integrated server");
        String[] commands = {
                "gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
                "time set noon", "weather clear", "kill @e[type=!player]",
                // no first-person arm in the media (an invisible player's empty hand is not drawn), no effect icon
                "effect give @a minecraft:invisibility infinite 0 true",
                "place feature minecraft:cherry 9 -60 29", "place feature minecraft:fancy_oak -9 -60 21",
                "place feature minecraft:birch 1 -60 31", "place feature minecraft:oak 18 -60 15",
                "place feature minecraft:oak -19 -60 27", "place feature minecraft:birch -3 -60 40",
                "place feature minecraft:birch 13 -60 34",
                "fill -6 -61 12 -1 -61 14 minecraft:water", "fill -5 -61 15 -2 -61 15 minecraft:water",
                "setblock 3 -60 6 minecraft:poppy", "setblock 5 -60 9 minecraft:dandelion",
                "setblock -7 -60 6 minecraft:cornflower", "setblock -7 -60 10 minecraft:oxeye_daisy",
                "setblock 8 -60 7 minecraft:allium", "setblock 1 -60 12 minecraft:azure_bluet",
                "setblock -9 -60 9 minecraft:red_tulip", "setblock 10 -60 11 minecraft:pink_tulip",
                "setblock 2 -60 15 minecraft:orange_tulip", "setblock -3 -60 10 minecraft:dandelion",
                "setblock 4 -60 4 minecraft:short_grass", "setblock -2 -60 4 minecraft:short_grass",
                "setblock 0 -60 8 minecraft:short_grass", "setblock -10 -60 13 minecraft:short_grass",
                "setblock 11 -60 14 minecraft:short_grass", "setblock 6 -60 13 minecraft:short_grass",
                "tp @a 0.5 -60 0.5 0 4",
        };
        server.execute(() -> {
            CommandSourceStack source = server.createCommandSourceStack();
            for (String command : commands) {
                server.getCommands().performPrefixedCommand(source, command);
            }
        });
    }

    /** The media crops assume these frame sizes (a HiDPI or too small screen would change them). */
    private void checkFrameSize(int width, int height, int guiScale) {
        var target = mc.gameRenderer.mainRenderTarget();
        LOG.info(TAG + "SHOWCASE frame {}x{} (window {}x{}, GUI scale {}, GUI {}x{})", target.width, target.height,
                mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.getWindow().getGuiScale(),
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        check(target.width == width && target.height == height && mc.getWindow().getGuiScale() == guiScale,
                "expected a " + width + "x" + height + " frame at GUI scale " + guiScale);
    }

    private void setGuiScale(int scale) {
        mc.options.guiScale().set(scale);
        mc.resizeGui();
    }

    private void setLanguage(String code) {
        LanguageManager languages = mc.getLanguageManager();
        languages.setSelected(code);
        mc.options.languageCode = code;
        languages.onResourceManagerReload(mc.getResourceManager());
    }

    private void restoreSettings() {
        if (originalConfig != null) {
            cfg().copyFrom(originalConfig);
            ConfigManager.save();
        }
        if (originalLanguage != null) {
            if (!originalLanguage.equals(mc.options.languageCode)) {
                setLanguage(originalLanguage);
            }
            mc.options.guiScale().set(originalGuiScale);
            mc.resizeGui();
            mc.options.pauseOnLostFocus = originalPauseOnLostFocus;
            mc.options.tutorialStep = originalTutorial;
            mc.options.save();
        }
    }

    // =================================================================== checks and output

    private void finalChecks() {
        check(chatOpens >= 4, "the chat was opened " + chatOpens + " times");
        TypingRenderer.Trace trace = chatTrace;
        check(trace != null && trace.done && trace.count > 0, "the traced chat frame was not drawn by our path");
        int transformed = 0;
        for (int i = 0; i < trace.count; i++) {
            if (trace.transformed[i]) {
                transformed++;
            }
        }
        check(transformed > 0, "no animated char in the traced chat frame");
        check(chatGlyphs > 0, "no animated glyph was drawn in the chat screen");
        check(chatGhosts > 0, "no deleted char was drawn in the chat screen");
        check(TypingRenderer.statFallbacks == 0, "renderer fallbacks: " + TypingRenderer.statFallbacks);
        check(TypingRenderer.firstError == null, "render path error: " + TypingRenderer.firstError);
        LOG.info(TAG + "SHOWCASE CHECKS OK: chat input kind CHAT ({} opens), traced chat frame {} chars ({} animated),"
                        + " {} animated glyphs and {} ghosts in chat frames, fallbacks {}", chatOpens, trace.count,
                transformed, chatGlyphs, chatGhosts, TypingRenderer.statFallbacks);
    }

    private void writeManifest() throws IOException {
        Properties p = new Properties();
        p.setProperty("frame.ms", Double.toString(ShowcaseRecorder.FRAME_MS));
        p.setProperty("gui.scale.world", Integer.toString(WORLD_GUI_SCALE));
        p.setProperty("gui.scale.ui", Integer.toString(UI_GUI_SCALE));
        for (Map.Entry<String, Integer> e : clipFrames.entrySet()) {
            p.setProperty("clip." + e.getKey() + ".frames", Integer.toString(e.getValue()));
            p.setProperty("clip." + e.getKey() + ".size", clipSizes.get(e.getKey()));
        }
        Files.createDirectories(rec.out());
        try (Writer w = Files.newBufferedWriter(rec.out().resolve("manifest.properties"), StandardCharsets.UTF_8)) {
            p.store(w, "Typing Animation showcase (mc/26.3 Showcase)");
        }
    }

    private boolean hasSword() {
        if (mc.player == null) {
            return false;
        }
        Inventory inventory = mc.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(Items.DIAMOND_SWORD)) {
                return true;
            }
        }
        return false;
    }

    private ChatComponent chat() {
        return mc.gui.hud.getChat();
    }

    private ChatScreen chatScreen() {
        if (mc.gui.screen() instanceof ChatScreen chat) {
            return chat;
        }
        throw new AssertionError("the chat screen is not open (" + mc.gui.screen() + ")");
    }

    /** The chat screen's input: its focused child. */
    private EditBox chatInput() {
        ChatScreen chat = chatScreen();
        if (chat.getFocused() instanceof EditBox box) {
            return box;
        }
        for (GuiEventListener child : chat.children()) {
            if (child instanceof EditBox box) {
                return box;
            }
        }
        throw new AssertionError("the chat screen has no input");
    }

    private static AbstractWidget findOptionsButton(Screen s) {
        for (GuiEventListener child : s.children()) {
            if (OptionsButton.isOptionsButton(child)) {
                return (AbstractWidget) child;
            }
        }
        return null;
    }

    private static TypingConfig cfg() {
        return ConfigManager.get();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
