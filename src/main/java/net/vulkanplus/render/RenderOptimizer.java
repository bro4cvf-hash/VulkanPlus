package net.vulkanplus.render;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.math.FastXoroshiro128PlusPlus;
import net.vulkanplus.memory.MatrixPool;
import net.vulkanplus.vulkan.ReverseZProjection;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Central coordinator for render optimization subsystems (frustum and particle culling,
 * matrix pooling, Reverse-Z projection, and fast PRNG jitter).
 */
public class RenderOptimizer {
    private static final FrustumCuller FRUSTUM_CULLER = new FrustumCuller();
    private static final ParticleCuller PARTICLE_CULLER = new ParticleCuller(FRUSTUM_CULLER);

    private static final Quaternionf CACHED_ROTATION = new Quaternionf();
    private static final Matrix4f CACHED_VIEW = new Matrix4f();
    private static final Matrix4f CACHED_VIEW_PROJ = new Matrix4f();
    private static final Matrix4f CACHED_REVERSE_Z_PROJ = new Matrix4f();
    private static final Matrix4f CACHED_REVERSE_Z_VIEW_PROJ = new Matrix4f();

    public static FrustumCuller getFrustumCuller() {
        return FRUSTUM_CULLER;
    }

    public static ParticleCuller getParticleCuller() {
        return PARTICLE_CULLER;
    }

    public static Quaternionf getCachedRotation() {
        return CACHED_ROTATION;
    }

    public static Matrix4f getCachedViewMatrix() {
        return CACHED_VIEW;
    }

    public static Matrix4f getCachedViewProjMatrix() {
        return CACHED_VIEW_PROJ;
    }

    public static Matrix4f getCachedReverseZProjection() {
        return CACHED_REVERSE_Z_PROJ;
    }

    public static Matrix4f getCachedReverseZViewProjMatrix() {
        return CACHED_REVERSE_Z_VIEW_PROJ;
    }

    /**
     * Zero-allocation camera matrix and frustum update wiring MatrixPool, ReverseZProjection, and FastXoroshiro128PlusPlus.
     */
    public static void updateCameraMatrices(Matrix4f projectionMatrix, Quaternionf cameraRotation, double camX, double camY, double camZ) {
        if (projectionMatrix == null || cameraRotation == null) {
            return;
        }
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        boolean active = cfg != null && cfg.enabled;

        MatrixPool pool = MatrixPool.get();
        Quaternionf scratchRot = pool.allocQuaternionf();
        Matrix4f scratchView = pool.allocMatrix4f();
        Matrix4f scratchViewProj = pool.allocMatrix4f();
        Matrix4f scratchRevZProj = pool.allocMatrix4f();
        Matrix4f scratchRevZViewProj = pool.allocMatrix4f();
        try {
            cameraRotation.conjugate(scratchRot);
            scratchView.rotation(scratchRot);
            scratchViewProj.set(projectionMatrix).mul(scratchView);

            CACHED_ROTATION.set(scratchRot);
            CACHED_VIEW.set(scratchView);
            CACHED_VIEW_PROJ.set(scratchViewProj);

            if (active && cfg.enableReverseZ) {
                ReverseZProjection.convertToReverseZ(projectionMatrix, 0.05f, 4096.0f, scratchRevZProj);
                scratchRevZViewProj.set(scratchRevZProj).mul(scratchView);
                CACHED_REVERSE_Z_PROJ.set(scratchRevZProj);
                CACHED_REVERSE_Z_VIEW_PROJ.set(scratchRevZViewProj);
            }
        } finally {
            pool.free(scratchRevZViewProj);
            pool.free(scratchRevZProj);
            pool.free(scratchViewProj);
            pool.free(scratchView);
            pool.free(scratchRot);
        }

        if (active && cfg.enableFastRandom) {
            FastXoroshiro128PlusPlus.current().nextLong();
        }

        onCameraUpdate(CACHED_VIEW_PROJ, camX, camY, camZ);
    }

    /**
     * Returns 0.0f. PRNG jitter was removed to prevent 144Hz particle strobing/flickering.
     */
    public static float nextParticleJitter() {
        return 0.0f;
    }

    public static void onCameraUpdate(Matrix4f viewProj, double camX, double camY, double camZ) {
        FRUSTUM_CULLER.updateFrustum(viewProj, camX, camY, camZ);
        PARTICLE_CULLER.updateCamera(camX, camY, camZ);
    }

    public static void resetFrameStats() {
        FRUSTUM_CULLER.resetStats();
        PARTICLE_CULLER.resetStats();
    }
}
