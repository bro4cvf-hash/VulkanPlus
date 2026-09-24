package net.vulkanplus.mixin.vulkan;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanplus.bridge.ViskCompatBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Intercepts {@link VkRenderPass#bindTexture} in VulkanMod to automatically heal stale/closed
 * {@link GpuTextureView} instances passed by mods (such as Visk Client's {@code SpriteRenderer})
 * after a resource reload.
 */
@Mixin(value = VkRenderPass.class, remap = false)
public abstract class VkRenderPassMixin {

    @ModifyVariable(
            method = "bindTexture",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private GpuTextureView vulkanplus$healClosedTextureView(GpuTextureView view) {
        return ViskCompatBridge.healTextureView(view);
    }
}
