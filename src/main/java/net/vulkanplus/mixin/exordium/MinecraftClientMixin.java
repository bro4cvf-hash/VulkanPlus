package net.vulkanplus.mixin.exordium;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.vulkanplus.exordium.ExordiumManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true)
    private void onGetFramebuffer(CallbackInfoReturnable<Framebuffer> cir) {
        ExordiumManager manager = ExordiumManager.getInstance();
        if (manager.isExordiumActive() && manager.isCapturing() && manager.getGuiFramebuffer() != null) {
            cir.setReturnValue(manager.getGuiFramebuffer());
        }
    }

    @Inject(method = "onResolutionChanged", at = @At("RETURN"))
    private void onResolutionChanged(CallbackInfo ci) {
        ExordiumManager.getInstance().markDirty();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        ExordiumManager.getInstance().cleanup();
    }
}
