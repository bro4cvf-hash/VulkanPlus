package net.vulkanplus.mixin.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.pacing.HighPrecisionFramePacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSystem.class, remap = false)
public class RenderSystemLimitFpsMixin {

    @Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true)
    private static void onLimitDisplayFPS(int maxFps, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && maxFps > 0 && maxFps < 260) {
            HighPrecisionFramePacer.pace(maxFps);
            ci.cancel();
        }
    }
}
