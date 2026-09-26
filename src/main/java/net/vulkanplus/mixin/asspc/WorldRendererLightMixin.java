package net.vulkanplus.mixin.asspc;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Short-circuits WorldRenderer.getLightmapCoordinates to O(1) constant 0xF000F0
 * when Engine Fullbright is enabled, bypassing per-frame sky/block light queries
 * for all entities, block entities, item frames, and particles.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererLightMixin {

    private static final Integer BOXED_MAX_LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    @Inject(
            method = "getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void vulkanplus$fastFullBrightCoordinates(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.fullBright) {
            cir.setReturnValue(BOXED_MAX_LIGHT);
        }
    }

    @Inject(
            method = "getLightmapCoordinates(Lnet/minecraft/client/render/WorldRenderer$BrightnessGetter;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void vulkanplus$fastFullBrightCoordinatesDetailed(
            WorldRenderer.BrightnessGetter brightnessGetter,
            BlockRenderView world,
            BlockState state,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir
    ) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.fullBright) {
            cir.setReturnValue(BOXED_MAX_LIGHT);
        }
    }
}
