package net.vulkanplus.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Central coordinator for render optimization subsystems (frustum and particle culling).
 */
public class RenderOptimizer {
    private static final FrustumCuller FRUSTUM_CULLER = new FrustumCuller();
    private static final ParticleCuller PARTICLE_CULLER = new ParticleCuller(FRUSTUM_CULLER);

    private static final Quaternionf CACHED_ROTATION = new Quaternionf();
    private static final Matrix4f CACHED_VIEW = new Matrix4f();
    private static final Matrix4f CACHED_VIEW_PROJ = new Matrix4f();

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

    public static void onCameraUpdate(Matrix4f viewProj, double camX, double camY, double camZ) {
        FRUSTUM_CULLER.updateFrustum(viewProj, camX, camY, camZ);
        PARTICLE_CULLER.updateCamera(camX, camY, camZ);
    }

    public static void resetFrameStats() {
        FRUSTUM_CULLER.resetStats();
        PARTICLE_CULLER.resetStats();
    }
}
