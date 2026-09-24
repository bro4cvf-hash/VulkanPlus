package net.vulkanplus.mixin.culling;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.culling.FoliageCuller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Optimizes vanilla BlockModelRenderer fallback path for 1.21.11 foliage & non-full blocks:
 * - Applies deterministic (x, z) density thinning and 24-block distance culling (shitFoliage)
 * - Forces flat lighting (disabling smooth AO) when shitFoliage is enabled
 * - Cuts cross-model quad emission by 50% (enableFastFoliage) or 75% (shitFoliage).
 */
@Mixin(BlockModelRenderer.class)
public abstract class BlockModelRendererMixin {

    @Shadow
    public abstract void renderFlat(BlockRenderView world, List<BlockModelPart> parts, BlockState state,
                                    BlockPos pos, MatrixStack matrices, VertexConsumer vertexConsumer,
                                    boolean cull, int overlay);

    @Unique
    private static final ThreadLocal<int[]> VULKANPLUS_QUAD_STATE = ThreadLocal.withInitial(() -> new int[3]);
    // index 0: crossQuadIndex, index 1: visibilityChecked (1 = checked), index 2: isCustom3DModel (1 = true)

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$onRenderBlockModel(BlockRenderView world, List<BlockModelPart> parts, BlockState state,
                                               BlockPos pos, MatrixStack matrices, VertexConsumer vertexConsumer,
                                               boolean cull, int overlay, CallbackInfo ci) {
        int[] quadState = VULKANPLUS_QUAD_STATE.get();
        quadState[0] = 0;
        quadState[1] = 0;
        quadState[2] = 0;

        if (parts == null || parts.isEmpty()) {
            return;
        }

        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || state == null || pos == null) {
            return;
        }

        if (!FoliageCuller.shouldRenderBlockAt(state, pos)) {
            ci.cancel();
            return;
        }
        quadState[1] = 1;
        if (FoliageCuller.isCrossModelPlant(state) && !(state.getBlock() instanceof net.minecraft.block.VineBlock)
                && FoliageCuller.isCustom3DModel(parts)) {
            quadState[2] = 1;
        }

        if (config.shitFoliage && FoliageCuller.isFoliageOrPlant(state)) {
            this.renderFlat(world, parts, state, pos, matrices, vertexConsumer, cull, overlay);
            ci.cancel();
        }
    }

    @Inject(method = "renderSmooth", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$onRenderSmoothHead(BlockRenderView world, List<BlockModelPart> parts, BlockState state,
                                               BlockPos pos, MatrixStack matrices, VertexConsumer vertexConsumer,
                                               boolean cull, int overlay, CallbackInfo ci) {
        int[] quadState = VULKANPLUS_QUAD_STATE.get();
        quadState[0] = 0;

        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || state == null || pos == null) {
            return;
        }

        if (quadState[1] == 0) {
            if (!FoliageCuller.shouldRenderBlockAt(state, pos)) {
                ci.cancel();
                return;
            }
            quadState[1] = 1;
            if (FoliageCuller.isCrossModelPlant(state) && !(state.getBlock() instanceof net.minecraft.block.VineBlock)
                    && FoliageCuller.isCustom3DModel(parts)) {
                quadState[2] = 1;
            }
        }

        if (config.shitFoliage && FoliageCuller.isFoliageOrPlant(state)) {
            this.renderFlat(world, parts, state, pos, matrices, vertexConsumer, cull, overlay);
            ci.cancel();
        }
    }

    @Inject(method = "renderFlat", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$onRenderFlatHead(BlockRenderView world, List<BlockModelPart> parts, BlockState state,
                                             BlockPos pos, MatrixStack matrices, VertexConsumer vertexConsumer,
                                             boolean cull, int overlay, CallbackInfo ci) {
        int[] quadState = VULKANPLUS_QUAD_STATE.get();
        quadState[0] = 0;

        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || state == null || pos == null) {
            return;
        }

        if (quadState[1] == 0) {
            if (!FoliageCuller.shouldRenderBlockAt(state, pos)) {
                ci.cancel();
                return;
            }
            quadState[1] = 1;
            if (FoliageCuller.isCrossModelPlant(state) && !(state.getBlock() instanceof net.minecraft.block.VineBlock)
                    && FoliageCuller.isCustom3DModel(parts)) {
                quadState[2] = 1;
            }
        }
    }

    @Inject(method = {"render", "renderSmooth", "renderFlat"}, at = @At("RETURN"))
    private void vulkanplus$onRenderBlockReturn(CallbackInfo ci) {
        int[] quadState = VULKANPLUS_QUAD_STATE.get();
        quadState[0] = 0;
        quadState[1] = 0;
        quadState[2] = 0;
    }

    @Inject(method = "renderQuad", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$onRenderQuad(BlockRenderView world, BlockState state, BlockPos pos,
                                         VertexConsumer vertexConsumer, MatrixStack.Entry matrixEntry,
                                         BakedQuad quad, @Coerce Object lightmap,
                                         int overlay, CallbackInfo ci) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || (!config.enableFastFoliage && !config.shitFoliage)) {
            return;
        }

        if (state != null && quad != null) {
            if (FoliageCuller.shouldCullFlushBottomQuad(state, quad.face(), config.enableFastFoliage, config.shitFoliage)) {
                ci.cancel();
                return;
            }
            int[] quadState = VULKANPLUS_QUAD_STATE.get();
            if (quadState[2] == 0 && FoliageCuller.shouldReduceCrossQuad(state, null, quad.face(), quad.tintIndex())) {
                int quadIdx = quadState[0]++;
                if (!FoliageCuller.shouldEmitCrossQuad(state, quadIdx, config.enableFastFoliage, config.shitFoliage)) {
                    ci.cancel();
                }
            }
        }
    }
}
