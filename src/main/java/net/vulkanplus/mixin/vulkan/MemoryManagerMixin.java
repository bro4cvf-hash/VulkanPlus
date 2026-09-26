package net.vulkanplus.mixin.vulkan;

import net.vulkanmod.vulkan.memory.MemoryManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = MemoryManager.class, remap = false)
public abstract class MemoryManagerMixin {
}
