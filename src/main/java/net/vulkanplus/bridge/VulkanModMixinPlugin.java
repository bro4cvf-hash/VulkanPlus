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
        return isVulkanModPresent;
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
