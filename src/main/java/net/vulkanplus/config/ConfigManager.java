package net.vulkanplus.config;

import net.fabricmc.loader.api.FabricLoader;
import net.vulkanplus.VulkanPlusMod;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Thread-safe configuration manager with atomic file persistence.
 */
public class ConfigManager {
    private static final Object LOCK = new Object();
    private static volatile boolean initialized = false;
    private static volatile VulkanPlusConfig currentConfig = new VulkanPlusConfig();

    static {
        load();
    }

    public static Path getConfigPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("vulkanplus.json");
        } catch (Throwable t) {
            return Paths.get("config", "vulkanplus.json");
        }
    }

    public static Path getTempPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("vulkanplus.json.tmp");
        } catch (Throwable t) {
            return Paths.get("config", "vulkanplus.json.tmp");
        }
    }

    public static VulkanPlusConfig getConfig() {
        if (!initialized) {
            load();
        }
        return currentConfig;
    }

    public static void setConfig(VulkanPlusConfig newConfig) {
        synchronized (LOCK) {
            if (newConfig != null) {
                currentConfig.copyFrom(newConfig);
            }
            save();
        }
    }

    /**
     * Loads configuration from disk, creating defaults if not present.
     */
    public static void load() {
        synchronized (LOCK) {
            Path configPath = getConfigPath();
            if (!Files.exists(configPath)) {
                VulkanPlusMod.LOGGER.info("[VulkanPlus] Config file not found. Generating defaults at {}", configPath);
                initialized = true;
                save();
                return;
            }

            try (BufferedReader reader = Files.newBufferedReader(configPath)) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                parseJson(sb.toString(), currentConfig);
                initialized = true;
                VulkanPlusMod.LOGGER.info("[VulkanPlus] Successfully loaded configuration from disk.");
            } catch (Exception e) {
                VulkanPlusMod.LOGGER.error("[VulkanPlus] Failed to load config from {}. Falling back to default settings.", configPath, e);
                currentConfig = new VulkanPlusConfig();
                initialized = true;
            }
        }
    }

    /**
     * Atomically saves active configuration to disk.
     */
    public static void save() {
        synchronized (LOCK) {
            try {
                Path configPath = getConfigPath();
                Path tempPath = getTempPath();
                if (configPath.getParent() != null && !Files.exists(configPath.getParent())) {
                    Files.createDirectories(configPath.getParent());
                }

                String json = toJson(currentConfig);
                try (BufferedWriter writer = Files.newBufferedWriter(tempPath)) {
                    writer.write(json);
                    writer.flush();
                }

                try {
                    Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                VulkanPlusMod.LOGGER.error("[VulkanPlus] Failed to save configuration atomically.", e);
            }
        }
    }

    /**
     * Lightweight custom JSON serializer to avoid third-party dependencies.
     */
    public static String toJson(VulkanPlusConfig config) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\n");
        sb.append("  \"enabled\": ").append(config.enabled).append(",\n");
        sb.append("  \"enableBufferPooling\": ").append(config.enableBufferPooling).append(",\n");
        sb.append("  \"enableDescriptorCaching\": ").append(config.enableDescriptorCaching).append(",\n");
        sb.append("  \"enablePsoCache\": ").append(config.enablePsoCache).append(",\n");
        sb.append("  \"enableReverseZ\": ").append(config.enableReverseZ).append(",\n");
        sb.append("  \"enableSwapchainTuning\": ").append(config.enableSwapchainTuning).append(",\n");
        sb.append("  \"presentMode\": \"").append(config.presentMode).append("\",\n");
        sb.append("  \"enableFastMath\": ").append(config.enableFastMath).append(",\n");
        sb.append("  \"enableFastRandom\": ").append(config.enableFastRandom).append(",\n");
        sb.append("  \"enableMoreCulling\": ").append(config.enableMoreCulling).append(",\n");
        sb.append("  \"enableEntityCulling\": ").append(config.enableEntityCulling).append(",\n");
        sb.append("  \"enableBlockEntityCulling\": ").append(config.enableBlockEntityCulling).append(",\n");
        sb.append("  \"enableBlockEntityOcclusion\": ").append(config.enableBlockEntityOcclusion).append(",\n");
        sb.append("  \"enableSmartLeaves\": ").append(config.enableSmartLeaves).append(",\n");
        sb.append("  \"enableBeaconBeamCulling\": ").append(config.enableBeaconBeamCulling).append(",\n");
        sb.append("  \"enableExtraGlassCulling\": ").append(config.enableExtraGlassCulling).append(",\n");
        sb.append("  \"enableParticleCulling\": ").append(config.enableParticleCulling).append(",\n");
        sb.append("  \"enableMatrixPooling\": ").append(config.enableMatrixPooling).append(",\n");
        sb.append("  \"opaqueLeaves\": ").append(config.opaqueLeaves).append(",\n");
        sb.append("  \"enableFastFoliage\": ").append(config.enableFastFoliage).append(",\n");
        sb.append("  \"foliageDensity\": ").append(config.foliageDensity).append(",\n");
        sb.append("  \"enableThreadPriority\": ").append(config.enableThreadPriority).append(",\n");
        sb.append("  \"renderThreadPriority\": ").append(config.renderThreadPriority).append(",\n");
        sb.append("  \"workerThreadPriority\": ").append(config.workerThreadPriority).append(",\n");
        sb.append("  \"ioThreadPriority\": ").append(config.ioThreadPriority).append(",\n");
        sb.append("  \"enableFastItemFrames\": ").append(config.enableFastItemFrames).append(",\n");
        sb.append("  \"enableItemFrameBlockOcclusion\": ").append(config.enableItemFrameBlockOcclusion).append(",\n");
        sb.append("  \"itemFrameMaxDistance\": ").append(config.itemFrameMaxDistance).append(",\n");
        sb.append("  \"itemFrameItemDistance\": ").append(config.itemFrameItemDistance).append(",\n");
        sb.append("  \"cullingDistanceFactor\": ").append(config.cullingDistanceFactor).append(",\n");
        sb.append("  \"particleCullingDistance\": ").append(config.particleCullingDistance).append(",\n");
        sb.append("  \"beaconProtection\": ").append(config.beaconProtection).append(",\n");
        sb.append("  \"chestProtection\": ").append(config.chestProtection).append(",\n");
        sb.append("  \"showDiagnosticsHud\": ").append(config.showDiagnosticsHud).append(",\n");
        sb.append("  \"showFps\": ").append(config.showFps).append(",\n");
        sb.append("  \"noMobAnimations\": ").append(config.noMobAnimations).append(",\n");
        sb.append("  \"noDroppedItemAnimation\": ").append(config.noDroppedItemAnimation).append(",\n");
        sb.append("  \"staticExpAnimations\": ").append(config.staticExpAnimations).append(",\n");
        sb.append("  \"noParticles\": ").append(config.noParticles).append(",\n");
        sb.append("  \"noTextureAnimations\": ").append(config.noTextureAnimations).append(",\n");
        sb.append("  \"noEntityShadows\": ").append(config.noEntityShadows).append(",\n");
        sb.append("  \"noItemGlint\": ").append(config.noItemGlint).append(",\n");
        sb.append("  \"noSky\": ").append(config.noSky).append(",\n");
        sb.append("  \"noFog\": ").append(config.noFog).append(",\n");
        sb.append("  \"noBlockEntityAnimations\": ").append(config.noBlockEntityAnimations).append(",\n");
        sb.append("  \"noChunkFade\": ").append(config.noChunkFade).append(",\n");
        sb.append("  \"fastChest\": ").append(config.fastChest).append(",\n");
        sb.append("  \"shitFoliage\": ").append(config.shitFoliage).append(",\n");
        sb.append("  \"fullBright\": ").append(config.fullBright).append(",\n");
        sb.append("  \"enableMemoryLeakFix\": ").append(config.enableMemoryLeakFix).append(",\n");
        sb.append("  \"enableC2MeOptimizations\": ").append(config.enableC2MeOptimizations).append(",\n");
        sb.append("  \"enableExordium\": ").append(config.enableExordium).append(",\n");
        sb.append("  \"hudTargetFps\": ").append(config.hudTargetFps).append(",\n");
        sb.append("  \"enableScreenPacing\": ").append(config.enableScreenPacing).append(",\n");
        sb.append("  \"screenTargetFps\": ").append(config.screenTargetFps).append(",\n");
        sb.append("  \"instantInputResponsiveness\": ").append(config.instantInputResponsiveness).append(",\n");
        sb.append("  \"dynamicHudUpdates\": ").append(config.dynamicHudUpdates).append(",\n");
        sb.append("  \"separateCrosshair\": ").append(config.separateCrosshair).append(",\n");
        sb.append("  \"bypassInDebugScreen\": ").append(config.bypassInDebugScreen).append(",\n");
        sb.append("  \"fastFadeTransitions\": ").append(config.fastFadeTransitions).append(",\n");
        sb.append("  \"enableAnimationLod\": ").append(config.enableAnimationLod).append(",\n");
        sb.append("  \"animationLodDistance\": ").append(config.animationLodDistance).append(",\n");
        sb.append("  \"enableEntityShadowCulling\": ").append(config.enableEntityShadowCulling).append(",\n");
        sb.append("  \"entityShadowMaxDistance\": ").append(config.entityShadowMaxDistance).append(",\n");
        sb.append("  \"vramBudgetMb\": ").append(config.vramBudgetMb).append(",\n");
        sb.append("  \"activePreset\": \"").append(config.activePreset.name()).append("\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Simple robust key-value parser for configuration JSON.
     */
    public static void parseJson(String json, VulkanPlusConfig target) {
        Map<String, String> map = new HashMap<>();
        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
            String[] pairs = trimmed.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String val = kv[1].trim().replace("\"", "");
                    map.put(key, val);
                }
            }
        }

        if (map.containsKey("enabled")) target.enabled = Boolean.parseBoolean(map.get("enabled"));
        if (map.containsKey("enableBufferPooling")) target.enableBufferPooling = Boolean.parseBoolean(map.get("enableBufferPooling"));
        if (map.containsKey("enableDescriptorCaching")) target.enableDescriptorCaching = Boolean.parseBoolean(map.get("enableDescriptorCaching"));
        if (map.containsKey("enablePsoCache")) target.enablePsoCache = Boolean.parseBoolean(map.get("enablePsoCache"));
        if (map.containsKey("enableReverseZ")) target.enableReverseZ = Boolean.parseBoolean(map.get("enableReverseZ"));
        if (map.containsKey("enableSwapchainTuning")) target.enableSwapchainTuning = Boolean.parseBoolean(map.get("enableSwapchainTuning"));
        if (map.containsKey("presentMode")) target.presentMode = map.get("presentMode");
        if (map.containsKey("enableFastMath")) target.enableFastMath = Boolean.parseBoolean(map.get("enableFastMath"));
        if (map.containsKey("enableFastRandom")) target.enableFastRandom = Boolean.parseBoolean(map.get("enableFastRandom"));

        if (map.containsKey("enableMoreCulling")) {
            target.enableMoreCulling = Boolean.parseBoolean(map.get("enableMoreCulling"));
            target.enableEntityCulling = target.enableMoreCulling;
        } else if (map.containsKey("enableEntityCulling")) {
            target.enableMoreCulling = Boolean.parseBoolean(map.get("enableEntityCulling"));
            target.enableEntityCulling = target.enableMoreCulling;
        }

        if (map.containsKey("enableBlockEntityCulling")) target.enableBlockEntityCulling = Boolean.parseBoolean(map.get("enableBlockEntityCulling"));
        if (map.containsKey("enableBlockEntityOcclusion")) target.enableBlockEntityOcclusion = Boolean.parseBoolean(map.get("enableBlockEntityOcclusion"));
        if (map.containsKey("enableSmartLeaves")) target.enableSmartLeaves = Boolean.parseBoolean(map.get("enableSmartLeaves"));
        if (map.containsKey("enableBeaconBeamCulling")) target.enableBeaconBeamCulling = Boolean.parseBoolean(map.get("enableBeaconBeamCulling"));
        if (map.containsKey("enableExtraGlassCulling")) target.enableExtraGlassCulling = Boolean.parseBoolean(map.get("enableExtraGlassCulling"));
        if (map.containsKey("enableParticleCulling")) target.enableParticleCulling = Boolean.parseBoolean(map.get("enableParticleCulling"));
        if (map.containsKey("enableMatrixPooling")) target.enableMatrixPooling = Boolean.parseBoolean(map.get("enableMatrixPooling"));
        if (map.containsKey("opaqueLeaves")) target.opaqueLeaves = Boolean.parseBoolean(map.get("opaqueLeaves"));
        if (map.containsKey("enableFastFoliage")) target.enableFastFoliage = Boolean.parseBoolean(map.get("enableFastFoliage"));
        if (map.containsKey("foliageDensity")) {
            try { target.foliageDensity = Integer.parseInt(map.get("foliageDensity")); } catch (NumberFormatException ignored) {}
        }

        if (map.containsKey("enableThreadPriority")) target.enableThreadPriority = Boolean.parseBoolean(map.get("enableThreadPriority"));
        if (map.containsKey("renderThreadPriority")) {
            try { target.renderThreadPriority = Integer.parseInt(map.get("renderThreadPriority")); } catch (NumberFormatException ignored) {}
        }
        if (map.containsKey("workerThreadPriority")) {
            try { target.workerThreadPriority = Integer.parseInt(map.get("workerThreadPriority")); } catch (NumberFormatException ignored) {}
        }
        if (map.containsKey("ioThreadPriority")) {
            try { target.ioThreadPriority = Integer.parseInt(map.get("ioThreadPriority")); } catch (NumberFormatException ignored) {}
        }

        if (map.containsKey("enableFastItemFrames")) target.enableFastItemFrames = Boolean.parseBoolean(map.get("enableFastItemFrames"));
        if (map.containsKey("enableItemFrameBlockOcclusion")) target.enableItemFrameBlockOcclusion = Boolean.parseBoolean(map.get("enableItemFrameBlockOcclusion"));
        if (map.containsKey("itemFrameMaxDistance")) {
            try { target.itemFrameMaxDistance = Double.parseDouble(map.get("itemFrameMaxDistance")); } catch (NumberFormatException ignored) {}
        }
        if (map.containsKey("itemFrameItemDistance")) {
            try { target.itemFrameItemDistance = Double.parseDouble(map.get("itemFrameItemDistance")); } catch (NumberFormatException ignored) {}
        }

        if (map.containsKey("cullingDistanceFactor")) {
            try { target.cullingDistanceFactor = Double.parseDouble(map.get("cullingDistanceFactor")); } catch (NumberFormatException ignored) {}
        }
        if (map.containsKey("particleCullingDistance")) {
            try { target.particleCullingDistance = Double.parseDouble(map.get("particleCullingDistance")); } catch (NumberFormatException ignored) {}
        }
        if (map.containsKey("beaconProtection")) target.beaconProtection = Boolean.parseBoolean(map.get("beaconProtection"));
        if (map.containsKey("chestProtection")) target.chestProtection = Boolean.parseBoolean(map.get("chestProtection"));
        if (map.containsKey("showDiagnosticsHud")) target.showDiagnosticsHud = Boolean.parseBoolean(map.get("showDiagnosticsHud"));
        if (map.containsKey("showFps")) target.showFps = Boolean.parseBoolean(map.get("showFps"));
        if (map.containsKey("noMobAnimations")) target.noMobAnimations = Boolean.parseBoolean(map.get("noMobAnimations"));
        if (map.containsKey("noDroppedItemAnimation")) target.noDroppedItemAnimation = Boolean.parseBoolean(map.get("noDroppedItemAnimation"));
        if (map.containsKey("staticExpAnimations")) target.staticExpAnimations = Boolean.parseBoolean(map.get("staticExpAnimations"));
        if (map.containsKey("noParticles")) target.noParticles = Boolean.parseBoolean(map.get("noParticles"));
        if (map.containsKey("noTextureAnimations")) target.noTextureAnimations = Boolean.parseBoolean(map.get("noTextureAnimations"));
        if (map.containsKey("noEntityShadows")) target.noEntityShadows = Boolean.parseBoolean(map.get("noEntityShadows"));
        if (map.containsKey("noItemGlint")) target.noItemGlint = Boolean.parseBoolean(map.get("noItemGlint"));
        if (map.containsKey("noSky")) target.noSky = Boolean.parseBoolean(map.get("noSky"));
        if (map.containsKey("noFog")) target.noFog = Boolean.parseBoolean(map.get("noFog"));
        if (map.containsKey("noBlockEntityAnimations")) target.noBlockEntityAnimations = Boolean.parseBoolean(map.get("noBlockEntityAnimations"));
        if (map.containsKey("noChunkFade")) target.noChunkFade = Boolean.parseBoolean(map.get("noChunkFade"));
        if (map.containsKey("fastChest")) target.fastChest = Boolean.parseBoolean(map.get("fastChest"));
        if (map.containsKey("shitFoliage")) target.shitFoliage = Boolean.parseBoolean(map.get("shitFoliage"));
        if (map.containsKey("fullBright")) target.fullBright = Boolean.parseBoolean(map.get("fullBright"));
        if (map.containsKey("enableMemoryLeakFix")) target.enableMemoryLeakFix = Boolean.parseBoolean(map.get("enableMemoryLeakFix"));
        if (map.containsKey("enableC2MeOptimizations")) target.enableC2MeOptimizations = Boolean.parseBoolean(map.get("enableC2MeOptimizations"));
        if (map.containsKey("enableExordium")) target.enableExordium = Boolean.parseBoolean(map.get("enableExordium"));
        if (map.containsKey("hudTargetFps")) {
            try { target.hudTargetFps = Integer.parseInt(map.get("hudTargetFps")); } catch (NumberFormatException ignored) {}
        }
        if (map.containsKey("enableScreenPacing")) target.enableScreenPacing = Boolean.parseBoolean(map.get("enableScreenPacing"));
        if (map.containsKey("screenTargetFps")) {
            try { target.screenTargetFps = Integer.parseInt(map.get("screenTargetFps")); } catch (NumberFormatException ignored) {}
        }
        if (map.containsKey("instantInputResponsiveness")) target.instantInputResponsiveness = Boolean.parseBoolean(map.get("instantInputResponsiveness"));
        if (map.containsKey("dynamicHudUpdates")) target.dynamicHudUpdates = Boolean.parseBoolean(map.get("dynamicHudUpdates"));
        if (map.containsKey("separateCrosshair")) target.separateCrosshair = Boolean.parseBoolean(map.get("separateCrosshair"));
        if (map.containsKey("bypassInDebugScreen")) target.bypassInDebugScreen = Boolean.parseBoolean(map.get("bypassInDebugScreen"));
        if (map.containsKey("fastFadeTransitions")) target.fastFadeTransitions = Boolean.parseBoolean(map.get("fastFadeTransitions"));
        if (map.containsKey("enableAnimationLod")) target.enableAnimationLod = Boolean.parseBoolean(map.get("enableAnimationLod"));
        if (map.containsKey("animationLodDistance")) {
            try { target.animationLodDistance = Double.parseDouble(map.get("animationLodDistance")); } catch (NumberFormatException ignored) {}
        }
        if (map.containsKey("enableEntityShadowCulling")) target.enableEntityShadowCulling = Boolean.parseBoolean(map.get("enableEntityShadowCulling"));
        if (map.containsKey("entityShadowMaxDistance")) {
            try { target.entityShadowMaxDistance = Double.parseDouble(map.get("entityShadowMaxDistance")); } catch (NumberFormatException ignored) {}
        }
        if (map.containsKey("vramBudgetMb")) {
            try { target.vramBudgetMb = Integer.parseInt(map.get("vramBudgetMb")); } catch (NumberFormatException ignored) {}
        }
        if (map.containsKey("activePreset")) {
            try { target.activePreset = Preset.valueOf(map.get("activePreset")); } catch (Exception ignored) {}
        }
    }
}
