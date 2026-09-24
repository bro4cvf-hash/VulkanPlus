package net.vulkanplus.mixin.vulkan;

import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanplus.VulkanPlusMod;
import net.vulkanplus.bridge.RenderEngineBridge;
import net.vulkanplus.bridge.VulkanDetector;
import net.vulkanplus.bridge.impl.VulkanModBridgeImpl;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.vulkan.PersistentPipelineCache;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Hooks VulkanMod's Pipeline cache lifecycle to load and save persistent PSO cache data across game sessions.
 */
@Mixin(value = Pipeline.class, remap = false)
public abstract class PipelineMixin {

    @Shadow
    @Final
    private static VkDevice DEVICE;

    @Shadow
    @Final
    protected static long PIPELINE_CACHE;

    @Inject(method = "createPipelineCache", at = @At("HEAD"), cancellable = true)
    private static void vulkanplus$loadPersistentPipelineCache(CallbackInfoReturnable<Long> cir) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg == null || !cfg.enabled || !cfg.enablePsoCache || DEVICE == null) {
            return;
        }

        try {
            VkPhysicalDeviceProperties props = DeviceManager.deviceProperties;
            if (props == null) {
                return;
            }

            int vendorId = props.vendorID();
            int deviceId = props.deviceID();
            byte[] uuid = new byte[16];
            ByteBuffer uuidBuf = props.pipelineCacheUUID();
            for (int i = 0; i < 16; i++) {
                uuid[i] = uuidBuf.get(i);
            }

            PersistentPipelineCache psoCache = getPsoCacheInstance();
            ByteBuffer cachedData = psoCache.loadCacheData(vendorId, deviceId, uuid);
            if (cachedData == null || cachedData.remaining() < PersistentPipelineCache.HEADER_SIZE) {
                return;
            }

            ByteBuffer directData = MemoryUtil.memAlloc(cachedData.remaining());
            try (MemoryStack stack = MemoryStack.stackPush()) {
                directData.put(cachedData.duplicate());
                directData.flip();

                VkPipelineCacheCreateInfo cacheCreateInfo = VkPipelineCacheCreateInfo.calloc(stack);
                cacheCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO);
                cacheCreateInfo.pInitialData(directData);

                LongBuffer pPipelineCache = stack.mallocLong(1);
                if (VK10.vkCreatePipelineCache(DEVICE, cacheCreateInfo, null, pPipelineCache) == VK10.VK_SUCCESS) {
                    cir.setReturnValue(pPipelineCache.get(0));
                }
            } finally {
                MemoryUtil.memFree(directData);
            }
        } catch (Throwable t) {
            VulkanPlusMod.LOGGER.debug("[VulkanPlus] Falling back to empty VkPipelineCache: {}", t.getMessage());
        }
    }

    @Inject(method = "destroyPipelineCache", at = @At("HEAD"))
    private static void vulkanplus$savePersistentPipelineCache(CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg == null || !cfg.enabled || !cfg.enablePsoCache || DEVICE == null || PIPELINE_CACHE == 0L) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pDataSize = stack.mallocPointer(1);
            if (VK10.vkGetPipelineCacheData(DEVICE, PIPELINE_CACHE, pDataSize, null) != VK10.VK_SUCCESS) {
                return;
            }
            int size = (int) pDataSize.get(0);
            if (size < PersistentPipelineCache.HEADER_SIZE) {
                return;
            }

            ByteBuffer directBuf = MemoryUtil.memAlloc(size);
            try {
                if (VK10.vkGetPipelineCacheData(DEVICE, PIPELINE_CACHE, pDataSize, directBuf) == VK10.VK_SUCCESS) {
                    byte[] bytes = new byte[size];
                    directBuf.get(bytes);
                    getPsoCacheInstance().saveCacheData(bytes);
                }
            } finally {
                MemoryUtil.memFree(directBuf);
            }
        } catch (Throwable t) {
            VulkanPlusMod.LOGGER.debug("[VulkanPlus] Could not persist VkPipelineCache on shutdown: {}", t.getMessage());
        }
    }

    private static PersistentPipelineCache getPsoCacheInstance() {
        RenderEngineBridge bridge = VulkanDetector.getBridge();
        if (bridge instanceof VulkanModBridgeImpl vkBridge) {
            return vkBridge.getPsoCache();
        }
        return new PersistentPipelineCache();
    }
}
