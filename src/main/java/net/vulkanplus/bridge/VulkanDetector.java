package net.vulkanplus.bridge;

import net.fabricmc.loader.api.FabricLoader;
import net.vulkanplus.VulkanPlusMod;

/**
 * Probes the runtime environment to detect VulkanMod and instantiate the appropriate bridge.
 */
public class VulkanDetector {
    private static RenderEngineBridge activeBridge;

    public static synchronized RenderEngineBridge getBridge() {
        if (activeBridge == null) {
            initBridge();
        }
        return activeBridge;
    }

    public static boolean isVulkanModLoaded() {
        try {
            if (FabricLoader.getInstance().isModLoaded("vulkanmod")) {
                return true;
            }
        } catch (Throwable ignored) {
            // In unit tests outside FabricLoader environment
        }

        try {
            Class.forName("net.vulkanmod.vulkan.Vulkan");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void initBridge() {
        if (isVulkanModLoaded()) {
            try {
                Class<?> implClass = Class.forName("net.vulkanplus.bridge.impl.VulkanModBridgeImpl");
                activeBridge = (RenderEngineBridge) implClass.getDeclaredConstructor().newInstance();
                VulkanPlusMod.LOGGER.info("[VulkanPlus] VulkanMod detected! Initialized VulkanModBridgeImpl.");
                return;
            } catch (Throwable t) {
                VulkanPlusMod.LOGGER.warn("[VulkanPlus] VulkanMod detected but failed to instantiate bridge: {}. Falling back to CPU mode.", t.getMessage());
            }
        }

        activeBridge = new FallbackCpuBridge();
        VulkanPlusMod.LOGGER.info("[VulkanPlus] Initialized FallbackCpuBridge.");
    }
}
