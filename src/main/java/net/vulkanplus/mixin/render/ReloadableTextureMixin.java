package net.vulkanplus.mixin.render;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.ReloadableTexture;
import net.vulkanplus.bridge.ViskCompatBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensures cached {@code GpuTextureView} instances in external client renderers (e.g. Visk Client)
 * are immediately invalidated whenever {@link ReloadableTexture#load(NativeImage)} closes an
 * existing {@code GpuTexture} and allocates a new one during resource reloads.
 */
@Mixin(ReloadableTexture.class)
public abstract class ReloadableTextureMixin {

    @Inject(method = "load(Lnet/minecraft/client/texture/NativeImage;)V", at = @At("RETURN"))
    private void vulkanplus$onTextureReloaded(NativeImage image, CallbackInfo ci) {
        ViskCompatBridge.markTextureReloaded();
        ViskCompatBridge.sanitizeTextureCaches();
    }
}
