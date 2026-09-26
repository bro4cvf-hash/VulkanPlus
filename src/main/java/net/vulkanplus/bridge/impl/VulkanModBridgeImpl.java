package net.vulkanplus.bridge.impl;

import net.vulkanplus.VulkanPlusMod;
import net.vulkanplus.bridge.RenderEngineBridge;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.memory.PersistentVmaManager;
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
    private volatile TransientRingBuffer ringBuffer;
    private volatile SlabSubAllocator slabAllocator;

    private boolean isInitialized = false;
    private boolean swapchainTuned = false;

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
        VulkanPlusMod.LOGGER.info("[VulkanPlus] Initializing VulkanMod Companion Bridge...");

        VulkanModGuiIntegration.register();

        isInitialized = true;
    }

    private static boolean isVulkanMemoryManagerReady() {
        try {
            return net.vulkanmod.vulkan.Vulkan.getAllocator() != 0L
                    && net.vulkanmod.vulkan.Renderer.getInstance() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public void tryTuneSwapchain() {
        if (swapchainTuned) {
            return;
        }
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || !config.enableSwapchainTuning) {
            return;
        }
        try {
            if (net.vulkanmod.Initializer.CONFIG != null
                    && net.vulkanmod.vulkan.Vulkan.getAllocator() != 0L
                    && net.vulkanmod.vulkan.memory.MemoryTypes.GPU_MEM != null
                    && net.vulkanmod.vulkan.memory.MemoryTypes.GPU_MEM.vkMemoryHeap != null) {
                long deviceMb = net.vulkanmod.vulkan.memory.MemoryTypes.GPU_MEM.vkMemoryHeap.size() / (1024L * 1024L);
                if (deviceMb >= 2048 && net.vulkanmod.Initializer.CONFIG.frameQueueSize < 3) {
                    net.vulkanmod.Initializer.CONFIG.frameQueueSize = 3;
                    scheduleSwapChainUpdateIfNeeded();
                    VulkanPlusMod.LOGGER.info("[VulkanPlus] Tuned VulkanMod frameQueueSize to 3 for triple-buffered frame pacing ({} MB VRAM).", deviceMb);
                }
                swapchainTuned = true;
            }
        } catch (Throwable ignored) {
        }
    }

    public static void scheduleSwapChainUpdateIfNeeded() {
        try {
            if (net.vulkanmod.vulkan.Renderer.getInstance() != null) {
                net.vulkanmod.vulkan.Renderer.scheduleSwapChainUpdate();
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onRenderFrameBegin() {
        if (!isInitialized) return;

        if (!swapchainTuned) {
            tryTuneSwapchain();
        }

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
        try {
            long allocator = net.vulkanmod.vulkan.Vulkan.getAllocator();
            PersistentVmaManager.cleanupAll(allocator);
        } catch (Throwable t) {
            PersistentVmaManager.cleanupAll(0L);
        }
        if (ringBuffer != null) {
            ringBuffer.destroy();
            this.ringBuffer = null;
        }
        if (slabAllocator != null) {
            slabAllocator.clear();
            this.slabAllocator = null;
        }
    }

    @Override
    public long getVramUsed() {
        if (isVulkanMemoryManagerReady()) {
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
        if (isVulkanMemoryManagerReady()) {
            try {
                net.vulkanmod.vulkan.memory.MemoryManager mm = net.vulkanmod.vulkan.memory.MemoryManager.getInstance();
                if (mm != null) {
                    long allocatedBytes = (mm.getAllocatedDeviceMemoryMB() + mm.getNativeMemoryMB()) * 1024L * 1024L;
                    if (allocatedBytes > 0) {
                        return allocatedBytes;
                    }
                    long deviceMb = mm.getDeviceMemoryMB();
                    if (deviceMb > 0) {
                        return deviceMb * 1024L * 1024L;
                    }
                }
            } catch (Throwable ignored) {
            }
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
        TransientRingBuffer rb = this.ringBuffer;
        if (rb == null && ConfigManager.getConfig().enableBufferPooling) {
            synchronized (this) {
                rb = this.ringBuffer;
                if (rb == null) {
                    rb = new TransientRingBuffer(2 * 1024 * 1024);
                    this.ringBuffer = rb;
                    VulkanPlusMod.LOGGER.info("[VulkanPlus] Transient ring buffer lazily initialized.");
                }
            }
        }
        return rb;
    }

    public SlabSubAllocator getSlabAllocator() {
        SlabSubAllocator sa = this.slabAllocator;
        if (sa == null && ConfigManager.getConfig().enableBufferPooling) {
            synchronized (this) {
                sa = this.slabAllocator;
                if (sa == null) {
                    sa = new SlabSubAllocator();
                    this.slabAllocator = sa;
                    VulkanPlusMod.LOGGER.info("[VulkanPlus] Slab sub-allocator lazily initialized.");
                }
            }
        }
        return sa;
    }
}
