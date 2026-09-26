package net.vulkanplus.memory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;

/**
 * Power-of-two slab memory sub-allocator for recycling discrete buffer blocks (VBO/EBO/Uniform blocks).
 * Prevents OS and driver allocation stalls by keeping a pool of reusable direct buffers.
 */
public class SlabSubAllocator {
    public static final int MIN_SLAB_SIZE = 64; // 2^6
    public static final int MAX_SLAB_SIZE = 65536; // 2^16 (64 KB)
    public static final int MAX_SLABS_PER_BUCKET = 256;
    private static final int BUCKET_COUNT = 11; // 2^6 .. 2^16 inclusive
    private static final int MIN_SLAB_SHIFT = 6;

    @SuppressWarnings("unchecked")
    private final ArrayDeque<ByteBuffer>[] freeBuckets = new ArrayDeque[BUCKET_COUNT];
    private long totalAllocatedBytes = 0;
    private long activeBytesInUse = 0;

    public SlabSubAllocator() {
        // Off-heap buffers and bucket queues remain lazy (0 bytes allocated) until requested
    }

    public SlabSubAllocator(int initialCapacity) {
        // Neutralized pre-allocation: 0 bytes off-heap allocated upfront
    }

    /**
     * Allocates or reuses a direct ByteBuffer of at least the requested capacity.
     */
    public synchronized ByteBuffer allocate(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        int slabSize = roundUpToPowerOfTwo(capacity);

        if (slabSize <= MAX_SLAB_SIZE) {
            int bucketIndex = Integer.numberOfTrailingZeros(slabSize) - MIN_SLAB_SHIFT;
            ArrayDeque<ByteBuffer> bucket = freeBuckets[bucketIndex];
            if (bucket != null && !bucket.isEmpty()) {
                ByteBuffer buffer = bucket.pop();
                buffer.clear();
                buffer.order(ByteOrder.nativeOrder());
                buffer.limit(capacity);
                activeBytesInUse += slabSize;
                return buffer;
            }
        }

        ByteBuffer buffer = ByteBuffer.allocateDirect(slabSize).order(ByteOrder.nativeOrder());
        buffer.limit(capacity);
        totalAllocatedBytes += slabSize;
        activeBytesInUse += slabSize;
        return buffer;
    }

    /**
     * Recycles a previously allocated buffer back into the appropriate slab bucket.
     */
    public synchronized void free(ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect()) return;
        int capacity = buffer.capacity();
        activeBytesInUse = Math.max(0L, activeBytesInUse - capacity);

        if (capacity >= MIN_SLAB_SIZE && capacity <= MAX_SLAB_SIZE && (capacity & (capacity - 1)) == 0) {
            int bucketIndex = Integer.numberOfTrailingZeros(capacity) - MIN_SLAB_SHIFT;
            ArrayDeque<ByteBuffer> bucket = freeBuckets[bucketIndex];
            if (bucket == null) {
                bucket = new ArrayDeque<>();
                freeBuckets[bucketIndex] = bucket;
            }
            if (bucket.size() < MAX_SLABS_PER_BUCKET) {
                buffer.clear();
                buffer.order(ByteOrder.nativeOrder());
                bucket.push(buffer);
                return;
            }
        }
        totalAllocatedBytes = Math.max(0L, totalAllocatedBytes - capacity);
    }

    public synchronized void trimToBudget(long maxBytes) {
        long targetBytes = Math.max(0L, maxBytes);
        for (int i = BUCKET_COUNT - 1; i >= 0 && totalAllocatedBytes > targetBytes; i--) {
            ArrayDeque<ByteBuffer> bucket = freeBuckets[i];
            if (bucket != null) {
                while (!bucket.isEmpty() && totalAllocatedBytes > targetBytes) {
                    ByteBuffer evicted = bucket.poll();
                    if (evicted != null) {
                        totalAllocatedBytes = Math.max(0L, totalAllocatedBytes - evicted.capacity());
                    }
                }
            }
        }
    }

    public synchronized void clear() {
        for (int i = 0; i < BUCKET_COUNT; i++) {
            if (freeBuckets[i] != null) {
                freeBuckets[i].clear();
                freeBuckets[i] = null;
            }
        }
        totalAllocatedBytes = 0;
        activeBytesInUse = 0;
    }

    public synchronized void destroy() {
        clear();
    }

    public synchronized boolean hasAllocatedMemory() {
        return totalAllocatedBytes > 0;
    }

    public static int roundUpToPowerOfTwo(int value) {
        int v = Math.max(MIN_SLAB_SIZE, value);
        if ((v & (v - 1)) == 0) return v;
        return 1 << (32 - Integer.numberOfLeadingZeros(v - 1));
    }

    public synchronized long getTotalAllocatedBytes() {
        return totalAllocatedBytes;
    }

    public synchronized long getActiveBytesInUse() {
        return activeBytesInUse;
    }
}
