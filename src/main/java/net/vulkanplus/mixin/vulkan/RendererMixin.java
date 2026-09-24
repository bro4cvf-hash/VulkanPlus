package net.vulkanplus.mixin.vulkan;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanplus.bridge.impl.VulkanModBridgeImpl;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * R4 — Deduplicates redundant GraphicsPipeline bindings via {@link net.vulkanplus.vulkan.VulkanStateCache}
 * and ensures pipeline state cache is cleanly reset at every frame and command buffer boundary.
 */
@Mixin(value = Renderer.class, remap = false)
public abstract class RendererMixin {

    @Shadow
    private RenderPass boundRenderPass;

    @Shadow
    private long boundPipelineHandle;

    @Shadow
    private Pipeline boundPipeline;

    @Inject(method = "bindGraphicsPipeline(Lnet/vulkanmod/vulkan/shader/GraphicsPipeline;)V", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$deduplicateBindGraphicsPipeline(GraphicsPipeline pipeline, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg == null || !cfg.enabled || !cfg.enableDescriptorCaching || pipeline == null) {
            return;
        }

        PipelineState currentState = PipelineState.getCurrentPipelineState(this.boundRenderPass);
        long handle = pipeline.getHandle(currentState);
        boolean shouldBind = VulkanModBridgeImpl.getStateCache().checkAndBindPipeline(handle);
        if (!shouldBind && this.boundPipelineHandle == handle) {
            ci.cancel();
        }
    }

    @Inject(method = "beginFrame()V", at = @At("HEAD"))
    private void vulkanplus$onBeginFrame(CallbackInfo ci) {
        VulkanModBridgeImpl.getStateCache().reset();
    }

    @Inject(method = "beginMainRenderPass(Lorg/lwjgl/system/MemoryStack;)V", at = @At("HEAD"))
    private void vulkanplus$onBeginMainRenderPass(MemoryStack stack, CallbackInfo ci) {
        VulkanModBridgeImpl.getStateCache().reset();
        this.boundPipelineHandle = 0L;
        this.boundPipeline = null;
    }

    @Inject(method = "resetDescriptors()V", at = @At("RETURN"))
    private void vulkanplus$onResetDescriptors(CallbackInfo ci) {
        VulkanModBridgeImpl.getStateCache().reset();
    }

    @Inject(method = "endFrame()V", at = @At("RETURN"))
    private void vulkanplus$onEndFrame(CallbackInfo ci) {
        VulkanModBridgeImpl.getStateCache().reset();
    }
}
