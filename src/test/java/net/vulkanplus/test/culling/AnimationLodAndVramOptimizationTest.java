package net.vulkanplus.test.culling;

import net.minecraft.util.math.BlockPos;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.memory.MatrixPool;
import net.vulkanplus.memory.SlabSubAllocator;
import net.vulkanplus.mixin.leakfix.BiomeMixin;
import net.vulkanplus.render.AnimationLodEvaluator;
import net.vulkanplus.vulkan.ReverseZProjection;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class AnimationLodAndVramOptimizationTest {

    private VulkanPlusConfig originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = ConfigManager.getConfig().copy();
    }

    @AfterEach
    void tearDown() {
        ConfigManager.setConfig(originalConfig);
    }

    @Test
    void testAnimationLodGetTickIntervalTiersAndEdgeCases() {
        double baseLodDist = 32.0;

        // Tier 0: < 16m (distSq < 256.0) -> interval 1
        assertEquals(1, AnimationLodEvaluator.getTickInterval(0.0, baseLodDist));
        assertEquals(1, AnimationLodEvaluator.getTickInterval(10.0 * 10.0, baseLodDist));
        assertEquals(1, AnimationLodEvaluator.getTickInterval(15.9 * 15.9, baseLodDist));

        // Tier 1: 16m - 32m (256.0 <= distSq < 1024.0) -> interval 2
        assertEquals(2, AnimationLodEvaluator.getTickInterval(16.0 * 16.0, baseLodDist));
        assertEquals(2, AnimationLodEvaluator.getTickInterval(24.0 * 24.0, baseLodDist));
        assertEquals(2, AnimationLodEvaluator.getTickInterval(31.9 * 31.9, baseLodDist));

        // Tier 2: 32m - 48m (1024.0 <= distSq < 2304.0) -> interval 4
        assertEquals(4, AnimationLodEvaluator.getTickInterval(32.0 * 32.0, baseLodDist));
        assertEquals(4, AnimationLodEvaluator.getTickInterval(40.0 * 40.0, baseLodDist));
        assertEquals(4, AnimationLodEvaluator.getTickInterval(47.9 * 47.9, baseLodDist));

        // Tier 3: >= 48m (distSq >= 2304.0) -> interval 8
        assertEquals(8, AnimationLodEvaluator.getTickInterval(48.0 * 48.0, baseLodDist));
        assertEquals(8, AnimationLodEvaluator.getTickInterval(64.0 * 64.0, baseLodDist));
        assertEquals(8, AnimationLodEvaluator.getTickInterval(128.0 * 128.0, baseLodDist));

        // Edge cases: NaN, negative, positive infinity must safely fall back to 1
        assertEquals(1, AnimationLodEvaluator.getTickInterval(Double.NaN, baseLodDist));
        assertEquals(1, AnimationLodEvaluator.getTickInterval(-1.0, baseLodDist));
        assertEquals(1, AnimationLodEvaluator.getTickInterval(Double.POSITIVE_INFINITY, baseLodDist));
    }

    @Test
    void testAnimationLodShouldUpdateFramePhaseStaggeringAndNegativeIds() {
        int[] intervals = {1, 2, 4, 8};
        int[] entityIds = {0, 1, 7, 42, -1, -12345, Integer.MIN_VALUE, Integer.MAX_VALUE};

        for (int interval : intervals) {
            int expectedUpdatesPer8Frames = 8 / interval;
            for (int entityId : entityIds) {
                int updateCount = 0;
                for (long frame = 0; frame < 8; frame++) {
                    final long currentFrame = frame;
                    boolean update = assertDoesNotThrow(
                            () -> AnimationLodEvaluator.shouldUpdateFrame(currentFrame, entityId, interval)
                    );
                    if (update) {
                        updateCount++;
                    }
                }
                assertEquals(expectedUpdatesPer8Frames, updateCount,
                        "Entity " + entityId + " with interval " + interval + " should update " + expectedUpdatesPer8Frames + " times in 8 frames");
            }
        }

        // Verify phase staggering across consecutive entity IDs at interval 4
        int frame0Updates = 0;
        for (int id = 0; id < 4; id++) {
            if (AnimationLodEvaluator.shouldUpdateFrame(0L, id, 4)) {
                frame0Updates++;
            }
        }
        assertEquals(1, frame0Updates, "Exactly 1 out of 4 consecutive entity IDs should update on frame 0 for interval 4");
    }

    @Test
    void testAnimationLodQuantizeAngle() {
        assertEquals(0.25f, AnimationLodEvaluator.quantizeAngle(0.26f, 0.25f), 1.0e-5f);
        assertEquals(0.50f, AnimationLodEvaluator.quantizeAngle(0.38f, 0.25f), 1.0e-5f);
        assertEquals(-0.50f, AnimationLodEvaluator.quantizeAngle(-0.48f, 0.25f), 1.0e-5f);
        assertEquals(0.0f, AnimationLodEvaluator.quantizeAngle(0.05f, 0.25f), 1.0e-5f);

        // NaN and non-positive step safety
        assertTrue(Float.isNaN(AnimationLodEvaluator.quantizeAngle(Float.NaN, 0.25f)));
        assertEquals(1.234f, AnimationLodEvaluator.quantizeAngle(1.234f, 0.0f), 1.0e-5f);
        assertEquals(1.234f, AnimationLodEvaluator.quantizeAngle(1.234f, -0.5f), 1.0e-5f);
    }

    @Test
    void testReverseZProjectionDegenerateAndNaNInputsProduceFiniteMatrices() {
        Matrix4f dest = new Matrix4f();

        // createInfinitePerspective degenerate cases
        assertAllElementsFinite(ReverseZProjection.createInfinitePerspective(0.0f, 16.0f / 9.0f, 0.05f, dest));
        assertAllElementsFinite(ReverseZProjection.createInfinitePerspective(1.2f, 0.0f, 0.05f, dest));
        assertAllElementsFinite(ReverseZProjection.createInfinitePerspective(Float.NaN, Float.NaN, Float.NaN, dest));

        // createFinitePerspective degenerate cases (aspectRatio=0, fov=0, zFar==zNear, NaN)
        assertAllElementsFinite(ReverseZProjection.createFinitePerspective(0.0f, 16.0f / 9.0f, 0.05f, 100.0f, dest));
        assertAllElementsFinite(ReverseZProjection.createFinitePerspective(1.2f, 0.0f, 0.05f, 100.0f, dest));
        assertAllElementsFinite(ReverseZProjection.createFinitePerspective(1.2f, 16.0f / 9.0f, 10.0f, 10.0f, dest));
        assertAllElementsFinite(ReverseZProjection.createFinitePerspective(Float.NaN, Float.NaN, Float.NaN, Float.NaN, dest));

        // convertToReverseZ degenerate cases
        Matrix4f baseProj = new Matrix4f().identity();
        assertAllElementsFinite(ReverseZProjection.convertToReverseZ(baseProj, 0.05f, 0.05f, dest));
        assertAllElementsFinite(ReverseZProjection.convertToReverseZ(baseProj, Float.NaN, Float.NaN, dest));

        Matrix4f nanProj = new Matrix4f();
        nanProj.m00(Float.NaN);
        nanProj.m11(0.0f);
        nanProj.m22(Float.NaN);
        nanProj.m32(Float.NaN);
        assertAllElementsFinite(ReverseZProjection.convertToReverseZ(nanProj, 0.05f, 0.05f, dest));
        assertAllElementsFinite(ReverseZProjection.convertToReverseZ(null, Float.NaN, Float.NaN, dest));
    }

    private static void assertAllElementsFinite(Matrix4f m) {
        assertNotNull(m);
        float[] values = new float[16];
        m.get(values);
        for (int i = 0; i < values.length; i++) {
            assertTrue(Float.isFinite(values[i]), "Matrix element at index " + i + " must be finite, got: " + values[i]);
        }
    }

    @Test
    void testMatrixPoolDoubleFreeProtectionAndThreadIsolation() throws Exception {
        MatrixPool pool = new MatrixPool();
        Matrix4f m = pool.allocMatrix4f();
        assertNotNull(m);

        // Double-free the same instance
        pool.free(m);
        pool.free(m);

        Matrix4f firstAlloc = pool.allocMatrix4f();
        Matrix4f secondAlloc = pool.allocMatrix4f();
        assertNotSame(firstAlloc, secondAlloc, "Double-free must not cause two subsequent allocations to return the same Matrix4f instance");

        // Verify getStaticMatrix4f() thread isolation
        Matrix4f mainThreadMat = MatrixPool.getStaticMatrix4f();
        mainThreadMat.m03(42.0f);

        AtomicReference<Matrix4f> workerThreadMatRef = new AtomicReference<>();
        AtomicReference<Float> workerInitialM03 = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            Matrix4f workerMat = MatrixPool.getStaticMatrix4f();
            workerInitialM03.set(workerMat.m03());
            workerMat.m03(99.0f);
            workerThreadMatRef.set(workerMat);
            latch.countDown();
        });
        worker.start();
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Worker thread timed out");

        assertNotSame(mainThreadMat, workerThreadMatRef.get(), "getStaticMatrix4f() must be isolated per thread");
        assertEquals(0.0f, workerInitialM03.get(), 1.0e-5f, "Worker thread static matrix must start as identity");
        assertEquals(42.0f, mainThreadMat.m03(), 1.0e-5f, "Worker thread mutation must not affect main thread matrix");
    }

    @Test
    void testBiomeCacheKeyUniquenessPerBiomeInstance() throws Exception {
        Object biomeA = new Object();
        Object biomeB = new Object();
        long posLong = new BlockPos(128, 64, -256).asLong();

        java.lang.reflect.Method computeKey = BiomeMixin.class.getDeclaredMethod("computeBiomeCacheKey", Object.class, long.class);
        computeKey.setAccessible(true);

        long keyA = (long) computeKey.invoke(null, biomeA, posLong);
        long keyB = (long) computeKey.invoke(null, biomeB, posLong);

        assertNotEquals(keyA, keyB, "Distinct biome instances at the same BlockPos must produce distinct cache keys");
        assertEquals(keyA, (long) computeKey.invoke(null, biomeA, posLong), "Cache key computation must be deterministic for the same biome and position");
    }

    @Test
    void testSlabSubAllocatorOversizedFreeBucketOverflowAndTrimToBudget() {
        SlabSubAllocator allocator = new SlabSubAllocator();
        assertEquals(0L, allocator.getTotalAllocatedBytes());

        // 1. Allocate and free a buffer larger than MAX_SLAB_SIZE (128 KB > 64 KB)
        int oversizedCapacity = SlabSubAllocator.MAX_SLAB_SIZE * 2;
        ByteBuffer oversized = allocator.allocate(oversizedCapacity);
        assertEquals(oversizedCapacity, allocator.getTotalAllocatedBytes());
        allocator.free(oversized);
        assertEquals(0L, allocator.getTotalAllocatedBytes(), "Freeing buffer larger than MAX_SLAB_SIZE must decrement totalAllocatedBytes");
        assertEquals(0L, allocator.getActiveBytesInUse());

        // 2. Allocate MAX_SLABS_PER_BUCKET + 1 buffers of size MIN_SLAB_SIZE and free them all
        int count = SlabSubAllocator.MAX_SLABS_PER_BUCKET + 1;
        List<ByteBuffer> buffers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            buffers.add(allocator.allocate(SlabSubAllocator.MIN_SLAB_SIZE));
        }
        long expectedPeak = (long) count * SlabSubAllocator.MIN_SLAB_SIZE;
        assertEquals(expectedPeak, allocator.getTotalAllocatedBytes());

        for (ByteBuffer buf : buffers) {
            allocator.free(buf);
        }
        long maxCachedBytes = (long) SlabSubAllocator.MAX_SLABS_PER_BUCKET * SlabSubAllocator.MIN_SLAB_SIZE;
        assertEquals(maxCachedBytes, allocator.getTotalAllocatedBytes(),
                "Buffer freed beyond MAX_SLABS_PER_BUCKET must be unpooled and decrement totalAllocatedBytes");
        assertEquals(0L, allocator.getActiveBytesInUse());

        // 3. Test trimToBudget
        long halfBudget = maxCachedBytes / 2;
        allocator.trimToBudget(halfBudget);
        assertTrue(allocator.getTotalAllocatedBytes() <= halfBudget,
                "trimToBudget(halfBudget) must reduce totalAllocatedBytes to at most halfBudget");

        allocator.trimToBudget(0L);
        assertEquals(0L, allocator.getTotalAllocatedBytes(), "trimToBudget(0) must evict all pooled slabs");
    }
}
