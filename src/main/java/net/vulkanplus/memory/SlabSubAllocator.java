package net.vulkanplus.memory;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Power-of-two slab memory sub-allocator for recycling discrete buffer blocks (VBO/EBO/Uniform blocks).
 * Prevents OS and driver allocation stalls by keeping a pool of reusable direct buffers.
 */
public class SlabSubAllocator {
    public static final int MIN_SLAB_SIZE = 16;
    public static final int MAX_SLAB_SIZE = 65536; // 64 KB

    private final Map<Integer, Deque<ByteBuffer>> freeBuckets = new HashMap<>();
    private long totalAllocatedBytes = 0;
    private long activeBytesInUse = 0;

    public SlabSubAllocator() {
        for (int size = MIN_SLAB_SIZE; size <= MAX_SLAB_SIZE; size <<= 1) {
            freeBuckets.put(size, new ArrayDeque<>());
        }
    }

    /**
     * Allocates or reuses a direct ByteBuffer of at least the requested capacity.
     */
    public synchronized ByteBuffer allocate(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        int slabSize = roundUpToPowerOfTwo(capacity);

        Deque<ByteBuffer> bucket = freeBuckets.get(slabSize);
        if (bucket != null && !bucket.isEmpty()) {
            ByteBuffer buffer = bucket.pop();
            buffer.clear();
            buffer.limit(capacity);
            activeBytesInUse += slabSize;
            return buffer;
        }

        ByteBuffer buffer = ByteBuffer.allocateDirect(slabSize);
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
        Deque<ByteBuffer> bucket = freeBuckets.get(capacity);
        if (bucket != null) {
            buffer.clear();
            bucket.push(buffer);
            activeBytesInUse = Math.max(0, activeBytesInUse - capacity);
        }
    }

    public synchronized void clear() {
        for (Deque<ByteBuffer> bucket : freeBuckets.values()) {
            bucket.clear();
        }
        activeBytesInUse = 0;
    }

    public static int roundUpToPowerOfTwo(int value) {
        int v = Math.max(MIN_SLAB_SIZE, value);
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v++;
        return v;
    }

    public synchronized long getTotalAllocatedBytes() {
        return totalAllocatedBytes;
    }

    public synchronized long getActiveBytesInUse() {
        return activeBytesInUse;
    }
}
