package dev.typinganimation.mc;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/**
 * Slider over a numeric config value in [min, max], snapped to {@code step} (the handle too); applies live. Arrow
 * keys move by exactly one step. Vanilla's keyboard state ({@code canChangeValue}) and {@code setValue} are private,
 * so the slider mirrors that state ({@link #keyAdjust}: same rules as vanilla) and sets the value itself.
 */
final class ConfigSlider extends AbstractSliderButton {
    private final Component label;
    private final double min, max, step;
    private final DoubleConsumer setter;
    private final DoubleFunction<Component> format;
    /** Arrow keys adjust the value (vanilla's private canChangeValue, mirrored). */
    private boolean keyAdjust;

    ConfigSlider(int x, int y, int width, int height, String key, double min, double max, double step,
                 DoubleSupplier getter, DoubleConsumer setter, DoubleFunction<Component> format) {
        super(x, y, width, height, Component.empty(), 0.0);
        this.label = Component.translatable(key);
        this.min = min;
        this.max = max;
        this.step = step;
        this.setter = setter;
        this.format = format;
        this.value = toSlider(getter.getAsDouble());
        setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
        updateMessage();
    }

    /** Current snapped config value. */
    double current() {
        double v = min + value * (max - min);
        if (step > 0) {
            v = Math.round((v - min) / step) * step + min;
        }
        return Mth.clamp(v, min, max);
    }

    private double toSlider(double v) {
        return max > min ? Mth.clamp((v - min) / (max - min), 0.0, 1.0) : 0.0;
    }

    @Override
    protected void updateMessage() {
        setMessage(CommonComponents.optionNameValue(label, format.apply(current())));
    }

    @Override
    protected void applyValue() {
        double v = current();
        value = toSlider(v); // the handle sits on the step grid, matching the label
        setter.accept(v);
    }

    /** Self-test: lets arrow keys adjust the slider (as after pressing Enter on it). */
    void allowKeys() {
        keyAdjust = true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            keyAdjust = false;
        } else {
            InputType type = Minecraft.getInstance().getLastInputType();
            if (type == InputType.MOUSE || type == InputType.KEYBOARD_TAB) {
                keyAdjust = true;
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (CommonInputs.selected(keyCode)) {
            keyAdjust = !keyAdjust;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        boolean left = keyCode == InputConstants.KEY_LEFT;
        if (left || keyCode == InputConstants.KEY_RIGHT) {
            if (!keyAdjust) {
                return false;
            }
            // vanilla moves by 1/(width-8) of the range, which is not a step: use exactly one step
            double old = value;
            value = toSlider(current() + (left ? -step : step));
            if (value != old) {
                applyValue();
            }
            updateMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
