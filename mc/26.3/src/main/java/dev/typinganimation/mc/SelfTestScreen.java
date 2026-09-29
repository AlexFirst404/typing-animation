package dev.typinganimation.mc;

import dev.typinganimation.core.FieldKind;
import dev.typinganimation.core.TypingAnimationMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev self-test screen: a bordered "plain" field (OTHER), a borderless chat-like field (CHAT) with a coloured,
 * partly bold formatter similar to command highlighting, and a multi-line field. Solid background, so pixel
 * comparisons do not depend on the panorama.
 */
final class SelfTestScreen extends Screen {
    static final int BACKGROUND = 0xFF20242C;
    static final int CHAT_BAR = 0xFF000000;
    /**
     * Value of the decorated fields, styled by {@link #decoStyleAt}: underline, strikethrough, italic and bold spans
     * that run across piece boundaries (same and different colours), decorated spaces, and a run in which every char
     * is its own piece.
     */
    static final String DECO_TEXT = "under line STRUCK ital BOLD mix";
    /** Caret of the decorated fields during the fidelity checks: inside "STR" (a segment boundary in a struck piece). */
    static final int DECO_CARET = 13;
    /** Vanilla EditBox text colour. */
    static final int DEFAULT_TEXT = 0xFFE0E0E0;
    /**
     * Translucent text colour of the decorated fields' translucent fidelity check: overlapping quads blend twice at
     * this alpha, which shows geometry and draw-order differences (e.g. an underline starting 1 px too far left) that
     * opaque text hides.
     */
    static final int TRANSLUCENT_TEXT = 0xE0E0E0E0;
    /** Colour of the bold "BOLD" in the decorated fields (ChatFormatting.YELLOW), used to find their text. */
    static final int DECO_YELLOW = 0xFFFFFF55;
    private static final ChatFormatting[] DECO_MIX = {ChatFormatting.RED, ChatFormatting.GREEN, ChatFormatting.BLUE,
            ChatFormatting.GOLD, ChatFormatting.AQUA};
    private static final ChatFormatting[] ARGUMENT_COLORS = {
            ChatFormatting.AQUA, ChatFormatting.YELLOW, ChatFormatting.GREEN, ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.GOLD};

    private final Screen parent;
    EditBox plain;
    EditBox chat;
    MultiLineEditBox multi;
    /** Decorated field with the default text shadow. */
    EditBox deco;
    /** The same decorated field without text shadow ({@code EditBox#setTextShadow(false)}). */
    EditBox decoFlat;
    long frames;

    SelfTestScreen(Screen parent) {
        super(Component.literal("Typing Animation self-test"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;
        plain = new EditBox(font, left, 22, 300, 20, Component.literal("plain"));
        plain.setMaxLength(512);
        addRenderableWidget(plain);

        chat = new EditBox(font, left, 52, 300, 12, Component.literal("chat"));
        chat.setBordered(false);
        chat.setMaxLength(256);
        EditBox chatBox = chat;
        chat.addFormatter((text, offset) -> formatChat(chatBox.getValue(), text, offset));
        ((TypingStateHolder) chat).typinganimation$setFieldKind(FieldKind.CHAT);
        addRenderableWidget(chat);

        multi = MultiLineEditBox.builder().setX(left).setY(72).setPlaceholder(Component.literal("multi-line"))
                .build(font, 300, 70, Component.literal("multi"));
        addRenderableWidget(multi);

        deco = decoBox(left, 150);
        addRenderableWidget(deco);
        decoFlat = decoBox(left, 166);
        decoFlat.setTextShadow(false);
        addRenderableWidget(decoFlat);
    }

    private EditBox decoBox(int x, int y) {
        EditBox box = new EditBox(font, x, y, 300, 12, Component.literal("decorated"));
        box.setBordered(false);
        box.setMaxLength(256);
        box.addFormatter((text, offset) -> formatDeco(box.getValue(), text, offset));
        return box;
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(plain);
        // the test types into all three fields: mark every one focused (widget-level flag)
        chat.setFocused(true);
        multi.setFocused(true);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(chat.getX() - 2, chat.getY() - 2, chat.getX() + chat.getWidth() + 2, chat.getY() + chat.getHeight(),
                CHAT_BAR);
        for (EditBox d : new EditBox[]{deco, decoFlat}) {
            graphics.fill(d.getX() - 2, d.getY() - 2, d.getX() + d.getWidth() + 2, d.getY() + d.getHeight(), CHAT_BAR);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        frames++;
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // Real mouse/keyboard input is ignored: the test drives the fields directly, and a stray click (e.g. when the
    // window gets focus) must not move a caret between steps.

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ignored("click", event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        ignored("scroll", x, y);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        ignored("key " + event.key(), 0, 0);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        ignored("char " + event.codepoint(), 0, 0);
        return true;
    }

    private static void ignored(String what, double x, double y) {
        TypingAnimationMod.LOGGER.info("[typinganimation] selftest: ignored real input ({} at {},{})", what, x, y);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    /**
     * Formatter of the decorated fields: a composite of {@code forward} pieces (sink positions restart at 0 per piece,
     * which is where vanilla starts an underline/strikethrough 1 px further left), split where the style changes and
     * at {@link #decoPieceBreak}. Styles depend only on the absolute index, so both caret segments agree.
     */
    static FormattedCharSequence formatDeco(String value, String text, int offset) {
        List<FormattedCharSequence> parts = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            Style style = decoStyleAt(value, offset + i);
            int j = i + Character.charCount(text.codePointAt(i));
            while (j < text.length() && !decoPieceBreak(offset + j) && decoStyleAt(value, offset + j).equals(style)) {
                j += Character.charCount(text.codePointAt(j));
            }
            parts.add(FormattedCharSequence.forward(text.substring(i, j), style));
            i = j;
        }
        return FormattedCharSequence.composite(parts);
    }

    /** A new piece starts at char {@code i} of the decorated value even where the style does not change. */
    private static boolean decoPieceBreak(int i) {
        return i == 2 || i == 25 || i >= 28;
    }

    /**
     * Style of char {@code i} of the decorated value ({@link #DECO_TEXT}); a '#' is obfuscated and underlined (typed
     * and removed again while animating: its random glyph cannot be compared with vanilla).
     */
    static Style decoStyleAt(String value, int i) {
        if (i < 0 || i >= value.length()) {
            return Style.EMPTY;
        }
        Style s = Style.EMPTY;
        if (value.charAt(i) == '#') {
            return s.withColor(ChatFormatting.AQUA).withObfuscated(true).withUnderlined(true);
        }
        if (i <= 5) { // "under " (a new piece of the same style starts at 2)
            return s.withColor(ChatFormatting.AQUA).withUnderlined(true);
        }
        if (i <= 7) { // "li": the underline goes on in another colour
            return s.withColor(ChatFormatting.GREEN).withUnderlined(true);
        }
        if (i <= 10) { // "ne ": underlined and struck
            return s.withColor(ChatFormatting.GREEN).withUnderlined(true).withStrikethrough(true);
        }
        if (i <= 13) { // "STR"
            return s.withColor(ChatFormatting.RED).withStrikethrough(true);
        }
        if (i <= 16) { // "UCK": the strikethrough goes on in another colour
            return s.withColor(ChatFormatting.GOLD).withStrikethrough(true);
        }
        if (i >= 18 && i <= 21) { // "ital"
            return s.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(true).withUnderlined(true);
        }
        if (i >= 23 && i <= 26) { // "BOLD" (a new piece of the same style starts at 25)
            return s.withColor(ChatFormatting.YELLOW).withBold(true).withStrikethrough(true);
        }
        if (i == 27) { // a bold underlined space
            return s.withColor(ChatFormatting.YELLOW).withBold(true).withUnderlined(true);
        }
        if (i >= 28) { // "mix": all four decorations, one piece per char
            return s.withColor(DECO_MIX[i % DECO_MIX.length]).withBold(true).withItalic(true).withUnderlined(true)
                    .withStrikethrough(true);
        }
        return s; // the plain spaces 17 and 22
    }

    /** Formatted form of the whole chat value (for width computations). */
    static FormattedCharSequence formatChatValue(String value) {
        return formatChat(value, value, 0);
    }

    /**
     * Chat-like formatter: the first word (command) white, every following word in a cycling colour, every third
     * argument bold, spaces unstyled. Built like CommandSuggestions: a composite of forward() parts.
     */
    static FormattedCharSequence formatChat(String value, String text, int offset) {
        List<FormattedCharSequence> parts = new ArrayList<>();
        int word = 0;
        for (int k = 0; k < offset && k < value.length(); k++) {
            if (value.charAt(k) == ' ') {
                word++;
            }
        }
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int j = i + 1;
            if (c == ' ') {
                while (j < text.length() && text.charAt(j) == ' ') {
                    j++;
                }
                parts.add(FormattedCharSequence.forward(text.substring(i, j), Style.EMPTY));
                word += j - i;
            } else {
                while (j < text.length() && text.charAt(j) != ' ') {
                    j++;
                }
                parts.add(FormattedCharSequence.forward(text.substring(i, j), styleOf(word)));
            }
            i = j;
        }
        return FormattedCharSequence.composite(parts);
    }

    private static Style styleOf(int word) {
        if (word == 0) {
            return Style.EMPTY.withColor(ChatFormatting.WHITE);
        }
        Style s = Style.EMPTY.withColor(ARGUMENT_COLORS[(word - 1) % ARGUMENT_COLORS.length]);
        return word % 3 == 2 ? s.withBold(true) : s;
    }
}
