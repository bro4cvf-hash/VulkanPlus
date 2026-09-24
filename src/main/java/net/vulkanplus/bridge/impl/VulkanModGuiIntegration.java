package net.vulkanplus.bridge.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.vulkanmod.config.gui.ModSettingsEntry;
import net.vulkanmod.config.gui.ModSettingsRegistry;
import net.vulkanmod.config.gui.OptionBlock;
import net.vulkanmod.config.option.CyclingOption;
import net.vulkanmod.config.option.Option;
import net.vulkanmod.config.option.OptionPage;
import net.vulkanmod.config.option.PerformanceImpact;
import net.vulkanmod.config.option.SwitchOption;
import net.vulkanplus.VulkanPlusMod;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.Preset;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.thread.ThreadPriorityManager;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Registers Vulkan Plus into VulkanMod's Video Settings GUI (VOptionScreen).
 * Provides a dedicated Vulkan Plus configuration page organized into 7 logical sections
 * with technical descriptions and performance impact ratings for every setting.
 */
public class VulkanModGuiIntegration {
    private static boolean registered = false;

    public static synchronized void register() {
        if (registered) return;
        try {
            ModSettingsRegistry.INSTANCE.addModEntry(new ModSettingsEntry(
                    Text.literal("Vulkan Plus"),
                    () -> Identifier.of("vulkanplus", "icon.png"),
                    VulkanModGuiIntegration::buildOptionPages,
                    () -> {
                        ConfigManager.save();
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null && client.worldRenderer != null) {
                            client.worldRenderer.reload();
                        }
                    }
            ));
            registered = true;
            VulkanPlusMod.LOGGER.info("[VulkanPlus] Registered Vulkan Plus companion page into VulkanMod Video Settings GUI.");
        } catch (Throwable t) {
            VulkanPlusMod.LOGGER.warn("[VulkanPlus] Failed to register into VulkanMod Video Settings GUI: {}", t.getMessage());
        }
    }

    private static SwitchOption createSwitch(
            String nameKey,
            Consumer<Boolean> setter,
            Supplier<Boolean> getter,
            String tooltipKey,
            PerformanceImpact impact,
            Runnable extraOnChange
    ) {
        SwitchOption option = new SwitchOption(
                Text.translatable(nameKey),
                val -> {
                    setter.accept(val);
                    ConfigManager.save();
                    if (extraOnChange != null) extraOnChange.run();
                },
                getter
        );
        option.setOnChange(() -> {
            boolean val = option.getNewValue();
            setter.accept(val);
            ConfigManager.save();
            if (extraOnChange != null) extraOnChange.run();
        });
        if (tooltipKey != null) {
            option.setTooltip(val -> Text.translatable(tooltipKey));
        }
        if (impact != null) {
            option.setImpact(impact);
        }
        return option;
    }

    public static List<OptionPage> buildOptionPages() {
        VulkanPlusConfig config = ConfigManager.getConfig();

        Runnable leavesReload = () -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                if (client.options != null) {
                    client.options.getCutoutLeaves().setValue(!config.opaqueLeaves);
                    client.options.write();
                }
                net.minecraft.client.render.BlockRenderLayers.setCutoutLeaves(!config.opaqueLeaves);
                net.minecraft.block.LeavesBlock.setCutoutLeaves(!config.opaqueLeaves);
                net.vulkanplus.bridge.ViskCompatBridge.invalidateAll();
                client.reloadResourcesConcurrently();
                if (client.worldRenderer != null) {
                    client.worldRenderer.reload();
                }
            }
        };

        Consumer<Preset> applyPresetWithSideEffects = preset -> {
            boolean oldOpaqueLeaves = config.opaqueLeaves;
            String oldPresentMode = config.presentMode;
            config.applyPreset(preset);
            ConfigManager.save();
            ThreadPriorityManager.sweepAndApplyAll();
            if (!java.util.Objects.equals(oldPresentMode, config.presentMode)) {
                VulkanModBridgeImpl.scheduleSwapChainUpdateIfNeeded();
            }
            if (oldOpaqueLeaves != config.opaqueLeaves) {
                leavesReload.run();
            }
        };

        CyclingOption<Preset> presetOption = new CyclingOption<>(
                Text.translatable("vulkanplus.options.preset"),
                Preset.values(),
                applyPresetWithSideEffects,
                () -> config.activePreset
        );
        presetOption.setTranslator(preset -> Text.literal(preset.getDisplayName()));
        presetOption.setTooltip(preset -> Text.literal(preset.getDescription()));
        presetOption.setOnChange(() -> applyPresetWithSideEffects.accept(presetOption.getNewValue()));

        Option<?>[] generalOptions = new Option<?>[]{
                createSwitch("vulkanplus.options.enabled", val -> config.enabled = val, () -> config.enabled,
                        "vulkanplus.options.enabled.tooltip", null, null),
                presetOption,
                createSwitch("vulkanplus.options.showFps", val -> ConfigManager.getConfig().showFps = val, () -> ConfigManager.getConfig().showFps,
                        "vulkanplus.options.showFps.tooltip", null, null),
                createSwitch("vulkanplus.options.showDiagnosticsHud", val -> config.showDiagnosticsHud = val, () -> config.showDiagnosticsHud,
                        "vulkanplus.options.showDiagnosticsHud.tooltip", null, null)
        };
        OptionBlock generalBlock = new OptionBlock(Text.translatable("vulkanplus.section.general").getString(), generalOptions);

        Runnable worldReload = () -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.worldRenderer != null) {
                client.worldRenderer.reload();
            }
        };

        Integer[] foliageDensities = new Integer[]{100, 75, 50, 25};
        CyclingOption<Integer> foliageDensityOption = new CyclingOption<>(
                Text.translatable("vulkanplus.options.foliageDensity"),
                foliageDensities,
                density -> {
                    config.foliageDensity = density;
                    ConfigManager.save();
                    worldReload.run();
                },
                () -> config.foliageDensity
        );
        foliageDensityOption.setTranslator(density -> Text.literal(density + "%"));
        foliageDensityOption.setTooltip(density -> Text.translatable("vulkanplus.options.foliageDensity.tooltip"));
        foliageDensityOption.setImpact(PerformanceImpact.HIGH);
        foliageDensityOption.setOnChange(() -> {
            config.foliageDensity = foliageDensityOption.getNewValue();
            ConfigManager.save();
            worldReload.run();
        });

        Option<?>[] foliageOptions = new Option<?>[]{
                createSwitch("vulkanplus.options.opaqueLeaves", val -> config.opaqueLeaves = val, () -> config.opaqueLeaves,
                        "vulkanplus.options.opaqueLeaves.tooltip", PerformanceImpact.HIGH, leavesReload),
                createSwitch("vulkanplus.options.smartLeaves", val -> config.enableSmartLeaves = val, () -> config.enableSmartLeaves,
                        "vulkanplus.options.smartLeaves.tooltip", PerformanceImpact.MEDIUM, worldReload),
                createSwitch("vulkanplus.options.fastFoliage", val -> config.enableFastFoliage = val, () -> config.enableFastFoliage,
                        "vulkanplus.options.fastFoliage.tooltip", PerformanceImpact.HIGH, worldReload),
                foliageDensityOption
        };
        OptionBlock foliageBlock = new OptionBlock(Text.translatable("vulkanplus.section.foliage").getString(), foliageOptions);

        String[] presentModes = new String[]{"DEFAULT", "MAILBOX", "IMMEDIATE", "FIFO", "FIFO_RELAXED"};
        CyclingOption<String> presentModeOption = new CyclingOption<>(
                Text.translatable("vulkanplus.options.presentMode"),
                presentModes,
                mode -> {
                    config.presentMode = mode;
                    ConfigManager.save();
                    VulkanModBridgeImpl.scheduleSwapChainUpdateIfNeeded();
                },
                () -> config.presentMode
        );
        presentModeOption.setTranslator(Text::literal);
        presentModeOption.setTooltip(mode -> Text.translatable("vulkanplus.options.presentMode.tooltip"));
        presentModeOption.setOnChange(() -> {
            config.presentMode = presentModeOption.getNewValue();
            ConfigManager.save();
            VulkanModBridgeImpl.scheduleSwapChainUpdateIfNeeded();
        });

        Option<?>[] vulkanOptions = new Option<?>[]{
                createSwitch("vulkanplus.options.bufferPooling", val -> config.enableBufferPooling = val, () -> config.enableBufferPooling,
                        "vulkanplus.options.bufferPooling.tooltip", PerformanceImpact.HIGH, null),
                createSwitch("vulkanplus.options.descriptorCaching", val -> config.enableDescriptorCaching = val, () -> config.enableDescriptorCaching,
                        "vulkanplus.options.descriptorCaching.tooltip", PerformanceImpact.MEDIUM, null),
                createSwitch("vulkanplus.options.psoCache", val -> config.enablePsoCache = val, () -> config.enablePsoCache,
                        "vulkanplus.options.psoCache.tooltip", PerformanceImpact.HIGH, null),
                createSwitch("vulkanplus.options.reverseZ", val -> config.enableReverseZ = val, () -> config.enableReverseZ,
                        "vulkanplus.options.reverseZ.tooltip", PerformanceImpact.LOW, null),
                createSwitch("vulkanplus.options.swapchainTuning", val -> config.enableSwapchainTuning = val, () -> config.enableSwapchainTuning,
                        "vulkanplus.options.swapchainTuning.tooltip", PerformanceImpact.MEDIUM, VulkanModBridgeImpl::scheduleSwapChainUpdateIfNeeded),
                presentModeOption
        };
        OptionBlock vulkanBlock = new OptionBlock(Text.translatable("vulkanplus.section.vulkan").getString(), vulkanOptions);

        Option<?>[] cullingOptions = new Option<?>[]{
                createSwitch("vulkanplus.options.moreCulling", val -> {
                    config.enableMoreCulling = val;
                    config.enableEntityCulling = val;
                }, () -> config.enableMoreCulling, "vulkanplus.options.moreCulling.tooltip", PerformanceImpact.HIGH, null),
                createSwitch("vulkanplus.options.blockEntityCulling", val -> config.enableBlockEntityCulling = val, () -> config.enableBlockEntityCulling,
                        "vulkanplus.options.blockEntityCulling.tooltip", PerformanceImpact.HIGH, null),
                createSwitch("vulkanplus.options.blockEntityOcclusion", val -> config.enableBlockEntityOcclusion = val, () -> config.enableBlockEntityOcclusion,
                        "vulkanplus.options.blockEntityOcclusion.tooltip", PerformanceImpact.MEDIUM, null),
                createSwitch("vulkanplus.options.chestProtection", val -> config.chestProtection = val, () -> config.chestProtection,
                        "vulkanplus.options.chestProtection.tooltip", PerformanceImpact.LOW, null),
                createSwitch("vulkanplus.options.beaconBeamCulling", val -> config.enableBeaconBeamCulling = val, () -> config.enableBeaconBeamCulling,
                        "vulkanplus.options.beaconBeamCulling.tooltip", PerformanceImpact.LOW, null),
                createSwitch("vulkanplus.options.beaconProtection", val -> config.beaconProtection = val, () -> config.beaconProtection,
                        "vulkanplus.options.beaconProtection.tooltip", PerformanceImpact.LOW, null)
        };
        OptionBlock cullingBlock = new OptionBlock(Text.translatable("vulkanplus.section.culling").getString(), cullingOptions);

        Option<?>[] worldOptions = new Option<?>[]{
                createSwitch("vulkanplus.options.extraGlassCulling", val -> config.enableExtraGlassCulling = val, () -> config.enableExtraGlassCulling,
                        "vulkanplus.options.extraGlassCulling.tooltip", PerformanceImpact.MEDIUM, null),
                createSwitch("vulkanplus.options.particleCulling", val -> config.enableParticleCulling = val, () -> config.enableParticleCulling,
                        "vulkanplus.options.particleCulling.tooltip", PerformanceImpact.MEDIUM, null)
        };
        OptionBlock worldBlock = new OptionBlock(Text.translatable("vulkanplus.section.world").getString(), worldOptions);

        Option<?>[] itemFrameOptions = new Option<?>[]{
                createSwitch("vulkanplus.options.fastItemFrames", val -> config.enableFastItemFrames = val, () -> config.enableFastItemFrames,
                        "vulkanplus.options.fastItemFrames.tooltip", PerformanceImpact.HIGH, null),
                createSwitch("vulkanplus.options.itemFrameBlockOcclusion", val -> config.enableItemFrameBlockOcclusion = val, () -> config.enableItemFrameBlockOcclusion,
                        "vulkanplus.options.itemFrameBlockOcclusion.tooltip", PerformanceImpact.MEDIUM, null)
        };
        OptionBlock itemFrameBlock = new OptionBlock(Text.translatable("vulkanplus.section.itemFrames").getString(), itemFrameOptions);

        Option<?>[] cpuOptions = new Option<?>[]{
                createSwitch("vulkanplus.options.threadPriority", val -> config.enableThreadPriority = val, () -> config.enableThreadPriority,
                        "vulkanplus.options.threadPriority.tooltip", PerformanceImpact.MEDIUM, ThreadPriorityManager::sweepAndApplyAll),
                createSwitch("vulkanplus.options.fastMath", val -> config.enableFastMath = val, () -> config.enableFastMath,
                        "vulkanplus.options.fastMath.tooltip", PerformanceImpact.LOW, null),
                createSwitch("vulkanplus.options.fastRandom", val -> config.enableFastRandom = val, () -> config.enableFastRandom,
                        "vulkanplus.options.fastRandom.tooltip", PerformanceImpact.LOW, null),
                createSwitch("vulkanplus.options.matrixPooling", val -> config.enableMatrixPooling = val, () -> config.enableMatrixPooling,
                        "vulkanplus.options.matrixPooling.tooltip", PerformanceImpact.LOW, null),
                createSwitch("vulkanplus.options.enableC2MeOptimizations", val -> config.enableC2MeOptimizations = val, () -> config.enableC2MeOptimizations,
                        "vulkanplus.options.enableC2MeOptimizations.tooltip", PerformanceImpact.HIGH, null),
                createSwitch("vulkanplus.options.enableMemoryLeakFix", val -> config.enableMemoryLeakFix = val, () -> config.enableMemoryLeakFix,
                        "vulkanplus.options.enableMemoryLeakFix.tooltip", PerformanceImpact.MEDIUM, null)
        };
        OptionBlock cpuBlock = new OptionBlock(Text.translatable("vulkanplus.section.cpu").getString(), cpuOptions);

        Option<?>[] assPcOptions = new Option<?>[]{
                createSwitch("vulkanplus.options.noMobAnimations", val -> config.noMobAnimations = val, () -> config.noMobAnimations,
                        "vulkanplus.options.noMobAnimations.tooltip", PerformanceImpact.HIGH, null),
                createSwitch("vulkanplus.options.noDroppedItemAnimation", val -> config.noDroppedItemAnimation = val, () -> config.noDroppedItemAnimation,
                        "vulkanplus.options.noDroppedItemAnimation.tooltip", PerformanceImpact.LOW, null),
                createSwitch("vulkanplus.options.staticExpAnimations", val -> config.staticExpAnimations = val, () -> config.staticExpAnimations,
                        "vulkanplus.options.staticExpAnimations.tooltip", PerformanceImpact.LOW, null),
                createSwitch("vulkanplus.options.noParticles", val -> config.noParticles = val, () -> config.noParticles,
                        "vulkanplus.options.noParticles.tooltip", PerformanceImpact.HIGH, null),
                createSwitch("vulkanplus.options.noTextureAnimations", val -> config.noTextureAnimations = val, () -> config.noTextureAnimations,
                        "vulkanplus.options.noTextureAnimations.tooltip", PerformanceImpact.HIGH, null),
                createSwitch("vulkanplus.options.noEntityShadows", val -> config.noEntityShadows = val, () -> config.noEntityShadows,
                        "vulkanplus.options.noEntityShadows.tooltip", PerformanceImpact.MEDIUM, null),
                createSwitch("vulkanplus.options.noItemGlint", val -> config.noItemGlint = val, () -> config.noItemGlint,
                        "vulkanplus.options.noItemGlint.tooltip", PerformanceImpact.MEDIUM, null),
                createSwitch("vulkanplus.options.noSky", val -> config.noSky = val, () -> config.noSky,
                        "vulkanplus.options.noSky.tooltip", PerformanceImpact.HIGH, null),
                createSwitch("vulkanplus.options.noBlockEntityAnimations", val -> config.noBlockEntityAnimations = val, () -> config.noBlockEntityAnimations,
                        "vulkanplus.options.noBlockEntityAnimations.tooltip", PerformanceImpact.MEDIUM, null),
                createSwitch("vulkanplus.options.noFogAndFade", val -> {
                    config.noFog = val;
                    config.noChunkFade = val;
                }, () -> config.noFog || config.noChunkFade,
                        "vulkanplus.options.noFogAndFade.tooltip", PerformanceImpact.HIGH, null),
                createSwitch("vulkanplus.options.fastChest", val -> config.fastChest = val, () -> config.fastChest,
                        "vulkanplus.options.fastChest.tooltip", PerformanceImpact.MEDIUM, worldReload),
                createSwitch("vulkanplus.options.shitFoliage", val -> config.shitFoliage = val, () -> config.shitFoliage,
                        "vulkanplus.options.shitFoliage.tooltip", PerformanceImpact.HIGH, worldReload)
        };
        OptionBlock assPcBlock = new OptionBlock(Text.translatable("vulkanplus.section.ass_pc").getString(), assPcOptions);

        Integer[] hudFpsOptions = new Integer[]{15, 30, 45, 60, 90, 120};
        CyclingOption<Integer> hudFpsOption = new CyclingOption<>(
                Text.translatable("vulkanplus.options.hudTargetFps"),
                hudFpsOptions,
                fps -> {
                    config.hudTargetFps = fps;
                    ConfigManager.save();
                },
                () -> config.hudTargetFps
        );
        hudFpsOption.setTranslator(fps -> Text.literal(fps + " FPS"));
        hudFpsOption.setTooltip(fps -> Text.translatable("vulkanplus.options.hudTargetFps.tooltip"));
        hudFpsOption.setOnChange(() -> {
            config.hudTargetFps = hudFpsOption.getNewValue();
            ConfigManager.save();
        });

        Integer[] screenFpsOptions = new Integer[]{30, 60};
        CyclingOption<Integer> screenFpsOption = new CyclingOption<>(
                Text.translatable("vulkanplus.options.screenTargetFps"),
                screenFpsOptions,
                fps -> {
                    config.screenTargetFps = fps;
                    ConfigManager.save();
                },
                () -> config.screenTargetFps
        );
        screenFpsOption.setTranslator(fps -> Text.literal(fps + " FPS"));
        screenFpsOption.setTooltip(fps -> Text.translatable("vulkanplus.options.screenTargetFps.tooltip"));
        screenFpsOption.setOnChange(() -> {
            config.screenTargetFps = screenFpsOption.getNewValue();
            ConfigManager.save();
        });

        Option<?>[] exordiumOptions = new Option<?>[]{
                createSwitch("vulkanplus.options.enableExordium", val -> config.enableExordium = val, () -> config.enableExordium,
                        "vulkanplus.options.enableExordium.tooltip", PerformanceImpact.HIGH, null),
                hudFpsOption,
                createSwitch("vulkanplus.options.enableScreenPacing", val -> config.enableScreenPacing = val, () -> config.enableScreenPacing,
                        "vulkanplus.options.enableScreenPacing.tooltip", PerformanceImpact.MEDIUM, null),
                screenFpsOption,
                createSwitch("vulkanplus.options.instantInputResponsiveness", val -> config.instantInputResponsiveness = val, () -> config.instantInputResponsiveness,
                        "vulkanplus.options.instantInputResponsiveness.tooltip", PerformanceImpact.LOW, null),
                createSwitch("vulkanplus.options.dynamicHudUpdates", val -> config.dynamicHudUpdates = val, () -> config.dynamicHudUpdates,
                        "vulkanplus.options.dynamicHudUpdates.tooltip", PerformanceImpact.LOW, null),
                createSwitch("vulkanplus.options.separateCrosshair", val -> config.separateCrosshair = val, () -> config.separateCrosshair,
                        "vulkanplus.options.separateCrosshair.tooltip", PerformanceImpact.LOW, null),
                createSwitch("vulkanplus.options.bypassInDebugScreen", val -> config.bypassInDebugScreen = val, () -> config.bypassInDebugScreen,
                        "vulkanplus.options.bypassInDebugScreen.tooltip", PerformanceImpact.LOW, null),
                createSwitch("vulkanplus.options.fastFadeTransitions", val -> config.fastFadeTransitions = val, () -> config.fastFadeTransitions,
                        "vulkanplus.options.fastFadeTransitions.tooltip", PerformanceImpact.LOW, null)
        };
        OptionBlock exordiumBlock = new OptionBlock(Text.translatable("vulkanplus.section.exordium").getString(), exordiumOptions);

        OptionPage generalPage = new OptionPage("General", new OptionBlock[]{
                generalBlock
        });

        OptionPage cullingPage = new OptionPage("Culling", new OptionBlock[]{
                cullingBlock,
                itemFrameBlock,
                worldBlock
        });

        OptionPage enginePage = new OptionPage("Engine", new OptionBlock[]{
                foliageBlock,
                vulkanBlock,
                cpuBlock
        });

        OptionPage exordiumPage = new OptionPage("⏱ Exordium", new OptionBlock[]{
                exordiumBlock
        });

        OptionPage assPcPage = new OptionPage("🥔 ASS PC", new OptionBlock[]{
                assPcBlock
        });

        return List.of(generalPage, cullingPage, enginePage, exordiumPage, assPcPage);
    }
}
