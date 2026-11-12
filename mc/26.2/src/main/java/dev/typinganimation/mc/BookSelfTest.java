package dev.typinganimation.mc;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import dev.typinganimation.core.AppearStyle;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.RemoveStyle;
import dev.typinganimation.core.TypingConfig;
import dev.typinganimation.mixin.MultilineTextFieldAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/** Dev fixture: real vanilla book screens and input/render paths, with no world, saves or packets. */
public final class BookSelfTest {
    private static final String WRAPPED_PAGE = "A longer book page checks ordinary wrapping across several lines of text.";
    private static Minecraft client;
    private static Screen previousScreen;
    private static TypingConfig previousConfig;
    private static BookEditScreen bookScreen;
    private static MultiLineEditBox page;
    private static EditBox title;
    private static long soundsBeforeNavigation, caretMark, glyphMark;

    private BookSelfTest() { }

    public static void prepare() throws Exception {
        require(previousConfig == null, "Book probe is already running");
        client = Minecraft.getInstance();
        previousScreen = client.gui.screen();
        previousConfig = ConfigManager.get().copy();
        TypingConfig cfg = ConfigManager.get();
        cfg.enabled = true;
        cfg.animateMultiline = true;
        cfg.animateOtherFields = true;
        cfg.onlyWhenFocused = true;
        cfg.appearStyle = AppearStyle.POP;
        cfg.removeStyle = RemoveStyle.FALL;
        cfg.durationMs = 1000;
        cfg.removeDurationMs = 1000;
        cfg.cursorGlideMs = 300;
        cfg.typingSounds = true;
        cfg.soundVolume = .1f;
        cfg.adaptiveTyping = false;
        cfg.cursorKeepVisibleWhileTyping = true;
        WritableBookContent content = new WritableBookContent(List.of("One", "Two", "", "Two!", WRAPPED_PAGE)
                .stream().map(Filterable::passThrough).toList());
        bookScreen = new BookEditScreen(fixtureOwner(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, content);
        client.gui.setScreen(bookScreen);
        page = bookScreen.children().stream().filter(MultiLineEditBox.class::isInstance)
                .map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
        require("One".equals(page.getValue()) && page.isFocused(), "Actual book page was not initialized/focused");
        TypingSounds.resetTestCounters();
    }

    public static boolean pageRendered() {
        return page != null && animation().activeFrames() >= 3;
    }

    public static void typePage() {
        glyphMark = TypingRenderer.statGlyphs;
        type(bookScreen, '!');
        require("One!".equals(page.getValue()), "Book screen did not route typed input to the page");
        require(TypingSounds.playedCount == 1, "Accepted book typing did not make exactly one sound");
    }

    public static void verifyTyping() {
        require(!animation().lastFrameIdle() && TypingRenderer.statGlyphs > glyphMark, "Actual book page did not animate");
        require(!animation().hasFailed(), "Actual book page render failed");
        caretMark = TypingRenderer.statCaretOffsets;
        key(bookScreen, InputConstants.KEY_LEFT, InputConstants.KEY_LEFT);
    }

    public static void deletePageCharacter() {
        require(TypingRenderer.statCaretOffsets > caretMark, "Book caret did not glide after moving left");
        key(bookScreen, InputConstants.KEY_BACKSPACE, InputConstants.KEY_BACKSPACE);
        require("On!".equals(page.getValue()), "Book deletion changed the wrong character");
    }

    public static void verifyDeletionAndNextPage() {
        require(animation().state.ghostCount() > 0, "Book deletion did not leave an animated ghost");
        nextPage("Two");
    }

    public static void nextPage(String expected) {
        soundsBeforeNavigation = TypingSounds.playedCount;
        key(bookScreen, InputConstants.KEY_PAGEDOWN, InputConstants.KEY_PAGEDOWN);
        require(expected.equals(page.getValue()), "PageDown did not open the expected page");
        require(animation().lastValue == null && animation().state.ghostCount() == 0 && !animation().caretTargetValid,
                "Page change did not explicitly forget old text, ghosts and caret");
    }

    public static void previousPage(String expected) {
        soundsBeforeNavigation = TypingSounds.playedCount;
        key(bookScreen, InputConstants.KEY_PAGEUP, InputConstants.KEY_PAGEUP);
        require(expected.equals(page.getValue()), "PageUp did not restore the expected page");
    }

    public static void verifyPageRest(String expected) {
        require(expected.equals(page.getValue()), "Unexpected page text after navigation");
        require(animation().lastFrameIdle() && animation().state.ghostCount() == 0,
                "A page turn replayed typing or removal animations");
        require(Math.abs(animation().caretDx) < .001f && Math.abs(animation().caretDy) < .001f,
                "A page turn carried the previous page's caret glide");
        require(TypingSounds.playedCount == soundsBeforeNavigation, "Page navigation made a typing sound");
    }

    public static void verifyWrappedPageAndType() throws Exception {
        verifyPageRest(WRAPPED_PAGE);
        MultilineTextField textField = textField();
        require(((MultilineTextFieldAccessor) (Object) textField).typinganimation$displayLines().size() > 1,
                "Real book fixture did not wrap into multiple lines");
        glyphMark = TypingRenderer.statGlyphs;
        type(bookScreen, 'X');
        require((WRAPPED_PAGE + "X").equals(page.getValue()), "Wrapped book input was not accepted");
    }

    public static void verifyWrappedTypingAndLimit() {
        require(!animation().lastFrameIdle() && TypingRenderer.statGlyphs > glyphMark, "Wrapped book text did not animate");
        page.setValue("X".repeat(1024), true);
        long sounds = TypingSounds.playedCount;
        type(bookScreen, 'Z');
        require(page.getValue().length() == 1024, "Book character limit changed");
        require(TypingSounds.playedCount == sounds, "Rejected book input made a sound");
        Button sign = bookScreen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(Component.translatable("book.signButton").getString()))
                .findFirst().orElseThrow();
        sign.onPress(new KeyEvent(0, 0, 0));
        require(client.gui.screen() instanceof BookSignScreen, "The actual vanilla signing screen did not open");
        title = client.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow();
    }

    public static boolean titleRendered() {
        return title != null && ((TypingStateHolder) title).typinganimation$animation().activeFrames() >= 3;
    }

    public static void typeTitle() {
        glyphMark = TypingRenderer.statGlyphs;
        type(client.gui.screen(), 'T');
        require("T".equals(title.getValue()), "Book title input was not accepted");
    }

    public static void verifyTitleAndReturn() {
        WidgetAnimation a = ((TypingStateHolder) title).typinganimation$animation();
        require(!a.lastFrameIdle() && TypingRenderer.statGlyphs > glyphMark, "Centered book title did not animate");
        // Exercise Cancel, never Finalize: the fixture has no server connection and does not write a book.
        Button cancel = client.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(Component.translatable("gui.cancel").getString()))
                .findFirst().orElseThrow();
        cancel.onPress(new KeyEvent(0, 0, 0));
        require(client.gui.screen() == bookScreen, "Signing Cancel did not restore the real editor");
        page = bookScreen.children().stream().filter(MultiLineEditBox.class::isInstance)
                .map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
        require(page.getValue().length() == 1024, "Returning from signing lost the current page");
        require(TypingRenderer.firstError == null && TypingSounds.firstError == null, "Book rendering/audio failed");
    }

    public static void cleanup() {
        if (previousConfig != null) ConfigManager.get().copyFrom(previousConfig);
        if (client != null && previousScreen != null) client.gui.setScreen(previousScreen);
        previousConfig = null;
        previousScreen = null;
        bookScreen = null;
        page = null;
        title = null;
    }

    /** Only getName is used by the real signing-screen constructor; this object is never ticked or put in a world. */
    public static Player fixtureOwner() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Player owner = (Player) unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, RemotePlayer.class);
        boolean setProfile = false;
        for (Class<?> c = RemotePlayer.class; c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getType() == GameProfile.class) {
                    field.setAccessible(true);
                    field.set(owner, new GameProfile(UUID.randomUUID(), "Typing Animation"));
                    setProfile = true;
                }
            }
        }
        require(setProfile && "Typing Animation".equals(owner.getName().getString()), "Book fixture owner is not initialized");
        return owner;
    }

    private static MultilineTextField textField() throws Exception {
        Field field = MultiLineEditBox.class.getDeclaredField("textField");
        field.setAccessible(true);
        return (MultilineTextField) field.get(page);
    }

    private static WidgetAnimation animation() { return ((TypingStateHolder) page).typinganimation$animation(); }
    private static void type(Screen s, char c) { require(s.charTyped(new CharacterEvent(c)), "Book input rejected"); }
    private static void key(Screen s, int key, int keycode) { s.keyPressed(new KeyEvent(key, 0, 0)); }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
