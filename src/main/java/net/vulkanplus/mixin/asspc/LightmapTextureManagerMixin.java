package net.vulkanplus.mixin.asspc;

import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.LightmapTextureManager;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optimizes LightmapTextureManager for Engine Fullbright:
 * 1. Uploads a pure-white (gamma 100.0, 0.0 darkness) 16x16 GPU lightmap texture once.
 * 2. Skips per-tick torch flicker random math and per-frame GPU RenderPass + UBO uploads
 *    for every subsequent frame while Engine Fullbright remains active.
 * 3. Automatically marks the lightmap dirty to restore normal lighting the instant Fullbright is turned off.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {

    @Shadow
    private boolean dirty;

    @Unique
    private boolean vulkanplus$fullBrightUploaded = false;

    @Unique
    private static final Double FULLBRIGHT_GAMMA = Double.valueOf(100.0d);

    @Unique
    private static final Double ZERO_DARKNESS = Double.valueOf(0.0d);

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$onTick(CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.fullBright) {
            if (this.vulkanplus$fullBrightUploaded) {
                ci.cancel();
            } else {
                this.dirty = true;
            }
        } else if (this.vulkanplus$fullBrightUploaded) {
            this.vulkanplus$fullBrightUploaded = false;
            this.dirty = true;
        }
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$onUpdateHead(float tickProgress, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.fullBright) {
            if (this.vulkanplus$fullBrightUploaded) {
                ci.cancel();
                return;
            }
            this.dirty = true;
        } else if (this.vulkanplus$fullBrightUploaded) {
            this.vulkanplus$fullBrightUploaded = false;
            this.dirty = true;
        }
    }

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;",
                    ordinal = 1
            )
    )
    private Object vulkanplus$redirectDarknessScale(SimpleOption<?> option) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.fullBright) {
            return ZERO_DARKNESS;
        }
        return option.getValue();
    }

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;",
                    ordinal = 2
            )
    )
    private Object vulkanplus$redirectGammaValue(SimpleOption<?> option) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.fullBright) {
            return FULLBRIGHT_GAMMA;
        }
        return option.getValue();
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void vulkanplus$onUpdateReturn(float tickProgress, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.fullBright) {
            this.vulkanplus$fullBrightUploaded = true;
            this.dirty = false;
        }
    }
}
