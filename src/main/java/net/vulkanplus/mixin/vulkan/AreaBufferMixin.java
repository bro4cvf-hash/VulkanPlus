package net.vulkanplus.mixin.vulkan;

import net.vulkanmod.render.chunk.buffer.AreaBuffer;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Optimizes VulkanMod's AreaBuffer initial SOLID capacity and geometric growth factor (+50% instead of +12.5%)
 * to eliminate cascading vmaCreateBuffer + vkCmdCopyBuffer reallocation stalls when opaqueLeaves is active.
 */
@Mixin(value = AreaBuffer.class, remap = false)
public abstract class AreaBufferMixin {

    @Shadow
    int size;

    @Shadow
    @Final
    private int elementSize;

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static int vulkanplus$boostInitialSolidCapacity(int elementCount) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableBufferPooling && elementCount == 100000) {
            return cfg.opaqueLeaves ? 262144 : 163840;
        }
        return elementCount;
    }

    @Redirect(
            method = "reallocate",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I")
    )
    private int vulkanplus$boostAreaBufferGrowth(int defaultIncrement, int uploadSize) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableBufferPooling && this.elementSize > 0) {
            int halfSize = this.size >> 1;
            int rem = halfSize % this.elementSize;
            int alignedHalf = (rem == 0) ? halfSize : (halfSize + this.elementSize - rem);
            return Math.max(Math.max(defaultIncrement, alignedHalf), uploadSize);
        }
        return Math.max(defaultIncrement, uploadSize);
    }
}
