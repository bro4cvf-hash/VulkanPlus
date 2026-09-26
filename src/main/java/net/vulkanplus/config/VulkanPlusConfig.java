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
    public boolean fullBright = false;

    // Engine optimizations (C2ME & MemoryLeakFix) - Enabled by default
    public boolean enableMemoryLeakFix = true;
    public boolean enableC2MeOptimizations = true;

    public boolean enableAnimationLod = true;
    public double animationLodDistance = 32.0;
    public boolean enableEntityShadowCulling = true;
    public double entityShadowMaxDistance = 24.0;
    public int vramBudgetMb = 2048;

    public boolean isAssPcActive() {
        return noMobAnimations || noDroppedItemAnimation || staticExpAnimations
                || noParticles || noTextureAnimations || noEntityShadows
                || noItemGlint || noSky || noFog || noBlockEntityAnimations
                || noChunkFade || fastChest || shitFoliage || fullBright;
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
                this.foliageDensity = 100;
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
                this.hudTargetFps = 30;
                this.enableScreenPacing = true;
                this.screenTargetFps = 30;
                this.instantInputResponsiveness = true;
                this.dynamicHudUpdates = true;
                this.separateCrosshair = true;
                this.bypassInDebugScreen = true;
                this.fastFadeTransitions = true;
                this.enableAnimationLod = true;
                this.animationLodDistance = 24.0;
                this.enableEntityShadowCulling = true;
                this.entityShadowMaxDistance = 16.0;
                this.vramBudgetMb = 2048;
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
                this.hudTargetFps = 60;
                this.enableScreenPacing = true;
                this.screenTargetFps = 60;
                this.instantInputResponsiveness = true;
                this.dynamicHudUpdates = true;
                this.separateCrosshair = true;
                this.bypassInDebugScreen = true;
                this.fastFadeTransitions = true;
                this.enableAnimationLod = true;
                this.animationLodDistance = 32.0;
                this.enableEntityShadowCulling = true;
                this.entityShadowMaxDistance = 24.0;
                this.vramBudgetMb = 2048;
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
                this.foliageDensity = 100;
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
                this.hudTargetFps = 30;
                this.enableScreenPacing = true;
                this.screenTargetFps = 30;
                this.instantInputResponsiveness = true;
                this.dynamicHudUpdates = true;
                this.separateCrosshair = true;
                this.bypassInDebugScreen = true;
                this.fastFadeTransitions = true;
                this.enableAnimationLod = true;
                this.animationLodDistance = 16.0;
                this.enableEntityShadowCulling = true;
                this.entityShadowMaxDistance = 12.0;
                this.vramBudgetMb = 2048;
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
                && this.hudTargetFps == sample.hudTargetFps
                && this.enableScreenPacing == sample.enableScreenPacing
                && this.screenTargetFps == sample.screenTargetFps
                && this.separateCrosshair == sample.separateCrosshair
                && this.enableAnimationLod == sample.enableAnimationLod
                && Double.compare(this.animationLodDistance, sample.animationLodDistance) == 0
                && this.enableEntityShadowCulling == sample.enableEntityShadowCulling
                && Double.compare(this.entityShadowMaxDistance, sample.entityShadowMaxDistance) == 0
                && this.vramBudgetMb == sample.vramBudgetMb;
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
     * Copies all configuration values from another instance in-place.
     */
    public void copyFrom(VulkanPlusConfig other) {
        if (other == null || other == this) return;
        this.enabled = other.enabled;
        this.enableBufferPooling = other.enableBufferPooling;
        this.enableDescriptorCaching = other.enableDescriptorCaching;
        this.enablePsoCache = other.enablePsoCache;
        this.enableReverseZ = other.enableReverseZ;
        this.enableSwapchainTuning = other.enableSwapchainTuning;
        this.presentMode = other.presentMode;
        this.enableFastMath = other.enableFastMath;
        this.enableFastRandom = other.enableFastRandom;
        this.enableMoreCulling = other.enableMoreCulling;
        this.enableEntityCulling = other.enableEntityCulling;
        this.enableBlockEntityCulling = other.enableBlockEntityCulling;
        this.enableBlockEntityOcclusion = other.enableBlockEntityOcclusion;
        this.enableSmartLeaves = other.enableSmartLeaves;
        this.enableBeaconBeamCulling = other.enableBeaconBeamCulling;
        this.enableExtraGlassCulling = other.enableExtraGlassCulling;
        this.enableParticleCulling = other.enableParticleCulling;
        this.enableMatrixPooling = other.enableMatrixPooling;
        this.opaqueLeaves = other.opaqueLeaves;
        this.enableFastFoliage = other.enableFastFoliage;
        this.foliageDensity = other.foliageDensity;
        this.enableThreadPriority = other.enableThreadPriority;
        this.renderThreadPriority = other.renderThreadPriority;
        this.workerThreadPriority = other.workerThreadPriority;
        this.ioThreadPriority = other.ioThreadPriority;
        this.enableFastItemFrames = other.enableFastItemFrames;
        this.enableItemFrameBlockOcclusion = other.enableItemFrameBlockOcclusion;
        this.itemFrameMaxDistance = other.itemFrameMaxDistance;
        this.itemFrameItemDistance = other.itemFrameItemDistance;
        this.cullingDistanceFactor = other.cullingDistanceFactor;
        this.particleCullingDistance = other.particleCullingDistance;
        this.beaconProtection = other.beaconProtection;
        this.chestProtection = other.chestProtection;
        this.showDiagnosticsHud = other.showDiagnosticsHud;
        this.showFps = other.showFps;
        this.activePreset = other.activePreset;
        this.noMobAnimations = other.noMobAnimations;
        this.noDroppedItemAnimation = other.noDroppedItemAnimation;
        this.staticExpAnimations = other.staticExpAnimations;
        this.noParticles = other.noParticles;
        this.noTextureAnimations = other.noTextureAnimations;
        this.noEntityShadows = other.noEntityShadows;
        this.noItemGlint = other.noItemGlint;
        this.noSky = other.noSky;
        this.noFog = other.noFog;
        this.noBlockEntityAnimations = other.noBlockEntityAnimations;
        this.noChunkFade = other.noChunkFade;
        this.fastChest = other.fastChest;
        this.shitFoliage = other.shitFoliage;
        this.fullBright = other.fullBright;
        this.enableMemoryLeakFix = other.enableMemoryLeakFix;
        this.enableC2MeOptimizations = other.enableC2MeOptimizations;
        this.enableExordium = other.enableExordium;
        this.hudTargetFps = other.hudTargetFps;
        this.enableScreenPacing = other.enableScreenPacing;
        this.screenTargetFps = other.screenTargetFps;
        this.instantInputResponsiveness = other.instantInputResponsiveness;
        this.dynamicHudUpdates = other.dynamicHudUpdates;
        this.separateCrosshair = other.separateCrosshair;
        this.bypassInDebugScreen = other.bypassInDebugScreen;
        this.fastFadeTransitions = other.fastFadeTransitions;
        this.enableAnimationLod = other.enableAnimationLod;
        this.animationLodDistance = other.animationLodDistance;
        this.enableEntityShadowCulling = other.enableEntityShadowCulling;
        this.entityShadowMaxDistance = other.entityShadowMaxDistance;
        this.vramBudgetMb = other.vramBudgetMb;
    }

    /**
     * Creates a deep copy of this configuration.
     */
    public VulkanPlusConfig copy() {
        VulkanPlusConfig clone = new VulkanPlusConfig();
        clone.copyFrom(this);
        return clone;
    }
}
