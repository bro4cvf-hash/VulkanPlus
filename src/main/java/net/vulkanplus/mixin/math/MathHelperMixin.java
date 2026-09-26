package net.vulkanplus.mixin.math;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.math.FastMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MathHelper.class)
public class MathHelperMixin {

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

    @Inject(method = "sin(D)F", at = @At("HEAD"), cancellable = true)
    private static void vulkanplus$fastSin(double value, CallbackInfoReturnable<Float> cir) {
        if (isFastMathEnabled()) {
            cir.setReturnValue(FastMath.sin((float) value));
        }
    }

    @Inject(method = "cos(D)F", at = @At("HEAD"), cancellable = true)
    private static void vulkanplus$fastCos(double value, CallbackInfoReturnable<Float> cir) {
        if (isFastMathEnabled()) {
            cir.setReturnValue(FastMath.cos((float) value));
        }
    }
}
