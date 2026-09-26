package net.vulkanplus.memory;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanplus.VulkanPlusMod;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.vulkan.VK10;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Manages persistent virtual memory address mappings for host-visible and ReBAR (Resizable BAR)
 * VMA allocations in VulkanMod.
 *
 * <p>Key optimizations:
 * <ul>
 *   <li>Guards a {@link Long2LongOpenHashMap} mapping cache with a {@link ReentrantReadWriteLock}
 *       for high-throughput multi-threaded read access without contention.</li>
 *   <li>Pre-maps host-visible allocations and serves cached 64-bit pointers without JNI roundtrips.</li>
 *   <li>Supplies a reusable thread-local {@link PointerBuffer} to completely eliminate off-heap
 *       {@code MemoryUtil.memAllocPointer(1)} leaks in {@code MemoryManager#Map}.</li>
 *   <li>Bypasses redundant {@code vmaMapMemory} and {@code vmaUnmapMemory} per-frame calls in
 *       {@code MemoryManager#MapAndCopy}.</li>
 *   <li>Evicts pointers safely via {@link #onAllocationFreed(long)} and {@link #cleanupAll(long)}.</li>
 * </ul>
 */
public final class PersistentVmaManager {

    private static final Long2LongOpenHashMap MAPPING_CACHE = new Long2LongOpenHashMap();
    private static final ReentrantReadWriteLock RW_LOCK = new ReentrantReadWriteLock();
    private static final Lock READ_LOCK = RW_LOCK.readLock();
    private static final Lock WRITE_LOCK = RW_LOCK.writeLock();

    /**
     * Reusable thread-local 1-element PointerBuffer.
     * Prevents off-heap memory leaks from repeatedly calling {@code MemoryUtil.memAllocPointer(1)}.
     */
    private static final ThreadLocal<PointerBuffer> TL_POINTER_BUFFER = ThreadLocal.withInitial(() -> MemoryUtil.memAllocPointer(1));

    static {
        MAPPING_CACHE.defaultReturnValue(0L);
    }

    private PersistentVmaManager() {
    }

    /**
     * Retrieves the cached mapped virtual address of the allocation or maps it via {@code vmaMapMemory}.
     *
     * @param allocator  VMA allocator handle (if 0, falls back to {@link Vulkan#getAllocator()})
     * @param allocation VMA allocation handle
     * @return 64-bit virtual memory address pointer, or 0L if mapping failed
     */
    public static long getOrCreateMapping(long allocator, long allocation) {
        if (allocation == 0L) {
            return 0L;
        }

        READ_LOCK.lock();
        try {
            long ptr = MAPPING_CACHE.get(allocation);
            if (ptr != 0L) {
                return ptr;
            }
        } finally {
            READ_LOCK.unlock();
        }

        WRITE_LOCK.lock();
        try {
            long ptr = MAPPING_CACHE.get(allocation);
            if (ptr != 0L) {
                return ptr;
            }

            if (allocator == 0L) {
                try {
                    allocator = Vulkan.getAllocator();
                } catch (Throwable ignored) {
                }
            }

            if (allocator == 0L) {
                return 0L;
            }

            PointerBuffer pb = TL_POINTER_BUFFER.get();
            pb.position(0);
            int result = Vma.vmaMapMemory(allocator, allocation, pb);
            if (result == VK10.VK_SUCCESS) {
                ptr = pb.get(0);
                MAPPING_CACHE.put(allocation, ptr);
                return ptr;
            } else {
                VulkanPlusMod.LOGGER.warn("[VulkanPlus] Failed to map VMA allocation 0x{}: error code {}",
                        Long.toHexString(allocation), result);
                return 0L;
            }
        } finally {
            WRITE_LOCK.unlock();
        }
    }

    /**
     * Overload using the default VulkanMod allocator.
     */
    public static long getOrCreateMapping(long allocation) {
        return getOrCreateMapping(0L, allocation);
    }

    /**
     * Pre-maps an allocation and records its pointer in the cache immediately.
     */
    public static void preMapAllocation(long allocator, long allocation) {
        if (allocation != 0L) {
            getOrCreateMapping(allocator, allocation);
        }
    }

    /**
     * Pre-maps an allocation using the default VulkanMod allocator.
     */
    public static void preMapAllocation(long allocation) {
        preMapAllocation(0L, allocation);
    }

    /**
     * Returns a reusable thread-local {@link PointerBuffer} containing the virtual memory
     * address of the specified allocation. Replaces {@code MemoryUtil.memAllocPointer(1)} in
     * {@code MemoryManager#Map}.
     *
     * @param allocation VMA allocation handle
     * @return reusable 1-element PointerBuffer at position 0
     */
    public static PointerBuffer getMappedPointerBuffer(long allocation) {
        return getMappedPointerBuffer(0L, allocation);
    }

    /**
     * Returns a reusable thread-local {@link PointerBuffer} with explicit allocator.
     */
    public static PointerBuffer getMappedPointerBuffer(long allocator, long allocation) {
        long ptr = getOrCreateMapping(allocator, allocation);
        PointerBuffer pb = TL_POINTER_BUFFER.get();
        pb.put(0, ptr);
        pb.position(0);
        return pb;
    }

    /**
     * Direct pointer accessor for high-performance memory copying without creating buffers.
     */
    public static long getMappedPointer(long allocation) {
        return getOrCreateMapping(0L, allocation);
    }

    /**
     * Bypasses repetitive {@code vmaMapMemory} and {@code vmaUnmapMemory} roundtrips by
     * executing the consumer with the persistently mapped pointer buffer.
     *
     * @param allocation VMA allocation handle
     * @param consumer   data transfer consumer
     */
    public static void executeCachedMapAndCopy(long allocation, Consumer<PointerBuffer> consumer) {
        executeCachedMapAndCopy(0L, allocation, consumer);
    }

    /**
     * Bypasses repetitive {@code vmaMapMemory} and {@code vmaUnmapMemory} with explicit allocator.
     */
    public static void executeCachedMapAndCopy(long allocator, long allocation, Consumer<PointerBuffer> consumer) {
        if (consumer == null) return;
        long ptr = getOrCreateMapping(allocator, allocation);
        if (ptr == 0L) {
            throw new IllegalStateException("Failed to obtain persistent mapping for allocation 0x" + Long.toHexString(allocation));
        }

        PointerBuffer pb = TL_POINTER_BUFFER.get();
        pb.put(0, ptr);
        pb.position(0);
        consumer.accept(pb);
    }

    /**
     * Called when an allocation is freed to safely invalidate and evict its cached pointer.
     *
     * @param allocation VMA allocation handle
     */
    public static void onAllocationFreed(long allocation) {
        if (allocation == 0L) return;

        WRITE_LOCK.lock();
        try {
            MAPPING_CACHE.remove(allocation);
        } finally {
            WRITE_LOCK.unlock();
        }
    }

    /**
     * Called when an allocation is freed with explicit allocator handle.
     */
    public static void onAllocationFreed(long allocator, long allocation) {
        onAllocationFreed(allocation);
    }

    /**
     * Releases and unmaps all persistently cached allocations and resets the cache.
     *
     * @param allocator VMA allocator handle
     */
    public static void cleanupAll(long allocator) {
        WRITE_LOCK.lock();
        try {
            if (allocator != 0L && !MAPPING_CACHE.isEmpty()) {
                for (long allocation : MAPPING_CACHE.keySet()) {
                    try {
                        Vma.vmaUnmapMemory(allocator, allocation);
                    } catch (Throwable ignored) {
                    }
                }
            }
            int cleared = MAPPING_CACHE.size();
            MAPPING_CACHE.clear();
            if (cleared > 0) {
                VulkanPlusMod.LOGGER.info("[VulkanPlus] PersistentVmaManager unmapped and evicted {} cached mappings.", cleared);
            }
        } finally {
            WRITE_LOCK.unlock();
        }
    }

    /**
     * Releases and unmaps all persistently cached allocations using the default Vulkan allocator.
     */
    public static void cleanupAll() {
        long allocator = 0L;
        try {
            allocator = Vulkan.getAllocator();
        } catch (Throwable ignored) {
        }
        cleanupAll(allocator);
    }

    /**
     * Returns the current number of active cached allocations.
     */
    public static int getCachedMappingCount() {
        READ_LOCK.lock();
        try {
            return MAPPING_CACHE.size();
        } finally {
            READ_LOCK.unlock();
        }
    }
}
