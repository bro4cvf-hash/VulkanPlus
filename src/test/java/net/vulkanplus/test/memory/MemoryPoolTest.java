package net.vulkanplus.test.memory;

import net.vulkanplus.memory.MatrixPool;
import net.vulkanplus.memory.SlabSubAllocator;
import net.vulkanplus.memory.TransientRingBuffer;
import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

public class MemoryPoolTest {

    @Test
    @DisplayName("TransientRingBuffer allocates aligned memory and advances frames correctly")
    public void testTransientRingBuffer() {
        int frameSize = 1024;
        TransientRingBuffer ring = new TransientRingBuffer(frameSize, 3);

        assertEquals(0, ring.getCurrentFrameIndex());
        assertEquals(3072, ring.getTotalSize());

        // Allocate 128 bytes with 64-byte alignment
        ByteBuffer b1 = ring.allocate(128, 64);
        assertNotNull(b1);
        assertEquals(128, b1.remaining());

        // Next frame
        ring.advanceFrame();
        assertEquals(1, ring.getCurrentFrameIndex());

        ByteBuffer b2 = ring.allocate(256, 16);
        assertNotNull(b2);
        assertEquals(256, b2.remaining());

        // Wrap around through frame 2 back to frame 0
        ring.advanceFrame();
        assertEquals(2, ring.getCurrentFrameIndex());
        ring.advanceFrame();
        assertEquals(0, ring.getCurrentFrameIndex());
    }

    @Test
    @DisplayName("TransientRingBuffer throws OutOfMemoryError on frame overflow")
    public void testTransientRingBufferOverflow() {
        TransientRingBuffer ring = new TransientRingBuffer(512, 2);
        assertThrows(OutOfMemoryError.class, () -> ring.allocate(1024, 16));
    }

    @Test
    @DisplayName("SlabSubAllocator allocates power-of-two blocks and recycles freed buffers")
    public void testSlabSubAllocator() {
        SlabSubAllocator slab = new SlabSubAllocator();

        // 50 bytes should round to 64-byte slab
        ByteBuffer buf = slab.allocate(50);
        assertNotNull(buf);
        assertEquals(64, buf.capacity());
        assertEquals(50, buf.limit());
        assertEquals(64, slab.getActiveBytesInUse());

        // Free back to pool
        slab.free(buf);
        assertEquals(0, slab.getActiveBytesInUse());

        // Reallocate 60 bytes, should reuse the 64-byte buffer
        ByteBuffer reused = slab.allocate(60);
        assertSame(buf, reused, "Freed slab buffer must be recycled and reused");
        assertEquals(64, slab.getActiveBytesInUse());

        slab.clear();
    }

    @Test
    @DisplayName("MatrixPool Scope provides reusable instances and recycles on close")
    public void testMatrixPoolScope() {
        MatrixPool pool = MatrixPool.get();

        Matrix4f instance1;
        try (MatrixPool.Scope scope = MatrixPool.openScope()) {
            instance1 = scope.matrix4f();
            assertNotNull(instance1);
            instance1.translate(5.0f, 10.0f, 15.0f);
        }

        // Next scope should reuse instance1 and reset it to identity
        try (MatrixPool.Scope scope = MatrixPool.openScope()) {
            Matrix4f instance2 = scope.matrix4f();
            assertEquals(new Matrix4f(), instance2, "Recycled matrix must be reset to identity");
        }
    }
}
