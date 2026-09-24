package net.vulkanplus.mixin.vulkan;

import net.vulkanmod.vulkan.framebuffer.SwapChain;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.vulkan.SwapchainTuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.IntBuffer;

/**
 * Wires VulkanPlusConfig.presentMode and SwapchainTuning into VulkanMod's SwapChain present mode selection.
 */
@Mixin(value = SwapChain.class, remap = false)
public abstract class SwapChainMixin {

    @Inject(method = "getPresentMode", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$overridePresentMode(IntBuffer availablePresentModes, CallbackInfoReturnable<Integer> cir) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg == null || !cfg.enabled || !cfg.enableSwapchainTuning || availablePresentModes == null) {
            return;
        }

        String modeStr = cfg.presentMode;
        if (modeStr == null || "DEFAULT".equalsIgnoreCase(modeStr)) {
            return;
        }

        int desiredMode = SwapchainTuning.parsePresentMode(modeStr);
        int limit = availablePresentModes.limit();
        boolean hasDesired = false;
        boolean hasMailbox = false;
        boolean hasImmediate = false;
        boolean hasRelaxed = false;

        for (int i = 0; i < limit; i++) {
            int m = availablePresentModes.get(i);
            if (m == desiredMode) hasDesired = true;
            if (m == SwapchainTuning.VK_PRESENT_MODE_MAILBOX_KHR) hasMailbox = true;
            if (m == SwapchainTuning.VK_PRESENT_MODE_IMMEDIATE_KHR) hasImmediate = true;
            if (m == SwapchainTuning.VK_PRESENT_MODE_FIFO_RELAXED_KHR) hasRelaxed = true;
        }

        if (hasDesired) {
            cir.setReturnValue(desiredMode);
        } else if (desiredMode == SwapchainTuning.VK_PRESENT_MODE_MAILBOX_KHR && hasImmediate) {
            cir.setReturnValue(SwapchainTuning.VK_PRESENT_MODE_IMMEDIATE_KHR);
        } else if (desiredMode == SwapchainTuning.VK_PRESENT_MODE_MAILBOX_KHR && hasRelaxed) {
            cir.setReturnValue(SwapchainTuning.VK_PRESENT_MODE_FIFO_RELAXED_KHR);
        } else if (desiredMode == SwapchainTuning.VK_PRESENT_MODE_IMMEDIATE_KHR && hasMailbox) {
            cir.setReturnValue(SwapchainTuning.VK_PRESENT_MODE_MAILBOX_KHR);
        } else {
            cir.setReturnValue(SwapchainTuning.VK_PRESENT_MODE_FIFO_KHR);
        }
    }
}
