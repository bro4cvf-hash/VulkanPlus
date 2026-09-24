package net.vulkanplus.mixin.leakfix;

import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import net.minecraft.world.biome.Biome;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
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

    private static ThreadLocal<Long2FloatLinkedOpenHashMap> SHARED_TEMP_CACHE;

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
            if (SHARED_TEMP_CACHE == null) {
                SHARED_TEMP_CACHE = ThreadLocal.withInitial(supplier);
            }
            return SHARED_TEMP_CACHE;
        }
        return ThreadLocal.withInitial(supplier);
    }
}
