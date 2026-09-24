package net.vulkanplus.mixin.vulkan;

import net.vulkanmod.config.gui.OptionBlock;
import net.vulkanmod.config.option.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ensures VulkanMod Graphics options are clean without injecting redundant duplicate switches.
 * Leaves configuration is handled seamlessly through Vulkan Plus's own Engine page.
 */
@Mixin(Options.class)
public class OptionsMixin {

    @Inject(method = "getGraphicsOpts", at = @At("RETURN"), cancellable = true, remap = false)
    private static void onGetGraphicsOpts(CallbackInfoReturnable<OptionBlock[]> cir) {
        // Do not inject a duplicate Opaque Leaves switch since VulkanMod already provides Cutout Leaves.
    }
}
