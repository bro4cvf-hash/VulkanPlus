package net.vulkanplus.mixin.asspc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.Vec3d;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.culling.VulkanSectionVisibility;
import net.vulkanplus.render.RenderOptimizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public class ParticleManagerAddMixin {

    @Inject(
            method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAddParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg == null || !cfg.enabled) return;

        if (cfg.noParticles) {
            cir.setReturnValue(null);
            return;
        }

        if (cfg.enableParticleCulling) {
            double camX = RenderOptimizer.getFrustumCuller().getCameraX();
            double camY = RenderOptimizer.getFrustumCuller().getCameraY();
            double camZ = RenderOptimizer.getFrustumCuller().getCameraZ();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.gameRenderer != null) {
                Camera camera = client.gameRenderer.getCamera();
                if (camera != null && camera.getCameraPos() != null) {
                    Vec3d pos = camera.getCameraPos();
                    camX = pos.x;
                    camY = pos.y;
                    camZ = pos.z;
                }
            }

            double factor = cfg.cullingDistanceFactor > 0.0 ? cfg.cullingDistanceFactor : 1.0;
            double maxDist = cfg.particleCullingDistance * factor;
            double dx = x - camX;
            double dy = y - camY;
            double dz = z - camZ;
            if (dx * dx + dy * dy + dz * dz > maxDist * maxDist) {
                cir.setReturnValue(null);
                return;
            }

            if (!VulkanSectionVisibility.isPositionVisible(
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z),
                    camX, camY, camZ)) {
                cir.setReturnValue(null);
            }
        }
    }
}
