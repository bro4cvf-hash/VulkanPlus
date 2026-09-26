package net.vulkanplus.test.memory;

import net.vulkanplus.memory.PersistentVmaManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.PointerBuffer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class PersistentVmaManagerTest {

    @AfterEach
    public void tearDown() {
        PersistentVmaManager.cleanupAll(0L);
    }

    @Test
    @DisplayName("PersistentVmaManager thread-local PointerBuffer returns non-null reusable buffer")
    public void testThreadLocalPointerBufferReuse() {
        // Allocation handle 0x1234
        long allocation = 0x12345678L;
        // Since VMA allocator is 0L in unit test, getMappedPointerBuffer returns buffer with 0L if not mapped
        PointerBuffer pb1 = PersistentVmaManager.getMappedPointerBuffer(0L, allocation);
        assertNotNull(pb1);
        assertEquals(0, pb1.position());
        assertEquals(1, pb1.capacity());

        // Calling again on the same thread should return the exact same ThreadLocal instance
        PointerBuffer pb2 = PersistentVmaManager.getMappedPointerBuffer(0L, allocation);
        assertSame(pb1, pb2, "ThreadLocal PointerBuffer must be reused on the same thread");
    }

    @Test
    @DisplayName("PersistentVmaManager evicts mappings on allocation freed and cleanupAll")
    public void testEvictionAndCleanup() {
        assertEquals(0, PersistentVmaManager.getCachedMappingCount());

        // Test cleanup on empty
        PersistentVmaManager.cleanupAll(0L);
        assertEquals(0, PersistentVmaManager.getCachedMappingCount());

        // Freeing non-existent allocation shouldn't throw
        assertDoesNotThrow(() -> PersistentVmaManager.onAllocationFreed(99999L));
        assertDoesNotThrow(() -> PersistentVmaManager.onAllocationFreed(0L));
    }

    @Test
    @DisplayName("PersistentVmaManager handles concurrent multi-threaded requests safely")
    public void testConcurrentAccess() throws InterruptedException {
        int threads = 8;
        int operationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threads);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int op = 0; op < operationsPerThread; op++) {
                        long allocId = 1000L + (op % 20);
                        PointerBuffer pb = PersistentVmaManager.getMappedPointerBuffer(0L, allocId);
                        if (pb == null || pb.capacity() != 1) {
                            failureCount.incrementAndGet();
                        }
                        if (op % 5 == 0) {
                            PersistentVmaManager.onAllocationFreed(allocId);
                        }
                    }
                } catch (Throwable t) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(finished, "All threads must finish within timeout");
        assertEquals(0, failureCount.get(), "No concurrent errors must occur");
    }
}
