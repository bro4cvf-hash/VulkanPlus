package net.vulkanplus.mixin.exordium;

import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import net.vulkanplus.exordium.ExordiumManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void onKey(long window, int key, KeyInput input, CallbackInfo ci) {
        ExordiumManager.getInstance().onInputActivity();
    }
}
