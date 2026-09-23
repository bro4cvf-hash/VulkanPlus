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
    // Plane equation: A*x + B*y + C*z + D = 0
    private final float[][] planes = new float[6][4];

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

        // Near plane: row 4 + row 3 (OpenGL) or row 3 (Vulkan [0, 1])
        setPlane(4, m03 + m02, m13 + m12, m23 + m22, m33 + m32);

        // Far plane: row 4 - row 3
        setPlane(5, m03 - m02, m13 - m12, m23 - m22, m33 - m32);
    }

    private void setPlane(int index, float a, float b, float c, float d) {
        float length = (float) Math.sqrt(a * a + b * b + c * c);
        if (length != 0.0f) {
            float invLength = 1.0f / length;
            planes[index][0] = a * invLength;
            planes[index][1] = b * invLength;
            planes[index][2] = c * invLength;
            planes[index][3] = d * invLength;
        } else {
            planes[index][0] = 0;
            planes[index][1] = 0;
            planes[index][2] = 0;
            planes[index][3] = 0;
        }
    }

    /**
     * Fast AABB visibility test in world space.
     *
     * @return true if the AABB intersects or is inside the frustum; false if completely outside.
     */
    public boolean isAabbVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        totalEntitiesTested++;

        float relMinX = (float) (minX - cameraX);
        float relMinY = (float) (minY - cameraY);
        float relMinZ = (float) (minZ - cameraZ);
        float relMaxX = (float) (maxX - cameraX);
        float relMaxY = (float) (maxY - cameraY);
        float relMaxZ = (float) (maxZ - cameraZ);

        for (int i = 0; i < 6; i++) {
            float[] p = planes[i];
            float pX = p[0] > 0.0f ? relMaxX : relMinX;
            float pY = p[1] > 0.0f ? relMaxY : relMinY;
            float pZ = p[2] > 0.0f ? relMaxZ : relMinZ;

            if (p[0] * pX + p[1] * pY + p[2] * pZ + p[3] < 0.0f) {
                culledEntitiesCount++;
                return false; // Box is completely outside this plane
            }
        }

        return true;
    }

    /**
     * Block Entity visibility check with protective margins for chests and beacons.
     */
    public boolean isBlockEntityVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, boolean isBeacon, boolean isChest) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (!config.enableBlockEntityCulling) return true;

        if (isBeacon && config.beaconProtection) {
            // Extend upper Y bound for beacon light beam
            maxY += 320.0;
        }

        if (isChest && config.chestProtection) {
            // Extra margin around chest lid opening
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

        for (int i = 0; i < 6; i++) {
            float[] p = planes[i];
            float dist = p[0] * relX + p[1] * relY + p[2] * relZ + p[3];
            if (dist < -radius) {
                return false;
            }
        }
        return true;
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
