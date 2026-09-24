package net.vulkanplus.mixin.asspc;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.SkeletonEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN")
    )
    private void onUpdateLivingRenderState(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.noMobAnimations) {
            state.limbSwingAnimationProgress = 0.0f;
            state.limbSwingAmplitude = 0.0f;
            state.relativeHeadYaw = 0.0f;
            state.pitch = 0.0f;
            state.deathTime = 0.0f;
            state.age = 0.0f;
            if (state instanceof ArmedEntityRenderState armed) {
                armed.handSwingProgress = 0.0f;
                armed.rightArmPose = BipedEntityModel.ArmPose.EMPTY;
                armed.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
            }
            if (state instanceof BipedEntityRenderState biped) {
                biped.leaningPitch = 0.0f;
                biped.crossbowPullTime = 0.0f;
                biped.itemUseTime = 0.0f;
                biped.isUsingItem = false;
                biped.isInSneakingPose = false;
                biped.isGliding = false;
                biped.isSwimming = false;
            }
            if (state instanceof SkeletonEntityRenderState skeleton) {
                skeleton.attacking = false;
                skeleton.holdingBow = false;
            }
        }
    }
}
