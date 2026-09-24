package net.vulkanplus.mixin.c2me;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Map;

/**
 * C2ME Optimization: Uses FastUtil's Object2ObjectOpenHashMap for NbtCompound entries
 * instead of standard java.util.HashMap.
 *
 * Accelerates chunk serialization, block entity tag lookup, and reduces heap allocation
 * overhead during chunk streaming.
 */
@Mixin(NbtCompound.class)
public class NbtCompoundMixin {

    @ModifyArg(
            method = "<init>()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtCompound;<init>(Ljava/util/Map;)V"
            ),
            index = 0
    )
    private static Map<String, NbtElement> useFastUtilMap(Map<String, NbtElement> original) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableC2MeOptimizations) {
            return new Object2ObjectOpenHashMap<>();
        }
        return original;
    }
}
