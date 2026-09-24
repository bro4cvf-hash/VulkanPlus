package net.vulkanplus.mixin.asspc;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererShadowMixin {

    @Inject(
            method = "updateShadow(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onUpdateShadow(Entity entity, EntityRenderState state, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.noEntityShadows) {
            ci.cancel();
        }
    }
}
