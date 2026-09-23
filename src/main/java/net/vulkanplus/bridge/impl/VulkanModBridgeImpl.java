package net.vulkanplus.bridge.impl;

import net.vulkanplus.VulkanPlusMod;
import net.vulkanplus.bridge.RenderEngineBridge;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.memory.SlabSubAllocator;
import net.vulkanplus.memory.TransientRingBuffer;
import net.vulkanplus.vulkan.PersistentPipelineCache;
import net.vulkanplus.vulkan.VulkanStateCache;

/**
 * Concrete bridge implementation connecting Vulkan Plus directly to VulkanMod.
 * Manages native memory pools, PSO disk cache, state caching, and swapchain pacing.
 */
public class VulkanModBridgeImpl implements RenderEngineBridge {
    private final PersistentPipelineCache psoCache = new PersistentPipelineCache();
    private final VulkanStateCache stateCache = new VulkanStateCache();
    private TransientRingBuffer ringBuffer;
    private SlabSubAllocator slabAllocator;

    private boolean isInitialized = false;

    public VulkanModBridgeImpl() {
    }

    @Override
    public boolean isVulkanActive() {
        return true;
    }

    @Override
    public String getEngineName() {
        return "VulkanMod (Vulkan 1.3 / Enhanced Pipeline)";
    }

    @Override
    public void onRenderInit() {
        VulkanPlusConfig config = ConfigManager.getConfig();
        VulkanPlusMod.LOGGER.info("[VulkanPlus] Initializing VulkanMod Companion Bridge...");

        if (config.enableBufferPooling) {
            this.ringBuffer = new TransientRingBuffer(16 * 1024 * 1024);
            this.slabAllocator = new SlabSubAllocator();
            VulkanPlusMod.LOGGER.info("[VulkanPlus] Transient ring buffer and slab sub-allocator initialized.");
        }

        if (config.enablePsoCache) {
            psoCache.loadCacheData(0, 0, null);
        }

        VulkanModGuiIntegration.register();

        isInitialized = true;
    }

    @Override
    public void onRenderFrameBegin() {
        if (!isInitialized) return;

        if (ringBuffer != null) {
            ringBuffer.advanceFrame();
        }

        if (stateCache != null) {
            stateCache.reset();
        }
    }

    @Override
    public void onRenderFrameEnd() {
    }

    @Override
    public void onShutdown() {
        VulkanPlusMod.LOGGER.info("[VulkanPlus] VulkanMod bridge shutting down. Releasing allocators.");
        if (slabAllocator != null) {
            slabAllocator.clear();
        }
        if (ringBuffer != null) {
            ringBuffer.reset();
        }
    }

    @Override
    public long getVramUsed() {
        long used = 0;
        if (ringBuffer != null) {
            used += ringBuffer.getUsedBytesInCurrentFrame();
        }
        if (slabAllocator != null) {
            used += slabAllocator.getActiveBytesInUse();
        }
        return used > 0 ? used : (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
    }

    @Override
    public long getVramAllocated() {
        long allocated = 0;
        if (ringBuffer != null) {
            allocated += ringBuffer.getTotalSize();
        }
        if (slabAllocator != null) {
            allocated += slabAllocator.getTotalAllocatedBytes();
        }
        return allocated > 0 ? allocated : Runtime.getRuntime().totalMemory();
    }

    public PersistentPipelineCache getPsoCache() {
        return psoCache;
    }

    public VulkanStateCache getStateCache() {
        return stateCache;
    }

    public TransientRingBuffer getRingBuffer() {
        return ringBuffer;
    }

    public SlabSubAllocator getSlabAllocator() {
        return slabAllocator;
    }
}
