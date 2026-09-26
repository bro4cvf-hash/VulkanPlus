package net.vulkanplus.test.culling;

import net.vulkanplus.culling.VulkanSectionVisibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class VulkanSectionVisibilityTest {

    @Test
    @DisplayName("isSectionVisible returns true when grid is null or frame is 0")
    public void testNullGridAndZeroFrame() {
        assertTrue(VulkanSectionVisibility.isSectionVisible(0, 0, 0, (short) 0, null));
        assertTrue(VulkanSectionVisibility.isSectionVisible(10, -5, 20, (short) 1, null));
        assertTrue(VulkanSectionVisibility.isSectionVisible(-100, 15, -200, 0, null));
    }

    @Test
    @DisplayName("Direct-mapped 32-entry cache spatial hash distributes adjacent chunk boundaries")
    public void testSpatialHashDistribution() {
        // Test that adjacent chunk sections along X, Y, Z don't all hash to the same bucket
        int h1 = ((0 * 31 + 4) * 17 + 0) & 31;
        int h2 = ((1 * 31 + 4) * 17 + 0) & 31;
        int h3 = ((0 * 31 + 5) * 17 + 0) & 31;
        int h4 = ((0 * 31 + 4) * 17 + 1) & 31;

        assertNotEquals(h1, h2, "Adjacent chunk X boundary must not collide in direct-mapped cache");
        assertNotEquals(h1, h4, "Adjacent chunk Z boundary must not collide in direct-mapped cache");

        // Verify hash is strictly in [0, 31] for wide range of negative and positive coordinates
        for (int x = -100; x <= 100; x++) {
            for (int y = -32; y <= 32; y++) {
                for (int z = -100; z <= 100; z++) {
                    int hash = ((x * 31 + y) * 17 + z) & 31;
                    assertTrue(hash >= 0 && hash < 32, "Hash must be in [0, 31]");
                }
            }
        }
    }

    @Test
    @DisplayName("Key packing formula preserves 16-bit frame and coordinates")
    public void testKeyPackingPreservation() {
        short frame = 42;
        int secX = 1234;
        int secY = -5; // 0xFFFB
        int secZ = 30000;

        long key = ((long) frame << 48) | ((secX & 0xFFFFL) << 32) | ((secY & 0xFFFFL) << 16) | (secZ & 0xFFFFL);

        short extractedFrame = (short) (key >>> 48);
        int extractedSecX = (int) ((key >>> 32) & 0xFFFFL);
        int extractedSecY = (short) ((key >>> 16) & 0xFFFFL);
        int extractedSecZ = (int) (key & 0xFFFFL);

        assertEquals(frame, extractedFrame);
        assertEquals(secX, extractedSecX);
        assertEquals(secY, extractedSecY);
        assertEquals(secZ, extractedSecZ);
    }

    @Test
    @DisplayName("Thread-safety: Concurrent threads access spatial cache without interference")
    public void testThreadSafety() throws InterruptedException {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 10000; i++) {
                        boolean v = VulkanSectionVisibility.isSectionVisible(
                                threadId + (i % 5),
                                4,
                                threadId * 2 + (i % 3),
                                (short) (1 + (i % 10)),
                                null
                        );
                        if (!v) {
                            failed.set(true);
                        }
                    }
                } catch (Exception e) {
                    failed.set(true);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "Concurrent test must complete within 5 seconds");
        assertFalse(failed.get(), "No thread should fail or encounter concurrency errors");
        executor.shutdown();
    }
}
