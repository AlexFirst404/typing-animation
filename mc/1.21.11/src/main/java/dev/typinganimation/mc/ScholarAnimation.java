package dev.typinganimation.mc;

import dev.typinganimation.core.*;
import dev.typinganimation.core.compat.ScholarAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Optional adapter for Scholar's independently rendered, formatted book text boxes. */
public final class ScholarAnimation {
    private final WidgetAnimation animation = new WidgetAnimation(FieldKind.MULTILINE);
    private final FieldAnimationState caretState = new FieldAnimationState();
    private final CursorBlinkState blink = new CursorBlinkState();
    private GuiGraphics graphics;
    private AbstractWidget widget;
    private Font font;
    private Object editor;
    private String value = "", previous, inputBefore;
    private final List<Line> lines = new ArrayList<>();
    private boolean pendingEdit, active, failed;
    private int cursor, cursorX, cursorY, cellWidth;
    private boolean selecting, atEnd, overflowCursor;
    private float caretDx, caretDy;
    private long elapsed;
    public long frames, animatedFrames, ghostFrames, caretGlides, verticalCaretGlides;

    private record Line(String rendered, String plain, int start, int x, int y, int width) { }
    public FieldAnimationState state() { return animation.state; }
    public boolean failed() { return failed || animation.failed; }

    public void reset() {
        animation.resetAll();
        caretState.reset(); blink.reset(); previous = null; pendingEdit = false; active = false;
        inputBefore = null;
    }

    /** A page turn is navigation, even when both pages happen to contain identical text. */
    public static void resetPages(Object screen) {
        try {
            for (Class<?> type = screen.getClass(); type != null && type.getName().startsWith("io.github.mortuusars.scholar."); type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Object candidate = field.get(screen);
                    if (candidate instanceof ScholarStateHolder holder) holder.typinganimation$scholarAnimation().reset();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            TypingAnimationMod.LOGGER.warn("[typinganimation] Could not reset Scholar page animations", failure);
        }
    }

    public void beforeInput(Object owner) {
        try { inputBefore = ScholarAccess.plain(owner); }
        catch (ReflectiveOperationException | RuntimeException failure) { fail(failure); }
    }

    public void afterInput(Object owner, boolean accepted, int key, boolean typed) {
        String before = inputBefore; inputBefore = null;
        if (!accepted || before == null || failed) return;
        // Accepted TextBox input plus a real plain-text change is independent of GLFW/SDL key codes.
        try {
            String after = ScholarAccess.plain(owner);
            if (!before.equals(after)) {
                pendingEdit = true;
                TypingSounds.edited(before, after, FieldKind.MULTILINE, false);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) { fail(failure); }
    }

    public void begin(Object owner, GuiGraphics g) {
        active = false; graphics = g;
        if (failed) return;
        try {
            widget = (AbstractWidget) owner;
            TypingConfig cfg = ConfigManager.get();
            if (!FieldKind.isAnimated(FieldKind.MULTILINE, cfg)) { reset(); return; }
            font = (Font) ScholarAccess.get(owner, "getFont");
            if (font.isBidirectional()) { reset(); return; }
            editor = ScholarAccess.get(owner, "getEditor");
            value = ScholarAccess.plain(owner);
            if (previous != null && !previous.equals(value) && !pendingEdit) reset();
            boolean edit = pendingEdit; pendingEdit = false;
            long now = net.minecraft.util.Util.getMillis();
            if (previous == null) animation.resetAll();
            if (edit) animation.lastValue = null;
            TypingRenderer.beginMultiline(animation, owner, g, widget.getX(), widget.getY(), 0, value, widget.isFocused());
            animation.setClip(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight());
            blink.observe(value, now);
            previous = value;
            lines.clear();
            Object display = ScholarAccess.get(owner, "getDisplayCache");
            for (Object line : (List<?>) ScholarAccess.get(display, "getLines")) {
                String plain = (String) ScholarAccess.get(ScholarAccess.get(line, "getString"), "toStringWithoutFormatting");
                plain = plain.replace("\n", "");
                int start = ScholarAccess.utf16Index(value, ScholarAccess.number(line, "firstCharIndex"));
                lines.add(new Line((String) ScholarAccess.get(line, "renderedString"), plain, start,
                        widget.getX() + ScholarAccess.number(line, "x"), widget.getY() + ScholarAccess.number(line, "y"),
                        ScholarAccess.number(line, "width")));
            }
            cursor = ScholarAccess.utf16Index(value, ScholarAccess.number(editor, "getCursorPos"));
            selecting = ScholarAccess.flag(editor, "isSelecting");
            atEnd = ScholarAccess.flag(editor, "isCursorAtEnd");
            Object nativeCursor = ScholarAccess.get(display, "getCursor");
            // Pos2i's coordinates are Scholar-owned public fields, not mapped Minecraft names.
            int localX = nativeCursor.getClass().getField("x").getInt(nativeCursor);
            int localY = nativeCursor.getClass().getField("y").getInt(nativeCursor);
            cursorX = widget.getX() + localX; cursorY = widget.getY() + localY;
            overflowCursor = atEnd && localY + font.lineHeight > widget.getHeight() && !lines.isEmpty();
            if (overflowCursor) { Line last = lines.get(lines.size()-1); cursorX = last.x + last.width; cursorY = last.y; }
            caretState.beginFrame(now, widget.getX(), widget.getY(), 0);
            caretState.sync("", !selecting && (!cfg.onlyWhenFocused || widget.isFocused()), cfg);
            TypingConfig caretCfg = selecting || (cfg.onlyWhenFocused && !widget.isFocused()) ? SNAP : cfg;
            caretDx = caretState.caretX(cursorX, caretCfg) - cursorX;
            caretDy = caretState.caretY(cursorY, caretCfg) - cursorY;
            if (caretDx != 0 || caretDy != 0) caretGlides++;
            if (caretDy != 0) verticalCaretGlides++;
            cellWidth = cursor >= value.length() ? Math.max(1, font.width("_"))
                    : Math.max(1, font.width(value.substring(cursor, cursor + Character.charCount(value.codePointAt(cursor)))));
            elapsed = Math.max(0, System.currentTimeMillis() - ScholarAccess.lastActionTime(owner));
            active = true; frames++;
            if (!animation.state.isIdle(cfg)) animatedFrames++;
        } catch (ReflectiveOperationException | RuntimeException failure) { fail(failure); }
    }

    private static final TypingConfig SNAP = snapConfig();
    private static TypingConfig snapConfig() { TypingConfig c = TypingConfig.defaults(); c.smoothCursor=false; return c; }

    /** Keeps Scholar's styles, line alignment and wrapping; only glyph rendering is replaced. */
    public String line(Font f, String rendered, int x, int y, int textColor, boolean shadow) {
        if (!active || rendered == null || rendered.isEmpty()) return rendered;
        try {
            for (Line line : lines) {
                if (line.x == x && line.y == y && line.rendered.equals(rendered) && value.startsWith(line.plain, line.start)) {
                    FormattedCharSequence sequence = sink -> StringDecomposer.iterateFormatted(rendered, Style.EMPTY, sink);
                    return TypingRenderer.formattedMultilineSegment(animation, widget, f, sequence, line.plain, line.start, x, y, textColor, shadow) ? "" : rendered;
                }
            }
        } catch (RuntimeException failure) { fail(failure); }
        return rendered;
    }

    /** Native focus/selection and overflow-caret semantics are retained, including Scholar's '<' marker. */
    public boolean caret(Object owner, int color) {
        if (!active || graphics == null || !widget.isFocused() || selecting) return false;
        try {
            TypingConfig cfg = ConfigManager.get();
            boolean visible = blink.visible(elapsed <= 200 || elapsed % 600 <= 300, elapsed, net.minecraft.util.Util.getMillis(), cfg, true);
            if (!visible) return true;
            int x = Math.round(cursorX + caretDx), y = Math.round(cursorY + caretDy);
            int tinted = blink.tint(color);
            if (!CursorAppearance.draw(graphics, cfg, x, y-1, font.lineHeight+1, cellWidth, tinted, !atEnd)) {
                int resolved = CursorAppearance.color(cfg, tinted);
                if (atEnd) graphics.drawString(font, overflowCursor ? "<" : "_", x, y, resolved, false);
                else graphics.fill(x, y-1, x+1, y+font.lineHeight, resolved);
            }
            return true;
        } catch (RuntimeException failure) { fail(failure); return false; }
    }

    public void end(Object owner) {
        if (!active) return;
        try {
            if (animation.state.ghostCount() > 0) ghostFrames++;
            TypingRenderer.end(animation, owner, font, false);
            caretState.endFrame();
        } catch (RuntimeException failure) { fail(failure); }
        // renderCursor follows renderLines; keep this frame's graphics only until the next begin.
    }

    private void fail(Throwable failure) {
        if (!failed) TypingAnimationMod.LOGGER.warn("[typinganimation] Scholar text API changed; retaining native book rendering", failure);
        failed = true; active = false;
    }
}
