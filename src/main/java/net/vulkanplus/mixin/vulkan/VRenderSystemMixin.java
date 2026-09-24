package net.vulkanplus.mixin.vulkan;

import net.minecraft.client.render.fog.FogData;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.util.MappedBuffer;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Completely disables all atmospheric and distance fog in VulkanMod when No Fog / Fade is enabled.
 *
 * In VulkanMod, terrain shaders compute fog uniforms directly from VRenderSystem.fogData and
 * VRenderSystem.shaderFogColor. Intercepting these values forces environmental and distance
 * fog boundaries to 1,000,000 blocks and zeroes the fog color alpha, eliminating all misty haze
 * on distant mountains and terrain.
 */
@Mixin(value = VRenderSystem.class, remap = false)
public class VRenderSystemMixin {

    @Shadow
    public static MappedBuffer shaderFogColor;

    @Unique
    private static final FogData NO_FOG_DATA = new FogData();

    static {
        NO_FOG_DATA.environmentalStart = 1_000_000.0f;
        NO_FOG_DATA.environmentalEnd = 1_000_000.0f;
        NO_FOG_DATA.renderDistanceStart = 1_000_000.0f;
        NO_FOG_DATA.renderDistanceEnd = 1_000_000.0f;
        NO_FOG_DATA.skyEnd = 1_000_000.0f;
        NO_FOG_DATA.cloudEnd = 1_000_000.0f;
    }

    @Inject(method = "getFogData", at = @At("HEAD"), cancellable = true)
    private static void onGetFogData(CallbackInfoReturnable<FogData> cir) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && (cfg.noFog || cfg.noChunkFade)) {
            cir.setReturnValue(NO_FOG_DATA);
        }
    }

    @Inject(method = "setShaderFogColor", at = @At("HEAD"), cancellable = true)
    private static void onSetShaderFogColor(float r, float g, float b, float a, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && (cfg.noFog || cfg.noChunkFade)) {
            if (shaderFogColor != null) {
                net.vulkanmod.vulkan.util.ColorUtil.setRGBA_Buffer(shaderFogColor, 0.0f, 0.0f, 0.0f, 0.0f);
            }
            ci.cancel();
        }
    }
}
