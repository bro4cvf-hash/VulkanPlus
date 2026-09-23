package net.vulkanplus.mixin.culling;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.culling.BlockEntityOcclusionCuller;
import net.vulkanplus.render.FrustumCuller;
import net.vulkanplus.render.RenderOptimizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderManager.class)
public class BlockEntityRenderManagerMixin {

    @Inject(method = "getRenderState", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void onGetRenderState(
            E blockEntity,
            float tickDelta,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlayCommand,
            CallbackInfoReturnable<S> cir
    ) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (!config.enabled || !config.enableBlockEntityCulling) return;

        // Never cull beacons or portal gateways whose beams extend far into the sky
        BlockEntityType<?> type = blockEntity.getType();
        if (BlockEntityOcclusionCuller.isProtectedType(type)) {
            return;
        }

        BlockPos pos = blockEntity.getPos();
        // Give chests and large tile entities a generous margin to prevent pop-in
        double minX = pos.getX() - 0.5;
        double minY = pos.getY() - 0.5;
        double minZ = pos.getZ() - 0.5;
        double maxX = pos.getX() + 1.5;
        double maxY = pos.getY() + 1.5;
        double maxZ = pos.getZ() + 1.5;

        FrustumCuller frustumCuller = RenderOptimizer.getFrustumCuller();
        if (!frustumCuller.isAabbVisible(minX, minY, minZ, maxX, maxY, maxZ)) {
            cir.setReturnValue(null);
            return;
        }

        if (config.enableBlockEntityOcclusion) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
                Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
                if (camPos != null) {
                    if (BlockEntityOcclusionCuller.shouldCull(blockEntity, camPos.x, camPos.y, camPos.z)) {
                        cir.setReturnValue(null);
                    }
                }
            }
        }
    }
}
