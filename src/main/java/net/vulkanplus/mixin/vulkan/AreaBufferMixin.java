package net.vulkanplus.mixin.vulkan;

import net.vulkanmod.render.chunk.buffer.AreaBuffer;
import net.vulkanmod.render.chunk.buffer.UploadManager;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCString;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;

/**
 * Optimizes VulkanMod's AreaBuffer initial SOLID and TRANSLUCENT capacity, geometric growth factor (at least 25% or 4MB aligned),
 * and ReBAR direct PCIe BAR upload path to eliminate staging buffer copies and reallocation stalls.
 */
@Mixin(value = AreaBuffer.class, remap = false)
public abstract class AreaBufferMixin {

    @Shadow
    int size;

    @Shadow
    @Final
    private int elementSize;

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static int vulkanplus$boostInitialSolidCapacity(int elementCount) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableBufferPooling) {
            if (elementCount == 100000) {
                return 131072;
            }
            if (elementCount == 60000) {
                return 98304;
            }
            if (elementCount == 250000 && cfg.opaqueLeaves) {
                return 81920;
            }
        }
        return elementCount;
    }

    @Unique
    private boolean vulkanplus$hasPendingReallocCopy = false;

    @Redirect(
            method = "reallocate",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I")
    )
    private int vulkanplus$boostAreaBufferGrowth(int defaultIncrement, int uploadSize) {
        this.vulkanplus$hasPendingReallocCopy = true;
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableBufferPooling && this.elementSize > 0) {
            if (this.size <= 0) {
                return Math.max(defaultIncrement, uploadSize);
            }
            long step = Math.max((long) (this.size >>> 2), 4L * 1024 * 1024);
            long elem = this.elementSize;
            long alignedStep = ((elem & (elem - 1)) == 0)
                    ? ((step + elem - 1) & -elem)
                    : (((step + elem - 1) / elem) * elem);
            long maxAllowed = (long) Integer.MAX_VALUE - this.size;
            if (maxAllowed > 0 && alignedStep > maxAllowed) {
                alignedStep = (maxAllowed / elem) * elem;
            }
            long increment = Math.max(Math.max((long) defaultIncrement, alignedStep), (long) uploadSize);
            if (maxAllowed > 0 && increment > maxAllowed && (long) uploadSize <= maxAllowed) {
                long alignedUpload = ((elem & (elem - 1)) == 0)
                        ? (((long) uploadSize + elem - 1) & -elem)
                        : ((((long) uploadSize + elem - 1) / elem) * elem);
                increment = (alignedUpload <= maxAllowed) ? alignedUpload : (long) uploadSize;
            }
            return (int) increment;
        }
        return Math.max(defaultIncrement, uploadSize);
    }

    @Redirect(
            method = "upload",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/vulkanmod/render/chunk/buffer/UploadManager;recordUpload(Lnet/vulkanmod/vulkan/memory/buffer/Buffer;JJLjava/nio/ByteBuffer;)V"
            )
    )
    private void vulkanplus$directReBarUpload(UploadManager uploadManager, Buffer dst, long dstOffset, long size, ByteBuffer src) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (!this.vulkanplus$hasPendingReallocCopy
                && cfg != null && cfg.enabled && cfg.enableBufferPooling
                && src != null && size > 0L && dstOffset >= 0L
                && dst != null && dst.type != null && dst.type.mappable() && dst.getDataPtr() != 0L
                && (dstOffset + size) <= dst.getBufferSize() && size <= src.remaining()) {
            LibCString.nmemcpy(dst.getDataPtr() + dstOffset, MemoryUtil.memAddress(src), size);
            return;
        }
        this.vulkanplus$hasPendingReallocCopy = false;
        uploadManager.recordUpload(dst, dstOffset, size, src);
    }
}
