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
        if (config == null || !config.enabled || !config.enableParticleCulling) return true;

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

        float effectiveRadius = Math.max(particleRadius, 1.0f);
        if (frustumCuller != null && !frustumCuller.isSphereVisible(x, y, z, effectiveRadius)) {
            culledParticleCount++;
            return false;
        }

        if (!net.vulkanplus.culling.VulkanSectionVisibility.isPositionVisible(
                net.vulkanplus.math.FastMath.fastFloor(x),
                net.vulkanplus.math.FastMath.fastFloor(y),
                net.vulkanplus.math.FastMath.fastFloor(z),
                cameraX, cameraY, cameraZ)) {
            culledParticleCount++;
            return false;
        }

        return true;
    }

    public boolean shouldRenderParticle(double x, double y, double z) {
        return shouldRenderParticle(x, y, z, 1.0f);
    }

    /**
     * Fast path for particles already verified to be inside the camera frustum by Vanilla's
     * BillboardParticleRenderer (frustum.intersectPoint(x, y, z)).
     * Performs distance culling (scaled by cullingDistanceFactor) and O(1) VulkanMod SectionGrid occlusion culling
     * without repeating the 6-plane frustum test.
     */
    public boolean shouldRenderInFrustumParticle(double x, double y, double z) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || !config.enableParticleCulling) return true;

        totalParticleCount++;

        double baseMaxDistSq = this.cachedMaxDistSq > 0 ? this.cachedMaxDistSq : (config.particleCullingDistance * config.particleCullingDistance);
        double factor = config.cullingDistanceFactor > 0.0 ? config.cullingDistanceFactor : 1.0;
        double maxDistSq = baseMaxDistSq * (factor * factor);

        double dx = x - cameraX;
        double dy = y - cameraY;
        double dz = z - cameraZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > maxDistSq) {
            culledParticleCount++;
            return false;
        }

        if (!net.vulkanplus.culling.VulkanSectionVisibility.isPositionVisible(
                net.vulkanplus.math.FastMath.fastFloor(x),
                net.vulkanplus.math.FastMath.fastFloor(y),
                net.vulkanplus.math.FastMath.fastFloor(z),
                cameraX, cameraY, cameraZ)) {
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
        if (config == null || !config.enabled || !config.enableParticleCulling) {
            Arrays.fill(outVisible, 0, count, true);
            return count;
        }

        int visibleCount = 0;
        double maxDistSq = this.cachedMaxDistSq > 0 ? this.cachedMaxDistSq : (config.particleCullingDistance * config.particleCullingDistance);
        float effectiveRadius = Math.max(radius, 1.0f);

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

            if (frustumCuller != null && !frustumCuller.isSphereVisible(xs[i], ys[i], zs[i], effectiveRadius)) {
                culledParticleCount++;
                outVisible[i] = false;
                continue;
            }

            if (!net.vulkanplus.culling.VulkanSectionVisibility.isPositionVisible(
                    net.vulkanplus.math.FastMath.fastFloor(xs[i]),
                    net.vulkanplus.math.FastMath.fastFloor(ys[i]),
                    net.vulkanplus.math.FastMath.fastFloor(zs[i]),
                    cameraX, cameraY, cameraZ)) {
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
