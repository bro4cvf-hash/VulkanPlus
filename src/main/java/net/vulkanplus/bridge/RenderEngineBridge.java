package net.vulkanplus.bridge;

/**
 * Common decoupled bridge interface between Vulkan Plus and the active render engine.
 */
public interface RenderEngineBridge {
    /**
     * @return true if VulkanMod is active and rendering via Vulkan.
     */
    boolean isVulkanActive();

    /**
     * @return human-readable engine name (e.g. "VulkanMod (Vulkan 1.3)" or "Vanilla / CPU Fallback").
     */
    String getEngineName();

    /**
     * Called during render engine initialization.
     */
    void onRenderInit();

    /**
     * Called at the start of each render frame.
     */
    void onRenderFrameBegin();

    /**
     * Called at the end of each render frame.
     */
    void onRenderFrameEnd();

    /**
     * Called on client shutdown to persist caches and free unmanaged resources.
     */
    void onShutdown();

    /**
     * @return current estimated VRAM usage in bytes.
     */
    long getVramUsed();

    /**
     * @return total allocated VRAM in bytes.
     */
    long getVramAllocated();
}
