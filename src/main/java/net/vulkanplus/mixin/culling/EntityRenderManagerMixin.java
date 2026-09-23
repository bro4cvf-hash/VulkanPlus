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
        if (!ConfigManager.getConfig().enabled || !ConfigManager.getConfig().enableEntityCulling) return;

        // Never cull dropped items or experience orbs in the early pass; let vanilla's renderer handle them safely
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity) {
            return;
        }

        Box box = entity.getBoundingBox();
        if (box == null || box.isNaN() || box.getAverageSideLength() == 0.0) {
            return;
        }

        // Expand bounding box with safety margin (0.5 blocks, matching vanilla) to prevent animation / limb clipping
        // Direct primitive coordinates passed to avoid 'new Box' heap allocation
        if (!RenderOptimizer.getFrustumCuller().isAabbVisible(
                box.minX - 0.5, box.minY - 0.5, box.minZ - 0.5,
                box.maxX + 0.5, box.maxY + 0.5, box.maxZ + 0.5)) {
            cir.setReturnValue(false);
        }
    }
}
