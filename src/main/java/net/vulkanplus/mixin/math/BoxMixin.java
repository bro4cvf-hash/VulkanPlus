package net.vulkanplus.mixin.math;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(Box.class)
public abstract class BoxMixin {
    @Shadow @Final public double minX;
    @Shadow @Final public double minY;
    @Shadow @Final public double minZ;
    @Shadow @Final public double maxX;
    @Shadow @Final public double maxY;
    @Shadow @Final public double maxZ;

    /**
     * @author VulkanPlus
     * @reason Inlined scalar zero-allocation ray-box intersection.
     */
    @Overwrite
    public Optional<Vec3d> raycast(Vec3d from, Vec3d to) {
        return raycast(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ, from, to);
    }

    /**
     * @author VulkanPlus
     * @reason Pure scalar raycast calculation on the thread stack without double[] allocation.
     */
    @Overwrite
    public static Optional<Vec3d> raycast(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Vec3d from, Vec3d to) {
        double deltaX = to.x - from.x;
        double deltaY = to.y - from.y;
        double deltaZ = to.z - from.z;

        double minT = 1.0;
        Direction direction = null;

        // X axis
        if (deltaX > 1.0E-7) {
            double d = (minX - from.x) / deltaX;
            if (d > 0.0 && d < minT) {
                double e = from.y + d * deltaY;
                double f = from.z + d * deltaZ;
                if (minY - 1.0E-7 < e && e < maxY + 1.0E-7 && minZ - 1.0E-7 < f && f < maxZ + 1.0E-7) {
                    minT = d;
                    direction = Direction.WEST;
                }
            }
        } else if (deltaX < -1.0E-7) {
            double d = (maxX - from.x) / deltaX;
            if (d > 0.0 && d < minT) {
                double e = from.y + d * deltaY;
                double f = from.z + d * deltaZ;
                if (minY - 1.0E-7 < e && e < maxY + 1.0E-7 && minZ - 1.0E-7 < f && f < maxZ + 1.0E-7) {
                    minT = d;
                    direction = Direction.EAST;
                }
            }
        }

        // Y axis
        if (deltaY > 1.0E-7) {
            double d = (minY - from.y) / deltaY;
            if (d > 0.0 && d < minT) {
                double e = from.z + d * deltaZ;
                double f = from.x + d * deltaX;
                if (minZ - 1.0E-7 < e && e < maxZ + 1.0E-7 && minX - 1.0E-7 < f && f < maxX + 1.0E-7) {
                    minT = d;
                    direction = Direction.DOWN;
                }
            }
        } else if (deltaY < -1.0E-7) {
            double d = (maxY - from.y) / deltaY;
            if (d > 0.0 && d < minT) {
                double e = from.z + d * deltaZ;
                double f = from.x + d * deltaX;
                if (minZ - 1.0E-7 < e && e < maxZ + 1.0E-7 && minX - 1.0E-7 < f && f < maxX + 1.0E-7) {
                    minT = d;
                    direction = Direction.UP;
                }
            }
        }

        // Z axis
        if (deltaZ > 1.0E-7) {
            double d = (minZ - from.z) / deltaZ;
            if (d > 0.0 && d < minT) {
                double e = from.x + d * deltaX;
                double f = from.y + d * deltaY;
                if (minX - 1.0E-7 < e && e < maxX + 1.0E-7 && minY - 1.0E-7 < f && f < maxY + 1.0E-7) {
                    minT = d;
                    direction = Direction.NORTH;
                }
            }
        } else if (deltaZ < -1.0E-7) {
            double d = (maxZ - from.z) / deltaZ;
            if (d > 0.0 && d < minT) {
                double e = from.x + d * deltaX;
                double f = from.y + d * deltaY;
                if (minX - 1.0E-7 < e && e < maxX + 1.0E-7 && minY - 1.0E-7 < f && f < maxY + 1.0E-7) {
                    minT = d;
                    direction = Direction.SOUTH;
                }
            }
        }

        if (direction == null) {
            return Optional.empty();
        }
        return Optional.of(from.add(minT * deltaX, minT * deltaY, minT * deltaZ));
    }

    /**
     * @author VulkanPlus
     * @reason Zero-allocation multi-box raycast inlined with scalar block offset, eliminating Box.offset() heap churn.
     */
    @Overwrite
    public static @Nullable BlockHitResult raycast(Iterable<Box> boxes, Vec3d from, Vec3d to, BlockPos pos) {
        double deltaX = to.x - from.x;
        double deltaY = to.y - from.y;
        double deltaZ = to.z - from.z;

        double posX = pos.getX();
        double posY = pos.getY();
        double posZ = pos.getZ();

        double minT = 1.0;
        Direction direction = null;

        for (Box box : boxes) {
            double minX = box.minX + posX;
            double minY = box.minY + posY;
            double minZ = box.minZ + posZ;
            double maxX = box.maxX + posX;
            double maxY = box.maxY + posY;
            double maxZ = box.maxZ + posZ;

            // X axis
            if (deltaX > 1.0E-7) {
                double d = (minX - from.x) / deltaX;
                if (d > 0.0 && d < minT) {
                    double e = from.y + d * deltaY;
                    double f = from.z + d * deltaZ;
                    if (minY - 1.0E-7 < e && e < maxY + 1.0E-7 && minZ - 1.0E-7 < f && f < maxZ + 1.0E-7) {
                        minT = d;
                        direction = Direction.WEST;
                    }
                }
            } else if (deltaX < -1.0E-7) {
                double d = (maxX - from.x) / deltaX;
                if (d > 0.0 && d < minT) {
                    double e = from.y + d * deltaY;
                    double f = from.z + d * deltaZ;
                    if (minY - 1.0E-7 < e && e < maxY + 1.0E-7 && minZ - 1.0E-7 < f && f < maxZ + 1.0E-7) {
                        minT = d;
                        direction = Direction.EAST;
                    }
                }
            }

            // Y axis
            if (deltaY > 1.0E-7) {
                double d = (minY - from.y) / deltaY;
                if (d > 0.0 && d < minT) {
                    double e = from.z + d * deltaZ;
                    double f = from.x + d * deltaX;
                    if (minZ - 1.0E-7 < e && e < maxZ + 1.0E-7 && minX - 1.0E-7 < f && f < maxX + 1.0E-7) {
                        minT = d;
                        direction = Direction.DOWN;
                    }
                }
            } else if (deltaY < -1.0E-7) {
                double d = (maxY - from.y) / deltaY;
                if (d > 0.0 && d < minT) {
                    double e = from.z + d * deltaZ;
                    double f = from.x + d * deltaX;
                    if (minZ - 1.0E-7 < e && e < maxZ + 1.0E-7 && minX - 1.0E-7 < f && f < maxX + 1.0E-7) {
                        minT = d;
                        direction = Direction.UP;
                    }
                }
            }

            // Z axis
            if (deltaZ > 1.0E-7) {
                double d = (minZ - from.z) / deltaZ;
                if (d > 0.0 && d < minT) {
                    double e = from.x + d * deltaX;
                    double f = from.y + d * deltaY;
                    if (minX - 1.0E-7 < e && e < maxX + 1.0E-7 && minY - 1.0E-7 < f && f < maxY + 1.0E-7) {
                        minT = d;
                        direction = Direction.NORTH;
                    }
                }
            } else if (deltaZ < -1.0E-7) {
                double d = (maxZ - from.z) / deltaZ;
                if (d > 0.0 && d < minT) {
                    double e = from.x + d * deltaX;
                    double f = from.y + d * deltaY;
                    if (minX - 1.0E-7 < e && e < maxX + 1.0E-7 && minY - 1.0E-7 < f && f < maxY + 1.0E-7) {
                        minT = d;
                        direction = Direction.SOUTH;
                    }
                }
            }
        }

        if (direction == null) {
            return null;
        }
        return new BlockHitResult(from.add(minT * deltaX, minT * deltaY, minT * deltaZ), direction, pos, false);
    }
}
