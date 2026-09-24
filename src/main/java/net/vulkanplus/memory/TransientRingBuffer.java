package net.vulkanplus.memory;

import java.nio.ByteBuffer;

/**
 * Ring-buffered transient memory allocator for dynamic per-frame vertex and uniform data.
 * Adopts triple-buffering to allow the GPU to read from frame N-1/N-2 while the CPU writes to frame N.
 */
public class TransientRingBuffer {
    public static final int DEFAULT_FRAME_COUNT = 3;
    private final int totalSize;
    private final int frameSize;
    private final int frameCount;
    private ByteBuffer backingBuffer;

    private int currentFrameIndex = 0;
    private int currentOffset = 0;
    private long totalAllocatedBytes = 0;

    public TransientRingBuffer(int frameSize) {
        this(frameSize, DEFAULT_FRAME_COUNT);
    }

    public TransientRingBuffer(int frameSize, int frameCount) {
        if (frameSize <= 0 || frameCount <= 0) {
            throw new IllegalArgumentException("frameSize and frameCount must be positive");
        }
        this.frameSize = frameSize;
        this.frameCount = frameCount;
        this.totalSize = frameSize * frameCount;
    }

    /**
     * Sub-allocates a slice of transient memory aligned to specified alignment.
     */
    public synchronized ByteBuffer allocate(int size, int alignment) {
        if (size <= 0) throw new IllegalArgumentException("Allocation size must be positive");
        if (size > frameSize) {
            throw new OutOfMemoryError("Requested allocation (" + size + " bytes) exceeds frame capacity (" + frameSize + " bytes)");
        }

        if (backingBuffer == null) {
            backingBuffer = ByteBuffer.allocateDirect(totalSize);
        }

        int alignedOffset = (currentOffset + (alignment - 1)) & ~(alignment - 1);
        int frameStart = currentFrameIndex * frameSize;
        int frameEnd = frameStart + frameSize;

        if (alignedOffset + size > frameEnd) {
            throw new OutOfMemoryError("Transient ring buffer frame " + currentFrameIndex + " overflowed!");
        }

        currentOffset = alignedOffset + size;
        totalAllocatedBytes += size;

        backingBuffer.limit(totalSize);
        backingBuffer.position(alignedOffset);
        backingBuffer.limit(alignedOffset + size);
        return backingBuffer.slice();
    }

    public synchronized boolean isBackingAllocated() {
        return backingBuffer != null;
    }

    /**
     * Advances to the next frame ring segment. Resets the frame write pointer.
     */
    public synchronized void advanceFrame() {
        currentFrameIndex = (currentFrameIndex + 1) % frameCount;
        currentOffset = currentFrameIndex * frameSize;
    }

    public synchronized void reset() {
        currentFrameIndex = 0;
        currentOffset = 0;
        totalAllocatedBytes = 0;
    }

    public int getCurrentFrameIndex() {
        return currentFrameIndex;
    }

    public int getFrameSize() {
        return frameSize;
    }

    public int getTotalSize() {
        return totalSize;
    }

    public synchronized int getUsedBytesInCurrentFrame() {
        return currentOffset - (currentFrameIndex * frameSize);
    }

    public synchronized long getTotalAllocatedBytes() {
        return totalAllocatedBytes;
    }
}
