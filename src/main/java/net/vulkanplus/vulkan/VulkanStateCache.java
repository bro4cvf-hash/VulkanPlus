package net.vulkanplus.vulkan;

import java.util.Arrays;

/**
 * High-speed Vulkan command recording filter and state cache.
 * Eliminates redundant vkCmdBindPipeline, vkCmdBindDescriptorSets, and dynamic state updates
 * (viewport, scissor, depth bias, blend constants, stencil ref, line width),
 * cutting CPU driver dispatch overhead.
 */
public class VulkanStateCache {
    public static final int BIND_POINT_GRAPHICS = 0;
    public static final int BIND_POINT_COMPUTE = 1;
    public static final int BIND_POINT_COUNT = 2;

    public static final int MAX_DESCRIPTOR_SETS = 8;

    private final long[] currentPipelines = new long[BIND_POINT_COUNT];
    private final long[] currentDescriptorSets = new long[MAX_DESCRIPTOR_SETS];

    private long currentPipelineId = -1;
    private long currentDescriptorSetId = -1;
    private long boundPackedState = -1L;

    private long currentViewportPos = Long.MIN_VALUE;
    private long currentViewportSize = Long.MIN_VALUE;
    private long currentScissorPos = Long.MIN_VALUE;
    private long currentScissorSize = Long.MIN_VALUE;

    // Dynamic states (packed 64-bit IEEE float bits)
    private long currentDepthBias = Long.MIN_VALUE;
    private long currentBlendConstants0 = Long.MIN_VALUE;
    private long currentBlendConstants1 = Long.MIN_VALUE;
    private int currentLineWidthBits = Integer.MIN_VALUE;
    private int currentStencilRef = -1;

    private long redundantBindsPrevented = 0;
    private long skippedPipelineBinds = 0;
    private long skippedDescriptorBinds = 0;
    private long skippedDynamicStateUpdates = 0;
    private long totalBindAttempts = 0;

    public VulkanStateCache() {
        Arrays.fill(currentPipelines, -1L);
        Arrays.fill(currentDescriptorSets, -1L);
        this.boundPackedState = -1L;
    }

    /**
     * Checks if the 64-bit packed pipeline state needs binding or is already active.
     * Uses bitwise XOR comparison to eliminate redundant pipeline state changes in O(1).
     *
     * @param packedState 64-bit packed pipeline and rasterization state bitmask
     * @return true if state changed and binding is required; false if redundant
     */
    public boolean checkAndBindPackedState(long packedState) {
        totalBindAttempts++;
        if ((this.boundPackedState ^ packedState) == 0L) {
            redundantBindsPrevented++;
            return false;
        }
        this.boundPackedState = packedState;
        return true;
    }

    /**
     * Checks if the graphics pipeline needs binding or is already active.
     *
     * @return true if pipeline binding is required; false if redundant.
     */
    public boolean checkAndBindPipeline(long pipelineId) {
        return checkAndBindPipeline(BIND_POINT_GRAPHICS, pipelineId);
    }

    /**
     * Checks if a pipeline for the specified bind point needs binding or is already active.
     */
    public boolean checkAndBindPipeline(int bindPoint, long pipelineId) {
        totalBindAttempts++;
        if (bindPoint >= 0 && bindPoint < BIND_POINT_COUNT) {
            if (this.currentPipelines[bindPoint] == pipelineId) {
                redundantBindsPrevented++;
                skippedPipelineBinds++;
                return false;
            }
            this.currentPipelines[bindPoint] = pipelineId;
            if (bindPoint == BIND_POINT_GRAPHICS) {
                this.currentPipelineId = pipelineId;
            }
            return true;
        }

        if (this.currentPipelineId == pipelineId) {
            redundantBindsPrevented++;
            skippedPipelineBinds++;
            return false;
        }
        this.currentPipelineId = pipelineId;
        return true;
    }

    /**
     * Checks if descriptor set at index 0 needs binding or is already active.
     */
    public boolean checkAndBindDescriptorSet(long descriptorSetId) {
        return checkAndBindDescriptorSet(0, descriptorSetId);
    }

    /**
     * Checks if descriptor set at specific slot index needs binding or is already active.
     */
    public boolean checkAndBindDescriptorSet(int setIndex, long descriptorSetId) {
        totalBindAttempts++;
        if (setIndex >= 0 && setIndex < MAX_DESCRIPTOR_SETS) {
            if (this.currentDescriptorSets[setIndex] == descriptorSetId) {
                redundantBindsPrevented++;
                skippedDescriptorBinds++;
                return false;
            }
            this.currentDescriptorSets[setIndex] = descriptorSetId;
            if (setIndex == 0) {
                this.currentDescriptorSetId = descriptorSetId;
            }
            return true;
        }

        if (this.currentDescriptorSetId == descriptorSetId) {
            redundantBindsPrevented++;
            skippedDescriptorBinds++;
            return false;
        }
        this.currentDescriptorSetId = descriptorSetId;
        return true;
    }

    /**
     * Checks dynamic viewport state using collision-free 64-bit coordinate packing.
     */
    public boolean checkViewport(int x, int y, int width, int height) {
        totalBindAttempts++;
        long pos = ((long) x << 32) | (y & 0xFFFFFFFFL);
        long size = ((long) width << 32) | (height & 0xFFFFFFFFL);
        if (this.currentViewportPos == pos && this.currentViewportSize == size) {
            redundantBindsPrevented++;
            skippedDynamicStateUpdates++;
            return false;
        }
        this.currentViewportPos = pos;
        this.currentViewportSize = size;
        return true;
    }

    /**
     * Checks dynamic scissor state using collision-free 64-bit coordinate packing.
     */
    public boolean checkScissor(int x, int y, int width, int height) {
        totalBindAttempts++;
        long pos = ((long) x << 32) | (y & 0xFFFFFFFFL);
        long size = ((long) width << 32) | (height & 0xFFFFFFFFL);
        if (this.currentScissorPos == pos && this.currentScissorSize == size) {
            redundantBindsPrevented++;
            skippedDynamicStateUpdates++;
            return false;
        }
        this.currentScissorPos = pos;
        this.currentScissorSize = size;
        return true;
    }

    /**
     * Resets dynamic scissor state cache to ensure subsequent setScissor calls are not falsely skipped.
     */
    public void resetScissor() {
        this.currentScissorPos = Long.MIN_VALUE;
        this.currentScissorSize = Long.MIN_VALUE;
    }

    /**
     * Checks dynamic depth bias state using packed 32-bit float factors.
     */
    public boolean checkDepthBias(float constantFactor, float clamp, float slopeFactor) {
        totalBindAttempts++;
        long packed = ((long) Float.floatToRawIntBits(constantFactor) << 32) | (Float.floatToRawIntBits(slopeFactor) & 0xFFFFFFFFL);
        if (this.currentDepthBias == packed) {
            redundantBindsPrevented++;
            skippedDynamicStateUpdates++;
            return false;
        }
        this.currentDepthBias = packed;
        return true;
    }

    /**
     * Checks dynamic blend constants state.
     */
    public boolean checkBlendConstants(float r, float g, float b, float a) {
        totalBindAttempts++;
        long p0 = ((long) Float.floatToRawIntBits(r) << 32) | (Float.floatToRawIntBits(g) & 0xFFFFFFFFL);
        long p1 = ((long) Float.floatToRawIntBits(b) << 32) | (Float.floatToRawIntBits(a) & 0xFFFFFFFFL);
        if (this.currentBlendConstants0 == p0 && this.currentBlendConstants1 == p1) {
            redundantBindsPrevented++;
            skippedDynamicStateUpdates++;
            return false;
        }
        this.currentBlendConstants0 = p0;
        this.currentBlendConstants1 = p1;
        return true;
    }

    /**
     * Checks dynamic line width state.
     */
    public boolean checkLineWidth(float lineWidth) {
        totalBindAttempts++;
        int bits = Float.floatToRawIntBits(lineWidth);
        if (this.currentLineWidthBits == bits) {
            redundantBindsPrevented++;
            skippedDynamicStateUpdates++;
            return false;
        }
        this.currentLineWidthBits = bits;
        return true;
    }

    /**
     * Checks dynamic stencil reference value.
     */
    public boolean checkStencilReference(int reference) {
        totalBindAttempts++;
        if (this.currentStencilRef == reference) {
            redundantBindsPrevented++;
            skippedDynamicStateUpdates++;
            return false;
        }
        this.currentStencilRef = reference;
        return true;
    }

    /**
     * Invalidates only the active pipeline and dynamic viewport/scissor/bias state (e.g. across RenderPass transitions).
     */
    public void invalidatePipelineAndDynamicState() {
        Arrays.fill(this.currentPipelines, -1L);
        this.currentPipelineId = -1;
        this.boundPackedState = -1L;
        this.currentViewportPos = Long.MIN_VALUE;
        this.currentViewportSize = Long.MIN_VALUE;
        this.currentScissorPos = Long.MIN_VALUE;
        this.currentScissorSize = Long.MIN_VALUE;
        this.currentDepthBias = Long.MIN_VALUE;
        this.currentBlendConstants0 = Long.MIN_VALUE;
        this.currentBlendConstants1 = Long.MIN_VALUE;
        this.currentLineWidthBits = Integer.MIN_VALUE;
        this.currentStencilRef = -1;
    }

    /**
     * Resets state cache at the boundary of a command buffer / frame.
     */
    public void reset() {
        Arrays.fill(this.currentPipelines, -1L);
        Arrays.fill(this.currentDescriptorSets, -1L);
        this.currentPipelineId = -1;
        this.currentDescriptorSetId = -1;
        this.boundPackedState = -1L;
        this.currentViewportPos = Long.MIN_VALUE;
        this.currentViewportSize = Long.MIN_VALUE;
        this.currentScissorPos = Long.MIN_VALUE;
        this.currentScissorSize = Long.MIN_VALUE;
        this.currentDepthBias = Long.MIN_VALUE;
        this.currentBlendConstants0 = Long.MIN_VALUE;
        this.currentBlendConstants1 = Long.MIN_VALUE;
        this.currentLineWidthBits = Integer.MIN_VALUE;
        this.currentStencilRef = -1;
    }

    public long getRedundantBindsPrevented() {
        return redundantBindsPrevented;
    }

    public long getSkippedPipelineBinds() {
        return skippedPipelineBinds;
    }

    public long getSkippedDescriptorBinds() {
        return skippedDescriptorBinds;
    }

    public long getSkippedDynamicStateUpdates() {
        return skippedDynamicStateUpdates;
    }

    public long getTotalBindAttempts() {
        return totalBindAttempts;
    }

    public float getFilteringRatio() {
        return totalBindAttempts > 0 ? (float) redundantBindsPrevented / totalBindAttempts : 0.0f;
    }
}
