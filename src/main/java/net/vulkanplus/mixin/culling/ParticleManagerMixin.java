package net.vulkanplus.mixin.culling;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.BillboardParticleSubmittable;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Box;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.render.RenderOptimizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BillboardParticle.class)
public abstract class ParticleManagerMixin {

    @Inject(method = "render(Lnet/minecraft/client/particle/BillboardParticleSubmittable;Lnet/minecraft/client/render/Camera;F)V", at = @At("HEAD"), cancellable = true)
    private void onRenderParticle(BillboardParticleSubmittable submittable, Camera camera, float tickDelta, CallbackInfo ci) {
        if (!ConfigManager.getConfig().enabled || !ConfigManager.getConfig().enableParticleCulling) return;

        BillboardParticle self = (BillboardParticle) (Object) this;
        Box box = self.getBoundingBox();
        if (box == null) return;

        double x = box.minX + (box.maxX - box.minX) * 0.5;
        double y = box.minY + (box.maxY - box.minY) * 0.5;
        double z = box.minZ + (box.maxZ - box.minZ) * 0.5;

        if (!RenderOptimizer.getParticleCuller().shouldRenderParticle(x, y, z, 0.2f)) {
            ci.cancel();
        }
    }
}
