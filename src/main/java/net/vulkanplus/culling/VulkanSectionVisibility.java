package net.vulkanplus.culling;

import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.SectionGrid;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanplus.bridge.VulkanDetector;

/**
 * O(1) section-graph occlusion visibility checker backed by VulkanMod's SectionGrid.
 * Queries whether the 16x16x16 RenderSection containing a world position was visited by
 * VulkanMod's cave/terrain occlusion graph during the current frame.
 */
public final class VulkanSectionVisibility {

    private static final double NEAR_CAMERA_SAFETY_DIST_SQ = 64.0; // 8-block radius always visible

    private VulkanSectionVisibility() {
    }

    /**
     * Returns true if the 16x16x16 chunk section at (blockX, blockY, blockZ) is visible in the current frame,
     * or if VulkanMod's section graph is unavailable / out of bounds / within the near-camera safety radius.
     */
    public static boolean isPositionVisible(int blockX, int blockY, int blockZ, double camX, double camY, double camZ) {
        if (!VulkanDetector.getBridge().isVulkanActive()) {
            return true;
        }

        double dx = (blockX + 0.5) - camX;
        double dy = (blockY + 0.5) - camY;
        double dz = (blockZ + 0.5) - camZ;
        if (dx * dx + dy * dy + dz * dz <= NEAR_CAMERA_SAFETY_DIST_SQ) {
            return true;
        }

        try {
            WorldRenderer wr = WorldRenderer.getInstance();
            if (wr == null) {
                return true;
            }
            short currentFrame = wr.getLastFrame();
            if (currentFrame == 0) {
                return true;
            }
            SectionGrid grid = wr.getSectionGrid();
            if (grid == null) {
                return true;
            }
            return isSectionVisibleRaw(grid, currentFrame, blockX, blockY, blockZ);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Returns true if ANY 16x16x16 chunk section overlapped by the given world-space AABB is visible
     * in the current frame. Prevents premature culling of tall/large entities or block entities straddling
     * a 16-block section boundary (x/y/z % 16 == 0).
     */
    public static boolean isAabbVisible(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double camX, double camY, double camZ
    ) {
        if (!VulkanDetector.getBridge().isVulkanActive()) {
            return true;
        }

        double cx = (minX + maxX) * 0.5 - camX;
        double cy = (minY + maxY) * 0.5 - camY;
        double cz = (minZ + maxZ) * 0.5 - camZ;
        if (cx * cx + cy * cy + cz * cz <= NEAR_CAMERA_SAFETY_DIST_SQ) {
            return true;
        }

        int minBx = (int) Math.floor(minX);
        int minBy = (int) Math.floor(minY);
        int minBz = (int) Math.floor(minZ);
        int maxBx = (int) Math.floor(maxX);
        int maxBy = (int) Math.floor(maxY);
        int maxBz = (int) Math.floor(maxZ);

        int minSecX = minBx >> 4;
        int minSecY = minBy >> 4;
        int minSecZ = minBz >> 4;
        int maxSecX = maxBx >> 4;
        int maxSecY = maxBy >> 4;
        int maxSecZ = maxBz >> 4;

        if ((maxSecX - minSecX) > 2 || (maxSecY - minSecY) > 2 || (maxSecZ - minSecZ) > 2) {
            return true;
        }

        try {
            WorldRenderer wr = WorldRenderer.getInstance();
            if (wr == null) {
                return true;
            }
            short currentFrame = wr.getLastFrame();
            if (currentFrame == 0) {
                return true;
            }
            SectionGrid grid = wr.getSectionGrid();
            if (grid == null) {
                return true;
            }

            if (minSecX == maxSecX && minSecY == maxSecY && minSecZ == maxSecZ) {
                return isSectionVisibleRaw(grid, currentFrame, minBx, minBy, minBz);
            }

            for (int sx = minSecX; sx <= maxSecX; sx++) {
                int bx = sx << 4;
                for (int sy = minSecY; sy <= maxSecY; sy++) {
                    int by = sy << 4;
                    for (int sz = minSecZ; sz <= maxSecZ; sz++) {
                        if (isSectionVisibleRaw(grid, currentFrame, bx, by, sz << 4)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean isSectionVisibleRaw(SectionGrid grid, short currentFrame, int blockX, int blockY, int blockZ) {
        RenderSection section = grid.getSectionAtBlockPos(blockX, blockY, blockZ);
        if (section == null) {
            // Outside vertical world bounds (e.g. open sky above build limit): never occluded by terrain
            return true;
        }
        // Verify toroidal SectionGrid ring-buffer slot actually corresponds to this world section
        if (section.xOffset() != (blockX & ~15)
                || section.yOffset() != (blockY & ~15)
                || section.zOffset() != (blockZ & ~15)) {
            return true;
        }
        return section.getLastFrame() == currentFrame;
    }
}
