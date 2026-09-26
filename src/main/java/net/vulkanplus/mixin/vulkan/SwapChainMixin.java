package net.vulkanplus.mixin.vulkan;

import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.vulkan.SwapchainTuning;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.IntBuffer;

/**
 * Wires VulkanPlusConfig.presentMode and SwapchainTuning into VulkanMod's SwapChain present mode selection
 * and optimal swapchain image count determination.
 */
@Mixin(value = SwapChain.class, remap = false)
public abstract class SwapChainMixin {

    @Unique
    private int vulkanplus$currentPresentMode = SwapchainTuning.VK_PRESENT_MODE_FIFO_KHR;

    @Unique
    private VkSurfaceCapabilitiesKHR vulkanplus$currentCapabilities;

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

        this.vulkanplus$currentPresentMode = optimalMode;
        cir.setReturnValue(optimalMode);
    }

    @Inject(method = "getPresentMode", at = @At("RETURN"))
    private void vulkanplus$capturePresentMode(IntBuffer availablePresentModes, CallbackInfoReturnable<Integer> cir) {
        this.vulkanplus$currentPresentMode = cir.getReturnValue();
    }

    @Redirect(
        method = "createSwapChain",
        at = @At(
            value = "INVOKE",
            target = "Lnet/vulkanmod/vulkan/device/DeviceManager;querySurfaceProperties(Lorg/lwjgl/vulkan/VkPhysicalDevice;Lorg/lwjgl/system/MemoryStack;)Lnet/vulkanmod/vulkan/device/DeviceManager$SurfaceProperties;"
        )
    )
    private DeviceManager.SurfaceProperties vulkanplus$captureSurfaceProperties(VkPhysicalDevice physicalDevice, MemoryStack stack) {
        DeviceManager.SurfaceProperties props = DeviceManager.querySurfaceProperties(physicalDevice, stack);
        if (props != null) {
            this.vulkanplus$currentCapabilities = props.capabilities;
        }
        return props;
    }

    @ModifyVariable(
        method = "createSwapChain",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/system/MemoryStack;ints(I)Ljava/nio/IntBuffer;"),
        ordinal = 1
    )
    private int vulkanplus$tuneRequestedImages(int requestedImages) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg == null || !cfg.enabled || !cfg.enableSwapchainTuning) {
            return requestedImages;
        }

        VkSurfaceCapabilitiesKHR capabilities = this.vulkanplus$currentCapabilities;
        if (capabilities == null && DeviceManager.surfaceProperties != null) {
            capabilities = DeviceManager.surfaceProperties.capabilities;
        }
        if (capabilities == null) {
            return requestedImages;
        }

        return SwapchainTuning.getOptimalImageCount(
            this.vulkanplus$currentPresentMode,
            capabilities.minImageCount(),
            capabilities.maxImageCount()
        );
    }
}
