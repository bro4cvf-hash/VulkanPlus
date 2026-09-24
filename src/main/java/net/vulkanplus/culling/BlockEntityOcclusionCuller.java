package net.vulkanplus.culling;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;

public final class BlockEntityOcclusionCuller {

    private static final ThreadLocal<BlockPos.Mutable> MUTABLE_POS = ThreadLocal.withInitial(BlockPos.Mutable::new);

    public static long culledOccludedBlockEntities = 0;

    public static void resetStats() {
        culledOccludedBlockEntities = 0;
    }

    /**
     * Checks if a BlockEntity type should be protected from occlusion culling.
     * Returns immediately after identity checks without allocating Strings on the normal fast path.
     */
    public static boolean isProtectedType(BlockEntityType<?> type) {
        if (type == null) return false;
        try {
            return type == BlockEntityType.BEACON
                    || type == BlockEntityType.END_GATEWAY
                    || type == BlockEntityType.END_PORTAL;
        } catch (Throwable ignored) {
            String s = String.valueOf(type);
            return s.contains("beacon") || s.contains("end_gateway") || s.contains("end_portal");
        }
    }

    /**
     * Helper for checking type protection by identifier or name string.
     */
    public static boolean isProtectedTypeName(String typeName) {
        if (typeName == null) return false;
        String lower = typeName.toLowerCase();
        return lower.contains("beacon") || lower.contains("end_gateway") || lower.contains("end_portal");
    }

    /**
     * Evaluates whether a BlockEntity is completely occluded from the camera's vantage point.
     * Uses primitive coordinate comparisons.
     */
    public static boolean shouldCull(BlockEntity blockEntity, double camX, double camY, double camZ) {
        if (blockEntity == null) return false;
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (!config.enabled || !config.enableBlockEntityOcclusion) {
            return false;
        }

        if (isProtectedType(blockEntity.getType())) {
            return false;
        }

        return shouldCullUnchecked(blockEntity, camX, camY, camZ);
    }

    /**
     * Fast path when config and type protection have already been verified by the caller.
     */
    public static boolean shouldCullUnchecked(BlockEntity blockEntity, double camX, double camY, double camZ) {
        World world = blockEntity.getWorld();
        if (world == null) return false;

        BlockPos pos = blockEntity.getPos();
        if (isOccluded(world, pos, camX, camY, camZ)) {
            culledOccludedBlockEntities++;
            return true;
        }
        return false;
    }

    /**
     * Evaluates whether a block at pos is occluded from the camera point.
     * Tests all camera-facing faces (at most 3) using a reusable ThreadLocal<BlockPos.Mutable>.
     */
    public static boolean isOccluded(World world, BlockPos pos, double camX, double camY, double camZ) {
        int ix = pos.getX();
        int iy = pos.getY();
        int iz = pos.getZ();
        double bx = ix;
        double by = iy;
        double bz = iz;

        if (camX >= bx && camX <= bx + 1.0
                && camY >= by && camY <= by + 1.0
                && camZ >= bz && camZ <= bz + 1.0) {
            return false;
        }

        BlockPos.Mutable mPos = MUTABLE_POS.get();
        boolean allOccluded = true;
        int checkedFaces = 0;

        if (camX > bx + 1.0) {
            checkedFaces++;
            BlockState neighbor = world.getBlockState(mPos.set(ix + 1, iy, iz));
            if (!neighbor.isOpaqueFullCube()) {
                allOccluded = false;
            }
        } else if (camX < bx) {
            checkedFaces++;
            BlockState neighbor = world.getBlockState(mPos.set(ix - 1, iy, iz));
            if (!neighbor.isOpaqueFullCube()) {
                allOccluded = false;
            }
        }

        if (allOccluded && camY > by + 1.0) {
            checkedFaces++;
            BlockState neighbor = world.getBlockState(mPos.set(ix, iy + 1, iz));
            if (!neighbor.isOpaqueFullCube()) {
                allOccluded = false;
            }
        } else if (allOccluded && camY < by) {
            checkedFaces++;
            BlockState neighbor = world.getBlockState(mPos.set(ix, iy - 1, iz));
            if (!neighbor.isOpaqueFullCube()) {
                allOccluded = false;
            }
        }

        if (allOccluded && camZ > bz + 1.0) {
            checkedFaces++;
            BlockState neighbor = world.getBlockState(mPos.set(ix, iy, iz + 1));
            if (!neighbor.isOpaqueFullCube()) {
                allOccluded = false;
            }
        } else if (allOccluded && camZ < bz) {
            checkedFaces++;
            BlockState neighbor = world.getBlockState(mPos.set(ix, iy, iz - 1));
            if (!neighbor.isOpaqueFullCube()) {
                allOccluded = false;
            }
        }

        return checkedFaces > 0 && allOccluded;
    }
}
