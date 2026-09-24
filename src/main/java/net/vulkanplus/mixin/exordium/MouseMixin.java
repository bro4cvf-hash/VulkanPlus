package net.vulkanplus.mixin.exordium;

import net.minecraft.client.Mouse;
import net.vulkanplus.exordium.ExordiumManager;
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
}
