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

    private static final int CACHE_SIZE = 32;
    private static final int CACHE_MASK = 31;

    // 32-entry direct-mapped spatial cache per thread: key stores (frame << 48) | (secX << 32) | (secY << 16) | secZ
    private static final ThreadLocal<long[]> cachedKeys = ThreadLocal.withInitial(
            () -> new long[CACHE_SIZE]
    );
    private static final ThreadLocal<boolean[]> cachedVisibilities = ThreadLocal.withInitial(
            () -> new boolean[CACHE_SIZE]
    );

    private VulkanSectionVisibility() {
    }

    /**
     * Returns true if the 16x16x16 chunk section at (blockX, blockY, blockZ) is visible in the current frame,
     * or if VulkanMod's section graph is not available / within the near-camera safety radius.
     */
    public static boolean isPositionVisible(int blockX, int blockY, int blockZ, double camX, double camY, double camZ) {
        double dx = (blockX + 0.5) - camX;
        double dy = (blockY + 0.5) - camY;
        double dz = (blockZ + 0.5) - camZ;
        if (dx * dx + dy * dy + dz * dz <= NEAR_CAMERA_SAFETY_DIST_SQ) {
            return true;
        }

        if (!VulkanDetector.getBridge().isVulkanActive()) {
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
            return isSectionVisible(blockX >> 4, blockY >> 4, blockZ >> 4, currentFrame, grid);
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
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) {
            return true;
        }

        double cx = (minX + maxX) * 0.5 - camX;
        double cy = (minY + maxY) * 0.5 - camY;
        double cz = (minZ + maxZ) * 0.5 - camZ;
        if (cx * cx + cy * cy + cz * cz <= NEAR_CAMERA_SAFETY_DIST_SQ) {
            return true;
        }

        if (!VulkanDetector.getBridge().isVulkanActive()) {
            return true;
        }

        int minBx = net.vulkanplus.math.FastMath.fastFloor(minX);
        int minBy = net.vulkanplus.math.FastMath.fastFloor(minY);
        int minBz = net.vulkanplus.math.FastMath.fastFloor(minZ);
        int maxBx = net.vulkanplus.math.FastMath.fastFloor(maxX > minX ? maxX - 1.0e-5 : maxX);
        int maxBy = net.vulkanplus.math.FastMath.fastFloor(maxY > minY ? maxY - 1.0e-5 : maxY);
        int maxBz = net.vulkanplus.math.FastMath.fastFloor(maxZ > minZ ? maxZ - 1.0e-5 : maxZ);

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
                return isSectionVisible(minSecX, minSecY, minSecZ, currentFrame, grid);
            }

            for (int sx = minSecX; sx <= maxSecX; sx++) {
                for (int sy = minSecY; sy <= maxSecY; sy++) {
                    for (int sz = minSecZ; sz <= maxSecZ; sz++) {
                        if (isSectionVisible(sx, sy, sz, currentFrame, grid)) {
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

    /**
     * Checks if a 16x16x16 chunk section is visible in the current frame using a 32-entry direct-mapped spatial cache.
     * Thread-safe and zero allocation.
     */
    public static boolean isSectionVisible(int secX, int secY, int secZ, short currentFrame, SectionGrid grid) {
        if (currentFrame == 0 || grid == null) {
            return true;
        }

        int hash = ((secX * 31 + secY) * 17 + secZ) & 31;
        long key = ((long) currentFrame << 48) | ((secX & 0xFFFFL) << 32) | ((secY & 0xFFFFL) << 16) | (secZ & 0xFFFFL);

        long[] keys = cachedKeys.get();
        boolean[] visibilities = cachedVisibilities.get();

        if (keys[hash] == key) {
            return visibilities[hash];
        }

        try {
            boolean visible = isSectionVisibleRaw(grid, currentFrame, secX << 4, secY << 4, secZ << 4);
            keys[hash] = key;
            visibilities[hash] = visible;
            return visible;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean isSectionVisible(int secX, int secY, int secZ, int currentFrame, SectionGrid grid) {
        return isSectionVisible(secX, secY, secZ, (short) currentFrame, grid);
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
