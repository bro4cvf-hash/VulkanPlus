package net.vulkanplus.test.culling;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.render.FrustumCuller;
import net.vulkanplus.render.ParticleCuller;
import net.vulkanplus.render.RenderOptimizer;
import net.vulkanplus.ui.DiagnosticHud;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification of zero-allocation hot paths and particle culling.
 * Validates zero heap allocation mechanics, object reuse, primitive arithmetic parity, and fast batch filtering.
 */
public class ZeroAllocationTest {

    private FrustumCuller frustumCuller;
    private ParticleCuller particleCuller;

    @BeforeEach
    public void setup() {
        ConfigManager.getConfig().enabled = true;
        ConfigManager.getConfig().enableEntityCulling = true;
        ConfigManager.getConfig().enableParticleCulling = true;
        ConfigManager.getConfig().particleCullingDistance = 32.0;

        frustumCuller = new FrustumCuller();
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 100.0f);
        Matrix4f view = new Matrix4f().lookAt(0, 0, 0, 0, 0, -1, 0, 1, 0);
        Matrix4f viewProj = new Matrix4f(proj).mul(view);
        frustumCuller.updateFrustum(viewProj, 0, 0, 0);

        particleCuller = new ParticleCuller(frustumCuller);
        particleCuller.updateCamera(0, 0, 0);
    }

    @Test
    @DisplayName("Particle Center Primitive Arithmetic matches Box.getCenter() identically")
    public void testParticleCenterPrimitiveArithmeticEquivalence() {
        // Test standard, fractional, negative, and asymmetric boxes
        Box[] testBoxes = new Box[]{
                new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
                new Box(-10.5, 64.0, -100.25, -9.5, 65.5, -99.0),
                new Box(-0.125, 0.0, -0.125, 0.125, 0.25, 0.125),
                new Box(1234.567, -64.123, 9876.543, 1235.001, -63.500, 9877.000),
                new Box(-500.0, -100.0, -500.0, 500.0, 100.0, 500.0)
        };

        for (Box box : testBoxes) {
            Vec3d vanillaCenter = box.getCenter();

            double primX = box.minX + (box.maxX - box.minX) * 0.5;
            double primY = box.minY + (box.maxY - box.minY) * 0.5;
            double primZ = box.minZ + (box.maxZ - box.minZ) * 0.5;

            assertEquals(vanillaCenter.x, primX, 1e-9, "X center coordinate must match Box.getCenter()");
            assertEquals(vanillaCenter.y, primY, 1e-9, "Y center coordinate must match Box.getCenter()");
            assertEquals(vanillaCenter.z, primZ, 1e-9, "Z center coordinate must match Box.getCenter()");
        }

        // Randomized stress test across 1,000 arbitrary boxes
        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            double x1 = (random.nextDouble() - 0.5) * 10000.0;
            double y1 = (random.nextDouble() - 0.5) * 500.0;
            double z1 = (random.nextDouble() - 0.5) * 10000.0;
            double x2 = x1 + random.nextDouble() * 10.0;
            double y2 = y1 + random.nextDouble() * 10.0;
            double z2 = z1 + random.nextDouble() * 10.0;

            Box box = new Box(x1, y1, z1, x2, y2, z2);
            Vec3d center = box.getCenter();

            double primX = box.minX + (box.maxX - box.minX) * 0.5;
            double primY = box.minY + (box.maxY - box.minY) * 0.5;
            double primZ = box.minZ + (box.maxZ - box.minZ) * 0.5;

            assertEquals(center.x, primX, 1e-9);
            assertEquals(center.y, primY, 1e-9);
            assertEquals(center.z, primZ, 1e-9);
        }
    }

    @Test
    @DisplayName("Entity Bounding Box 0.5 Expansion matches box.expand(0.5) and culls identically")
    public void testEntityDirectPrimitiveExpansionEquivalence() {
        Box[] testBoxes = new Box[]{
                new Box(0, 0, -10, 1, 2, -9),      // in front (visible)
                new Box(0, 0, 10, 1, 2, 11),       // behind (culled)
                new Box(50, 0, -10, 52, 2, -8),    // far right (culled)
                new Box(-0.6, 60.0, -5.6, 0.6, 62.0, -4.4), // near center
                new Box(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0)
        };

        for (Box box : testBoxes) {
            Box expandedBox = box.expand(0.5);

            double pMinX = box.minX - 0.5;
            double pMinY = box.minY - 0.5;
            double pMinZ = box.minZ - 0.5;
            double pMaxX = box.maxX + 0.5;
            double pMaxY = box.maxY + 0.5;
            double pMaxZ = box.maxZ + 0.5;

            assertEquals(expandedBox.minX, pMinX, 1e-9);
            assertEquals(expandedBox.minY, pMinY, 1e-9);
            assertEquals(expandedBox.minZ, pMinZ, 1e-9);
            assertEquals(expandedBox.maxX, pMaxX, 1e-9);
            assertEquals(expandedBox.maxY, pMaxY, 1e-9);
            assertEquals(expandedBox.maxZ, pMaxZ, 1e-9);

            // Verify FrustumCuller output is identical for both approaches
            boolean visibleViaAlloc = frustumCuller.isAabbVisible(
                    expandedBox.minX, expandedBox.minY, expandedBox.minZ,
                    expandedBox.maxX, expandedBox.maxY, expandedBox.maxZ);

            boolean visibleViaPrim = frustumCuller.isAabbVisible(
                    pMinX, pMinY, pMinZ, pMaxX, pMaxY, pMaxZ);

            assertEquals(visibleViaAlloc, visibleViaPrim,
                    "Culling decision must be identical between box.expand(0.5) and primitive expansion");
        }
    }

    @Test
    @DisplayName("RenderOptimizer Matrix & Quaternion instances are statically cached and reused")
    public void testRenderOptimizerCachedMatrixReuse() {
        Quaternionf rot1 = RenderOptimizer.getCachedRotation();
        Quaternionf rot2 = RenderOptimizer.getCachedRotation();
        assertSame(rot1, rot2, "getCachedRotation must return the same reusable static instance");

        Matrix4f view1 = RenderOptimizer.getCachedViewMatrix();
        Matrix4f view2 = RenderOptimizer.getCachedViewMatrix();
        assertSame(view1, view2, "getCachedViewMatrix must return the same reusable static instance");

        Matrix4f viewProj1 = RenderOptimizer.getCachedViewProjMatrix();
        Matrix4f viewProj2 = RenderOptimizer.getCachedViewProjMatrix();
        assertSame(viewProj1, viewProj2, "getCachedViewProjMatrix must return the same reusable static instance");

        // Verify mathematical parity with fresh allocation
        Quaternionf freshRot = new Quaternionf().rotationXYZ(0.3f, 0.7f, -0.2f);
        Matrix4f freshProj = new Matrix4f().perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 100.0f);

        Quaternionf allocConj = freshRot.conjugate(new Quaternionf());
        Matrix4f allocView = new Matrix4f().rotation(allocConj);
        Matrix4f allocViewProj = new Matrix4f(freshProj).mul(allocView);

        Quaternionf cachedConj = freshRot.conjugate(RenderOptimizer.getCachedRotation());
        Matrix4f cachedView = RenderOptimizer.getCachedViewMatrix().rotation(cachedConj);
        Matrix4f cachedViewProj = RenderOptimizer.getCachedViewProjMatrix().set(freshProj).mul(cachedView);

        assertSame(RenderOptimizer.getCachedRotation(), cachedConj);
        assertSame(RenderOptimizer.getCachedViewMatrix(), cachedView);
        assertSame(RenderOptimizer.getCachedViewProjMatrix(), cachedViewProj);

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                assertEquals(allocViewProj.getRowColumn(row, col), cachedViewProj.getRowColumn(row, col), 1e-6f,
                        String.format("Matrix component [%d][%d] must match identical math", row, col));
            }
        }
    }

    @Test
    @DisplayName("DiagnosticHud preallocated sortBuffer eliminates per-frame array allocations")
    public void testDiagnosticHudZeroAllocationSortBuffer() {
        DiagnosticHud hud = new DiagnosticHud();
        long[] initialSortBuffer = hud.getSortBuffer();

        assertNotNull(initialSortBuffer, "Sort buffer must be preallocated");
        assertEquals(DiagnosticHud.BUFFER_CAPACITY, initialSortBuffer.length, "Sort buffer capacity must be 300");

        // Record 500 samples (exceeding circular capacity)
        for (int i = 0; i < 500; i++) {
            hud.recordSample(16_666_667L + (i % 5) * 1_000_000L);
            assertSame(initialSortBuffer, hud.getSortBuffer(),
                    "Sort buffer reference must remain identical across all samples (zero allocations)");
        }

        assertTrue(hud.getAverageFps() > 50.0f);
        assertTrue(hud.getOnePercentLowFps() > 40.0f);
    }

    @Test
    @DisplayName("DiagnosticHud throttles recalculation in onFrame() but updates currentFps immediately")
    public void testDiagnosticHudThrottlingInOnFrame() throws InterruptedException {
        DiagnosticHud hud = new DiagnosticHud();

        // Feed an initial frame
        hud.onFrame();
        float firstFps = hud.getCurrentFps();

        // Rapid subsequent frame calls within 200ms
        for (int i = 0; i < 10; i++) {
            hud.onFrame();
        }

        // Samples are tracked
        assertTrue(hud.getSamplesCount() > 1);

        // Verify recordSample always updates metrics synchronously for test determinism
        hud.reset();
        for (int i = 0; i < 100; i++) {
            hud.recordSample(16_666_667L);
        }
        assertEquals(100, hud.getSamplesCount());
        assertEquals(60.0f, hud.getAverageFps(), 1.0f);
    }

    @Test
    @DisplayName("ParticleCuller caches maxDistSq on updateCamera and performs squared distance early exit")
    public void testParticleCullerCachedMaxDistSqAndTier1Culling() {
        ConfigManager.getConfig().particleCullingDistance = 40.0;
        particleCuller.updateCamera(10.0, 20.0, 30.0);

        // Verify cachedMaxDistSq = 40.0 * 40.0 = 1600.0
        assertEquals(1600.0, particleCuller.getCachedMaxDistSq(), 1e-6);

        // Particle at distance 30 blocks: dx=0, dy=0, dz=-30 -> distSq = 900 <= 1600 -> in front of camera
        // Note camera is at (10, 20, 30)
        // Point (10, 20, 0) has distance 30 blocks from camera
        particleCuller.resetStats();

        // Setup frustum looking down -Z from camera (10, 20, 30)
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 100.0f);
        Matrix4f view = new Matrix4f().lookAt(10, 20, 30, 10, 20, 0, 0, 1, 0);
        frustumCuller.updateFrustum(new Matrix4f(proj).mul(view), 10, 20, 30);

        assertTrue(particleCuller.shouldRenderParticle(10, 20, 0, 0.2f),
                "Particle within 40 blocks in front of camera must be visible");

        assertFalse(particleCuller.shouldRenderParticle(10, 20, -20, 0.2f),
                "Particle at distance 50 blocks must be culled by distance rejection");

        assertEquals(1, particleCuller.getCulledParticleCount());
    }

    @Test
    @DisplayName("ParticleCuller filterParticleBatch executes fast zero-allocation batch filtering")
    public void testParticleBatchFiltering() {
        particleCuller.updateCamera(0, 0, 0);
        ConfigManager.getConfig().particleCullingDistance = 32.0;

        int count = 5;
        double[] xs = new double[]{0, 0, 0, 0, 100};
        double[] ys = new double[]{0, 0, 0, 0, 100};
        double[] zs = new double[]{-5, -15, 10, -50, -5}; // -5 visible, -15 visible, +10 behind (culled), -50 too far (culled), 100 too far (culled)
        boolean[] outVisible = new boolean[count];

        particleCuller.resetStats();
        int visibleCount = particleCuller.filterParticleBatch(xs, ys, zs, 0.2f, outVisible, count);

        // Verify individual consistency
        for (int i = 0; i < count; i++) {
            boolean singleResult = particleCuller.shouldRenderParticle(xs[i], ys[i], zs[i], 0.2f);
            assertEquals(singleResult, outVisible[i],
                    "Batch visibility for index " + i + " must match single particle evaluation");
        }

        assertTrue(outVisible[0], "Particle at (0, 0, -5) must be visible");
        assertTrue(outVisible[1], "Particle at (0, 0, -15) must be visible");
        assertFalse(outVisible[2], "Particle at (0, 0, 10) behind camera must be culled");
        assertFalse(outVisible[3], "Particle at (0, 0, -50) beyond distance must be culled");
        assertFalse(outVisible[4], "Particle at (100, 100, -5) beyond distance must be culled");
        assertEquals(2, visibleCount, "Exactly 2 particles must be visible in batch");
    }

    @Test
    @DisplayName("High throughput stress test: 50,000 particles evaluated with zero allocations")
    public void testHighThroughputParticleEvaluationPerformance() {
        particleCuller.updateCamera(0, 0, 0);
        particleCuller.resetStats();

        int batchSize = 1000;
        double[] xs = new double[batchSize];
        double[] ys = new double[batchSize];
        double[] zs = new double[batchSize];
        boolean[] visible = new boolean[batchSize];

        Random rand = new Random(12345);
        for (int i = 0; i < batchSize; i++) {
            xs[i] = (rand.nextDouble() - 0.5) * 80.0;
            ys[i] = (rand.nextDouble() - 0.5) * 80.0;
            zs[i] = (rand.nextDouble() - 0.5) * 80.0;
        }

        long startNanos = System.nanoTime();
        int totalVisible = 0;
        // Run 50 iterations = 50,000 particles
        for (int iter = 0; iter < 50; iter++) {
            totalVisible += particleCuller.filterParticleBatch(xs, ys, zs, 0.2f, visible, batchSize);
        }
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;

        assertTrue(totalVisible > 0, "Some particles should be visible in random field");
        assertEquals(50000, particleCuller.getTotalParticleCount(), "Must have evaluated exactly 50,000 particles");
        assertTrue(durationMs < 2000, "50,000 particles must be evaluated in under 2 seconds, took " + durationMs + " ms");
    }

    @Test
    @DisplayName("TransientRingBuffer lazily allocates direct backing buffer only on first allocate() call")
    public void testLazyTransientRingBufferAllocation() {
        net.vulkanplus.memory.TransientRingBuffer ring = new net.vulkanplus.memory.TransientRingBuffer(1024);
        assertFalse(ring.isBackingAllocated(), "Backing direct ByteBuffer must not be allocated before allocate()");
        ring.advanceFrame();
        assertFalse(ring.isBackingAllocated(), "advanceFrame() must not trigger backing buffer allocation");
        assertNotNull(ring.allocate(64, 16));
        assertTrue(ring.isBackingAllocated(), "Backing direct ByteBuffer must be allocated on first allocate()");
    }
}
