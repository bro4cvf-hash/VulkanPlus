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
            "net.vulkanplus.mixin.vulkan.VkRenderPassMixin",
            "net.vulkanplus.mixin.vulkan.TaskDispatcherMixin",
            "net.vulkanplus.mixin.vulkan.AreaBufferMixin",
            "net.vulkanplus.mixin.vulkan.SwapChainMixin",
            "net.vulkanplus.mixin.vulkan.PipelineMixin",
            "net.vulkanplus.mixin.vulkan.MemoryTypesMixin",
            "net.vulkanplus.mixin.vulkan.RendererMixin",
            "net.vulkanplus.mixin.vulkan.WorldRendererMixin",
            "net.vulkanplus.mixin.vulkan.VRenderSystemMixin",
            "net.vulkanplus.mixin.vulkan.DescriptorSetsMixin",
            "net.vulkanplus.mixin.vulkan.MemoryManagerMixin",
            "net.vulkanplus.mixin.culling.VulkanBlockRendererMixin"
    );

    private boolean isVulkanModPresent = isModLoaded("vulkanmod");

    private static boolean isModLoaded(String modId) {
        try {
            return FabricLoader.getInstance().isModLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
        this.isVulkanModPresent = isModLoaded("vulkanmod");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (targetClassName == null || mixinClassName == null) {
            return false;
        }
        if (mixinClassName.contains(".modmenu.") || targetClassName.startsWith("com.terraformersmc.modmenu.")) {
            return isModLoaded("modmenu");
        }
        if (mixinClassName.endsWith("BiomeMixin")
                && (isModLoaded("ferritecore") || isModLoaded("memoryleakfix"))) {
            return false;
        }
        if (mixinClassName.contains(".c2me.") && isModLoaded("c2me")) {
            return false;
        }
        if (isModLoaded("lomka")) {
            if (mixinClassName.contains(".math.BoxMixin")
                    || mixinClassName.contains(".world.")) {
                return false;
            }
        }
        if (VULKANMOD_MIXINS.contains(mixinClassName)
                || mixinClassName.contains(".mixin.vulkan.")
                || targetClassName.startsWith("net.vulkanmod.")) {
            return isVulkanModPresent || isModLoaded("vulkanmod");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        if (isModLoaded("modmenu")) {
            return List.of("modmenu.FabricIconHandlerMixin");
        }
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
