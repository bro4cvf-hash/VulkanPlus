package net.vulkanplus.mixin.asspc;

import net.minecraft.client.render.entity.ExperienceOrbEntityRenderer;
import net.minecraft.client.render.entity.state.ExperienceOrbEntityRenderState;
import net.minecraft.entity.ExperienceOrbEntity;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrbEntityRenderer.class)
public class ExperienceOrbRendererMixin {

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/ExperienceOrbEntity;Lnet/minecraft/client/render/entity/state/ExperienceOrbEntityRenderState;F)V",
            at = @At("RETURN")
    )
    private void onUpdateRenderState(ExperienceOrbEntity entity, ExperienceOrbEntityRenderState state, float tickDelta, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.staticExpAnimations) {
            state.age = 0.0f;
        }
    }
}
