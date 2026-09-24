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
    private static final VulkanStateCache STATE_CACHE = new VulkanStateCache();
    private final PersistentPipelineCache psoCache = new PersistentPipelineCache();
    private final VulkanStateCache stateCache = STATE_CACHE;
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

        try {
            if (config.enabled && config.enableSwapchainTuning && net.vulkanmod.Initializer.CONFIG != null) {
                if (net.vulkanmod.Initializer.CONFIG.frameQueueSize < 3) {
                    net.vulkanmod.Initializer.CONFIG.frameQueueSize = 3;
                    VulkanPlusMod.LOGGER.info("[VulkanPlus] Tuned VulkanMod frameQueueSize to 3 for triple-buffered frame pacing.");
                }
            }
        } catch (Throwable ignored) {
        }

        VulkanModGuiIntegration.register();

        isInitialized = true;
    }

    public static void scheduleSwapChainUpdateIfNeeded() {
        try {
            net.vulkanmod.vulkan.Renderer.scheduleSwapChainUpdate();
        } catch (Throwable ignored) {
        }
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
        try {
            net.vulkanmod.vulkan.memory.MemoryManager mm = net.vulkanmod.vulkan.memory.MemoryManager.getInstance();
            if (mm != null) {
                long usedMb = mm.getAllocatedDeviceMemoryMB();
                if (usedMb > 0) {
                    return usedMb * 1024L * 1024L;
                }
            }
        } catch (Throwable ignored) {
        }

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
        try {
            net.vulkanmod.vulkan.memory.MemoryManager mm = net.vulkanmod.vulkan.memory.MemoryManager.getInstance();
            if (mm != null) {
                long deviceMb = mm.getDeviceMemoryMB();
                if (deviceMb > 0) {
                    return deviceMb * 1024L * 1024L;
                }
            }
        } catch (Throwable ignored) {
        }

        long allocated = 0;
        if (ringBuffer != null && ringBuffer.isBackingAllocated()) {
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

    public static VulkanStateCache getStateCache() {
        return STATE_CACHE;
    }

    public TransientRingBuffer getRingBuffer() {
        return ringBuffer;
    }

    public SlabSubAllocator getSlabAllocator() {
        return slabAllocator;
    }
}
