package dev.typinganimation.mc;

import dev.typinganimation.core.AppearStyle;
import dev.typinganimation.core.CharTransform;
import dev.typinganimation.core.ConfigManager;
import dev.typinganimation.core.RemoveStyle;
import dev.typinganimation.core.TypingConfig;
import dev.typinganimation.core.compat.BetterCommandBlockAccess;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Executes the real optional editor and suggestor without opening, saving or sending a command block. */
public final class BetterCommandCompatibilityProbe {
    private static final String PREFIX="bettercommandblockui.main.ui.";
    private static final String INITIAL="execute as @e[nbt={CustomName:'A\uD83D\uDE03',Tags:['alpha','beta']}] run say hello";
    private static Minecraft client;
    private static Screen previousScreen;
    private static TypingConfig original;
    private static ProbeScreen screen;
    private static Class<?> widgetClass,suggestorClass;
    private static int phase;
    private static long nextFrame,animatedMark,segmentMark,caretMark,verticalMark,ghostMark,soundMark;
    private static String untouched;
    private static volatile boolean capturePending;
    private static volatile String captureError;
    private static int[] nativePixels;
    private static int pixelMatches;

    private BetterCommandCompatibilityProbe() { }
    public static boolean available() {
        return BetterCommandCompatibilityProbe.class.getClassLoader().getResource(
                PREFIX.replace('.','/')+"MultiLineTextFieldWidget.class")!=null;
    }
    public static void prepare() throws Exception {
        require(available()&&original==null,"Better Command Block UI probe cannot start");
        client=Minecraft.getInstance();previousScreen=client.gui.screen();original=ConfigManager.get().copy();
        TypingConfig cfg=ConfigManager.get();cfg.enabled=true;cfg.animateMultiline=true;cfg.animateOtherFields=false;
        cfg.onlyWhenFocused=true;cfg.appearStyle=AppearStyle.POP;cfg.removeStyle=RemoveStyle.FALL;
        cfg.durationMs=1000;cfg.removeDurationMs=1000;cfg.smoothCursor=true;cfg.cursorGlideMs=300;
        cfg.typingSounds=true;cfg.soundVolume=.1f;cfg.adaptiveTyping=false;cfg.cursorKeepVisibleWhileTyping=true;
        widgetClass=Class.forName(PREFIX+"MultiLineTextFieldWidget");
        suggestorClass=Class.forName(PREFIX+"MultiLineCommandSuggestor");
        phase=0;nextFrame=3;capturePending=false;captureError=null;nativePixels=null;pixelMatches=0;
        screen=new ProbeScreen();client.gui.setScreen(screen);TypingSounds.resetTestCounters();
    }
    public static boolean tick() throws Exception {
        require(original!=null&&client.gui.screen()==screen,"Better Command Block UI probe screen changed");
        if (screen.frames<nextFrame || capturePending) return false;
        require(captureError==null,"Command text pixel comparison: "+captureError);
        BetterCommandAnimation main=animation(screen.main),raw=animation(screen.raw);
        require(!main.failed()&&!raw.failed(),"Better Command Block UI adapter fell back after an error");
        switch (phase++) {
            case 0 -> {
                require(main.frames>=3&&main.segments>0,"Better Command Block UI render hooks did not run");
                require(main.state().isIdle(ConfigManager.get()),"Opening an existing command replayed typing");
                require(((List<?>)BetterCommandBlockAccess.field(screen.main,"lines")).size()>1,"Native command fixture did not wrap/indent");
                require(((List<?>)BetterCommandBlockAccess.field(screen.main,"textColors")).size()>1,"Native command fixture lost syntax colours");
                animatedMark=main.animatedFrames;segmentMark=main.segments;
                type(screen.main,'Z');
                require(screen.main.getValue().endsWith("Z"),"Real command editor rejected typing");
                require(TypingSounds.playedCount==1,"A typed character must play exactly one multiline sound");
            }
            case 1 -> {
                require(main.animatedFrames>animatedMark&&main.segments>segmentMark,"Native coloured command text did not animate");
                soundMark=TypingSounds.playedCount;caretMark=main.caretGlides;key(screen.main,InputConstants.KEY_LEFT);
            }
            case 2 -> {
                require(main.caretGlides>caretMark,"Command caret did not glide horizontally");
                require(TypingSounds.playedCount==soundMark,"Command navigation played typing audio");
                verticalMark=main.verticalCaretGlides;key(screen.main,InputConstants.KEY_UP);
            }
            case 3 -> {
                require(main.verticalCaretGlides>verticalMark,"Command caret did not glide vertically");
                ghostMark=main.ghostFrames;key(screen.main,InputConstants.KEY_BACKSPACE);
            }
            case 4 -> {
                require(main.ghostFrames>ghostMark&&main.state().ghostCount()>0,"Command deletion did not animate");
                soundMark=TypingSounds.playedCount;String same=screen.main.getValue();assign(screen.main,same);
                require(main.state().ghostCount()==0,"Same-value command assignment retained removal effects");
            }
            case 5 -> {
                rest(screen.main);require(TypingSounds.playedCount==soundMark,"Command assignment made sound");
                assign(screen.main,"say ready");
            }
            case 6 -> {
                rest(screen.main);require("say ready".equals(screen.main.getValue()),"Native short assignment changed input");
                ConfigManager.get().animateMultiline=false;ConfigManager.get().animateOtherFields=true;
                soundMark=TypingSounds.playedCount;animatedMark=main.animatedFrames;type(screen.main,'X');
            }
            case 7 -> {
                require(main.animatedFrames==animatedMark&&TypingSounds.playedCount==soundMark,
                        "Disabled multiline command field leaked animation/audio through OTHER settings");
                require(screen.main.getValue().endsWith("X"),"Disabling effects changed command editing");
                ConfigManager.get().animateMultiline=true;
            }
            case 8 -> {
                rest(screen.main);untouched=screen.main.getValue();focus(screen.raw);
                animatedMark=raw.animatedFrames;type(screen.raw,'R');
            }
            case 9 -> {
                require(raw.animatedFrames>animatedMark&&untouched.equals(screen.main.getValue()),"Raw command widget did not keep independent effects");
                soundMark=TypingSounds.playedCount;String before=screen.raw.getValue();
                require(!screen.raw.charTyped(new CharacterEvent(10)),"Command fixture accepted a rejected control character");
                require(before.equals(screen.raw.getValue())&&TypingSounds.playedCount==soundMark,"Rejected input changed text/audio");
                screen.raw.insertText("paste");
                require(TypingSounds.playedCount==soundMark+1,"Direct native paste/autocomplete did not produce one sound");
            }
            case 10 -> {
                focus(screen.main);assign(screen.main,INITIAL+" "+INITIAL+" "+INITIAL);
                untouched=screen.main.getValue();soundMark=TypingSounds.playedCount;
                widgetClass.getMethod("setScroll",double.class).invoke(screen.main,1d);
                widgetClass.getMethod("setHorizontalOffset",double.class).invoke(screen.main,.5d);
            }
            case 11 -> {
                require(untouched.equals(screen.main.getValue())&&TypingSounds.playedCount==soundMark,"Viewport navigation changed command/audio");
                widgetClass.getMethod("setScroll",double.class).invoke(screen.main,0d);
                widgetClass.getMethod("setHorizontalOffset",double.class).invoke(screen.main,0d);
                assign(screen.main,INITIAL);assign(screen.raw,screen.raw.getValue());
            }
            case 12 -> {
                rest(screen.main);rest(screen.raw);
                require(TypingRenderer.firstError==null&&TypingSounds.firstError==null,"Command renderer or sound error");
                screen.main.setFocused(false);screen.raw.setFocused(false);
                assign(screen.raw,"say \u00a7aGREEN \u00a7lbold\u00a7r text");
                require(screen.raw.getValue().contains("\u00a7a"),"Loaded legacy-format command fixture was filtered");
                ConfigManager.get().enabled=false;
            }
            case 13 -> capture(true);
            case 14 -> {
                ConfigManager.get().enabled=true;ConfigManager.get().onlyWhenFocused=false;
                TypingRenderer.debugForcePerChar=true;
            }
            case 15 -> capture(false);
            case 16 -> {
                require(pixelMatches>0,"Native versus animated command pixel comparison did not run");
                TypingRenderer.debugForcePerChar=false;
                return true;
            }
            default -> { return true; }
        }
        nextFrame=screen.frames+3;return false;
    }
    public static void cleanup() {
        TypingRenderer.debugForcePerChar=false;
        if (original!=null) ConfigManager.get().copyFrom(original);
        if (client!=null&&previousScreen!=null) client.gui.setScreen(previousScreen);
        original=null;screen=null;previousScreen=null;
    }
    public static void abort() { cleanup(); }
    private static BetterCommandAnimation animation(EditBox box) {
        require(box instanceof BetterCommandStateHolder,"Optional Better Command Block UI mixin was not applied");
        return ((BetterCommandStateHolder)box).typinganimation$betterCommandAnimation();
    }
    private static void rest(EditBox box) {
        BetterCommandAnimation a=animation(box);TypingConfig cfg=ConfigManager.get();
        require(a.state().isIdle(cfg)&&a.state().ghostCount()==0,"Command assignment/re-enable/scroll replayed effects");
        CharTransform transform=new CharTransform();
        for (int i=0;i<box.getValue().length();i+=Character.charCount(box.getValue().codePointAt(i)))
            require(!a.state().appearTransform(i,cfg,transform),"Existing command text received an appearance effect");
    }
    private static void focus(EditBox box) { screen.main.setFocused(box==screen.main);screen.raw.setFocused(box==screen.raw); }
    private static void type(EditBox box,char c) { require(box.charTyped(new CharacterEvent(c)),"Command editor rejected test input"); }
    private static void key(EditBox box,int key) {
        int before=box.getCursorPosition();
        require(box.keyPressed(new KeyEvent(key,0,0)),"Native command editor rejected navigation/delete key "+key);
        require(box.getCursorPosition()!=before,"Native command cursor did not move for key "+key);
    }
    private static void assign(EditBox box,String value) throws Exception { widgetClass.getMethod("setRawText",String.class).invoke(box,value); }
    private static void require(boolean condition,String message) { if (!condition) throw new AssertionError(message); }

    /** Real Brigadier parse data supplies syntax ranges without a player connection or server commands. */
    private static void primeSuggestor(Object suggestor) throws Exception {
        CommandDispatcher<Object> dispatcher=new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("execute").then(
                RequiredArgumentBuilder.<Object,String>argument("arguments",StringArgumentType.greedyString())));
        ParseResults<Object> parsed=dispatcher.parse(INITIAL,new Object());
        boolean found=false;
        for (Class<?> type=suggestor.getClass();type!=null;type=type.getSuperclass()) {
            for (var field:type.getDeclaredFields()) if (field.getType()==ParseResults.class) {
                require(!found,"Command suggestor fixture found ambiguous parse state");
                field.setAccessible(true);field.set(suggestor,parsed);found=true;
            }
        }
        require(found,"Command suggestor fixture could not find its parse state");
    }

    private static void capture(boolean nativeRendering) {
        capturePending=true;
        int scale=client.getWindow().getGuiScale();
        int x0=Math.max(0,(screen.main.getX()-2)*scale),y0=Math.max(0,(screen.main.getY()-2)*scale);
        int x1=(screen.main.getX()+screen.main.getWidth()+14)*scale,y1=(screen.raw.getY()+screen.raw.getHeight()+14)*scale;
        Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(),image->{
            try {
                int right=Math.min(x1,image.getWidth()),bottom=Math.min(y1,image.getHeight());
                int[] pixels=new int[(right-x0)*(bottom-y0)];int at=0;
                for (int y=y0;y<bottom;y++) for (int x=x0;x<right;x++) pixels[at++]=image.getPixel(x,y);
                if (nativeRendering) nativePixels=pixels;
                else {
                    require(nativePixels!=null&&nativePixels.length==pixels.length,"Command comparison capture sizes differ");
                    int different=0;for (int i=0;i<pixels.length;i++) if (pixels[i]!=nativePixels[i]) different++;
                    require(different==0,"Native/animated command editor pixels differ: "+different);
                    pixelMatches=pixels.length;
                }
                var dir=client.gameDirectory.toPath().toAbsolutePath().normalize().resolve("screenshots");
                java.nio.file.Files.createDirectories(dir);
                image.writeToFile(dir.resolve("typinganimation-selftest-"+(nativeRendering?"36-better-command-native":"37-better-command-per-char")+".png"));
            } catch (Throwable failure) { captureError=failure.toString(); }
            finally { image.close();capturePending=false; }
        });
    }

    private static final class ProbeScreen extends Screen {
        EditBox main,raw;long frames;
        ProbeScreen() { super(Component.literal("Better Command Block UI compatibility self-test")); }
        @Override protected void init() {
            try {
                Class<?> ownerClass=Class.forName(PREFIX+"screen.AbstractBetterCommandBlockScreen");
                var constructor=widgetClass.getConstructor(Font.class,int.class,int.class,int.class,int.class,Component.class,ownerClass);
                main=(EditBox)constructor.newInstance(font,30,32,Math.min(280,width-70),140,Component.literal("Command"),null);
                raw=(EditBox)constructor.newInstance(font,30,200,Math.min(280,width-70),30,Component.literal("Raw command field"),null);
                main.setMaxLength(32767);raw.setMaxLength(32767);assign(main,"say ready");assign(raw,"Raw field");
                Object suggestor=suggestorClass.getConstructor(Minecraft.class,Screen.class,EditBox.class,Font.class,
                        boolean.class,boolean.class,int.class,int.class,boolean.class,int.class)
                        .newInstance(client,this,main,font,true,false,0,7,false,0xCC000000);
                primeSuggestor(suggestor);
                widgetClass.getMethod("setCommandSuggestor",suggestorClass).invoke(main,suggestor);
                assign(main,INITIAL);addRenderableWidget(main);addRenderableWidget(raw);focus(main);
            } catch (Exception failure) { throw new IllegalStateException("Better Command Block UI fixture initialization",failure); }
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
            frames++;graphics.fill(0,0,width,height,0xFF20242C);super.extractRenderState(graphics,mouseX,mouseY,partialTick);
        }
        @Override public void onClose() { }
        @Override public boolean shouldCloseOnEsc() { return false; }
    }
}
