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
    public boolean enableFastFoliage = true;
    public int foliageDensity = 100;

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

    // Exordium (GUI & HUD Framerate Decoupling)
    public boolean enableExordium = false;
    public int hudTargetFps = 60;
    public boolean enableScreenPacing = true;
    public int screenTargetFps = 60;
    public boolean instantInputResponsiveness = true;
    public boolean dynamicHudUpdates = true;
    public boolean separateCrosshair = true;
    public boolean bypassInDebugScreen = true;
    public boolean fastFadeTransitions = true;

    // ASS PC (Extreme Potato Mode) - All false by default
    public boolean noMobAnimations = false;
    public boolean noDroppedItemAnimation = false;
    public boolean staticExpAnimations = false;
    public boolean noParticles = false;
    public boolean noTextureAnimations = false;
    public boolean noEntityShadows = false;
    public boolean noItemGlint = false;
    public boolean noSky = false;
    public boolean noFog = false;
    public boolean noBlockEntityAnimations = false;
    public boolean noChunkFade = false;
    public boolean fastChest = false;
    public boolean shitFoliage = false;

    // Engine optimizations (C2ME & MemoryLeakFix) - Enabled by default
    public boolean enableMemoryLeakFix = true;
    public boolean enableC2MeOptimizations = true;

    public boolean isAssPcActive() {
        return noMobAnimations || noDroppedItemAnimation || staticExpAnimations
                || noParticles || noTextureAnimations || noEntityShadows
                || noItemGlint || noSky || noFog || noBlockEntityAnimations
                || noChunkFade || fastChest || shitFoliage;
    }

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
                this.enableFastFoliage = true;
                this.foliageDensity = 75;
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
                this.enableExordium = false;
                this.hudTargetFps = 30;
                this.enableScreenPacing = true;
                this.screenTargetFps = 30;
                this.instantInputResponsiveness = true;
                this.dynamicHudUpdates = true;
                this.separateCrosshair = true;
                this.bypassInDebugScreen = true;
                this.fastFadeTransitions = true;
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
                this.enableFastFoliage = true;
                this.foliageDensity = 100;
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
                this.enableExordium = false;
                this.hudTargetFps = 60;
                this.enableScreenPacing = true;
                this.screenTargetFps = 60;
                this.instantInputResponsiveness = true;
                this.dynamicHudUpdates = true;
                this.separateCrosshair = true;
                this.bypassInDebugScreen = true;
                this.fastFadeTransitions = true;
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
                this.enableFastFoliage = true;
                this.foliageDensity = 50;
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
                this.enableExordium = false;
                this.hudTargetFps = 30;
                this.enableScreenPacing = true;
                this.screenTargetFps = 30;
                this.instantInputResponsiveness = true;
                this.dynamicHudUpdates = true;
                this.separateCrosshair = true;
                this.bypassInDebugScreen = true;
                this.fastFadeTransitions = true;
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
                && this.enableFastFoliage == sample.enableFastFoliage
                && this.foliageDensity == sample.foliageDensity
                && this.enableThreadPriority == sample.enableThreadPriority
                && this.chestProtection == sample.chestProtection
                && this.enableExordium == sample.enableExordium
                && this.hudTargetFps == sample.hudTargetFps
                && this.enableScreenPacing == sample.enableScreenPacing
                && this.screenTargetFps == sample.screenTargetFps
                && this.separateCrosshair == sample.separateCrosshair;
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
        clone.enableFastFoliage = this.enableFastFoliage;
        clone.foliageDensity = this.foliageDensity;
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
        clone.noMobAnimations = this.noMobAnimations;
        clone.noDroppedItemAnimation = this.noDroppedItemAnimation;
        clone.staticExpAnimations = this.staticExpAnimations;
        clone.noParticles = this.noParticles;
        clone.noTextureAnimations = this.noTextureAnimations;
        clone.noEntityShadows = this.noEntityShadows;
        clone.noItemGlint = this.noItemGlint;
        clone.noSky = this.noSky;
        clone.noFog = this.noFog;
        clone.noBlockEntityAnimations = this.noBlockEntityAnimations;
        clone.noChunkFade = this.noChunkFade;
        clone.fastChest = this.fastChest;
        clone.shitFoliage = this.shitFoliage;
        clone.enableMemoryLeakFix = this.enableMemoryLeakFix;
        clone.enableC2MeOptimizations = this.enableC2MeOptimizations;
        clone.enableExordium = this.enableExordium;
        clone.hudTargetFps = this.hudTargetFps;
        clone.enableScreenPacing = this.enableScreenPacing;
        clone.screenTargetFps = this.screenTargetFps;
        clone.instantInputResponsiveness = this.instantInputResponsiveness;
        clone.dynamicHudUpdates = this.dynamicHudUpdates;
        clone.separateCrosshair = this.separateCrosshair;
        clone.bypassInDebugScreen = this.bypassInDebugScreen;
        clone.fastFadeTransitions = this.fastFadeTransitions;
        return clone;
    }
}
