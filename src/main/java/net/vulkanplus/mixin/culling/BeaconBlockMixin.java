package net.vulkanplus.mixin.culling;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BeaconBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TranslucentBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.util.math.Direction;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.class)
public class BeaconBlockMixin {

    @Inject(method = "isSideInvisible", at = @At("HEAD"), cancellable = true)
    private void cullBeaconAgainstGlassOrBeacon(BlockState state, BlockState neighborState, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || !config.enableExtraGlassCulling) return;

        if (state != null && (state.isOf(Blocks.BEACON) || (Object) this instanceof BeaconBlock)) {
            if (neighborState != null && (neighborState.isOf(Blocks.BEACON) || neighborState.getBlock() instanceof TransparentBlock || neighborState.getBlock() instanceof TranslucentBlock)) {
                cir.setReturnValue(true);
            }
        }
    }
}
