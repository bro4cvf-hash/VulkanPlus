package net.vulkanplus.mixin.asspc;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.ChestBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.ChestBlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.util.math.Vec3d;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChestBlockEntityRenderer.class)
public class ChestBlockEntityRendererMixin {

    @Inject(
            method = "updateRenderState(Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/client/render/block/entity/state/ChestBlockEntityRenderState;FLnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
            at = @At("RETURN")
    )
    private void onUpdateRenderState(BlockEntity entity, ChestBlockEntityRenderState state, float tickDelta, Vec3d pos, ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlayCommand, CallbackInfo ci) {
        if (state == null) return;
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled) {
            if (cfg.noBlockEntityAnimations || cfg.fastChest) {
                state.lidAnimationProgress = 0.0f;
                return;
            }
            if (pos != null && pos.lengthSquared() > 24.0 * 24.0) {
                state.lidAnimationProgress = state.lidAnimationProgress > 0.5f ? 1.0f : 0.0f;
                return;
            }
            if (state.lidAnimationProgress < 0.001f) {
                state.lidAnimationProgress = 0.0f;
            }
        }
    }
}
