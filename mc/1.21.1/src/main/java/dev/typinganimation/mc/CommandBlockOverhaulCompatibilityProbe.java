package dev.typinganimation.mc;

import com.mojang.blaze3d.platform.NativeImage;
import dev.typinganimation.core.*;
import dev.typinganimation.core.compat.CommandBlockOverhaulAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.Arrays;
import java.util.List;

/** Opt-in checks against the real optional editor, without a world or command submission. */
public final class CommandBlockOverhaulCompatibilityProbe {
    private static Minecraft client;
    private static Screen previousScreen;
    private static TypingConfig previousConfig;
    private static boolean previousPerChar;
    private static ProbeScreen screen;
    private static Class<?> editorClass;
    private static int stage;
    private static long nextFrame, animationMark, glyphMark, caretMark, verticalMark, ghostMark, soundMark;
    private static int[] renderedPixels;
    private static String otherValue;

    private CommandBlockOverhaulCompatibilityProbe() { }
    public static boolean available() {
        return CommandBlockOverhaulCompatibilityProbe.class.getClassLoader().getResource(
                CommandBlockOverhaulAccess.WIDGET.replace('.','/')+".class") != null;
    }
    public static void prepare() throws Exception {
        require(available() && previousConfig==null,"CommandBlockUIOverhaul fixture cannot start");
        client=Minecraft.getInstance();previousScreen=client.screen;previousConfig=ConfigManager.get().copy();
        previousPerChar=TypingRenderer.forcePerChar;
        TypingConfig cfg=ConfigManager.get();cfg.copyFrom(TypingConfig.defaults());
        cfg.enabled=true;cfg.animateMultiline=true;cfg.onlyWhenFocused=true;
        cfg.appearStyle=AppearStyle.POP;cfg.removeStyle=RemoveStyle.FALL;
        cfg.durationMs=1000;cfg.removeDurationMs=1000;cfg.cursorGlideMs=300;cfg.smoothCursor=true;
        cfg.typingSounds=true;cfg.soundVolume=.1f;cfg.cursorKeepVisibleWhileTyping=true;
        editorClass=Class.forName(CommandBlockOverhaulAccess.WIDGET);
        screen=new ProbeScreen();client.setScreen(screen);stage=0;nextFrame=3;
        TypingSounds.resetTestCounters();otherValue=screen.other.getValue();
    }
    public static boolean tick() throws Exception {
        require(previousConfig!=null && client.screen==screen,"CommandBlockUIOverhaul fixture screen changed");
        if(screen.frames<nextFrame)return false;
        CommandBlockOverhaulAnimation a=animation(screen.box);
        require(!a.failed() && !animation(screen.other).failed(),"CommandBlockUIOverhaul adapter fell back");
        require(otherValue.equals(screen.other.getValue()),"Editing one command changed the independent editor");
        switch(stage++) {
            case 0 -> {
                require(a.frames>=3 && animation(screen.other).frames>=3,"Command editor render hooks did not run");
                require(a.state().isIdle(ConfigManager.get()) && TypingSounds.playedCount==0,"Opening command text animated or made sound");
                require(((List<?>)CommandBlockOverhaulAccess.field(screen.box,"lines")).size()>1,"Command fixture did not wrap");
                animationMark=a.animatedFrames;glyphMark=glyphs();
                require(screen.box.charTyped('!',0),"Command editor rejected typing");
                require(TypingSounds.playedCount==1,"Accepted command typing must produce one sound");
            }
            case 1 -> {
                require(a.animatedFrames>animationMark && glyphs()>glyphMark,"Formatted command glyphs did not animate");
                require(animation(screen.other).state().isIdle(ConfigManager.get()),"Typing animated an independent editor");
                caretMark=a.caretGlides;soundMark=TypingSounds.playedCount;
                screen.box.keyPressed(263,0,0);
            }
            case 2 -> {
                require(a.caretGlides>caretMark && TypingSounds.playedCount==soundMark,"Command caret did not glide silently");
                verticalMark=a.verticalCaretGlides;screen.box.setCursorPosition(1);screen.box.setHighlightPos(1);
            }
            case 3 -> {
                require(a.verticalCaretGlides>verticalMark,"Command caret did not glide across wrapped rows");
                require(TypingSounds.playedCount==soundMark,"Moving the command caret made sound");
                ghostMark=a.ghostFrames;screen.box.keyPressed(259,0,0);
                require(TypingSounds.playedCount==soundMark+1,"Accepted command deletion must produce one sound");
            }
            case 4 -> {
                require(a.ghostFrames>ghostMark && a.state().ghostCount()>0,"Command deletion did not render removal effects");
                soundMark=TypingSounds.playedCount;screen.box.insertText("Paste \uD83D\uDE03 ");
                require(screen.box.getValue().contains("Paste \uD83D\uDE03 "),"Command paste/Unicode contents changed");
                require(TypingSounds.playedCount==soundMark+1,"Bulk command input must produce one sound");
            }
            case 5 -> {
                glyphMark=glyphs();soundMark=TypingSounds.playedCount;animationMark=a.frames;
                ConfigManager.get().animateMultiline=false;
                require(screen.box.charTyped('X',0),"Disabling effects changed command input");
            }
            case 6 -> {
                require(a.frames==animationMark && glyphs()==glyphMark && TypingSounds.playedCount==soundMark,
                        "Disabled multiline command input still animated or made sound");
                ConfigManager.get().animateMultiline=true;
                require(screen.box.charTyped('Y',0),"Command edit before assignment was rejected");
                require(TypingSounds.playedCount==soundMark+1,"Accepted pending command edit did not make one sound");
                soundMark=TypingSounds.playedCount;
                // A responder/server update can replace the text before the typed glyph is first rendered.
                screen.box.setValue("Replacement command");
            }
            case 7 -> {
                require(a.state().isIdle(ConfigManager.get()) && a.state().ghostCount()==0,"Replacing/re-enabling command contents replayed edits");
                require(TypingSounds.playedCount==soundMark,"Programmatic command assignment made sound");
                screen.box.setEditable(false);String before=screen.box.getValue();
                require(!screen.box.charTyped('?',0) && before.equals(screen.box.getValue()),"Read-only command field accepted input");
                require(TypingSounds.playedCount==soundMark,"Rejected command input made sound");
                screen.box.setEditable(true);screen.box.setHighlightPos(0);
            }
            case 8 -> {
                require(screen.box.getHighlighted().equals(screen.box.getValue()) && CommandBlockOverhaulAccess.number(screen.box,"highlightPos")!=screen.box.getCursorPosition(),"Command selection semantics changed");
                screen.box.setHighlightPos(screen.box.getCursorPosition());
                screen.box.setFocused(false);screen.setFocused(null);
                ConfigManager.get().onlyWhenFocused=false;TypingRenderer.forcePerChar=true;
                screen.box.setValue("execute as @a run say RED \uD83D\uDE03");
            }
            case 9 -> { renderedPixels=pixels();ConfigManager.get().enabled=false; }
            case 10 -> {
                samePixels(renderedPixels,pixels(),"bordered formatted command editor");
                ConfigManager.get().enabled=true;screen.box.setBordered(false);
                screen.box.setValue("Unbordered RED \uD83D\uDE03 text");
            }
            case 11 -> { renderedPixels=pixels();ConfigManager.get().enabled=false; }
            case 12 -> {
                samePixels(renderedPixels,pixels(),"unbordered formatted command editor");
                ConfigManager.get().enabled=true;TypingRenderer.forcePerChar=false;
                screen.box.setBordered(true);screen.box.setFocused(true);screen.setFocused(screen.box);
                screen.box.setValue("say "+"word ".repeat(70));
            }
            case 13 -> {
                require(a.state().isIdle(ConfigManager.get()),"Replacing a long command animated its initial contents");
                soundMark=TypingSounds.playedCount;
                screen.box.mouseScrolled(screen.box.getX()+10,screen.box.getY()+10,0,-1);
            }
            case 14 -> {
                require(a.state().isIdle(ConfigManager.get()) && a.state().ghostCount()==0,"Scrolling replayed command edits");
                require(TypingSounds.playedCount==soundMark,"Scrolling command text made sound");
                ConfigManager.get().cursorKeepVisibleWhileTyping=false;
                ConfigManager.get().smoothCursor=false;TypingRenderer.forcePerChar=true;
                screen.box.setValue("native caret text");screen.other.setValue("native caret text");
                otherValue=screen.other.getValue();screen.box.setBordered(true);screen.other.setBordered(true);
                screen.pixelMode=true;
            }
            case 15 -> {
                samePixels(pixels(),pixels(screen.other),"bordered native append cursor");
                screen.box.setCursorPosition(2);screen.box.setHighlightPos(2);screen.other.setCursorPosition(2);screen.other.setHighlightPos(2);
            }
            case 16 -> {
                require(CommandBlockOverhaulAccess.number(screen.box,"highlightPos")==screen.box.getCursorPosition(),"Native insert cursor fixture unexpectedly has a selection");
                samePixels(pixels(),pixels(screen.other),"bordered native insert cursor");
                screen.box.setBordered(false);screen.other.setBordered(false);
                screen.box.setCursorPosition(screen.box.getValue().length());screen.box.setHighlightPos(screen.box.getCursorPosition());
                screen.other.setCursorPosition(screen.other.getValue().length());screen.other.setHighlightPos(screen.other.getCursorPosition());
            }
            case 17 -> {
                samePixels(pixels(),pixels(screen.other),"unbordered native append cursor");
                screen.box.setCursorPosition(2);screen.box.setHighlightPos(2);screen.other.setCursorPosition(2);screen.other.setHighlightPos(2);
            }
            case 18 -> {
                require(CommandBlockOverhaulAccess.number(screen.box,"highlightPos")==screen.box.getCursorPosition(),"Unbordered insert cursor fixture unexpectedly has a selection");
                samePixels(pixels(),pixels(screen.other),"unbordered native insert cursor");
                screen.pixelMode=false;TypingRenderer.forcePerChar=false;
                TypingAnimationMod.LOGGER.info("[typinganimation] CommandBlockUIOverhaul compatibility: formatted glyphs, wrapping, cursor, independent editors, deletion, Unicode input, selection, clipping, scroll, field gates and native pixels passed");
                return true;
            }
            default -> throw new AssertionError("Command editor fixture advanced after completion");
        }
        nextFrame=screen.frames+3;return false;
    }
    public static void cleanup() {
        if(previousConfig!=null)ConfigManager.get().copyFrom(previousConfig);
        TypingRenderer.forcePerChar=previousPerChar;
        if(client!=null && previousScreen!=null)client.setScreen(previousScreen);
        previousScreen=null;previousConfig=null;screen=null;renderedPixels=null;
    }
    public static void abort() {cleanup();}
    private static CommandBlockOverhaulAnimation animation(EditBox editor) {
        require(editor instanceof CommandBlockOverhaulStateHolder,"Command editor optional mixin was not applied");
        return ((CommandBlockOverhaulStateHolder)editor).typinganimation$commandBlockAnimation();
    }
    private static long glyphs() {return TypingRenderer.STATS.transformedGlyphs+TypingRenderer.STATS.plainGlyphs;}
    private static int[] pixels() {return pixels(screen.box);}
    private static int[] pixels(EditBox target) {
        try(NativeImage image=Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            double scale=client.getWindow().getGuiScale();
            int left=(int)((target.getX()-1)*scale),top=(int)((target.getY()-1)*scale);
            int width=(int)((target.getWidth()+2)*scale),height=(int)((target.getHeight()+2)*scale);
            int[] pixels=new int[width*height];
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)pixels[y*width+x]=image.getPixelRGBA(left+x,top+y);
            return pixels;
        }
    }
    private static void samePixels(int[] animated,int[] nativePixels,String label) {
        require(Arrays.equals(animated,nativePixels),"Native pixel mismatch in "+label);
        require(Arrays.stream(nativePixels).distinct().limit(5).count()>=5,"Command pixel fixture was empty");
        TypingAnimationMod.LOGGER.info("[typinganimation] CommandBlockUIOverhaul native pixels identical: {}",label);
    }
    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    private static FormattedCharSequence styled(String text,int ignored) {
        return sink->{for(int i=0;i<text.length();) {int cp=text.codePointAt(i);
            Style style=Style.EMPTY.withColor(i%2==0?0xFF7777:0x77BBFF);
            if(!sink.accept(i,style,cp))return false;i+=Character.charCount(cp);}return true;};
    }
    private static final class ProbeScreen extends Screen {
        EditBox box,other;long frames;boolean pixelMode;
        ProbeScreen(){super(Component.literal("CommandBlockUIOverhaul compatibility self-test"));}
        @Override protected void init() {
            try {
                int w=Math.max(100,(width-56)/2),h=Math.max(80,Math.min(180,height-60));
                box=make(20,30,w,h,"summon pig ~ ~ ~ {CustomName:'Hi \uD83D\uDE03',NoAI:1b}");
                other=make(36+w,30,w,h,"Independent editor");
                addRenderableWidget(box);addRenderableWidget(other);box.setFocused(true);setFocused(box);
            }catch(Exception failure){throw new IllegalStateException("Command editor fixture initialization",failure);}
        }
        private EditBox make(int x,int y,int w,int h,String value)throws Exception {
            EditBox editor=(EditBox)editorClass.getConstructor(Font.class,int.class,int.class,int.class,int.class,Component.class)
                    .newInstance(font,x,y,w,h,Component.literal("Command"));
            editor.setMaxLength(32767);editor.setFormatter(CommandBlockOverhaulCompatibilityProbe::styled);editor.setValue(value);
            return editor;
        }
        @Override public void render(GuiGraphics g,int x,int y,float partialTick) {
            frames++;g.fill(0,0,width,height,0xFF20242C);
            if(!pixelMode){super.render(g,x,y,partialTick);return;}
            // Both native clocks start in their visible phase for a same-frame pixel comparison.
            box.setFocused(false);other.setFocused(false);box.setFocused(true);other.setFocused(true);
            TypingConfig cfg=ConfigManager.get();boolean enabled=cfg.enabled;
            try {cfg.enabled=true;box.render(g,x,y,partialTick);cfg.enabled=false;other.render(g,x,y,partialTick);}
            finally {cfg.enabled=enabled;}
        }
        @Override public void onClose(){}
        @Override public boolean shouldCloseOnEsc(){return false;}
    }
}
