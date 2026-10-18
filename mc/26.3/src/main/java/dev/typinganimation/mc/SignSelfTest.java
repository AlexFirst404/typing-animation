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
            return new HangingSignEditScreen(sign, net.minecraft.world.level.block.entity.SignTextSlot.FRONT, false) {
                @Override public void tick() { /* No player/world in the title-screen test fixture. */ }
                @Override public void removed() { this.minecraft.textInputManager().stopTextInput(this); /* Fixture never sends sign updates. */ }
            };
        }
        return new SignEditScreen(sign, net.minecraft.world.level.block.entity.SignTextSlot.FRONT, false) {
            @Override public void tick() { /* No player/world in the title-screen test fixture. */ }
            @Override public void removed() { this.minecraft.textInputManager().stopTextInput(this); /* Fixture never sends sign updates. */ }
        };
    }

    static void type(Screen screen, char c) { screen.charTyped(new net.minecraft.client.input.CharacterEvent(c)); }
    static void left(Screen screen) { key(screen, InputConstants.KEY_LEFT, InputConstants.KEYCODE_LEFT); }
    static void down(Screen screen) { key(screen, InputConstants.KEY_DOWN, InputConstants.KEYCODE_DOWN); }
    static void backspace(Screen screen) { key(screen, InputConstants.KEY_BACKSPACE, InputConstants.KEYCODE_BACKSPACE); }
    private static void key(Screen screen, int key, int keycode) { screen.keyPressed(new net.minecraft.client.input.KeyEvent(key, keycode, 0)); }

    private static final class FixtureSign extends SignBlockEntity {
        private final boolean hanging;
        private SignText stored;
        FixtureSign(boolean hanging) {
            super(hanging ? net.minecraft.world.level.block.entity.BlockEntityTypes.HANGING_SIGN
                    : net.minecraft.world.level.block.entity.BlockEntityTypes.SIGN, BlockPos.ZERO, (hanging ? Blocks.OAK_HANGING_SIGN : Blocks.OAK_SIGN).defaultBlockState());
            this.hanging = hanging;
            this.stored = SignText.EMPTY.asMutable().setColor(DyeColor.CYAN).setTextGlowing(hanging).asImmutable();
        }
        @Override public SignText getText(net.minecraft.world.level.block.entity.SignTextSlot slot) { return stored; }
        @Override public void setText(SignText text, net.minecraft.world.level.block.entity.SignTextSlot slot) {
            stored = text;
        }
        @Override public int getTextLineHeight() { return hanging ? 9 : 10; }
        @Override public int getMaxTextLineWidth() { return hanging ? 60 : 90; }
    }
}
