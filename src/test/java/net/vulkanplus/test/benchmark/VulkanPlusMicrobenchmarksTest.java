package net.vulkanplus.test.benchmark;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.math.FastMath;
import net.vulkanplus.memory.MatrixPool;
import net.vulkanplus.memory.SlabSubAllocator;
import net.vulkanplus.memory.TransientRingBuffer;
import net.vulkanplus.render.FrustumCuller;
import net.vulkanplus.vulkan.VulkanStateCache;
import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

public class VulkanPlusMicrobenchmarksTest {

    @Test
    @DisplayName("VulkanStateCache multi-slot and dynamic state filtering test & benchmark")
    public void testVulkanStateCacheMultiSlotFiltering() {
        VulkanStateCache cache = new VulkanStateCache();

        // Multi-slot descriptor set tests
        assertTrue(cache.checkAndBindDescriptorSet(0, 100L));
        assertFalse(cache.checkAndBindDescriptorSet(0, 100L));
        assertTrue(cache.checkAndBindDescriptorSet(1, 200L));
        assertFalse(cache.checkAndBindDescriptorSet(1, 200L));
        assertFalse(cache.checkAndBindDescriptorSet(0, 100L), "Slot 0 must remain cached while slot 1 was updated");

        // Bind points
        assertTrue(cache.checkAndBindPipeline(VulkanStateCache.BIND_POINT_GRAPHICS, 500L));
        assertFalse(cache.checkAndBindPipeline(VulkanStateCache.BIND_POINT_GRAPHICS, 500L));
        assertTrue(cache.checkAndBindPipeline(VulkanStateCache.BIND_POINT_COMPUTE, 600L));
        assertFalse(cache.checkAndBindPipeline(VulkanStateCache.BIND_POINT_GRAPHICS, 500L), "Graphics pipeline must remain cached");

        // Dynamic states
        assertTrue(cache.checkDepthBias(1.0f, 0.0f, 1.0f));
        assertFalse(cache.checkDepthBias(1.0f, 0.0f, 1.0f));

        assertTrue(cache.checkBlendConstants(1.0f, 1.0f, 1.0f, 1.0f));
        assertFalse(cache.checkBlendConstants(1.0f, 1.0f, 1.0f, 1.0f));

        assertTrue(cache.checkLineWidth(2.0f));
        assertFalse(cache.checkLineWidth(2.0f));

        assertTrue(cache.checkStencilReference(5));
        assertFalse(cache.checkStencilReference(5));

        assertTrue(cache.getRedundantBindsPrevented() >= 8);
        assertTrue(cache.getSkippedDynamicStateUpdates() >= 4);

        // Throughput benchmark: 1,000,000 checks
        long start = System.nanoTime();
        int redundant = 0;
        for (int i = 0; i < 1_000_000; i++) {
            if (!cache.checkAndBindDescriptorSet(0, 100L)) {
                redundant++;
            }
        }
        long durationNs = System.nanoTime() - start;
        double opsPerSec = (1_000_000.0 / durationNs) * 1e9;
        System.out.printf("[Benchmark] VulkanStateCache filtering throughput: %.2f M ops/sec (%.2f ns/op)%n",
                opsPerSec / 1e6, (double) durationNs / 1_000_000);
        assertEquals(1_000_000, redundant);
    }

    @Test
    @DisplayName("FastMath trigonometric and hypot accuracy & speed benchmark")
    public void testFastMathSpeedAndAccuracy() {
        // Degree trig accuracy
        for (float deg = 0; deg <= 360; deg += 15.0f) {
            float expectedSin = (float) Math.sin(Math.toRadians(deg));
            float actualSin = FastMath.sinDeg(deg);
            assertEquals(expectedSin, actualSin, 1e-4f, "sinDeg error exceeded 1e-4 at " + deg + " deg");

            float expectedCos = (float) Math.cos(Math.toRadians(deg));
            float actualCos = FastMath.cosDeg(deg);
            assertEquals(expectedCos, actualCos, 1e-4f, "cosDeg error exceeded 1e-4 at " + deg + " deg");
        }

        // Fast hypot accuracy
        float h = FastMath.fastHypot(3.0f, 4.0f);
        assertEquals(5.0f, h, 1e-4f);

        // Benchmark FastMath.fastHypot vs Math.hypot across 500,000 ops
        long startStd = System.nanoTime();
        double sumStd = 0.0;
        for (int i = 0; i < 500_000; i++) {
            sumStd += Math.hypot(i * 0.01, (i + 1) * 0.01);
        }
        long durationStd = System.nanoTime() - startStd;

        long startFast = System.nanoTime();
        float sumFast = 0.0f;
        for (int i = 0; i < 500_000; i++) {
            sumFast += FastMath.fastHypot(i * 0.01f, (i + 1) * 0.01f);
        }
        long durationFast = System.nanoTime() - startFast;

        System.out.printf("[Benchmark] Math.hypot: %.2f ms | FastMath.fastHypot: %.2f ms (Speedup: %.2fx)%n",
                durationStd / 1e6, durationFast / 1e6, (double) durationStd / durationFast);
        assertTrue(sumFast > 0);
    }

    @Test
    @DisplayName("FrustumCuller unrolled 6-plane visibility benchmark")
    public void testFrustumCullerUnrolledBenchmark() {
        FrustumCuller culler = new FrustumCuller();
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 16.0f / 9.0f, 0.05f, 1000.0f);
        culler.updateFrustum(proj, 0, 0, 0);

        // Directly in front
        assertTrue(culler.isAabbVisible(-1, -1, -20, 1, 1, -18));
        // Behind camera
        assertFalse(culler.isAabbVisible(-1, -1, 5, 1, 1, 10));

        // Benchmark 1,000,000 unrolled AABB tests
        long start = System.nanoTime();
        int visibleCount = 0;
        for (int i = 0; i < 1_000_000; i++) {
            float z = -5.0f - (i % 200);
            if (culler.isRelativeAabbVisible(-0.5f, -0.5f, z - 0.5f, 0.5f, 0.5f, z + 0.5f)) {
                visibleCount++;
            }
        }
        long durationNs = System.nanoTime() - start;
        double opsPerSec = (1_000_000.0 / durationNs) * 1e9;
        System.out.printf("[Benchmark] FrustumCuller AABB test throughput: %.2f M tests/sec (%.2f ns/test)%n",
                opsPerSec / 1e6, (double) durationNs / 1_000_000);
        assertTrue(visibleCount > 0);
    }

    @Test
    @DisplayName("TransientRingBuffer zero-allocation offset benchmark")
    public void testTransientRingBufferZeroAllocBenchmark() {
        TransientRingBuffer ring = new TransientRingBuffer(1024 * 1024);

        // Test offset vs slice
        int offset0 = ring.allocateOffset(256, 16);
        assertEquals(0, offset0);

        int offset1 = ring.allocateOffset(256, 16);
        assertEquals(256, offset1);

        assertNotNull(ring.getBackingBuffer());

        // Benchmark 500,000 offset allocations
        long start = System.nanoTime();
        ring.reset();
        for (int i = 0; i < 500_000; i++) {
            ring.allocateOffset(64, 16);
            if ((i & 0x1FFF) == 0) {
                ring.advanceFrame();
            }
        }
        long durationNs = System.nanoTime() - start;
        System.out.printf("[Benchmark] TransientRingBuffer zero-object offset allocation: %.2f ns/op%n",
                (double) durationNs / 500_000);
    }

    @Test
    @DisplayName("ConfigManager Exordium roundtrip persistence")
    public void testConfigManagerExordiumRoundtrip() {
        VulkanPlusConfig cfg = new VulkanPlusConfig();
        cfg.enableExordium = true;
        cfg.hudTargetFps = 144;
        cfg.screenTargetFps = 120;
        cfg.dynamicHudUpdates = true;
        cfg.separateCrosshair = false;
        cfg.bypassInDebugScreen = false;
        cfg.fastFadeTransitions = true;

        String json = ConfigManager.toJson(cfg);
        assertTrue(json.contains("\"enableExordium\": true"));
        assertTrue(json.contains("\"hudTargetFps\": 144"));
        assertTrue(json.contains("\"screenTargetFps\": 120"));

        VulkanPlusConfig parsed = new VulkanPlusConfig();
        ConfigManager.parseJson(json, parsed);

        assertTrue(parsed.enableExordium);
        assertEquals(144, parsed.hudTargetFps);
        assertEquals(120, parsed.screenTargetFps);
        assertTrue(parsed.dynamicHudUpdates);
        assertFalse(parsed.separateCrosshair);
        assertFalse(parsed.bypassInDebugScreen);
        assertTrue(parsed.fastFadeTransitions);
    }
}
