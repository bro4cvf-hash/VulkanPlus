package net.vulkanplus.mixin.culling;

import net.minecraft.block.BeaconBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.Direction;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PaneBlock.class)
public class PaneBlockMixin {

    @Inject(method = "isSideInvisible", at = @At("HEAD"), cancellable = true)
    private void cullMatchingPaneSides(BlockState state, BlockState neighborState, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (state == null || neighborState == null || direction == null) return;
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || !config.enableExtraGlassCulling) return;

        if (direction.getAxis().isHorizontal()) {
            BooleanProperty prop = ConnectingBlock.FACING_PROPERTIES.get(direction);
            if (prop != null && state.contains(prop) && state.get(prop)) {
                if (neighborState.getBlock() instanceof TransparentBlock
                        || neighborState.getBlock() instanceof BeaconBlock
                        || neighborState.isOpaqueFullCube()) {
                    cir.setReturnValue(true);
                }
            }
        }
    }
}
