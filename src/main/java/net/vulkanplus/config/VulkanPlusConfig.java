package net.vulkanplus.config;

/**
 * Configuration data holder for Vulkan Plus.
 */
public class VulkanPlusConfig {
    public boolean enabled = true;

    public boolean enableBufferPooling = true;
    public boolean enableDescriptorCaching = true;
    public boolean enablePsoCache = true;
    public boolean enableReverseZ = true;
    public boolean enableSwapchainTuning = true;
    public String presentMode = "MAILBOX";

    public boolean enableFastMath = true;
    public boolean enableFastRandom = true;

    public boolean enableMoreCulling = true;
    public boolean enableEntityCulling = true;
    public boolean enableBlockEntityCulling = true;
    public boolean enableBlockEntityOcclusion = true;
    public boolean enableSmartLeaves = true;
    public boolean enableBeaconBeamCulling = true;
    public boolean enableExtraGlassCulling = true;
    public boolean enableParticleCulling = true;
    public boolean enableMatrixPooling = true;
    public boolean opaqueLeaves = false;

    public boolean enableThreadPriority = true;
    public int renderThreadPriority = 8;
    public int workerThreadPriority = 1;
    public int ioThreadPriority = 3;

    public boolean enableFastItemFrames = true;
    public boolean enableItemFrameBlockOcclusion = true;
    public double itemFrameMaxDistance = 64.0;
    public double itemFrameItemDistance = 24.0;

    public double cullingDistanceFactor = 1.0;
    public double particleCullingDistance = 32.0;
    public boolean beaconProtection = true;
    public boolean chestProtection = true;

    public boolean showDiagnosticsHud = false;
    public boolean showFps = false;
    public Preset activePreset = Preset.BALANCED;

    public VulkanPlusConfig() {
    }

    /**
     * Applies a predefined performance preset.
     */
    public void applyPreset(Preset preset) {
        this.activePreset = preset;
        switch (preset) {
            case FAST -> {
                this.enableBufferPooling = true;
                this.enableDescriptorCaching = true;
                this.enablePsoCache = true;
                this.enableReverseZ = true;
                this.enableSwapchainTuning = true;
                this.presentMode = "IMMEDIATE";
                this.enableFastMath = true;
                this.enableFastRandom = true;
                this.enableMoreCulling = true;
                this.enableEntityCulling = true;
                this.enableBlockEntityCulling = true;
                this.enableBlockEntityOcclusion = true;
                this.enableSmartLeaves = true;
                this.enableBeaconBeamCulling = true;
                this.enableExtraGlassCulling = true;
                this.enableParticleCulling = true;
                this.enableMatrixPooling = true;
                this.opaqueLeaves = true;
                this.enableThreadPriority = true;
                this.renderThreadPriority = 9;
                this.workerThreadPriority = 1;
                this.ioThreadPriority = 2;
                this.enableFastItemFrames = true;
                this.enableItemFrameBlockOcclusion = true;
                this.itemFrameMaxDistance = 48.0;
                this.itemFrameItemDistance = 16.0;
                this.cullingDistanceFactor = 0.8;
                this.particleCullingDistance = 24.0;
                this.beaconProtection = true;
                this.chestProtection = true;
            }
            case BALANCED -> {
                this.enableBufferPooling = true;
                this.enableDescriptorCaching = true;
                this.enablePsoCache = true;
                this.enableReverseZ = true;
                this.enableSwapchainTuning = true;
                this.presentMode = "MAILBOX";
                this.enableFastMath = true;
                this.enableFastRandom = true;
                this.enableMoreCulling = true;
                this.enableEntityCulling = true;
                this.enableBlockEntityCulling = true;
                this.enableBlockEntityOcclusion = true;
                this.enableSmartLeaves = true;
                this.enableBeaconBeamCulling = true;
                this.enableExtraGlassCulling = true;
                this.enableParticleCulling = true;
                this.enableMatrixPooling = true;
                this.opaqueLeaves = false;
                this.enableThreadPriority = true;
                this.renderThreadPriority = 8;
                this.workerThreadPriority = 2;
                this.ioThreadPriority = 3;
                this.enableFastItemFrames = true;
                this.enableItemFrameBlockOcclusion = true;
                this.itemFrameMaxDistance = 64.0;
                this.itemFrameItemDistance = 24.0;
                this.cullingDistanceFactor = 1.0;
                this.particleCullingDistance = 32.0;
                this.beaconProtection = true;
                this.chestProtection = true;
            }
            case EXTREME -> {
                this.enableBufferPooling = true;
                this.enableDescriptorCaching = true;
                this.enablePsoCache = true;
                this.enableReverseZ = true;
                this.enableSwapchainTuning = true;
                this.presentMode = "MAILBOX";
                this.enableFastMath = true;
                this.enableFastRandom = true;
                this.enableMoreCulling = true;
                this.enableEntityCulling = true;
                this.enableBlockEntityCulling = true;
                this.enableBlockEntityOcclusion = true;
                this.enableSmartLeaves = true;
                this.enableBeaconBeamCulling = true;
                this.enableExtraGlassCulling = true;
                this.enableParticleCulling = true;
                this.enableMatrixPooling = true;
                this.opaqueLeaves = true;
                this.enableThreadPriority = true;
                this.renderThreadPriority = 9;
                this.workerThreadPriority = 1;
                this.ioThreadPriority = 2;
                this.enableFastItemFrames = true;
                this.enableItemFrameBlockOcclusion = true;
                this.itemFrameMaxDistance = 96.0;
                this.itemFrameItemDistance = 32.0;
                this.cullingDistanceFactor = 0.6;
                this.particleCullingDistance = 16.0;
                this.beaconProtection = true;
                this.chestProtection = false;
            }
        }
    }

    /**
     * Checks if the current configuration values match a preset's parameters.
     */
    public boolean matchesPreset(Preset preset) {
        if (preset == null) return false;
        VulkanPlusConfig sample = new VulkanPlusConfig();
        sample.applyPreset(preset);
        return this.enableBufferPooling == sample.enableBufferPooling
                && this.enableDescriptorCaching == sample.enableDescriptorCaching
                && this.enablePsoCache == sample.enablePsoCache
                && this.enableReverseZ == sample.enableReverseZ
                && this.enableSwapchainTuning == sample.enableSwapchainTuning
                && (this.presentMode == null ? sample.presentMode == null : this.presentMode.equals(sample.presentMode))
                && this.enableFastMath == sample.enableFastMath
                && this.enableFastRandom == sample.enableFastRandom
                && this.enableMoreCulling == sample.enableMoreCulling
                && this.enableEntityCulling == sample.enableEntityCulling
                && this.enableBlockEntityCulling == sample.enableBlockEntityCulling
                && this.enableBlockEntityOcclusion == sample.enableBlockEntityOcclusion
                && this.enableSmartLeaves == sample.enableSmartLeaves
                && this.enableBeaconBeamCulling == sample.enableBeaconBeamCulling
                && this.enableExtraGlassCulling == sample.enableExtraGlassCulling
                && this.enableParticleCulling == sample.enableParticleCulling
                && this.enableMatrixPooling == sample.enableMatrixPooling
                && this.opaqueLeaves == sample.opaqueLeaves
                && this.enableThreadPriority == sample.enableThreadPriority
                && this.chestProtection == sample.chestProtection;
    }

    /**
     * Returns the matching preset, or null if the configuration has custom modifications.
     */
    public Preset getEffectivePreset() {
        if (matchesPreset(Preset.FAST)) return Preset.FAST;
        if (matchesPreset(Preset.BALANCED)) return Preset.BALANCED;
        if (matchesPreset(Preset.EXTREME)) return Preset.EXTREME;
        return null;
    }

    /**
     * Creates a deep copy of this configuration.
     */
    public VulkanPlusConfig copy() {
        VulkanPlusConfig clone = new VulkanPlusConfig();
        clone.enabled = this.enabled;
        clone.enableBufferPooling = this.enableBufferPooling;
        clone.enableDescriptorCaching = this.enableDescriptorCaching;
        clone.enablePsoCache = this.enablePsoCache;
        clone.enableReverseZ = this.enableReverseZ;
        clone.enableSwapchainTuning = this.enableSwapchainTuning;
        clone.presentMode = this.presentMode;
        clone.enableFastMath = this.enableFastMath;
        clone.enableFastRandom = this.enableFastRandom;
        clone.enableMoreCulling = this.enableMoreCulling;
        clone.enableEntityCulling = this.enableEntityCulling;
        clone.enableBlockEntityCulling = this.enableBlockEntityCulling;
        clone.enableBlockEntityOcclusion = this.enableBlockEntityOcclusion;
        clone.enableSmartLeaves = this.enableSmartLeaves;
        clone.enableBeaconBeamCulling = this.enableBeaconBeamCulling;
        clone.enableExtraGlassCulling = this.enableExtraGlassCulling;
        clone.enableParticleCulling = this.enableParticleCulling;
        clone.enableMatrixPooling = this.enableMatrixPooling;
        clone.opaqueLeaves = this.opaqueLeaves;
        clone.enableThreadPriority = this.enableThreadPriority;
        clone.renderThreadPriority = this.renderThreadPriority;
        clone.workerThreadPriority = this.workerThreadPriority;
        clone.ioThreadPriority = this.ioThreadPriority;
        clone.enableFastItemFrames = this.enableFastItemFrames;
        clone.enableItemFrameBlockOcclusion = this.enableItemFrameBlockOcclusion;
        clone.itemFrameMaxDistance = this.itemFrameMaxDistance;
        clone.itemFrameItemDistance = this.itemFrameItemDistance;
        clone.cullingDistanceFactor = this.cullingDistanceFactor;
        clone.particleCullingDistance = this.particleCullingDistance;
        clone.beaconProtection = this.beaconProtection;
        clone.chestProtection = this.chestProtection;
        clone.showDiagnosticsHud = this.showDiagnosticsHud;
        clone.showFps = this.showFps;
        clone.activePreset = this.activePreset;
        return clone;
    }
}
