package dev.typinganimation.mc;

import dev.typinganimation.core.*;
import dev.typinganimation.core.compat.CommandBlockOverhaulAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

/** Optional rendering adapter; the foreign editor retains layout, formatting, selection and input. */
public final class CommandBlockOverhaulAnimation {
    private final WidgetAnimator animation = new WidgetAnimator(FieldKind.MULTILINE);
    private final FieldAnimationState caret = new FieldAnimationState();
    private final CursorBlinkState blink = new CursorBlinkState();
    private EditBox box;
    private GuiGraphics graphics;
    private Font font;
    private List<String> lines;
    private List<?> formatted;
    private int[] starts;
    private List<String> cachedLines;
    private String cachedValue;
    private int cachedWidth, cachedHeight, clipLeft, clipTop, clipRight, clipBottom;
    private Boolean nativeClip;
    private String cursorLineText;
    private int cursorLinePosition=-1, cursorPrefixWidth;
    private String previous, value, inputBefore;
    private int inputDepth, cursor, cursorX, cursorY, scroll, previousScroll, cellWidth, appendColor;
    private boolean pendingEdit, active, failed, clipOpen, selecting, insert, cursorVisible;
    private static boolean warned;
    private float caretDx, caretDy;
    private long elapsed;
    public long frames, animatedFrames, ghostFrames, caretGlides, verticalCaretGlides;

    public FieldAnimationState state() { return animation.state; }
    public boolean failed() { return failed || animation.failed; }
    public boolean ownsCaret() { return active && !selecting; }
    public void reset() {
        animation.disable(); animation.state.reset(); caret.reset(); blink.reset(); previous = null;
        pendingEdit = false;
    }

    /** Loading another command is assignment even if a user edit is awaiting its first render. */
    public void assignment() { if(inputDepth==0) reset(); }

    public void beforeInput(Object owner) {
        if (failed) return;
        if (inputDepth++ == 0) inputBefore = ((EditBox) owner).getValue();
    }
    public void afterInput(Object owner) {
        if (failed || inputDepth <= 0 || --inputDepth != 0) return;
        String before = inputBefore; inputBefore = null;
        String after = ((EditBox) owner).getValue();
        if (before != null && !before.equals(after)) {
            pendingEdit = true;
            TypingSounds.edited(before, after, FieldKind.MULTILINE, false);
        }
    }

    @SuppressWarnings("unchecked")
    public void begin(Object owner, GuiGraphics g) {
        active = false; graphics = g; box = (EditBox) owner;
        if (failed) return;
        try {
            TypingConfig cfg = ConfigManager.get();
            if (!box.visible || !FieldKind.isAnimated(FieldKind.MULTILINE, cfg)) { reset(); return; }
            font = (Font) CommandBlockOverhaulAccess.field(owner, "font");
            if (font.isBidirectional()) { reset(); return; }
            value = box.getValue();
            scroll = CommandBlockOverhaulAccess.number(owner, "scrolledLines");
            if (previous != null && ((!previous.equals(value) && !pendingEdit)
                    || (previous.equals(value) && previousScroll != scroll))) reset();
            pendingEdit = false;
            lines = (List<String>) CommandBlockOverhaulAccess.field(owner, "lines");
            formatted = (List<?>) CommandBlockOverhaulAccess.field(owner, "formattedLines");
            if (cachedLines == null || !value.equals(cachedValue) || !cachedLines.equals(lines)
                    || cachedWidth != box.getWidth() || cachedHeight != box.getHeight()) {
                starts = CommandBlockOverhaulAccess.lineStarts(value, lines);
                cachedLines = List.copyOf(lines); cachedValue = value;
                cachedWidth = box.getWidth(); cachedHeight = box.getHeight();
            }
            int height = CommandBlockOverhaulAccess.number(owner, "lineHeight");
            int padding = (Boolean) CommandBlockOverhaulAccess.field(owner, "bordered") ? 4 : 0;
            long now = net.minecraft.Util.getMillis();
            animation.font = font; animation.shadow = true; animation.lastValue = value;
            boolean continuous = animation.touch(now);
            animation.state.beginFrame(now, box.getX(), box.getY() - scroll * height, scroll);
            animation.state.sync(value, continuous && animation.animate(cfg, box.isFocused()), cfg);
            animation.idle = animation.state.isIdle(cfg) && !TypingRenderer.forcePerChar;
            animation.active = true;
            if (nativeClip == null) {
                try { owner.getClass().getMethod("applyUserEdit",String.class,int.class); nativeClip = true; }
                catch (NoSuchMethodException absent) { nativeClip = false; }
            }
            int inset = nativeClip || padding > 0 ? 1 : 0;
            clipLeft=box.getX()+inset; clipTop=box.getY()+inset;
            clipRight=box.getX()+box.getWidth()-inset; clipBottom=box.getY()+box.getHeight()-inset;
            animation.setClip(clipLeft,clipTop,clipRight,clipBottom);
            blink.observe(value, now);
            cursor = box.getCursorPosition();
            selecting = CommandBlockOverhaulAccess.number(owner, "highlightPos") != cursor;
            insert = cursor < value.length() || value.length() >= CommandBlockOverhaulAccess.number(owner, "maxLength");
            int row = CommandBlockOverhaulAccess.number(owner, "cursorLine");
            int inRow = CommandBlockOverhaulAccess.number(owner, "cursorIndexInLine");
            int visible = CommandBlockOverhaulAccess.number(owner, "visibleLines");
            cursorVisible = box.isFocused() && row >= scroll && row < scroll+visible && row < lines.size();
            if (row < 0 || row >= lines.size()) throw new IllegalArgumentException("Invalid command editor cursor row");
            List<Integer> indents = (List<Integer>) CommandBlockOverhaulAccess.field(owner, "indentLevels");
            String rowText=lines.get(row);
            int rowPosition=Math.min(inRow,rowText.length());
            if(rowPosition!=cursorLinePosition || !rowText.equals(cursorLineText)) {
                cursorPrefixWidth=font.width(rowText.substring(0,rowPosition));
                cursorLineText=rowText;cursorLinePosition=rowPosition;
            }
            cursorX = box.getX()+padding + indents.get(row)*CommandBlockOverhaulAccess.number(owner,"indentation")*font.width(" ")
                    + cursorPrefixWidth;
            cursorY = box.getY()+padding+(row-scroll)*height;
            caret.beginFrame(now, box.getX(), box.getY()-scroll*height, scroll);
            caret.sync("", !selecting && (!cfg.onlyWhenFocused || box.isFocused()), cfg);
            TypingConfig caretConfig = selecting || (cfg.onlyWhenFocused && !box.isFocused()) ? SNAP : cfg;
            caretDx = caret.caretX(cursorX, caretConfig)-cursorX;
            caretDy = caret.caretY(cursorY, caretConfig)-cursorY;
            if (caretDx != 0 || caretDy != 0) caretGlides++;
            if (caretDy != 0) verticalCaretGlides++;
            cellWidth = animation.cursorCellWidth(cursor);
            appendColor = ((Number) CommandBlockOverhaulAccess.accessor(owner,
                    (Boolean) CommandBlockOverhaulAccess.accessor(owner,"getIsEditable") ? "getTextColor" : "getTextColorUneditable")).intValue();
            elapsed = Math.max(0,now-((Number)CommandBlockOverhaulAccess.accessor(owner,"getFocusedTime")).longValue());
            previous = value; previousScroll = scroll;
            active = true; frames++;
            if (!animation.state.isIdle(cfg)) animatedFrames++;
        } catch (ReflectiveOperationException | RuntimeException failure) { fail(failure); }
    }
    private static final TypingConfig SNAP = snap();
    private static TypingConfig snap() { TypingConfig cfg=TypingConfig.defaults();cfg.smoothCursor=false;return cfg; }

    public boolean line(GuiGraphics g, String text, int x, int y, int color, int index) {
        if (!active) return false;
        try {
            if (index < 0 || index >= starts.length || !lines.get(index).equals(text)) return false;
            if (index < formatted.size() && formatted.get(index) instanceof FormattedCharSequence sequence)
                TypingRenderer.drawSequence(g,font,sequence,x,y,color,true,animation,starts[index],text.length());
            else TypingRenderer.drawPlain(g,font,text,x,y,color,true,animation,starts[index]);
            return true;
        } catch (RuntimeException failure) { fail(failure); return false; }
    }

    public void end(Object owner) {
        try {
            if (active) {
                graphics.enableScissor(clipLeft,clipTop,clipRight,clipBottom); clipOpen=true;
                if (animation.state.ghostCount()>0) ghostFrames++;
                TypingRenderer.drawGhosts(graphics,animation);
                if (ownsCaret() && cursorVisible) {
                    TypingConfig cfg = ConfigManager.get();
                    boolean visible = blink.visible(elapsed/300%2==0,elapsed,net.minecraft.Util.getMillis(),cfg,true);
                    if (visible) {
                        int x=Math.round(cursorX+caretDx),y=Math.round(cursorY+caretDy);
                        int color=blink.tint(insert ? -3092272 : appendColor);
                        if (!CursorAppearance.draw(graphics,cfg,x,y-1,11,cellWidth,color,insert)) {
                            if (insert) graphics.fill(RenderType.guiOverlay(),x,y-1,x+1,y+10,CursorAppearance.color(cfg,color));
                            else graphics.drawString(font,"_",x,y,CursorAppearance.color(cfg,color));
                        }
                    }
                }
                TypingRenderer.endFrame(animation,owner); caret.endFrame();
            }
        } catch (RuntimeException failure) { fail(failure); }
        finally { if(clipOpen) {graphics.disableScissor();clipOpen=false;} active=false; }
    }
    private void fail(Throwable failure) {
        if(!warned) {
            warned=true;
            TypingAnimationMod.LOGGER.warn("[typinganimation] CommandBlockUIOverhaul text API changed; retaining its native rendering",failure);
        }
        failed=true;active=false;
    }
}
