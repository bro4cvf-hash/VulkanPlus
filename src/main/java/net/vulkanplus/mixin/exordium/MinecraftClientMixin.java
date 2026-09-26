package net.vulkanplus.mixin.exordium;

import net.minecraft.client.MinecraftClient;
import net.vulkanplus.exordium.ExordiumManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "onResolutionChanged", at = @At("RETURN"))
    private void onResolutionChanged(CallbackInfo ci) {
        ExordiumManager.getInstance().markDirty();
    }

    @Inject(method = "reloadResources()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
    private void onReloadResources(CallbackInfoReturnable<?> cir) {
        ExordiumManager.getInstance().cleanup();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        net.vulkanplus.config.ConfigManager.save();
        ExordiumManager.getInstance().cleanup();
    }
}
