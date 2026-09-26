package net.vulkanplus.mixin.culling;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.culling.FoliageCuller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optimizes Minecraft 1.21.11 foliage and non-full plant blocks at the BlockState level:
 * - Eliminates per-block random getModelOffset coordinate hash computation (Vec3d.ZERO)
 * - Culls hidden interior and bottom faces on stacked/flush plants (SugarCaneBlock, BambooBlock,
 *   KelpBlock, VineBlock, MangroveRootsBlock, LeafLitterBlock, PaleMossCarpetBlock, FlowerbedBlock)
 * - Forces full ambient occlusion light level (1.0f) when Fast Foliage or Shit Foliage is active.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class FoliageBlockMixin {

    private static final Float BOXED_ONE_FLOAT = 1.0f;

    @Shadow
    public abstract Block getBlock();

    @Shadow
    protected abstract BlockState asBlockState();

    @Inject(method = "getModelOffset", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$eliminateFoliageModelOffset(BlockPos pos, CallbackInfoReturnable<Vec3d> cir) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config != null && config.enabled && (config.enableFastFoliage || config.shitFoliage)) {
            if (FoliageCuller.isFoliageOrPlant(this.getBlock())) {
                cir.setReturnValue(Vec3d.ZERO);
            }
        }
    }

    @Inject(method = "hasModelOffset", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$disableHasModelOffset(CallbackInfoReturnable<Boolean> cir) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config != null && config.enabled && (config.enableFastFoliage || config.shitFoliage)) {
            if (FoliageCuller.isFoliageOrPlant(this.getBlock())) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "isSideInvisible", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$cullStackedAndFlushPlantFaces(BlockState neighborState, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (FoliageCuller.shouldCullPlantFace(this.asBlockState(), neighborState, direction)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getAmbientOcclusionLightLevel", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$fastFoliageAmbientLightLevel(BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config != null && config.enabled) {
            if (config.fullBright || ((config.enableFastFoliage || config.shitFoliage) && FoliageCuller.isFoliageOrPlant(this.getBlock()))) {
                cir.setReturnValue(BOXED_ONE_FLOAT);
            }
        }
    }
}
