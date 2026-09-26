package net.vulkanplus.mixin.culling;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.BillboardParticleSubmittable;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.culling.VulkanSectionVisibility;
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
        if (cfg == null || !cfg.enabled) return;

        if (cfg.noParticles) {
            ci.cancel();
            return;
        }

        if (cfg.enableParticleCulling) {
            double jitter = cfg.enableFastRandom ? RenderOptimizer.nextParticleJitter() : 0.0;
            double px = this.x + jitter;
            double py = this.y;
            double pz = this.z + jitter;

            double camX = RenderOptimizer.getFrustumCuller().getCameraX();
            double camY = RenderOptimizer.getFrustumCuller().getCameraY();
            double camZ = RenderOptimizer.getFrustumCuller().getCameraZ();
            if (camera != null && camera.getCameraPos() != null) {
                Vec3d pos = camera.getCameraPos();
                camX = pos.x;
                camY = pos.y;
                camZ = pos.z;
            }

            double factor = cfg.cullingDistanceFactor > 0.0 ? cfg.cullingDistanceFactor : 1.0;
            double maxDist = cfg.particleCullingDistance * factor;
            double dx = px - camX;
            double dy = py - camY;
            double dz = pz - camZ;
            if (dx * dx + dy * dy + dz * dz > maxDist * maxDist) {
                ci.cancel();
                return;
            }

            if (!VulkanSectionVisibility.isPositionVisible(
                    (int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz),
                    camX, camY, camZ)) {
                ci.cancel();
                return;
            }

            if (!RenderOptimizer.getParticleCuller().shouldRenderInFrustumParticle(px, py, pz)) {
                ci.cancel();
            }
        }
    }
}
