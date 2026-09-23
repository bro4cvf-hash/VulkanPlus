package net.vulkanplus.mixin.math;

import net.minecraft.util.math.MathHelper;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.math.FastMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(MathHelper.class)
public class MathHelperMixin {

    /**
     * @author VulkanPlus
     * @reason Replace standard sine with ultra-fast polynomial approximation
     */
    @Overwrite
    public static float sin(double value) {
        if (ConfigManager.getConfig().enabled && ConfigManager.getConfig().enableFastMath) {
            return FastMath.sin((float) value);
        }
        return (float) Math.sin(value);
    }

    /**
     * @author VulkanPlus
     * @reason Replace standard cosine with ultra-fast polynomial approximation
     */
    @Overwrite
    public static float cos(double value) {
        if (ConfigManager.getConfig().enabled && ConfigManager.getConfig().enableFastMath) {
            return FastMath.cos((float) value);
        }
        return (float) Math.cos(value);
    }
}
