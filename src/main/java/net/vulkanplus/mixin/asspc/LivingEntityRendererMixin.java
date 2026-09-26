package net.vulkanplus.mixin.asspc;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.SkeletonEntityRenderState;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.render.AnimationLodEvaluator;
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
        if (state == null) {
            return;
        }
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg == null || !cfg.enabled) {
            return;
        }
        if (cfg.noMobAnimations) {
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
        } else if (cfg.enableAnimationLod && entity != null) {
            if (entity instanceof ClientPlayerEntity
                    || entity instanceof EnderDragonEntity
                    || entity instanceof WitherEntity
                    || entity.deathTime > 0
                    || entity.hurtTime > 0
                    || entity.hasVehicle()
                    || (entity instanceof Leashable l && l.isLeashed())) {
                return;
            }
            double distSq = state.squaredDistanceToCamera;
            int interval = AnimationLodEvaluator.getTickInterval(distSq, cfg.animationLodDistance * cfg.cullingDistanceFactor);
            if (interval == 2) {
                state.limbSwingAnimationProgress = AnimationLodEvaluator.quantizeAngle(state.limbSwingAnimationProgress, 0.125f);
                state.relativeHeadYaw = AnimationLodEvaluator.quantizeAngle(state.relativeHeadYaw, 2.0f);
            } else if (interval == 4) {
                state.limbSwingAnimationProgress = AnimationLodEvaluator.quantizeAngle(state.limbSwingAnimationProgress, 0.25f);
                state.limbSwingAmplitude = AnimationLodEvaluator.quantizeAngle(state.limbSwingAmplitude, 0.1f);
                state.relativeHeadYaw = AnimationLodEvaluator.quantizeAngle(state.relativeHeadYaw, 5.0f);
                state.pitch = 0.0f;
            } else if (interval >= 8) {
                state.limbSwingAmplitude = 0.0f;
                state.limbSwingAnimationProgress = 0.0f;
                state.relativeHeadYaw = 0.0f;
                state.pitch = 0.0f;
            }
        }
    }
}
