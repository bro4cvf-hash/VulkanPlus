package net.vulkanplus.vulkan;

/**
 * High-speed Vulkan command recording filter and state cache.
 * Eliminates redundant vkCmdBindPipeline, vkCmdBindDescriptorSets, and dynamic state updates,
 * cutting CPU driver dispatch overhead.
 */
public class VulkanStateCache {
    private long currentPipelineId = -1;
    private long currentDescriptorSetId = -1;
    private int currentViewportHash = 0;
    private int currentScissorHash = 0;

    private long redundantBindsPrevented = 0;
    private long totalBindAttempts = 0;

    /**
     * Checks if the pipeline needs binding or is already active.
     *
     * @return true if pipeline binding is required; false if redundant.
     */
    public boolean checkAndBindPipeline(long pipelineId) {
        totalBindAttempts++;
        if (this.currentPipelineId == pipelineId) {
            redundantBindsPrevented++;
            return false;
        }
        this.currentPipelineId = pipelineId;
        return true;
    }

    /**
     * Checks if descriptor set needs binding or is already active.
     */
    public boolean checkAndBindDescriptorSet(long descriptorSetId) {
        totalBindAttempts++;
        if (this.currentDescriptorSetId == descriptorSetId) {
            redundantBindsPrevented++;
            return false;
        }
        this.currentDescriptorSetId = descriptorSetId;
        return true;
    }

    /**
     * Checks dynamic viewport state.
     */
    public boolean checkViewport(int x, int y, int width, int height) {
        int hash = 31 * (31 * (31 * x + y) + width) + height;
        if (this.currentViewportHash == hash) {
            redundantBindsPrevented++;
            return false;
        }
        this.currentViewportHash = hash;
        return true;
    }

    /**
     * Checks dynamic scissor state.
     */
    public boolean checkScissor(int x, int y, int width, int height) {
        int hash = 31 * (31 * (31 * x + y) + width) + height;
        if (this.currentScissorHash == hash) {
            redundantBindsPrevented++;
            return false;
        }
        this.currentScissorHash = hash;
        return true;
    }

    /**
     * Resets state cache at the boundary of a command buffer / frame.
     */
    public void reset() {
        this.currentPipelineId = -1;
        this.currentDescriptorSetId = -1;
        this.currentViewportHash = 0;
        this.currentScissorHash = 0;
    }

    public long getRedundantBindsPrevented() {
        return redundantBindsPrevented;
    }

    public long getTotalBindAttempts() {
        return totalBindAttempts;
    }

    public float getFilteringRatio() {
        return totalBindAttempts > 0 ? (float) redundantBindsPrevented / totalBindAttempts : 0.0f;
    }
}
