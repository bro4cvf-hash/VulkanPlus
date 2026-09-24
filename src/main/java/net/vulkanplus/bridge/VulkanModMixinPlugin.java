package net.vulkanplus.bridge;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Ensures mixins targeting VulkanMod internal classes are strictly loaded
 * only when the 'vulkanmod' mod is actually present at runtime.
 */
public class VulkanModMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> VULKANMOD_MIXINS = Set.of(
            "net.vulkanplus.mixin.vulkan.OptionsMixin",
            "net.vulkanplus.mixin.vulkan.VkRenderPassMixin",
            "net.vulkanplus.mixin.vulkan.TaskDispatcherMixin",
            "net.vulkanplus.mixin.vulkan.AreaBufferMixin",
            "net.vulkanplus.mixin.vulkan.SwapChainMixin",
            "net.vulkanplus.mixin.vulkan.PipelineMixin",
            "net.vulkanplus.mixin.vulkan.MemoryTypesMixin",
            "net.vulkanplus.mixin.vulkan.RendererMixin",
            "net.vulkanplus.mixin.vulkan.WorldRendererMixin",
            "net.vulkanplus.mixin.vulkan.VRenderSystemMixin",
            "net.vulkanplus.mixin.culling.VulkanBlockRendererMixin"
    );

    private boolean isVulkanModPresent;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            this.isVulkanModPresent = FabricLoader.getInstance().isModLoaded("vulkanmod");
        } catch (Throwable t) {
            this.isVulkanModPresent = false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (VULKANMOD_MIXINS.contains(mixinClassName)
                || mixinClassName.contains(".mixin.vulkan.")
                || targetClassName.startsWith("net.vulkanmod.")) {
            return isVulkanModPresent;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
