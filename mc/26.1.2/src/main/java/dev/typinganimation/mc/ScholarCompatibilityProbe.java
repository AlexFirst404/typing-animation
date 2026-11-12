package dev.typinganimation.mc;

import com.mojang.blaze3d.platform.InputConstants;
import dev.typinganimation.core.AppearStyle;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.CharTransform;
import dev.typinganimation.core.RemoveStyle;
import dev.typinganimation.core.TypingConfig;
import dev.typinganimation.core.compat.ScholarAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Optional runtime checks against real Scholar widgets, without a world, saved book or network connection. */
public final class ScholarCompatibilityProbe {
    private static final String PREFIX = "io.github.mortuusars.scholar.client.gui.";
    private static Minecraft client;
    private static Screen previousScreen;
    private static TypingConfig previousConfig;
    private static ProbeScreen screen;
    private static Class<?> textClass, formattedClass;
    private static int stage;
    private static long nextFrame, animatedMark, glyphMark, caretMark, verticalMark, ghostMark, soundMark;
    private static String unchangedLeft, unchangedRight;
    private static Object pageFixture;

    private ScholarCompatibilityProbe() { }

    public static boolean available() {
        return ScholarCompatibilityProbe.class.getClassLoader().getResource(
                PREFIX.replace('.', '/') + "widget/textbox/TextBox.class") != null;
    }

    public static void prepare() throws Exception {
        require(available() && previousConfig == null, "Scholar probe cannot start");
        client = Minecraft.getInstance();
        previousScreen = client.screen;
        previousConfig = ConfigManager.get().copy();
        TypingConfig cfg = ConfigManager.get();
        cfg.enabled = true;
        cfg.animateMultiline = true;
        cfg.onlyWhenFocused = true;
        cfg.appearStyle = AppearStyle.POP;
        cfg.removeStyle = RemoveStyle.FALL;
        cfg.durationMs = 1000;
        cfg.removeDurationMs = 1000;
        cfg.smoothCursor = true;
        cfg.cursorGlideMs = 300;
        cfg.typingSounds = true;
        cfg.soundVolume = .1f;
        cfg.adaptiveTyping = false;
        cfg.cursorKeepVisibleWhileTyping = true;
        textClass = Class.forName(PREFIX + "widget.textbox.TextBox");
        formattedClass = Class.forName(PREFIX + "widget.textbox.text.FormattedString");
        screen = new ProbeScreen();
        client.setScreen(screen);
        stage = 0;
        nextFrame = 3;
        TypingSounds.resetTestCounters();
    }

    /** Called by the outer self-test after rendered frames; returns true only after every assertion completes. */
    public static boolean tick() throws Exception {
        require(previousConfig != null && client.screen == screen, "Scholar probe screen changed");
        if (screen.frames < nextFrame) return false;
        ScholarAnimation left = animation(screen.left), right = animation(screen.right);
        require(!left.failed() && !right.failed() && !animation(screen.titleBox).failed(), "Scholar rendering fell back after an error");
        switch (stage++) {
            case 0 -> {
                require(left.frames >= 3 && right.frames >= 3, "Scholar page render hooks did not run");
                require(left.state().isIdle(ConfigManager.get()), "Opening a Scholar page animated its existing contents");
                require(plain(screen.left).contains("\uD83D\uDE03") && formatted(screen.left).contains("\u00a7"), "Scholar formatting/Unicode fixture is missing");
                animatedMark = left.animatedFrames; glyphMark = TypingRenderer.statGlyphs;
                type(screen.left, '!');
                require(plain(screen.left).endsWith("!"), "Scholar typing was rejected");
                require(TypingSounds.playedCount == 1, "Accepted Scholar typing did not produce one sound");
            }
            case 1 -> {
                require(left.animatedFrames > animatedMark && TypingRenderer.statGlyphs > glyphMark,
                        "Formatted Scholar page did not animate its typed glyphs");
                caretMark = left.caretGlides; verticalMark = left.verticalCaretGlides;
                soundMark = TypingSounds.playedCount;
                key(screen.left, InputConstants.KEY_LEFT);
            }
            case 2 -> {
                require(left.caretGlides > caretMark, "Scholar caret did not glide horizontally");
                require(TypingSounds.playedCount == soundMark, "Scholar cursor navigation made a typing sound");
                key(screen.left, InputConstants.KEY_UP);
            }
            case 3 -> {
                require(left.verticalCaretGlides > verticalMark, "Scholar caret did not glide across lines");
                // Force the next rendered caret into the red formatted span, not only plain text.
                Object editor = ScholarAccess.get(screen.left, "getEditor");
                editor.getClass().getMethod("setCursorPos", int.class, boolean.class).invoke(editor, 8, false);
                ScholarAccess.get(ScholarAccess.get(screen.left, "getDisplayCache"), "scheduleUpdate");
                ghostMark = left.ghostFrames;
                key(screen.left, InputConstants.KEY_BACKSPACE);
            }
            case 4 -> {
                require(left.state().ghostCount() > 0 && left.ghostFrames > ghostMark, "Scholar deletion did not animate");
                unchangedLeft = plain(screen.left);
                focus(screen.right);
                animatedMark = right.animatedFrames;
                type(screen.right, 'R');
            }
            case 5 -> {
                require(right.animatedFrames > animatedMark && unchangedLeft.equals(plain(screen.left)),
                        "Scholar pages did not keep independent text and animation state");
                focus(screen.titleBox);
                animatedMark = animation(screen.titleBox).animatedFrames;
                type(screen.titleBox, 'T');
            }
            case 6 -> {
                require(animation(screen.titleBox).animatedFrames > animatedMark, "Centered Scholar signing-field text did not animate");
                focus(screen.right);
                key(screen.right, InputConstants.KEY_BACKSPACE);
            }
            case 7 -> {
                require(right.state().ghostCount() > 0, "Identical-page reset fixture needs an active deletion");
                unchangedLeft = plain(screen.left); unchangedRight = plain(screen.right);
                pageFixture = createPageFixture(screen.left, screen.right, formatted(screen.left), formatted(screen.right));
                soundMark = TypingSounds.playedCount;
                assignPages();
                require(left.state().ghostCount() == 0 && right.state().ghostCount() == 0,
                        "Real Scholar page-assignment hook did not forget old ghosts for identical text");
                animatedMark = left.animatedFrames + right.animatedFrames;
                caretMark = left.caretGlides + right.caretGlides;
            }
            case 8 -> {
                require(unchangedLeft.equals(plain(screen.left)) && unchangedRight.equals(plain(screen.right)), "Identical page assignment changed book text");
                requireRest(screen.left); requireRest(screen.right);
                require(left.caretGlides + right.caretGlides == caretMark && TypingSounds.playedCount == soundMark,
                        "Identical Scholar page navigation replayed a caret glide or typing sound");
                field(pageFixture.getClass(), "pages").set(pageFixture, new ArrayList<>(List.of("short", "second")));
                assignPages();
                animatedMark = left.animatedFrames + right.animatedFrames;
                caretMark = left.caretGlides + right.caretGlides;
            }
            case 9 -> {
                require("short".equals(plain(screen.left)) && "second".equals(plain(screen.right)), "Real Scholar page assignment did not update both pages");
                requireRest(screen.left); requireRest(screen.right);
                require(left.caretGlides + right.caretGlides == caretMark && TypingSounds.playedCount == soundMark,
                        "A short Scholar page change replayed a caret glide or typing sound");
                focus(screen.left);
                ConfigManager.get().animateMultiline = false;
                glyphMark = TypingRenderer.statGlyphs;
                type(screen.left, 'X');
                require(plain(screen.left).contains("X"), "Disabling animation changed Scholar input");
            }
            case 10 -> {
                require(TypingRenderer.statGlyphs == glyphMark && TypingSounds.playedCount == soundMark,
                        "Disabled Scholar fields still animated or played typing sounds");
                ConfigManager.get().animateMultiline = true;
                animatedMark = left.animatedFrames + right.animatedFrames;
            }
            case 11 -> {
                requireRest(screen.left); requireRest(screen.right);
                require(TypingRenderer.firstError == null && TypingSounds.firstError == null, "Scholar renderer or sound error");
                return true;
            }
            default -> { return true; }
        }
        nextFrame = screen.frames + 3;
        return false;
    }

    public static void cleanup() {
        if (previousConfig != null) ConfigManager.get().copyFrom(previousConfig);
        if (client != null && previousScreen != null) client.setScreen(previousScreen);
        previousConfig = null; previousScreen = null; screen = null; pageFixture = null;
    }
    public static void abort() { cleanup(); }

    private static ScholarAnimation animation(AbstractWidget box) {
        require(box instanceof ScholarStateHolder, "Optional Scholar mixin was not applied");
        return ((ScholarStateHolder) box).typinganimation$scholarAnimation();
    }
    private static String plain(Object box) throws Exception { return ScholarAccess.plain(box); }
    private static void requireRest(AbstractWidget box) throws Exception {
        ScholarAnimation a = animation(box);
        require(a.state().isIdle(ConfigManager.get()) && a.state().ghostCount() == 0,
                "Scholar page navigation or re-enabling replayed removal or reflow");
        CharTransform transform = new CharTransform();
        String text = plain(box);
        for (int i = 0; i < text.length(); i += Character.charCount(text.codePointAt(i))) {
            require(!a.state().appearTransform(i, ConfigManager.get(), transform),
                    "Scholar page navigation or re-enabling created an appearance effect");
        }
    }
    private static String formatted(Object box) throws Exception {
        return ScholarAccess.get(ScholarAccess.get(box, "getEditor"), "getString").toString();
    }
    private static void focus(AbstractWidget target) {
        screen.left.setFocused(target == screen.left);
        screen.right.setFocused(target == screen.right);
        screen.titleBox.setFocused(target == screen.titleBox);
    }
    private static void type(AbstractWidget box, char c) {
        require(box.charTyped(new CharacterEvent(c)), "Scholar widget rejected a test character");
    }
    private static void key(AbstractWidget box, int key) { box.keyPressed(new KeyEvent(key, 0, 0)); }

    /** Runs Scholar's real page setter, skipping the concrete screen's world/network-dependent constructor. */
    private static Object createPageFixture(AbstractWidget left, AbstractWidget right, String leftText, String rightText) throws Exception {
        Class<?> type = Class.forName(PREFIX + "screen.edit.InHandSpreadBookEditScreen");
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        Object instance = unsafeType.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), type);
        field(type, "leftPageTextBox").set(instance, left);
        field(type, "rightPageTextBox").set(instance, right);
        field(type, "pages").set(instance, new ArrayList<>(List.of(leftText, rightText)));
        field(type, "currentSpread").setInt(instance, 0);
        return instance;
    }
    private static void assignPages() throws Exception {
        Class<?> type = Class.forName(PREFIX + "screen.edit.SpreadBookEditScreen");
        var setter = type.getDeclaredMethod("setTextBoxes", boolean.class); setter.setAccessible(true);
        setter.invoke(pageFixture, true);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }

    private static final class ProbeScreen extends Screen {
        AbstractWidget left, right, titleBox;
        long frames;
        ProbeScreen() { super(Component.literal("Scholar compatibility self-test")); }
        @Override protected void init() {
            try {
                int x = width / 2 - 125;
                left = box(x, 36, 114, 128, "Hello \u00a7cRED \u00a7lbold\u00a7r\nnext \uD83D\uDE03 end");
                right = box(x + 136, 36, 114, 128, "Right page");
                titleBox = box(x + 66, 180, 114, 18, "Title");
                Class<?> alignment = Class.forName(PREFIX + "widget.textbox.display.HorizontalAlignment");
                textClass.getMethod("setHorizontalAlignment", alignment).invoke(titleBox, alignment.getField("CENTER").get(null));
                addRenderableWidget(left); addRenderableWidget(right); addRenderableWidget(titleBox);
                focus(left);
            } catch (Exception failure) { throw new IllegalStateException("Scholar fixture initialization", failure); }
        }
        private AbstractWidget box(int x, int y, int width, int height, String value) throws Exception {
            AbstractWidget box = (AbstractWidget) textClass.getConstructor(Font.class, int.class, int.class, int.class, int.class)
                    .newInstance(font, x, y, width, height);
            Object parsed = formattedClass.getMethod("parse", String.class).invoke(null, value);
            textClass.getMethod("setText", formattedClass).invoke(box, parsed);
            textClass.getMethod("setFontColor", int.class).invoke(box, 0xFFFFFFFF);
            textClass.getMethod("setFontUnfocusedColor", int.class).invoke(box, 0xFFFFFFFF);
            Object editor = ScholarAccess.get(box, "getEditor");
            editor.getClass().getMethod("setCursorToEnd", boolean.class).invoke(editor, false);
            return box;
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            frames++;
            graphics.fill(0, 0, width, height, 0xFF20242C);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
        @Override public void onClose() { }
        @Override public boolean shouldCloseOnEsc() { return false; }
    }
}
