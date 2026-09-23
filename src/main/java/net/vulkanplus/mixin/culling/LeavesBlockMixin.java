package net.vulkanplus.mixin.culling;

import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.util.math.Direction;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LeavesBlock.class)
public class LeavesBlockMixin {

    /**
     * Culls interior leaf faces when adjacent to another leaf block.
     * When Smart Leaves is enabled, this eliminates internal hidden leaf faces in Fancy mode
     * while preserving transparent/cutout textures on exterior faces.
     */
    @Inject(method = "isSideInvisible", at = @At("HEAD"), cancellable = true)
    private void cullInteriorLeafFaces(BlockState state, BlockState neighborState, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config != null && config.enabled && (config.enableSmartLeaves || config.opaqueLeaves)) {
            if (neighborState != null && neighborState.getBlock() instanceof LeavesBlock) {
                cir.setReturnValue(true);
            }
        }
    }

    /**
     * When opaqueLeaves (FastLeaves mode) is active, enforces false for cutout leaves to force solid rendering.
     */
    @ModifyVariable(method = "setCutoutLeaves", at = @At("HEAD"), argsOnly = true)
    private static boolean enforceOpaqueLeaves(boolean cutoutLeaves) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config != null && config.enabled && config.opaqueLeaves) {
            return false;
        }
        return cutoutLeaves;
    }
}
