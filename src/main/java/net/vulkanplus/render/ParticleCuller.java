package net.vulkanplus.render;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;

import java.util.Arrays;

/**
 * Particle culling using distance threshold and camera frustum intersection.
 */
public class ParticleCuller {
    private final FrustumCuller frustumCuller;

    private double cameraX = 0;
    private double cameraY = 0;
    private double cameraZ = 0;
    private double cachedMaxDistSq = 32.0 * 32.0;

    private long culledParticleCount = 0;
    private long totalParticleCount = 0;

    public ParticleCuller(FrustumCuller frustumCuller) {
        this.frustumCuller = frustumCuller;
        double maxDist = ConfigManager.getConfig().particleCullingDistance;
        this.cachedMaxDistSq = maxDist * maxDist;
    }

    public void updateCamera(double camX, double camY, double camZ) {
        this.cameraX = camX;
        this.cameraY = camY;
        this.cameraZ = camZ;
        double maxDist = ConfigManager.getConfig().particleCullingDistance;
        this.cachedMaxDistSq = maxDist * maxDist;
    }

    /**
     * Determines whether a particle at world position (x, y, z) should be rendered.
     */
    public boolean shouldRenderParticle(double x, double y, double z, float particleRadius) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (!config.enableParticleCulling) return true;

        totalParticleCount++;

        double maxDistSq = this.cachedMaxDistSq > 0 ? this.cachedMaxDistSq : (config.particleCullingDistance * config.particleCullingDistance);

        double dx = x - cameraX;
        double dy = y - cameraY;
        double dz = z - cameraZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > maxDistSq) {
            culledParticleCount++;
            return false;
        }

        if (frustumCuller != null && !frustumCuller.isSphereVisible(x, y, z, particleRadius)) {
            culledParticleCount++;
            return false;
        }

        return true;
    }

    /**
     * Fast batch filtering before matrix/vertex operations.
     * Evaluates multiple particles in one pass without heap allocation.
     * Returns the count of visible particles and sets outVisible[i].
     */
    public int filterParticleBatch(double[] xs, double[] ys, double[] zs, float radius, boolean[] outVisible, int count) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (!config.enableParticleCulling) {
            Arrays.fill(outVisible, 0, count, true);
            return count;
        }

        int visibleCount = 0;
        double maxDistSq = this.cachedMaxDistSq > 0 ? this.cachedMaxDistSq : (config.particleCullingDistance * config.particleCullingDistance);

        for (int i = 0; i < count; i++) {
            totalParticleCount++;
            double dx = xs[i] - cameraX;
            double dy = ys[i] - cameraY;
            double dz = zs[i] - cameraZ;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > maxDistSq) {
                culledParticleCount++;
                outVisible[i] = false;
                continue;
            }

            if (frustumCuller != null && !frustumCuller.isSphereVisible(xs[i], ys[i], zs[i], radius)) {
                culledParticleCount++;
                outVisible[i] = false;
                continue;
            }

            outVisible[i] = true;
            visibleCount++;
        }
        return visibleCount;
    }

    public double getCachedMaxDistSq() {
        return cachedMaxDistSq;
    }

    public void resetStats() {
        culledParticleCount = 0;
        totalParticleCount = 0;
    }

    public long getCulledParticleCount() {
        return culledParticleCount;
    }

    public long getTotalParticleCount() {
        return totalParticleCount;
    }
}
