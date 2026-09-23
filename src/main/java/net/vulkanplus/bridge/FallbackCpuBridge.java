package net.vulkanplus.bridge;

import net.vulkanplus.VulkanPlusMod;

/**
 * Fallback bridge used when VulkanMod is not detected or running in CPU fallback mode.
 * Completely free of any Vulkan classloader dependencies.
 */
public class FallbackCpuBridge implements RenderEngineBridge {

    @Override
    public boolean isVulkanActive() {
        return false;
    }

    @Override
    public String getEngineName() {
        return "Vanilla / CPU Fallback Mode";
    }

    @Override
    public void onRenderInit() {
        VulkanPlusMod.LOGGER.info("[VulkanPlus] Running in CPU Fallback Mode. Vulkan features bypassed safely.");
    }

    @Override
    public void onRenderFrameBegin() {
        // No Vulkan command buffers to synchronize
    }

    @Override
    public void onRenderFrameEnd() {
        // No Vulkan present sync needed
    }

    @Override
    public void onShutdown() {
        VulkanPlusMod.LOGGER.info("[VulkanPlus] Fallback bridge shutdown.");
    }

    @Override
    public long getVramUsed() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    @Override
    public long getVramAllocated() {
        return Runtime.getRuntime().totalMemory();
    }
}
