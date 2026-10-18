package dev.typinganimation.mc;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

/** Dev-only real sign editor fixture. Only world persistence/ticking are stubbed: rendering and input are vanilla. */
final class SignSelfTest {
    private SignSelfTest() {}

    static Screen create(boolean hanging) {
        SignBlockEntity sign = new FixtureSign(hanging);
        if (hanging) {
            return new HangingSignEditScreen(sign, true, false) {
                @Override public void tick() { /* No player/world in the title-screen test fixture. */ }
                @Override public void removed() {  /* Fixture never sends sign updates. */ }
            };
        }
        return new SignEditScreen(sign, true, false) {
            @Override public void tick() { /* No player/world in the title-screen test fixture. */ }
            @Override public void removed() {  /* Fixture never sends sign updates. */ }
        };
    }

    static void type(Screen screen, char c) { screen.charTyped(new net.minecraft.client.input.CharacterEvent(c, 0)); }
    static void left(Screen screen) { key(screen, InputConstants.KEY_LEFT, 0); }
    static void down(Screen screen) { key(screen, InputConstants.KEY_DOWN, 0); }
    static void backspace(Screen screen) { key(screen, InputConstants.KEY_BACKSPACE, 0); }
    private static void key(Screen screen, int key, int keycode) { screen.keyPressed(new net.minecraft.client.input.KeyEvent(key, keycode, 0)); }

    private static final class FixtureSign extends SignBlockEntity {
        private final boolean hanging;
        private SignText stored;
        FixtureSign(boolean hanging) {
            super(hanging ? net.minecraft.world.level.block.entity.BlockEntityType.HANGING_SIGN
                    : net.minecraft.world.level.block.entity.BlockEntityType.SIGN, BlockPos.ZERO, (hanging ? Blocks.OAK_HANGING_SIGN : Blocks.OAK_SIGN).defaultBlockState());
            this.hanging = hanging;
            this.stored = new SignText().setColor(DyeColor.CYAN).setHasGlowingText(hanging);
        }
        @Override public SignText getText(boolean slot) { return stored; }
        @Override public boolean setText(SignText text, boolean slot) {
            stored = text;
            return true;
        }
        @Override public int getTextLineHeight() { return hanging ? 9 : 10; }
        @Override public int getMaxTextLineWidth() { return hanging ? 60 : 90; }
    }
}
