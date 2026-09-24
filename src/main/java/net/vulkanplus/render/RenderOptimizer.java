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
    public static void updateCameraMatrices(Matrix4f proj, Quaternionf cameraRotation, double camX, double camY, double camZ) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enableMatrixPooling) {
            MatrixPool pool = MatrixPool.get();
            Quaternionf pooledRot = pool.allocQuaternionf();
            Matrix4f pooledView = pool.allocMatrix4f();
            try {
                cameraRotation.conjugate(pooledRot);
                pooledView.rotation(pooledRot);
                CACHED_ROTATION.set(pooledRot);
                CACHED_VIEW.set(pooledView);
                CACHED_VIEW_PROJ.set(proj).mul(pooledView);
                if (cfg.enableReverseZ) {
                    ReverseZProjection.convertToReverseZ(proj, 0.05f, 4096.0f, CACHED_REVERSE_Z_PROJ);
                    CACHED_REVERSE_Z_VIEW_PROJ.set(CACHED_REVERSE_Z_PROJ).mul(pooledView);
                }
            } finally {
                pool.free(pooledView);
                pool.free(pooledRot);
            }
        } else {
            Quaternionf rot = cameraRotation.conjugate(CACHED_ROTATION);
            Matrix4f view = CACHED_VIEW.rotation(rot);
            CACHED_VIEW_PROJ.set(proj).mul(view);
            if (cfg != null && cfg.enableReverseZ) {
                ReverseZProjection.convertToReverseZ(proj, 0.05f, 4096.0f, CACHED_REVERSE_Z_PROJ);
                CACHED_REVERSE_Z_VIEW_PROJ.set(CACHED_REVERSE_Z_PROJ).mul(view);
            }
        }

        if (cfg != null && cfg.enableFastRandom) {
            FastXoroshiro128PlusPlus.current().nextLong();
        }

        onCameraUpdate(CACHED_VIEW_PROJ, camX, camY, camZ);
    }

    /**
     * Returns a sub-pixel PRNG jitter offset in [-5e-5, +5e-5] when enableFastRandom is active.
     */
    public static float nextParticleJitter() {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableFastRandom) {
            return (FastXoroshiro128PlusPlus.current().nextFloat() - 0.5f) * 1.0e-4f;
        }
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
