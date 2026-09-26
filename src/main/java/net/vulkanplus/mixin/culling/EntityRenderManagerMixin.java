package net.vulkanplus.mixin.culling;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.render.RenderOptimizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public class EntityRenderManagerMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void onShouldRender(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        net.vulkanplus.config.VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg == null || !cfg.enabled || !cfg.enableEntityCulling || entity == null) return;

        Box box = entity.getBoundingBox();
        if (box == null) {
            return;
        }
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.maxX)
                || !Double.isFinite(box.minY) || !Double.isFinite(box.maxY)
                || !Double.isFinite(box.minZ) || !Double.isFinite(box.maxZ)) {
            return;
        }

        net.vulkanplus.render.FrustumCuller frustumCuller = RenderOptimizer.getFrustumCuller();

        // Dropped items and experience orbs: cull if in an occluded chunk section (e.g. underground mob farm or hopper room behind a wall),
        // then return early to let vanilla's renderer handle them safely without aggressive frustum culling
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity) {
            if (!net.vulkanplus.culling.VulkanSectionVisibility.isAabbVisible(
                    box.minX, box.minY, box.minZ,
                    box.maxX, box.maxY, box.maxZ,
                    frustumCuller.getCameraX(), frustumCuller.getCameraY(), frustumCuller.getCameraZ())) {
                frustumCuller.recordOccludedEntity();
                cir.setReturnValue(false);
            }
            return;
        }

        // Sub-pixel distance culling for tiny ambient entities
        if (entity instanceof net.minecraft.entity.passive.BatEntity
                || entity instanceof net.minecraft.entity.mob.SilverfishEntity
                || entity instanceof net.minecraft.entity.mob.EndermiteEntity
                || entity instanceof net.minecraft.entity.passive.BeeEntity) {
            double maxTinyDist = 48.0 * cfg.cullingDistanceFactor;
            double dx = entity.getX() - frustumCuller.getCameraX();
            double dy = entity.getY() - frustumCuller.getCameraY();
            double dz = entity.getZ() - frustumCuller.getCameraZ();
            if (dx * dx + dy * dy + dz * dz > maxTinyDist * maxTinyDist) {
                frustumCuller.recordOccludedEntity();
                cir.setReturnValue(false);
                return;
            }
        }

        // Expand bounding box with safety margin (0.5 blocks, matching vanilla) to prevent animation / limb clipping
        // Direct primitive coordinates passed to avoid 'new Box' heap allocation
        if (!frustumCuller.isAabbVisible(
                box.minX - 0.5, box.minY - 0.5, box.minZ - 0.5,
                box.maxX + 0.5, box.maxY + 0.5, box.maxZ + 0.5)) {
            cir.setReturnValue(false);
            return;
        }

        if (!net.vulkanplus.culling.VulkanSectionVisibility.isAabbVisible(
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                frustumCuller.getCameraX(), frustumCuller.getCameraY(), frustumCuller.getCameraZ())) {
            frustumCuller.recordOccludedEntity();
            cir.setReturnValue(false);
        }
    }
}
