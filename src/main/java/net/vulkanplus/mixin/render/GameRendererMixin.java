package net.vulkanplus.mixin.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;
import net.vulkanplus.bridge.VulkanDetector;
import net.vulkanplus.render.RenderOptimizer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    @Final
    private Camera camera;

    @Shadow
    public abstract Matrix4f getBasicProjectionMatrix(float fov);

    @Shadow
    protected abstract float getFov(Camera camera, float tickDelta, boolean changingFov);

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderBegin(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        net.vulkanplus.bridge.ViskCompatBridge.sanitizeTextureCachesIfDirty();
    }

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void onRenderWorldBegin(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!net.vulkanplus.config.ConfigManager.getConfig().enabled) {
            return;
        }
        VulkanDetector.getBridge().onRenderFrameBegin();
        RenderOptimizer.resetFrameStats();

        Vec3d camPos = camera.getCameraPos();
        net.vulkanplus.culling.FoliageCuller.updateCameraPosition(camPos.x, camPos.y, camPos.z);
        float fov = getFov(camera, tickCounter.getTickProgress(true), true);
        Matrix4f proj = getBasicProjectionMatrix(fov);
        RenderOptimizer.updateCameraMatrices(proj, camera.getRotation(), camPos.x, camPos.y, camPos.z);
    }

    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void onRenderWorldEnd(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (net.vulkanplus.config.ConfigManager.getConfig().enabled) {
            VulkanDetector.getBridge().onRenderFrameEnd();
        }
    }
}
