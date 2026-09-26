package net.vulkanplus.mixin.vulkan;

import net.vulkanmod.vulkan.framebuffer.SwapChain;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.vulkan.SwapchainTuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.IntBuffer;

/**
 * Wires VulkanPlusConfig.presentMode and SwapchainTuning into VulkanMod's SwapChain present mode selection.
 */
@Mixin(value = SwapChain.class, remap = false)
public abstract class SwapChainMixin {

    @Shadow
    public abstract boolean isVsync();

    @Inject(method = "getPresentMode", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$overridePresentMode(IntBuffer availablePresentModes, CallbackInfoReturnable<Integer> cir) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg == null || !cfg.enabled || !cfg.enableSwapchainTuning || availablePresentModes == null) {
            return;
        }

        String modeStr = cfg.presentMode;
        boolean userConfigured = modeStr != null && !modeStr.trim().isEmpty() && !"DEFAULT".equalsIgnoreCase(modeStr.trim());

        int preferredMode;
        if (this.isVsync()) {
            if (userConfigured) {
                preferredMode = SwapchainTuning.parsePresentMode(modeStr);
            } else {
                preferredMode = SwapchainTuning.VK_PRESENT_MODE_FIFO_KHR;
            }
        } else {
            if (!userConfigured) {
                return;
            }
            preferredMode = SwapchainTuning.parsePresentMode(modeStr);
        }

        int optimalMode = SwapchainTuning.selectOptimalPresentMode(preferredMode, availablePresentModes);

        // If Minecraft's VSync is active, preserve FIFO / FIFO_RELAXED instead of forcing an uncapped tear mode
        // unless the user explicitly configured presentMode.
        if (this.isVsync() && optimalMode == SwapchainTuning.VK_PRESENT_MODE_IMMEDIATE_KHR) {
            if (!userConfigured || !"IMMEDIATE".equalsIgnoreCase(modeStr.trim())) {
                optimalMode = SwapchainTuning.VK_PRESENT_MODE_FIFO_KHR;
            }
        }

        cir.setReturnValue(optimalMode);
    }
}
