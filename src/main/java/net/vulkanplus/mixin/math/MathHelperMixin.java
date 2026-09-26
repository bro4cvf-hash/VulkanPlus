package net.vulkanplus.mixin.math;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.math.FastMath;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MathHelper.class)
public class MathHelperMixin {

    @Shadow
    @Final
    private static float[] SINE_TABLE;

    @Unique
    private static final boolean IS_LITHIUM_LOADED = detectLithium();

    @Unique
    private static boolean detectLithium() {
        try {
            return FabricLoader.getInstance().isModLoaded("lithium");
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Unique
    private static boolean isFastMathEnabled() {
        if (IS_LITHIUM_LOADED) {
            return false;
        }
        final VulkanPlusConfig cfg = ConfigManager.getConfig();
        return cfg != null && cfg.enabled && cfg.enableFastMath;
    }

    /**
     * @author VulkanPlus
     * @reason Direct primitive float trigonometric evaluation with zero heap allocations.
     */
    @Overwrite
    public static float sin(double value) {
        if (isFastMathEnabled()) {
            return FastMath.sin((float) value);
        }
        return SINE_TABLE[(int) ((long) (value * 10430.378350470453) & 0xFFFFL)];
    }

    /**
     * @author VulkanPlus
     * @reason Direct primitive float trigonometric evaluation with zero heap allocations.
     */
    @Overwrite
    public static float cos(double value) {
        if (isFastMathEnabled()) {
            return FastMath.cos((float) value);
        }
        return SINE_TABLE[(int) ((long) (value * 10430.378350470453 + 16384.0) & 0xFFFFL)];
    }
}
