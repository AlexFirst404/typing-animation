package dev.typinganimation.mc;

import dev.typinganimation.core.*;
import dev.typinganimation.core.compat.BetterCommandBlockAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

/** Adapts the mod's real formatted lines; its parser, indentation, selection and scrolling stay native. */
public final class BetterCommandAnimation {
    private static boolean warned;
    private final WidgetAnimation animation = new WidgetAnimation(FieldKind.MULTILINE);
    private final FieldAnimationState caret = new FieldAnimationState();
    private final CursorBlinkState blink = new CursorBlinkState();
    private GuiGraphics graphics;
    private EditBox widget;
    private Font font;
    private String value = "", previous, inputBefore;
    private List<?> lines, indentation, offsets;
    private int horizontal, scroll, visibleLines, previousHorizontal = -1, previousScroll = -1;
    private int inputDepth, lineStart, virtualIndent, segmentOffset;
    private boolean pendingEdit, active, failed, lineActive, selecting, cursorVisible, atEnd;
    private int cursorX, cursorY, cellWidth;
    private float caretDx, caretDy;
    private long elapsed;
    public long frames, animatedFrames, ghostFrames, caretGlides, verticalCaretGlides, segments;

    public FieldAnimationState state() { return animation.state; }
    public boolean failed() { return failed || animation.failed; }
    public void reset() {
        animation.resetAll(); caret.reset(); blink.reset(); previous = null;
        pendingEdit = false; active = false; lineActive = false;
    }
    public void assignment() { if (inputDepth == 0) reset(); }
    public void beforeInput(Object owner) {
        if (inputDepth++ == 0) inputBefore = ((EditBox) owner).getValue();
    }
    public void afterInput(Object owner, boolean accepted) {
        if (inputDepth <= 0 || --inputDepth != 0) return;
        String before = inputBefore; inputBefore = null;
        String after = ((EditBox) owner).getValue();
        if (accepted && before != null && !before.equals(after)) {
            pendingEdit = true;
            TypingSounds.edited(before, after, FieldKind.MULTILINE, false);
        }
    }

    public void begin(Object owner, GuiGraphics g) {
        active = false; lineActive = false; graphics = g;
        if (failed) return;
        try {
            widget = (EditBox) owner;
            TypingConfig cfg = ConfigManager.get();
            if (!widget.isVisible() || !FieldKind.isAnimated(FieldKind.MULTILINE, cfg)) { reset(); return; }
            font = (Font) BetterCommandBlockAccess.accessor(owner, "getFont", "getTextRenderer");
            if (font.isBidirectional()) { reset(); return; }
            value = widget.getValue();
            horizontal = BetterCommandBlockAccess.number(owner, "horizontalOffset");
            scroll = BetterCommandBlockAccess.number(owner, "scrolledLines");
            visibleLines = BetterCommandBlockAccess.number(owner, "visibleLines");
            lines = (List<?>) BetterCommandBlockAccess.field(owner, "lines");
            indentation = (List<?>) BetterCommandBlockAccess.field(owner, "lineOffsets");
            offsets = (List<?>) BetterCommandBlockAccess.field(owner, "textOffsets");
            if (previous != null && !pendingEdit && (!previous.equals(value)
                    || previousHorizontal != horizontal || previousScroll != scroll)) reset();
            boolean edit = pendingEdit; pendingEdit = false;
            if (previous == null) animation.resetAll();
            if (edit) animation.lastValue = null;
            long now = net.minecraft.Util.getMillis();
            TypingRenderer.beginMultiline(animation, owner, g, widget.getX()+5, widget.getY()+5, scroll, value, widget.isFocused());
            animation.setClip(widget.getX()+1, widget.getY()+1, widget.getX()+widget.getWidth()-1, widget.getY()+widget.getHeight()-1);
            blink.observe(value, now);
            previous = value; previousHorizontal = horizontal; previousScroll = scroll;
            int cursor = widget.getCursorPosition();
            int highlight = ((Number) BetterCommandBlockAccess.accessor(owner, "getHighlightPos", "getSelectionEnd")).intValue();
            selecting = cursor != highlight;
            cursorVisible = false;
            if (BetterCommandBlockAccess.flag(owner, "hasCommandSuggestor") && !lines.isEmpty()) {
                for (int i=0;i<lines.size();i++) {
                    String line = (String) lines.get(i);
                    int indent = ((Number) indentation.get(i)).intValue();
                    int start = ((Number) offsets.get(i)).intValue();
                    if (start + line.length()-indent < cursor && i+1<lines.size()) continue;
                    int column = cursor-start+indent;
                    int from = Math.min(horizontal,line.length());
                    int to = Math.max(from,Math.min(column,line.length()));
                    cursorX = widget.getX()+5+font.width(line.substring(from,to));
                    cursorY = widget.getY()+5+10*(i-scroll);
                    cursorVisible = column>=horizontal && i>=scroll && i<scroll+visibleLines
                            && cursorX<=widget.getX()+5+widget.getInnerWidth();
                    break;
                }
            }
            atEnd = cursor>=value.length();
            caret.beginFrame(now,widget.getX(),widget.getY(),0);
            caret.sync("",widget.isFocused()&&!selecting,cfg);
            TypingConfig cursorConfig = selecting || !widget.isFocused() ? SNAP : cfg;
            caretDx = caret.caretX(cursorX,cursorConfig)-cursorX;
            caretDy = caret.caretY(cursorY,cursorConfig)-cursorY;
            if (caretDx!=0 || caretDy!=0) caretGlides++;
            if (caretDy!=0) verticalCaretGlides++;
            cellWidth = atEnd ? Math.max(1,font.width("_"))
                    : Math.max(1,font.width(value.substring(cursor,cursor+Character.charCount(value.codePointAt(cursor)))));
            elapsed = Math.max(0,net.minecraft.Util.getMillis()
                    -((Number) BetterCommandBlockAccess.accessor(owner,"getFocusedTime","getLastSwitchFocusTime")).longValue());
            active = true; frames++;
            if (!animation.state.isIdle(cfg)) animatedFrames++;
        } catch (ReflectiveOperationException | RuntimeException failure) { fail(failure); }
    }

    private static final TypingConfig SNAP = snap();
    private static TypingConfig snap() { TypingConfig c=TypingConfig.defaults();c.smoothCursor=false;return c; }

    public void coloredLine(int index) {
        lineActive = false;
        if (!active) return;
        try {
            int indent=((Number)indentation.get(index)).intValue();
            lineStart=BetterCommandBlockAccess.sourceIndex(((Number)offsets.get(index)).intValue(),indent,horizontal,0);
            virtualIndent=Math.max(0,indent-horizontal);segmentOffset=0;lineActive=true;
        } catch (RuntimeException failure) { fail(failure); }
    }
    public void rawLine() {
        lineStart=Math.max(0,Math.min(horizontal,value.length()-1));virtualIndent=0;segmentOffset=0;lineActive=active;
    }
    public String segment(Font f,String text,int x,int y,int color) {
        if (!active || !lineActive || text==null || text.isEmpty()) return text;
        int position=segmentOffset;segmentOffset+=text.length();
        // Native String drawing interprets legacy formatting codes; those chunks retain its parser.
        if (text.indexOf('\u00a7')>=0) return text;
        int prefix=Math.max(0,Math.min(text.length(),virtualIndent-position));
        String plain=text.substring(prefix);
        int start=lineStart+position+prefix;
        if (plain.isEmpty() || start<0 || !value.startsWith(plain,start)) return text;
        try {
            FormattedCharSequence sequence=FormattedCharSequence.forward(plain,Style.EMPTY);
            segments++;
            return TypingRenderer.formattedMultilineSegment(animation,widget,f,sequence,plain,start,
                    x+f.width(text.substring(0,prefix)),y,color,true) ? "" : text;
        } catch (RuntimeException failure) { fail(failure);return text; }
    }
    private boolean ownsCursor() { return active && widget.isFocused() && !selecting && cursorVisible; }
    public String nativeCursor(String text) { return ownsCursor() && "_".equals(text) ? "" : text; }
    public int nativeFill(int x0,int y0,int x1,int y1,int color) {
        return ownsCursor() && color==-3092272 && x1-x0==1 && y1-y0==font.lineHeight+2 ? 0 : color;
    }
    public void end(Object owner) {
        if (!active) return;
        try {
            if (animation.state.ghostCount()>0) ghostFrames++;
            TypingRenderer.end(animation,owner,font,true);
            if (ownsCursor() && blink.visible(elapsed/300%2==0,elapsed,net.minecraft.Util.getMillis(),ConfigManager.get(),true)) {
                TypingConfig cfg=ConfigManager.get();
                int x=Math.round(cursorX+caretDx),y=Math.round(cursorY+caretDy),color=blink.tint(-3092272);
                graphics.enableScissor(widget.getX()+1,widget.getY()+1,widget.getX()+widget.getWidth()-1,widget.getY()+widget.getHeight()-1);
                try {
                    if (!CursorAppearance.draw(graphics,cfg,x,y-1,font.lineHeight+2,cellWidth,color,!atEnd)) {
                        int resolved=CursorAppearance.color(cfg,color);
                        if (atEnd) graphics.drawString(font,"_",x,y,resolved);
                        else graphics.fill(x,y-1,x+1,y+font.lineHeight+1,resolved);
                    }
                } finally { graphics.disableScissor(); }
            }
            caret.endFrame();
        } catch (RuntimeException failure) { fail(failure); }
        active=false;lineActive=false;
    }
    private void fail(Throwable failure) {
        if (!warned) {
            warned=true;
            TypingAnimationMod.LOGGER.warn("[typinganimation] Better Command Block UI API changed; retaining native editor rendering",failure);
        }
        failed=true;active=false;lineActive=false;
    }
}
