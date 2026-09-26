package net.vulkanplus.mixin.exordium;

import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import net.vulkanplus.exordium.ExordiumManager;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Inject(method = "onCursorPos", at = @At("HEAD"))
    private void onCursorPos(long window, double x, double y, CallbackInfo ci) {
        ExordiumManager.getInstance().onInputActivity();
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"))
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        ExordiumManager.getInstance().onHotbarInput();
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS) {
            ExordiumManager.getInstance().onHotbarInput();
        } else {
            ExordiumManager.getInstance().onInputActivity();
        }
    }
}
