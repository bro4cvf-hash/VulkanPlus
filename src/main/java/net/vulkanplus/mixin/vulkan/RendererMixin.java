package net.vulkanplus.mixin.vulkan;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanplus.bridge.impl.VulkanModBridgeImpl;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * R4 — Deduplicates redundant GraphicsPipeline bindings, viewport updates, and scissor updates
 * via {@link net.vulkanplus.vulkan.VulkanStateCache}, eliminating double PipelineState lookups
 * and resetting state cleanly across frame and RenderPass boundaries.
 */
@Mixin(value = Renderer.class, remap = false)
public abstract class RendererMixin {

    @Shadow
    private VkCommandBuffer currentCmdBuffer;

    @Shadow
    private RenderPass boundRenderPass;

    @Shadow
    private long boundPipelineHandle;

    @Shadow
    private Pipeline boundPipeline;

    @Unique
    private PipelineState vulkanplus$lastPipelineState;

    @Shadow
    public abstract void addUsedPipeline(Pipeline pipeline);

    @Inject(method = "bindGraphicsPipeline(Lnet/vulkanmod/vulkan/shader/GraphicsPipeline;)V", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$deduplicateBindGraphicsPipeline(GraphicsPipeline pipeline, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg == null || !cfg.enabled || !cfg.enableDescriptorCaching || pipeline == null) {
            return;
        }
        if (this.boundRenderPass == null || this.currentCmdBuffer == null) {
            return;
        }

        PipelineState currentState = PipelineState.getCurrentPipelineState(this.boundRenderPass);
        if (pipeline == this.boundPipeline && currentState == this.vulkanplus$lastPipelineState && this.boundPipelineHandle != 0L) {
            ci.cancel();
            return;
        }

        long handle = pipeline.getHandle(currentState);
        boolean shouldBind = VulkanModBridgeImpl.getStateCache().checkAndBindPipeline(handle);
        if (!shouldBind && this.boundPipelineHandle == handle) {
            this.boundPipeline = pipeline;
            this.vulkanplus$lastPipelineState = currentState;
            ci.cancel();
            return;
        }

        if (handle != 0L) {
            VK10.vkCmdBindPipeline(this.currentCmdBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, handle);
            this.boundPipelineHandle = handle;
            this.boundPipeline = pipeline;
            this.vulkanplus$lastPipelineState = currentState;
            this.addUsedPipeline(pipeline);
            ci.cancel();
        }
    }

    @Inject(method = "setViewport(IIIILorg/lwjgl/system/MemoryStack;)V", at = @At("HEAD"), cancellable = true)
    private static void vulkanplus$deduplicateSetViewport(int x, int y, int width, int height, MemoryStack stack, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableDescriptorCaching) {
            if (!VulkanModBridgeImpl.getStateCache().checkViewport(x, y, width, height)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "setScissor(IIII)V", at = @At("HEAD"), cancellable = true)
    private static void vulkanplus$deduplicateSetScissor(int x, int y, int width, int height, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableDescriptorCaching) {
            if (!VulkanModBridgeImpl.getStateCache().checkScissor(x, y, width, height)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "resetScissor()V", at = @At("HEAD"))
    private static void vulkanplus$onResetScissor(CallbackInfo ci) {
        VulkanModBridgeImpl.getStateCache().resetScissor();
    }

    @Inject(method = "beginFrame()V", at = @At("HEAD"))
    private void vulkanplus$onBeginFrame(CallbackInfo ci) {
        this.vulkanplus$lastPipelineState = null;
        VulkanModBridgeImpl.getStateCache().reset();
    }

    @Inject(method = "beginMainRenderPass(Lorg/lwjgl/system/MemoryStack;)V", at = @At("HEAD"))
    private void vulkanplus$onBeginMainRenderPass(MemoryStack stack, CallbackInfo ci) {
        this.vulkanplus$lastPipelineState = null;
        VulkanModBridgeImpl.getStateCache().reset();
        this.boundPipelineHandle = 0L;
        this.boundPipeline = null;
    }

    @Inject(
            method = "beginRenderPass(Lnet/vulkanmod/vulkan/framebuffer/RenderPass;Lnet/vulkanmod/vulkan/framebuffer/Framebuffer;)Z",
            at = @At("HEAD")
    )
    private void vulkanplus$onBeginRenderPass(RenderPass renderPass, Framebuffer framebuffer, CallbackInfoReturnable<Boolean> cir) {
        this.vulkanplus$lastPipelineState = null;
        VulkanModBridgeImpl.getStateCache().invalidatePipelineAndDynamicState();
    }

    @Inject(method = "endRenderPass(Lorg/lwjgl/vulkan/VkCommandBuffer;)V", at = @At("RETURN"))
    private void vulkanplus$onEndRenderPass(VkCommandBuffer commandBuffer, CallbackInfo ci) {
        this.vulkanplus$lastPipelineState = null;
        VulkanModBridgeImpl.getStateCache().invalidatePipelineAndDynamicState();
    }

    @Inject(method = "resetDescriptors()V", at = @At("RETURN"))
    private void vulkanplus$onResetDescriptors(CallbackInfo ci) {
        this.vulkanplus$lastPipelineState = null;
        VulkanModBridgeImpl.getStateCache().reset();
    }

    @Inject(method = "endFrame()V", at = @At("RETURN"))
    private void vulkanplus$onEndFrame(CallbackInfo ci) {
        this.vulkanplus$lastPipelineState = null;
        VulkanModBridgeImpl.getStateCache().reset();
    }
}
