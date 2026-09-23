package net.vulkanplus.mixin.render;

import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BlockRenderLayers;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRenderLayers.class)
public class BlockRenderLayersMixin {

    @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
    private static void overrideLeavesLayer(BlockState state, CallbackInfoReturnable<BlockRenderLayer> cir) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config != null && config.enabled && config.opaqueLeaves) {
            if (state != null && state.getBlock() instanceof LeavesBlock) {
                cir.setReturnValue(BlockRenderLayer.SOLID);
            }
        }
    }

    @ModifyVariable(method = "setCutoutLeaves", at = @At("HEAD"), argsOnly = true)
    private static boolean enforceOpaqueLeaves(boolean cutoutLeaves) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config != null && config.enabled && config.opaqueLeaves) {
            return false;
        }
        return cutoutLeaves;
    }
}
