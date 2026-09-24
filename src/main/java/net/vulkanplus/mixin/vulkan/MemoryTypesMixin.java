package net.vulkanplus.mixin.vulkan;

import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.MemoryType;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanplus.VulkanPlusMod;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkMemoryType;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;

/**
 * R1 — Resizable BAR (ReBAR) / Smart Access Memory (SAM) DeviceMappableMemory Activation.
 *
 * <p>In VulkanMod 0.6.8, {@link MemoryTypes#createMemoryTypes()} returns early after Loop 1
 * whenever {@code propertyFlags == 1} (DEVICE_LOCAL) and {@code propertyFlags == 6}
 * (HOST_VISIBLE | HOST_COHERENT) are found, making Loop 2 ({@code DeviceMappableMemory})
 * unreachable on discrete GPUs.
 *
 * <p>This mixin inspects {@link DeviceManager#memoryProperties} at {@code RETURN} of
 * {@code createMemoryTypes()} and upgrades {@link MemoryTypes#GPU_MEM} to
 * {@code MemoryTypes$DeviceMappableMemory} when a coherent device-mappable memory type
 * ({@code (propertyFlags & 7) == 7}) backed by a heap of at least 512 MB is available.
 */
@Mixin(value = MemoryTypes.class, remap = false)
public abstract class MemoryTypesMixin {

    /**
     * VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT (1)
     * | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT (2)
     * | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT (4) = 7.
     */
    @Unique
    private static final int DEVICE_LOCAL_HOST_COHERENT_FLAGS = 7;

    /** Minimum BAR heap size to safely use DeviceMappableMemory: 512 MB (536,870,912 bytes). */
    @Unique
    private static final long MIN_REBAR_HEAP_BYTES = 536_870_912L;

    @Inject(method = "createMemoryTypes", at = @At("RETURN"))
    private static void vulkanplus$enableReBarDeviceMappableMemory(CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && (!cfg.enabled || !cfg.enableBufferPooling)) {
            return;
        }

        if (MemoryTypes.GPU_MEM != null && MemoryTypes.GPU_MEM.mappable()) {
            return;
        }

        VkPhysicalDeviceMemoryProperties memProps = DeviceManager.memoryProperties;
        if (memProps == null) {
            return;
        }

        int typeCount = memProps.memoryTypeCount();
        for (int i = 0; i < typeCount; i++) {
            VkMemoryType memoryType = memProps.memoryTypes(i);
            int flags = memoryType.propertyFlags();
            if ((flags & DEVICE_LOCAL_HOST_COHERENT_FLAGS) == DEVICE_LOCAL_HOST_COHERENT_FLAGS) {
                VkMemoryHeap heap = memProps.memoryHeaps(memoryType.heapIndex());
                long heapSize = heap.size();
                if (heapSize >= MIN_REBAR_HEAP_BYTES) {
                    try {
                        Class<?> clazz = Class.forName("net.vulkanmod.vulkan.memory.MemoryTypes$DeviceMappableMemory");
                        Constructor<?> ctor = clazz.getDeclaredConstructor(VkMemoryType.class, VkMemoryHeap.class);
                        ctor.setAccessible(true);
                        MemoryTypes.GPU_MEM = (MemoryType) ctor.newInstance(memoryType, heap);
                        VulkanPlusMod.LOGGER.info(
                                "[VulkanPlus] Activated ReBAR/SAM DeviceMappableMemory (typeIndex={}, flags=0x{}, heapSize={} MB)",
                                i, Integer.toHexString(flags), heapSize / (1024L * 1024L));
                        return;
                    } catch (Throwable t) {
                        VulkanPlusMod.LOGGER.warn("[VulkanPlus] Failed to instantiate DeviceMappableMemory, keeping default GPU_MEM", t);
                        return;
                    }
                }
            }
        }
    }
}
