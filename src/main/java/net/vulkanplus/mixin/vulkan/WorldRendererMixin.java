package net.vulkanplus.mixin.vulkan;

import net.minecraft.client.option.SimpleOption;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Eliminates chunk fade-in animation duration in VulkanMod when noChunkFade is active.
 * Forces chunk fade duration to 0ms for instant chunk visibility without alpha transition math.
 */
@Mixin(targets = "net.vulkanmod.render.chunk.WorldRenderer", remap = false)
public class WorldRendererMixin {

    @Redirect(
            method = "renderSectionLayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;",
                    ordinal = 1
            ),
            remap = false
    )
    private Object onGetChunkFadeValue(SimpleOption<?> option) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.noChunkFade) {
            return 0.0d;
        }
        return option.getValue();
    }
}
