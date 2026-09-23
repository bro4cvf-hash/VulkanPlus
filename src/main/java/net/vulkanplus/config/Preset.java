package net.vulkanplus.config;

/**
 * Performance presets for Vulkan Plus.
 */
public enum Preset {
    FAST("Fast", "Maximum performance for lower-end hardware and integrated GPUs"),
    BALANCED("Balanced", "Optimal balance between visual quality and high frame rates"),
    EXTREME("Extreme", "Aggressive optimizations, maximum culling, and lowest latency");

    private final String displayName;
    private final String description;

    Preset(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
