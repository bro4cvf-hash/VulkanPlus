package net.vulkanplus.mixin.asspc;

import net.minecraft.client.model.Model;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Model.class)
public class EntityModelMixin {

    @Inject(method = "setAngles(Ljava/lang/Object;)V", at = @At("HEAD"), cancellable = true)
    private void onSetAngles(Object state, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.noMobAnimations) {
            ((Model) (Object) this).resetTransforms();
            ci.cancel();
        }
    }
}
