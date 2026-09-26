package net.vulkanplus.mixin.leakfix;

import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

/**
 * MemoryLeakFix: In vanilla Minecraft, every Biome instance creates its own non-static
 * ThreadLocal<Long2FloatLinkedOpenHashMap> temperature cache.
 *
 * Because there are dozens of biomes, this spawns hundreds of separate ThreadLocal maps
 * per thread, bloating heap memory and causing garbage collection pressure.
 *
 * Sharing a single static ThreadLocal cache across all biomes completely fixes this leak.
 */
@Mixin(Biome.class)
public abstract class BiomeMixin {

    private static final ThreadLocal<Long2FloatLinkedOpenHashMap> SHARED_TEMP_CACHE = ThreadLocal.withInitial(() -> {
        Long2FloatLinkedOpenHashMap map = new Long2FloatLinkedOpenHashMap(1024, 0.25f);
        map.defaultReturnValue(Float.NaN);
        return map;
    });

    @Unique
    private static long computeBiomeCacheKey(Object biome, long posLong) {
        long z = ((long) System.identityHashCode(biome)) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return posLong ^ z;
    }

    @Unique
    private long vulkanplus$hashBiomeAndPos(BlockPos pos) {
        long posLong = pos.asLong();
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableMemoryLeakFix) {
            return computeBiomeCacheKey(this, posLong);
        }
        return posLong;
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/ThreadLocal;withInitial(Ljava/util/function/Supplier;)Ljava/lang/ThreadLocal;"
            )
    )
    private ThreadLocal<Long2FloatLinkedOpenHashMap> onInitTemperatureCache(Supplier<Long2FloatLinkedOpenHashMap> supplier) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableMemoryLeakFix) {
            return SHARED_TEMP_CACHE;
        }
        return ThreadLocal.withInitial(supplier);
    }

    @Redirect(
            method = "getTemperature",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/BlockPos;asLong()J"
            ),
            require = 0
    )
    private long onGetTemperatureBlockPosAsLong(BlockPos pos) {
        return vulkanplus$hashBiomeAndPos(pos);
    }
}
