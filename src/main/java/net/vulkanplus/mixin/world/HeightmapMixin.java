package net.vulkanplus.mixin.world;

import net.minecraft.block.BlockState;
import net.minecraft.util.collection.PaletteStorage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.duck.HeightmapDuck;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Predicate;

@Mixin(value = Heightmap.class, priority = 999)
public abstract class HeightmapMixin implements HeightmapDuck {
    @Shadow @Final private Chunk chunk;
    @Shadow @Final private Predicate<BlockState> blockPredicate;
    @Shadow @Final private PaletteStorage storage;

    @Shadow public abstract int get(int x, int z);
    @Shadow abstract void set(int x, int z, int height);

    @Unique
    private volatile long vulkanplus$sectionMask = -1L;

    @Override
    public long vulkanplus$getSectionMask() {
        if (this.vulkanplus$sectionMask == -1L) {
            vulkanplus$updateSectionMask();
        }
        return this.vulkanplus$sectionMask;
    }

    @Override
    public void vulkanplus$setSectionMask(long mask) {
        this.vulkanplus$sectionMask = mask;
    }

    @Override
    public void vulkanplus$updateSectionMask() {
        ChunkSection[] sections = this.chunk.getSectionArray();
        long mask = 0L;
        for (int i = 0; i < sections.length && i < 64; i++) {
            ChunkSection s = sections[i];
            if (s != null && !s.isEmpty()) {
                mask |= (1L << i);
            }
        }
        this.vulkanplus$sectionMask = mask;
    }

    @Override
    public boolean vulkanplus$isSectionEmpty(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= 64) {
            return false;
        }
        long mask = this.vulkanplus$sectionMask;
        if (mask == -1L) {
            vulkanplus$updateSectionMask();
            mask = this.vulkanplus$sectionMask;
        }
        return (mask & (1L << sectionIndex)) == 0L;
    }

    /**
     * @author VulkanPlus
     * @reason High-performance section-skipping trackUpdate with zero allocations and bitmask tracking.
     */
    @Overwrite
    public boolean trackUpdate(int x, int y, int z, BlockState state) {
        int currentHeight = this.get(x, z);
        if (y <= currentHeight - 2) {
            return false;
        }

        if (this.blockPredicate.test(state)) {
            if (y >= currentHeight) {
                this.set(x, z, y + 1);
                int sectionIdx = this.chunk.getSectionIndex(y);
                if (sectionIdx >= 0 && sectionIdx < 64 && this.vulkanplus$sectionMask != -1L) {
                    this.vulkanplus$sectionMask |= (1L << sectionIdx);
                }
                return true;
            }
        } else if (currentHeight - 1 == y) {
            VulkanPlusConfig cfg = ConfigManager.getConfig();
            if (cfg == null || !cfg.enabled || !cfg.enableFastWorldGen) {
                BlockPos.Mutable mutable = new BlockPos.Mutable();
                for (int j = y - 1; j >= this.chunk.getBottomY(); --j) {
                    mutable.set(x, j, z);
                    if (!this.blockPredicate.test(this.chunk.getBlockState(mutable))) continue;
                    this.set(x, z, j + 1);
                    return true;
                }
                this.set(x, z, this.chunk.getBottomY());
                return true;
            }

            int bottomY = this.chunk.getBottomY();
            int currentY = y - 1;
            ChunkSection[] sections = this.chunk.getSectionArray();

            int localX = x & 15;
            int localZ = z & 15;

            while (currentY >= bottomY) {
                int sectionIndex = this.chunk.getSectionIndex(currentY);
                if (sectionIndex < 0 || sectionIndex >= sections.length) {
                    break;
                }

                ChunkSection section = sections[sectionIndex];
                if (section == null || section.isEmpty() || vulkanplus$isSectionEmpty(sectionIndex)) {
                    // Skip entire 16-block chunk section in one jump
                    currentY = (currentY & ~15) - 1;
                    continue;
                }

                int sectionBaseY = currentY & ~15;
                int startLocalY = currentY & 15;

                for (int localY = startLocalY; localY >= 0; localY--) {
                    BlockState s = section.getBlockState(localX, localY, localZ);
                    if (this.blockPredicate.test(s)) {
                        this.set(x, z, sectionBaseY + localY + 1);
                        return true;
                    }
                }

                currentY = sectionBaseY - 1;
            }

            this.set(x, z, bottomY);
            return true;
        }

        return false;
    }
}
