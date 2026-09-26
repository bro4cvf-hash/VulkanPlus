package net.vulkanplus.render;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * High-performance CPU-side camera frustum culler.
 * Extracts 6 normalized clipping planes from the combined View-Projection matrix using Gribb-Hartmann.
 * Performs branch-optimized AABB and sphere intersection tests to skip off-screen entity and block entity draw calls.
 */
public class FrustumCuller {
    // 6 Frustum planes: Left, Right, Bottom, Top, Near, Far
    // Plane equation: A*x + B*y + C*z + D = 0 stored contiguously (6 * 4 = 24 floats)
    private final float[] planes = new float[24];
    private final float[] absPlanes = new float[24];

    private double cameraX = 0;
    private double cameraY = 0;
    private double cameraZ = 0;

    private long culledEntitiesCount = 0;
    private long totalEntitiesTested = 0;

    /**
     * Updates the 6 frustum clipping planes from the view-projection matrix.
     */
    public void updateFrustum(Matrix4f viewProj, double camX, double camY, double camZ) {
        this.cameraX = camX;
        this.cameraY = camY;
        this.cameraZ = camZ;

        // Matrix elements: m<col><row>
        float m00 = viewProj.m00(), m01 = viewProj.m01(), m02 = viewProj.m02(), m03 = viewProj.m03();
        float m10 = viewProj.m10(), m11 = viewProj.m11(), m12 = viewProj.m12(), m13 = viewProj.m13();
        float m20 = viewProj.m20(), m21 = viewProj.m21(), m22 = viewProj.m22(), m23 = viewProj.m23();
        float m30 = viewProj.m30(), m31 = viewProj.m31(), m32 = viewProj.m32(), m33 = viewProj.m33();

        // Left plane: row 4 + row 1
        setPlane(0, m03 + m00, m13 + m10, m23 + m20, m33 + m30);

        // Right plane: row 4 - row 1
        setPlane(1, m03 - m00, m13 - m10, m23 - m20, m33 - m30);

        // Bottom plane: row 4 + row 2
        setPlane(2, m03 + m01, m13 + m11, m23 + m21, m33 + m31);

        // Top plane: row 4 - row 2
        setPlane(3, m03 - m01, m13 - m11, m23 - m21, m33 - m31);

        // Near plane: row 3 (Vulkan clip space Z in [0, 1])
        setPlane(4, m02, m12, m22, m32);

        // Far plane: row 4 - row 3
        setPlane(5, m03 - m02, m13 - m12, m23 - m22, m33 - m32);
    }

    private void setPlane(int index, float a, float b, float c, float d) {
        int base = index << 2;
        float sq = a * a + b * b + c * c;
        if (sq > 1.0e-8f && Float.isFinite(sq)) {
            float invLength = net.vulkanplus.math.FastMath.fastInvSqrt(sq);
            float na = a * invLength;
            float nb = b * invLength;
            float nc = c * invLength;
            float nd = d * invLength;
            planes[base] = na;
            planes[base + 1] = nb;
            planes[base + 2] = nc;
            planes[base + 3] = nd;
            absPlanes[base] = Math.abs(na);
            absPlanes[base + 1] = Math.abs(nb);
            absPlanes[base + 2] = Math.abs(nc);
            absPlanes[base + 3] = nd;
        } else {
            planes[base] = 0;
            planes[base + 1] = 0;
            planes[base + 2] = 0;
            planes[base + 3] = 0;
            absPlanes[base] = 0;
            absPlanes[base + 1] = 0;
            absPlanes[base + 2] = 0;
            absPlanes[base + 3] = 0;
        }
    }

    /**
     * Sodium-style pre-folded chunk section visibility test using branchless Math.fma dot products.
     * Evaluates AABB center and half-extents against the 6 frustum planes without branching.
     *
     * @param minX minimum X coordinate (camera-relative)
     * @param minY minimum Y coordinate (camera-relative)
     * @param minZ minimum Z coordinate (camera-relative)
     * @param maxX maximum X coordinate (camera-relative)
     * @param maxY maximum Y coordinate (camera-relative)
     * @param maxZ maximum Z coordinate (camera-relative)
     * @return true if visible or intersecting frustum; false if completely culled
     */
    public boolean isSectionVisible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float cz = (minZ + maxZ) * 0.5f;

        float hx = (maxX - minX) * 0.5f;
        float hy = (maxY - minY) * 0.5f;
        float hz = (maxZ - minZ) * 0.5f;

        final float[] p = this.planes;
        final float[] ap = this.absPlanes;

        // Plane 0 (Left)
        if (Math.fma(p[0], cx, Math.fma(p[1], cy, Math.fma(p[2], cz, p[3]))) +
                Math.fma(ap[0], hx, Math.fma(ap[1], hy, ap[2] * hz)) < 0.0f) return false;

        // Plane 1 (Right)
        if (Math.fma(p[4], cx, Math.fma(p[5], cy, Math.fma(p[6], cz, p[7]))) +
                Math.fma(ap[4], hx, Math.fma(ap[5], hy, ap[6] * hz)) < 0.0f) return false;

        // Plane 2 (Bottom)
        if (Math.fma(p[8], cx, Math.fma(p[9], cy, Math.fma(p[10], cz, p[11]))) +
                Math.fma(ap[8], hx, Math.fma(ap[9], hy, ap[10] * hz)) < 0.0f) return false;

        // Plane 3 (Top)
        if (Math.fma(p[12], cx, Math.fma(p[13], cy, Math.fma(p[14], cz, p[15]))) +
                Math.fma(ap[12], hx, Math.fma(ap[13], hy, ap[14] * hz)) < 0.0f) return false;

        // Plane 4 (Near)
        if (Math.fma(p[16], cx, Math.fma(p[17], cy, Math.fma(p[18], cz, p[19]))) +
                Math.fma(ap[16], hx, Math.fma(ap[17], hy, ap[18] * hz)) < 0.0f) return false;

        // Plane 5 (Far)
        if (Math.fma(p[20], cx, Math.fma(p[21], cy, Math.fma(p[22], cz, p[23]))) +
                Math.fma(ap[20], hx, Math.fma(ap[21], hy, ap[22] * hz)) < 0.0f) return false;

        return true;
    }

    /**
     * Fast unrolled AABB visibility test in camera-relative coordinates using branchless Math.fma center-extent dot products.
     */
    public boolean isRelativeAabbVisible(float relMinX, float relMinY, float relMinZ, float relMaxX, float relMaxY, float relMaxZ) {
        totalEntitiesTested++;
        float cx = (relMinX + relMaxX) * 0.5f;
        float cy = (relMinY + relMaxY) * 0.5f;
        float cz = (relMinZ + relMaxZ) * 0.5f;

        float hx = (relMaxX - relMinX) * 0.5f;
        float hy = (relMaxY - relMinY) * 0.5f;
        float hz = (relMaxZ - relMinZ) * 0.5f;

        final float[] p = this.planes;
        final float[] ap = this.absPlanes;

        // Plane 0 (Left)
        if (Math.fma(p[0], cx, Math.fma(p[1], cy, Math.fma(p[2], cz, p[3]))) +
                Math.fma(ap[0], hx, Math.fma(ap[1], hy, ap[2] * hz)) < 0.0f) {
            culledEntitiesCount++;
            return false;
        }

        // Plane 1 (Right)
        if (Math.fma(p[4], cx, Math.fma(p[5], cy, Math.fma(p[6], cz, p[7]))) +
                Math.fma(ap[4], hx, Math.fma(ap[5], hy, ap[6] * hz)) < 0.0f) {
            culledEntitiesCount++;
            return false;
        }

        // Plane 2 (Bottom)
        if (Math.fma(p[8], cx, Math.fma(p[9], cy, Math.fma(p[10], cz, p[11]))) +
                Math.fma(ap[8], hx, Math.fma(ap[9], hy, ap[10] * hz)) < 0.0f) {
            culledEntitiesCount++;
            return false;
        }

        // Plane 3 (Top)
        if (Math.fma(p[12], cx, Math.fma(p[13], cy, Math.fma(p[14], cz, p[15]))) +
                Math.fma(ap[12], hx, Math.fma(ap[13], hy, ap[14] * hz)) < 0.0f) {
            culledEntitiesCount++;
            return false;
        }

        // Plane 4 (Near)
        if (Math.fma(p[16], cx, Math.fma(p[17], cy, Math.fma(p[18], cz, p[19]))) +
                Math.fma(ap[16], hx, Math.fma(ap[17], hy, ap[18] * hz)) < 0.0f) {
            culledEntitiesCount++;
            return false;
        }

        // Plane 5 (Far)
        if (Math.fma(p[20], cx, Math.fma(p[21], cy, Math.fma(p[22], cz, p[23]))) +
                Math.fma(ap[20], hx, Math.fma(ap[21], hy, ap[22] * hz)) < 0.0f) {
            culledEntitiesCount++;
            return false;
        }

        return true;
    }

    /**
     * Fast AABB visibility test in world space.
     *
     * @return true if the AABB intersects or is inside the frustum; false if completely outside.
     */
    public boolean isAabbVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return isRelativeAabbVisible(
                (float) (minX - cameraX),
                (float) (minY - cameraY),
                (float) (minZ - cameraZ),
                (float) (maxX - cameraX),
                (float) (maxY - cameraY),
                (float) (maxZ - cameraZ)
        );
    }

    /**
     * Block Entity visibility check with protective margins for chests and beacons.
     */
    public boolean isBlockEntityVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, boolean isBeacon, boolean isChest) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || !config.enableBlockEntityCulling) return true;

        if (isBeacon && config.beaconProtection) {
            maxY += 320.0;
        }

        if (isChest && config.chestProtection) {
            minX -= 0.1; maxX += 0.1;
            minY -= 0.1; maxY += 0.2;
            minZ -= 0.1; maxZ += 0.1;
        }

        return isAabbVisible(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Bounding sphere visibility test.
     */
    public boolean isSphereVisible(double centerX, double centerY, double centerZ, float radius) {
        float relX = (float) (centerX - cameraX);
        float relY = (float) (centerY - cameraY);
        float relZ = (float) (centerZ - cameraZ);
        final float[] p = this.planes;

        if (p[0] * relX + p[1] * relY + p[2] * relZ + p[3] < -radius) return false;
        if (p[4] * relX + p[5] * relY + p[6] * relZ + p[7] < -radius) return false;
        if (p[8] * relX + p[9] * relY + p[10] * relZ + p[11] < -radius) return false;
        if (p[12] * relX + p[13] * relY + p[14] * relZ + p[15] < -radius) return false;
        if (p[16] * relX + p[17] * relY + p[18] * relZ + p[19] < -radius) return false;
        if (p[20] * relX + p[21] * relY + p[22] * relZ + p[23] < -radius) return false;

        return true;
    }

    public void recordOccludedEntity() {
        culledEntitiesCount++;
    }

    public double getCameraX() {
        return cameraX;
    }

    public double getCameraY() {
        return cameraY;
    }

    public double getCameraZ() {
        return cameraZ;
    }

    public void resetStats() {
        culledEntitiesCount = 0;
        totalEntitiesTested = 0;
    }

    public long getCulledEntitiesCount() {
        return culledEntitiesCount;
    }

    public long getTotalEntitiesTested() {
        return totalEntitiesTested;
    }
}
