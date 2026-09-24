package net.vulkanplus.mixin.culling;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.BillboardParticleSubmittable;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.render.RenderOptimizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BillboardParticle.class)
public abstract class ParticleManagerMixin extends Particle {

    protected ParticleManagerMixin(ClientWorld world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Inject(method = "render(Lnet/minecraft/client/particle/BillboardParticleSubmittable;Lnet/minecraft/client/render/Camera;F)V", at = @At("HEAD"), cancellable = true)
    private void onRenderParticle(BillboardParticleSubmittable submittable, Camera camera, float tickDelta, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (!cfg.enabled) return;

        if (cfg.noParticles) {
            ci.cancel();
            return;
        }

        double jitter = cfg.enableFastRandom ? RenderOptimizer.nextParticleJitter() : 0.0;
        if (cfg.enableParticleCulling && !RenderOptimizer.getParticleCuller().shouldRenderInFrustumParticle(this.x + jitter, this.y, this.z + jitter)) {
            ci.cancel();
        }
    }
}
