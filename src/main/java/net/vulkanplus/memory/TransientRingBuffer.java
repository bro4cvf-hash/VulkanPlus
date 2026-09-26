package net.vulkanplus.memory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Ring-buffered transient memory allocator for dynamic per-frame vertex and uniform data.
 * Adopts triple-buffering to allow the GPU to read from frame N-1/N-2 while the CPU writes to frame N.
 */
public class TransientRingBuffer {
    public static final int DEFAULT_FRAME_COUNT = 3;
    public static final int DEFAULT_FRAME_SIZE = 0;
    private int totalSize;
    private int frameSize;
    private int frameCount;
    private ByteBuffer backingBuffer;

    private int currentFrameIndex = 0;
    private int currentOffset = 0;
    private long totalAllocatedBytes = 0;

    public TransientRingBuffer() {
        this(DEFAULT_FRAME_SIZE, DEFAULT_FRAME_COUNT);
    }

    public TransientRingBuffer(int frameSize) {
        this(frameSize, DEFAULT_FRAME_COUNT);
    }

    public TransientRingBuffer(int frameSize, int frameCount) {
        if (frameSize < 0 || frameCount <= 0) {
            throw new IllegalArgumentException("frameSize cannot be negative and frameCount must be positive");
        }
        this.frameSize = frameSize;
        this.frameCount = frameCount;
        this.totalSize = frameSize * frameCount;
    }

    /**
     * Sub-allocates a slice and returns only the integer byte offset within the backing buffer,
     * completely eliminating the heap allocation of an intermediate ByteBuffer slice wrapper object.
     */
    public int allocateOffset(int size, int alignment) {
        if (size <= 0) throw new IllegalArgumentException("Allocation size must be positive");
        if (frameSize == 0) {
            this.frameSize = Math.max(1024 * 1024, size * 2);
            this.totalSize = this.frameSize * this.frameCount;
        }
        if (size > frameSize) {
            throw new OutOfMemoryError("Requested allocation (" + size + " bytes) exceeds frame capacity (" + frameSize + " bytes)");
        }

        if (alignment <= 0) {
            alignment = 1;
        } else if ((alignment & (alignment - 1)) != 0) {
            alignment = Integer.highestOneBit(alignment - 1) << 1;
        }

        if (backingBuffer == null) {
            if (totalSize > 0) {
                backingBuffer = ByteBuffer.allocateDirect(totalSize).order(ByteOrder.nativeOrder());
            } else {
                backingBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
            }
        }

        int alignedOffset = (currentOffset + (alignment - 1)) & ~(alignment - 1);
        int frameStart = currentFrameIndex * frameSize;
        int frameEnd = frameStart + frameSize;

        if (alignedOffset + size > frameEnd) {
            throw new OutOfMemoryError("Transient ring buffer frame " + currentFrameIndex + " overflowed!");
        }

        currentOffset = alignedOffset + size;
        totalAllocatedBytes += size;
        return alignedOffset;
    }

    /**
     * Sub-allocates a slice of transient memory aligned to specified alignment.
     */
    public ByteBuffer allocate(int size, int alignment) {
        int alignedOffset = allocateOffset(size, alignment);
        backingBuffer.limit(totalSize);
        backingBuffer.position(alignedOffset);
        backingBuffer.limit(alignedOffset + size);
        return backingBuffer.slice().order(ByteOrder.nativeOrder());
    }

    public ByteBuffer getBackingBuffer() {
        if (backingBuffer == null) {
            if (this.totalSize <= 0) {
                this.frameSize = 2 * 1024 * 1024;
                this.frameCount = DEFAULT_FRAME_COUNT;
                this.totalSize = this.frameSize * this.frameCount;
            }
            backingBuffer = ByteBuffer.allocateDirect(this.totalSize).order(ByteOrder.nativeOrder());
        }
        return backingBuffer;
    }

    public boolean isBackingAllocated() {
        return backingBuffer != null && backingBuffer.capacity() > 0;
    }

    /**
     * Advances to the next frame ring segment. Resets the frame write pointer.
     */
    public void advanceFrame() {
        currentFrameIndex = (currentFrameIndex + 1) % frameCount;
        currentOffset = currentFrameIndex * frameSize;
    }

    public void reset() {
        currentFrameIndex = 0;
        currentOffset = 0;
        totalAllocatedBytes = 0;
    }

    /**
     * Releases the backing native buffer reference and resets all ring pointers.
     */
    public void destroy() {
        backingBuffer = null;
        reset();
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

    public int getUsedBytesInCurrentFrame() {
        return currentOffset - (currentFrameIndex * frameSize);
    }

    public long getTotalAllocatedBytes() {
        return totalAllocatedBytes;
    }
}
