package net.vulkanplus.culling;

import net.minecraft.block.BlockState;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;

public final class ItemFrameCuller {
    private static final ThreadLocal<BlockPos.Mutable> MUTABLE_POS = ThreadLocal.withInitial(BlockPos.Mutable::new);

    // Epsilon tolerance to protect grazing angles and 3D item geometry
    public static final double BACKFACE_EPSILON = -0.02;

    public static long culledBackfaceFrames = 0;
    public static long culledOccludedFrames = 0;
    public static long culledDistanceFrames = 0;
    public static long culledItemModels = 0;

    public static void resetStats() {
        culledBackfaceFrames = 0;
        culledOccludedFrames = 0;
        culledDistanceFrames = 0;
        culledItemModels = 0;
    }

    /**
     * Evaluates whether an ItemFrameEntity (including GlowItemFrameEntity) should be culled completely.
     *
     * @return true if the frame should be culled (hidden), false if it should render.
     */
    public static boolean shouldCullFrame(ItemFrameEntity frame, double camX, double camY, double camZ) {
        if (frame == null) return false;
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || !config.enableFastItemFrames) {
            return false;
        }

        double fx = frame.getX();
        double fy = frame.getY();
        double fz = frame.getZ();

        double dx = camX - fx;
        double dy = camY - fy;
        double dz = camZ - fz;
        double distSq = dx * dx + dy * dy + dz * dz;

        double maxDist = config.itemFrameMaxDistance * config.cullingDistanceFactor;
        if (distSq > maxDist * maxDist) {
            culledDistanceFrames++;
            return true;
        }

        Direction facing = frame.getFacing();
        if (facing == null) {
            facing = frame.getHorizontalFacing();
        }
        if (facing == null) {
            return false;
        }

        int nx = facing.getOffsetX();
        int ny = facing.getOffsetY();
        int nz = facing.getOffsetZ();

        if (isBackfaceCulled(nx, ny, nz, fx, fy, fz, camX, camY, camZ)) {
            culledBackfaceFrames++;
            return true;
        }

        if (config.enableItemFrameBlockOcclusion) {
            World world = frame.getEntityWorld();
            BlockPos framePos = frame.getBlockPos();
            if (world != null && framePos != null) {
                BlockState stateAtFrame = world.getBlockState(framePos);

                if (stateAtFrame != null && stateAtFrame.isOpaqueFullCube()) {
                    culledOccludedFrames++;
                    return true;
                }

                BlockPos.Mutable frontPos = MUTABLE_POS.get().set(
                        framePos.getX() + nx,
                        framePos.getY() + ny,
                        framePos.getZ() + nz
                );
                BlockState stateInFront = world.getBlockState(frontPos);
                if (stateInFront != null && stateInFront.isOpaqueFullCube()) {
                    culledOccludedFrames++;
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Evaluates directional backface culling purely from coordinates and normal vector.
     */
    public static boolean isBackfaceCulled(int nx, int ny, int nz, double fx, double fy, double fz, double camX, double camY, double camZ) {
        double dx = camX - fx;
        double dy = camY - fy;
        double dz = camZ - fz;
        double dot = nx * dx + ny * dy + nz * dz;
        return dot <= BACKFACE_EPSILON;
    }

    /**
     * Evaluates whether the 3D item stack inside the frame should be culled due to distance.
     */
    public static boolean shouldCullContainedItem(ItemFrameEntity frame, double camX, double camY, double camZ) {
        if (frame == null) return false;
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || !config.enableFastItemFrames) {
            return false;
        }

        double dx = camX - frame.getX();
        double dy = camY - frame.getY();
        double dz = camZ - frame.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;

        double maxItemDist = config.itemFrameItemDistance * config.cullingDistanceFactor;
        if (distSq > maxItemDist * maxItemDist) {
            culledItemModels++;
            return true;
        }
        return false;
    }
}
