package net.vulkanplus.mixin.asspc;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.texture.SpriteContents$Animator")
public class SpriteAnimatorMixin {

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void onTickAnimation(CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.noTextureAnimations) {
            ci.cancel();
        }
    }
}
