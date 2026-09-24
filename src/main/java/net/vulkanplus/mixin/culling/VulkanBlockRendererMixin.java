package net.vulkanplus.mixin.culling;

import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import net.vulkanmod.render.chunk.build.frapi.render.AbstractBlockRenderContext;
import net.vulkanmod.render.chunk.build.renderer.BlockRenderer;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.culling.FoliageCuller;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optimizes VulkanMod's chunk BlockRenderer for 1.21.11 foliage & non-full plant blocks:
 * - Applies deterministic (x, z) density thinning and 24-block distance culling (shitFoliage)
 *   for purely decorative ground clutter while never hiding gameplay plants
 * - Cuts cross-model quad emission by 50% (enableFastFoliage: 2 quads) or 75% (shitFoliage: 1 quad)
 * - Disables smooth Ambient Occlusion (forcing flatLightPipeline) when shitFoliage is enabled.
 */
@Mixin(value = BlockRenderer.class, remap = false)
public abstract class VulkanBlockRendererMixin extends AbstractBlockRenderContext {

    @Unique
    private int vulkanplus$crossQuadIndex = 0;

    @Unique
    private boolean vulkanplus$isCrossFoliage = false;

    @Unique
    private boolean vulkanplus$forceFlatFoliageLighting = false;

    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$onRenderBlockHead(BlockState state, BlockPos pos, Vector3f posVec, CallbackInfo ci) {
        this.vulkanplus$crossQuadIndex = 0;
        this.vulkanplus$isCrossFoliage = false;
        this.vulkanplus$forceFlatFoliageLighting = false;

        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || state == null || pos == null) {
            return;
        }

        if (!FoliageCuller.shouldRenderBlockAt(state, pos)) {
            ci.cancel();
            return;
        }

        if (config.enableFastFoliage || config.shitFoliage) {
            this.vulkanplus$isCrossFoliage = FoliageCuller.isFoliageOrPlant(state);
        }
        if (config.shitFoliage && FoliageCuller.isFoliageOrPlant(state)) {
            this.vulkanplus$forceFlatFoliageLighting = true;
            this.defaultAO = false;
        }
    }

    @Inject(method = "endRenderQuad", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$onEndRenderQuad(MutableQuadViewImpl quad, CallbackInfo ci) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled) {
            return;
        }

        if (this.vulkanplus$isCrossFoliage && quad != null) {
            if (FoliageCuller.shouldCullFlushBottomQuad(this.blockState, quad.lightFace(), config.enableFastFoliage, config.shitFoliage)) {
                ci.cancel();
                return;
            }
            if (FoliageCuller.shouldReduceCrossQuad(this.blockState, quad.cullFace(), quad.lightFace(), quad.tintIndex())) {
                int quadIdx = this.vulkanplus$crossQuadIndex++;
                if (!FoliageCuller.shouldEmitCrossQuad(this.blockState, quadIdx, config.enableFastFoliage, config.shitFoliage)) {
                    ci.cancel();
                    return;
                }
            }
        }

        if (this.vulkanplus$forceFlatFoliageLighting && quad != null) {
            this.useAO = false;
            this.defaultAO = false;
            quad.ambientOcclusion(TriState.FALSE);
        }
    }

    @Inject(method = "renderBlock", at = @At("RETURN"))
    private void vulkanplus$onRenderBlockReturn(BlockState state, BlockPos pos, Vector3f posVec, CallbackInfo ci) {
        this.vulkanplus$crossQuadIndex = 0;
        this.vulkanplus$isCrossFoliage = false;
        this.vulkanplus$forceFlatFoliageLighting = false;
    }
}
