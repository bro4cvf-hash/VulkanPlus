package net.vulkanplus.mixin.asspc;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.fog.FogRenderer;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @Shadow
    @Final
    private GpuBuffer emptyBuffer;

    @Shadow
    @Final
    public static int FOG_UBO_SIZE;

    @Inject(
            method = "getFogBuffer(Lnet/minecraft/client/render/fog/FogRenderer$FogType;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onGetFogBuffer(FogRenderer.FogType fogType, CallbackInfoReturnable<GpuBufferSlice> cir) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && (cfg.noFog || cfg.noChunkFade) && emptyBuffer != null) {
            cir.setReturnValue(this.emptyBuffer.slice(0L, (long) FOG_UBO_SIZE));
        }
    }

    @org.spongepowered.asm.mixin.injection.ModifyArg(
            method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
            ),
            index = 3
    )
    private float modifyEnvironmentalStart(float start) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && (cfg.noFog || cfg.noChunkFade)) {
            return Float.MAX_VALUE;
        }
        return start;
    }

    @org.spongepowered.asm.mixin.injection.ModifyArg(
            method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
            ),
            index = 5
    )
    private float modifyRenderDistanceStart(float start) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && (cfg.noFog || cfg.noChunkFade)) {
            return Float.MAX_VALUE;
        }
        return start;
    }
}
