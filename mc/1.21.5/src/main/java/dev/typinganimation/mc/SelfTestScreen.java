package dev.typinganimation.mc;

import com.mojang.blaze3d.platform.NativeImage;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.FieldKind;
import dev.typinganimation.core.TypingConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dev self-test screen (docs/SPEC.md section 9). Top half: animated fields driven by {@link SelfTest} (a plain
 * bordered EditBox, a chat-like unbordered EditBox of kind CHAT with a coloured formatter, a MultiLineEditBox).
 * Bottom half: the at-rest fidelity pairs, which receive identical input; the left column is drawn with the mod
 * disabled (vanilla path), the right one enabled (optionally forced through the per-char path), and the pixels of
 * both columns are compared right after they are drawn. Also serves render-time screenshots.
 */
final class SelfTestScreen extends Screen {
    static final int BG = 0xFF1B1F27;
    static final int CHAT_BG = 0x80000000;
    /** Horizontal distance between the vanilla (left) and animated (right) fidelity columns. */
    static final int FID_DX = 206;
    /** Vanilla EditBox text colour. */
    static final int DEFAULT_TEXT = 0xE0E0E0;
    /**
     * Translucent text colour for the translucent fidelity check of the chat and decorated pairs (Font keeps this
     * alpha for styled colours). 0xE0 still leaves the bold yellow ink above the "text present" threshold of
     * {@link #compare}. Overlapping quads blend twice at this alpha, so it shows geometry differences (e.g. an
     * underline starting 1 px too far left) that opaque text hides.
     */
    static final int FID_TRANSLUCENT_TEXT = 0xE0E0E0E0;
    /**
     * Value of the decorated fidelity pair, styled by {@link #decoStyleAt}: underline, strikethrough, italic and bold
     * spans that run across piece boundaries (same and different colours), decorated spaces, and a run in which every
     * char is its own piece.
     */
    static final String DECO_TEXT = "under line STRUCK ital BOLD mix";
    /** Caret of the decorated pair during the checks: inside "STR" (a segment boundary within a struck piece). */
    static final int DECO_CARET = 13;
    private static final int LEFT = 10;
    private static final int FID_DECO_Y = 202;
    private static final ChatFormatting[] DECO_MIX = {ChatFormatting.RED, ChatFormatting.GREEN, ChatFormatting.BLUE,
            ChatFormatting.GOLD, ChatFormatting.AQUA};
    /** The unbordered chat pair is narrower: vanilla measures the visible text without bold, so it can overflow. */
    private static final int FID_CHAT_W = 176;
    /** GUI rectangles (left column) compared against the same rectangles shifted by FID_DX. */
    private static final int[][] FID_RECTS = {
            {LEFT - 1, 89, LEFT + 201, 111},   // plain pair (with border)
            {LEFT - 2, 115, LEFT + 196, 131},  // chat pair (narrower box: bold text may overflow it)
            {LEFT - 1, 135, LEFT + 201, 197},  // multi-line pair
            {LEFT - 2, FID_DECO_Y - 3, LEFT + 202, FID_DECO_Y + 13},  // decorated pair (unbordered)
    };
    private static final String[] FID_RECT_NAMES = {"plain", "chat", "multi-line", "decorated"};

    EditBox plain;
    EditBox chat;
    MultiLineEditBox multi;
    EditBox fidPlainL, fidPlainR, fidChatL, fidChatR;
    MultiLineEditBox fidMultiL, fidMultiR;
    /** Decorated fidelity pair: underline/strikethrough/italic/bold spans (see {@link #decoStyleAt}). */
    EditBox fidDecoL, fidDecoR;
    String statusLine = "";
    /** The plain fidelity pair draws without text shadow (NeoForge's EditBox#setTextShadow was available). */
    boolean textShadowOff;

    // ---------------------------------------------------------------- render-time requests
    private String captureName;
    private long captureNotBefore;
    private Consumer<String> captureDone;
    /**
     * 0 = none, 1 = normal (idle fast path), 2 = forced per-char path, 3 = forced per-char path (used with the
     * translucent text colour of the chat and decorated pairs).
     */
    int fidelityMode;
    private boolean fidelityCheck;
    /** A read-back was issued and its (asynchronous) result has not arrived yet. */
    private boolean fidelityInFlight;
    private int fidelityAttempts;
    private Consumer<String> fidelityResult;

    SelfTestScreen() {
        super(Component.literal("Typing Animation self-test"));
    }

    @Override
    protected void init() {
        plain = new EditBox(this.font, LEFT, 16, 200, 20, Component.literal("plain"));
        plain.setMaxLength(256);
        addRenderableWidget(plain);

        chat = chatBox(LEFT, 44, 200);
        addRenderableWidget(chat);

        multi = new MultiLineEditBox(this.font, 216, 16, 200, 58, Component.literal("multi-line placeholder"),
                Component.literal("multi"));
        addRenderableWidget(multi);

        // fidelity pairs: rendered manually (see render), never as screen widgets
        fidPlainL = new EditBox(this.font, LEFT, 90, 200, 20, Component.literal("fidelity plain L"));
        fidPlainR = new EditBox(this.font, LEFT + FID_DX, 90, 200, 20, Component.literal("fidelity plain R"));
        fidPlainL.setMaxLength(256);
        fidPlainR.setMaxLength(256);
        // NeoForge only: its EditBox patch has a text-shadow switch (6-arg drawString). Turning it off for the plain
        // pair puts our shadow=false path through the pixel comparison; the chat pair keeps the default shadow.
        textShadowOff = disableTextShadow(fidPlainL) & disableTextShadow(fidPlainR);
        fidChatL = chatBox(LEFT, 118, FID_CHAT_W);
        fidChatR = chatBox(LEFT + FID_DX, 118, FID_CHAT_W);
        fidMultiL = new MultiLineEditBox(this.font, LEFT, 136, 200, 60, Component.literal("ph"),
                Component.literal("fidelity multi L"));
        fidMultiR = new MultiLineEditBox(this.font, LEFT + FID_DX, 136, 200, 60, Component.literal("ph"),
                Component.literal("fidelity multi R"));
        fidDecoL = decoBox(LEFT, FID_DECO_Y, 200);
        fidDecoR = decoBox(LEFT + FID_DX, FID_DECO_Y, 200);
    }

    /**
     * Calls NeoForge's {@code EditBox#setTextShadow(false)} when it exists (reflection: the method is a loader
     * addition that shared code must not link against). Returns whether it was applied.
     */
    private static boolean disableTextShadow(EditBox box) {
        try {
            EditBox.class.getMethod("setTextShadow", boolean.class).invoke(box, false);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private EditBox chatBox(int x, int y, int width) {
        EditBox box = new EditBox(this.font, x, y, width, 12, Component.literal("chat"));
        box.setMaxLength(256);
        box.setBordered(false);
        box.setFormatter((part, start) -> chatFormat(box.getValue(), part, start));
        ((TypingStateHolder) box).typinganimation$setKind(FieldKind.CHAT);
        return box;
    }

    private EditBox decoBox(int x, int y, int width) {
        EditBox box = new EditBox(this.font, x, y, width, 12, Component.literal("decorated"));
        box.setMaxLength(256);
        box.setBordered(false);
        box.setFormatter((part, start) -> decoFormat(box.getValue(), part, start));
        return box;
    }

    /**
     * Formatter of the decorated pair: a composite of {@code forward} pieces (sink positions restart at 0 per piece,
     * which is where vanilla starts an underline/strikethrough 1 px further left), split where the style changes and
     * at {@link #decoPieceBreak}. Styles depend only on the absolute index, so both caret segments agree.
     */
    static FormattedCharSequence decoFormat(String full, String part, int start) {
        List<FormattedCharSequence> parts = new ArrayList<>();
        int i = 0;
        while (i < part.length()) {
            Style style = decoStyleAt(full, start + i);
            int j = i + Character.charCount(part.codePointAt(i));
            while (j < part.length() && !decoPieceBreak(start + j) && decoStyleAt(full, start + j).equals(style)) {
                j += Character.charCount(part.codePointAt(j));
            }
            parts.add(FormattedCharSequence.forward(part.substring(i, j), style));
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
    static Style decoStyleAt(String full, int i) {
        if (i < 0 || i >= full.length()) {
            return Style.EMPTY;
        }
        Style s = Style.EMPTY;
        if (full.charAt(i) == '#') {
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

    /**
     * Chat-like formatter in the style of command highlighting: a composite of {@code forward} parts whose
     * positions restart at 0 per part (as CommandSuggestions#formatChat produces). The style of a char depends only
     * on its absolute position in the full value, so the before/after-cursor segments agree.
     */
    static FormattedCharSequence chatFormat(String full, String part, int start) {
        List<FormattedCharSequence> parts = new ArrayList<>();
        int i = 0;
        while (i < part.length()) {
            Style style = styleAt(full, start + i);
            int j = i + Character.charCount(part.codePointAt(i));
            while (j < part.length() && styleAt(full, start + j).equals(style)) {
                j += Character.charCount(part.codePointAt(j));
            }
            parts.add(FormattedCharSequence.forward(part.substring(i, j), style));
            i = j;
        }
        return FormattedCharSequence.composite(parts);
    }

    private static Style styleAt(String full, int index) {
        if (index < 0 || index >= full.length() || full.charAt(index) == ' ') {
            return Style.EMPTY;
        }
        int wordStart = index;
        while (wordStart > 0 && full.charAt(wordStart - 1) != ' ') {
            wordStart--;
        }
        int wordEnd = index;
        while (wordEnd < full.length() && full.charAt(wordEnd) != ' ') {
            wordEnd++;
        }
        String word = full.substring(wordStart, wordEnd);
        if (wordStart == 0 && word.startsWith("/")) {
            return Style.EMPTY.withColor(ChatFormatting.GOLD);
        }
        if (word.startsWith("@")) {
            return Style.EMPTY.withColor(ChatFormatting.AQUA).withItalic(true);
        }
        if (word.chars().anyMatch(Character::isDigit)) {
            return Style.EMPTY.withColor(ChatFormatting.GREEN).withUnderlined(true);
        }
        if (word.indexOf(':') >= 0) {
            return Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true);
        }
        int words = 0;
        for (int k = 0; k < wordStart; k++) {
            if (full.charAt(k) == ' ' && (k + 1 >= full.length() || full.charAt(k + 1) != ' ')) {
                words++;
            }
        }
        return words % 2 == 0 ? Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE) : Style.EMPTY;
    }

    @Override
    public void renderBackground(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        gg.fill(0, 0, this.width, this.height, BG);
        gg.drawString(this.font, "Typing Animation self-test", LEFT, 4, 0xFFFFFF);
        gg.fill(LEFT - 2, 42, LEFT + 202, 58, CHAT_BG);
        gg.drawString(this.font, "vanilla (mod disabled)", LEFT, 78, 0xA0A0A0);
        gg.drawString(this.font, "animated, at rest", LEFT + FID_DX, 78, 0xA0A0A0);
        gg.fill(LEFT - 2, 116, LEFT + FID_CHAT_W + 2, 130, CHAT_BG);
        gg.fill(LEFT - 2 + FID_DX, 116, LEFT + FID_CHAT_W + 2 + FID_DX, 130, CHAT_BG);
        gg.fill(LEFT - 2, FID_DECO_Y - 2, LEFT + 202, FID_DECO_Y + 12, CHAT_BG);
        gg.fill(LEFT - 2 + FID_DX, FID_DECO_Y - 2, LEFT + 202 + FID_DX, FID_DECO_Y + 12, CHAT_BG);
        gg.drawString(this.font, statusLine, LEFT, this.height - 11, 0xC0C0C0);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        super.render(gg, mouseX, mouseY, partialTick);
        renderFidelityPairs(gg, partialTick);
        gg.flush();
        if (fidelityCheck) {
            checkFidelity();
        }
        if (captureName != null && Util.getMillis() >= captureNotBefore) {
            String name = captureName;
            Consumer<String> done = captureDone;
            captureName = null;
            captureDone = null;
            String path = SelfTest.writeScreenshot(name);
            if (done != null) {
                done.accept(path);
            }
        }
    }

    private void renderFidelityPairs(GuiGraphics gg, float partialTick) {
        TypingConfig cfg = ConfigManager.get();
        boolean enabled = cfg.enabled;
        try {
            cfg.enabled = false; // left column: the mod is off -> pure vanilla rendering
            fidPlainL.render(gg, -1, -1, partialTick);
            fidChatL.render(gg, -1, -1, partialTick);
            fidMultiL.render(gg, -1, -1, partialTick);
            fidDecoL.render(gg, -1, -1, partialTick);
        } finally {
            cfg.enabled = enabled;
        }
        TypingRenderer.forcePerChar = fidelityMode >= 2;
        try {
            fidPlainR.render(gg, -1, -1, partialTick);
            fidChatR.render(gg, -1, -1, partialTick);
            fidMultiR.render(gg, -1, -1, partialTick);
            fidDecoR.render(gg, -1, -1, partialTick);
        } finally {
            TypingRenderer.forcePerChar = false;
        }
    }

    /** Applies the same input to both boxes of each fidelity pair. */
    void fidelityInput(Consumer<EditBox> plainOp, Consumer<EditBox> chatOp, Consumer<MultiLineEditBox> multiOp) {
        if (plainOp != null) {
            plainOp.accept(fidPlainL);
            plainOp.accept(fidPlainR);
        }
        if (chatOp != null) {
            chatOp.accept(fidChatL);
            chatOp.accept(fidChatR);
        }
        if (multiOp != null) {
            multiOp.accept(fidMultiL);
            multiOp.accept(fidMultiR);
        }
    }

    /** Applies the same input to both boxes of the decorated pair. */
    void decoInput(Consumer<EditBox> op) {
        op.accept(fidDecoL);
        op.accept(fidDecoR);
    }

    void requestCapture(String name, long delayMs, Consumer<String> done) {
        captureName = name;
        captureNotBefore = Util.getMillis() + delayMs;
        captureDone = done;
    }

    boolean capturePending() {
        return captureName != null;
    }

    /** Compares the columns on the next rendered frames (retrying a few frames for a caret blink edge). */
    void requestFidelity(int mode, Consumer<String> result) {
        fidelityMode = mode;
        fidelityAttempts = 0;
        fidelityResult = result;
        fidelityCheck = true;
    }

    /**
     * Issues the read-back of this frame (both columns are drawn and flushed at this point). 1.21.5 reads the render
     * target back asynchronously: the copy is taken now, the comparison runs when the GPU is done (a fenced task at
     * the start of a later frame); frames rendered meanwhile issue nothing.
     */
    private void checkFidelity() {
        if (fidelityInFlight) {
            return;
        }
        fidelityInFlight = true;
        final double guiScale = this.minecraft.getWindow().getGuiScale();
        try {
            Screenshot.takeScreenshot(this.minecraft.getMainRenderTarget(), image -> {
                String diff;
                try (NativeImage img = image) {
                    diff = compare(img, guiScale);
                } catch (RuntimeException e) {
                    diff = "read-back failed: " + e;
                }
                fidelityInFlight = false;
                fidelityAttempt(diff);
            });
        } catch (RuntimeException e) {
            fidelityInFlight = false;
            fidelityAttempt("read-back failed: " + e);
        }
    }

    private void fidelityAttempt(String diff) {
        if (!fidelityCheck) {
            return;
        }
        fidelityAttempts++;
        if (diff == null || fidelityAttempts >= 12) {
            fidelityCheck = false;
            Consumer<String> r = fidelityResult;
            fidelityResult = null;
            if (diff != null) {
                SelfTest.writeScreenshot("fidelity-mismatch-mode" + fidelityMode);
            }
            if (r != null) {
                r.accept(diff == null ? "" : diff);
            }
        }
    }

    /** Null when both columns are pixel-identical, else a description of the first difference. */
    private static String compare(NativeImage img, double guiScale) {
        int scale = (int) Math.round(guiScale);
        int w = img.getWidth();
        int h = img.getHeight();
        int dx = FID_DX * scale;
        int total = 0;
        int yellow = 0;
        String first = null;
        for (int ri = 0; ri < FID_RECTS.length; ri++) {
            int[] r = FID_RECTS[ri];
            int x0 = Math.max(0, r[0] * scale);
            int y0 = Math.max(0, r[1] * scale);
            int x1 = Math.min(w - dx, r[2] * scale);
            int y1 = Math.min(h, r[3] * scale);
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int a = img.getPixel(x, y); // ARGB (1.21.5 NativeImage#getPixel)
                    int b = img.getPixel(x + dx, y);
                    if (((a >> 16) & 0xFF) > 0xC0 && ((a >> 8) & 0xFF) > 0xC0 && (a & 0xFF) < 0x80) {
                        yellow++;
                    }
                    if (a != b) {
                        total++;
                        if (first == null) {
                            first = String.format("first at px (%d,%d) in the %s pair: vanilla=%08X animated=%08X",
                                    x, y, FID_RECT_NAMES[ri], a, b);
                        }
                    }
                }
            }
        }
        if (total == 0 && yellow < 20) {
            // the chat pair shows bold yellow "entity:minecraft": no such ink means the read-back missed the frame
            return "the fidelity area shows no text (" + yellow + " yellow pixels): read-back of the wrong frame?";
        }
        return total == 0 ? null : total + " differing pixels, " + first;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
