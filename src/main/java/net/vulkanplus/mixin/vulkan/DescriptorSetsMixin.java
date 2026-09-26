package net.vulkanplus.mixin.vulkan;

import net.vulkanmod.vulkan.shader.DescriptorSets;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Pre-sizes the initial VulkanMod descriptor pool and allocated descriptor sets to 512
 * (instead of the default 10) when VulkanPlus descriptor caching / optimizations are enabled.
 * This avoids multiple expensive pool reallocations (10 -> 20 -> 40 -> 80 -> 160 -> 320 -> 640)
 * per pipeline during initialization and early rendering frames.
 */
@Mixin(value = DescriptorSets.class, remap = false)
public abstract class DescriptorSetsMixin {

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 10))
    private static int vulkanplus$modifyInitialPoolSize(int original) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableDescriptorCaching) {
            return 512;
        }
        return original;
    }
}
