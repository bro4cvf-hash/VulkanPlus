package net.vulkanplus.mixin.culling;

import net.minecraft.block.BeaconBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.TranslucentBlock;
import net.minecraft.util.math.Direction;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TranslucentBlock.class)
public class TranslucentBlockMixin {

    @Inject(method = "isSideInvisible", at = @At("HEAD"), cancellable = true)
    private void cullGlassAgainstBeacon(BlockState state, BlockState neighborState, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || !config.enableExtraGlassCulling) return;

        if (neighborState != null && neighborState.getBlock() instanceof BeaconBlock) {
            cir.setReturnValue(true);
        }
    }
}
