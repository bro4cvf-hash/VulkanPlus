package net.vulkanplus.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.Preset;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.thread.ThreadPriorityManager;

import java.util.function.Consumer;

/**
 * Modern in-game configuration screen for Vulkan Plus.
 * Features categorized tabs, color-coded toggles, active preset highlights,
 * descriptive tooltips, and responsive layout.
 */
public class VulkanPlusConfigScreen extends Screen {
    private static final Identifier LOGO_ICON = Identifier.of("vulkanplus", "icon.png");

    public enum Tab {
        GENERAL("✦ General", "General options, HUD, and frame pacing"),
        CULLING("👁 Culling", "Occlusion culling, entity culling, and distances"),
        GRAPHICS("🌿 Graphics", "Foliage quality, transparency, and depth buffer"),
        ENGINE("⚡ Engine", "Vulkan pipeline caches, buffer pools, and math");

        public final String title;
        public final String description;

        Tab(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    private final Screen parent;
    private VulkanPlusConfig configCopy;
    private Tab currentTab = Tab.GENERAL;

    public VulkanPlusConfigScreen(Screen parent) {
        super(Text.literal("Vulkan Plus Settings"));
        this.parent = parent;
        this.configCopy = ConfigManager.getConfig().copy();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        Preset effective = configCopy.getEffectivePreset();

        // 1. Master Toggle (Top)
        this.addDrawableChild(ButtonWidget.builder(
                masterSwitchText(configCopy.enabled),
                button -> {
                    configCopy.enabled = !configCopy.enabled;
                    this.clearAndInit();
                }
        ).dimensions(centerX - 160, 24, 320, 20)
         .tooltip(Tooltip.of(Text.literal("Enables or disables all Vulkan Plus optimizations globally.")))
         .build());

        // 2. Presets Row
        int presetY = 47;
        int presetWidth = 104;
        this.addDrawableChild(ButtonWidget.builder(
                presetButtonText(Preset.FAST, effective),
                button -> selectPreset(Preset.FAST)
        ).dimensions(centerX - 160, presetY, presetWidth, 20)
         .tooltip(Tooltip.of(Text.literal("§e§l⚡ Fast Preset\n§7Maximum performance for lower-end hardware and integrated GPUs.")))
         .build());

        this.addDrawableChild(ButtonWidget.builder(
                presetButtonText(Preset.BALANCED, effective),
                button -> selectPreset(Preset.BALANCED)
        ).dimensions(centerX - 52, presetY, presetWidth, 20)
         .tooltip(Tooltip.of(Text.literal("§b§l⚖ Balanced Preset\n§7Optimal balance between visual quality and high frame rates.")))
         .build());

        this.addDrawableChild(ButtonWidget.builder(
                presetButtonText(Preset.EXTREME, effective),
                button -> selectPreset(Preset.EXTREME)
        ).dimensions(centerX + 56, presetY, presetWidth, 20)
         .tooltip(Tooltip.of(Text.literal("§c§l🔥 Extreme Preset\n§7Aggressive optimizations, maximum culling, and lowest latency.")))
         .build());

        // 3. Category Tabs
        int tabY = 70;
        int tabWidth = 78;
        int tabGap = 3;
        int tabStartX = centerX - 160;

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            int x = tabStartX + i * (tabWidth + tabGap);
            this.addDrawableChild(ButtonWidget.builder(
                    tabButtonText(tab),
                    button -> {
                        this.currentTab = tab;
                        this.clearAndInit();
                    }
            ).dimensions(x, tabY, tabWidth, 20)
             .tooltip(Tooltip.of(Text.literal(tab.description)))
             .build());
        }

        // 4. Tab Content
        int col1X = centerX - 160;
        int col2X = centerX + 5;
        int contentY = 94;
        int rowSpacing = 23;
        int btnWidth = 155;
        int btnHeight = 20;

        switch (currentTab) {
            case GENERAL -> {
                addToggle(col1X, contentY, btnWidth, btnHeight, "Show FPS", configCopy.showFps,
                        "Displays real-time FPS counter in the top-left corner.",
                        val -> configCopy.showFps = val);
                addToggle(col2X, contentY, btnWidth, btnHeight, "Diagnostics HUD", configCopy.showDiagnosticsHud,
                        "Displays technical rendering statistics and frame pacing metrics (F8).",
                        val -> configCopy.showDiagnosticsHud = val);

                addToggle(col1X, contentY + rowSpacing, btnWidth, btnHeight, "Thread Priority", configCopy.enableThreadPriority,
                        "Elevates render thread priority to prevent background micro-stutters.",
                        val -> configCopy.enableThreadPriority = val);
                this.addDrawableChild(ButtonWidget.builder(
                        presentModeText(configCopy.presentMode),
                        button -> {
                            configCopy.presentMode = switch (configCopy.presentMode) {
                                case "MAILBOX" -> "IMMEDIATE";
                                case "IMMEDIATE" -> "FIFO";
                                default -> "MAILBOX";
                            };
                            this.clearAndInit();
                        }
                ).dimensions(col2X, contentY + rowSpacing, btnWidth, btnHeight)
                 .tooltip(Tooltip.of(Text.literal("Vulkan presentation mode:\n• MAILBOX: Tear-free low latency (Triple-buffer)\n• IMMEDIATE: Uncapped minimum latency\n• FIFO: V-Sync lock")))
                 .build());

                addToggle(col1X, contentY + rowSpacing * 2, btnWidth, btnHeight, "Swapchain Tuning", configCopy.enableSwapchainTuning,
                        "Optimizes swapchain presentation timings to lower display latency.",
                        val -> configCopy.enableSwapchainTuning = val);
            }

            case CULLING -> {
                addToggle(col1X, contentY, btnWidth, btnHeight, "Entity Culling", configCopy.enableMoreCulling,
                        "Skips rendering entities occluded behind terrain or camera view frustum.",
                        val -> {
                            configCopy.enableMoreCulling = val;
                            configCopy.enableEntityCulling = val;
                        });
                addToggle(col2X, contentY, btnWidth, btnHeight, "Block Entity Culling", configCopy.enableBlockEntityCulling,
                        "Omits drawing chests, signs, and banners outside view frustum.",
                        val -> configCopy.enableBlockEntityCulling = val);

                addToggle(col1X, contentY + rowSpacing, btnWidth, btnHeight, "Block Occlusion", configCopy.enableBlockEntityOcclusion,
                        "Skips rendering block entities enclosed inside opaque solid blocks.",
                        val -> configCopy.enableBlockEntityOcclusion = val);
                addToggle(col2X, contentY + rowSpacing, btnWidth, btnHeight, "Beacon Beam Culling", configCopy.enableBeaconBeamCulling,
                        "Culls vertical beacon beam segments outside camera view frustum.",
                        val -> configCopy.enableBeaconBeamCulling = val);

                addToggle(col1X, contentY + rowSpacing * 2, btnWidth, btnHeight, "Particle Culling", configCopy.enableParticleCulling,
                        "Omits drawing particles hidden behind walls or beyond distance limits.",
                        val -> configCopy.enableParticleCulling = val);
                addToggle(col2X, contentY + rowSpacing * 2, btnWidth, btnHeight, "Fast Item Frames", configCopy.enableFastItemFrames,
                        "Directional backface and frustum culling for item frames.",
                        val -> {
                            configCopy.enableFastItemFrames = val;
                            configCopy.enableItemFrameBlockOcclusion = val;
                        });

                addToggle(col1X, contentY + rowSpacing * 3, btnWidth, btnHeight, "Chest Protection", configCopy.chestProtection,
                        "Prevents aggressive culling on chests near crosshair to avoid pop-in.",
                        val -> configCopy.chestProtection = val);
            }

            case GRAPHICS -> {
                addToggle(col1X, contentY, btnWidth, btnHeight, "Opaque Leaves", configCopy.opaqueLeaves,
                        "Renders solid leaves (Fast) for huge framerate boost in dense forests.",
                        val -> configCopy.opaqueLeaves = val);
                addToggle(col2X, contentY, btnWidth, btnHeight, "Smart Leaves", configCopy.enableSmartLeaves,
                        "Culls internal faces between adjacent leaves while keeping fancy transparent leaves.",
                        val -> configCopy.enableSmartLeaves = val);

                addToggle(col1X, contentY + rowSpacing, btnWidth, btnHeight, "Extra Glass Culling", configCopy.enableExtraGlassCulling,
                        "Culls hidden inner faces of glass blocks and stained glass panes.",
                        val -> configCopy.enableExtraGlassCulling = val);
                addToggle(col2X, contentY + rowSpacing, btnWidth, btnHeight, "Reverse-Z Depth", configCopy.enableReverseZ,
                        "Reverses depth buffer coordinates for greater precision and zero z-fighting.",
                        val -> configCopy.enableReverseZ = val);
            }

            case ENGINE -> {
                addToggle(col1X, contentY, btnWidth, btnHeight, "Buffer Pooling", configCopy.enableBufferPooling,
                        "Recycles Vulkan vertex and staging buffers to reduce memory allocation churn.",
                        val -> configCopy.enableBufferPooling = val);
                addToggle(col2X, contentY, btnWidth, btnHeight, "Descriptor Caching", configCopy.enableDescriptorCaching,
                        "Caches descriptor sets to eliminate redundant driver GPU bind calls.",
                        val -> configCopy.enableDescriptorCaching = val);

                addToggle(col1X, contentY + rowSpacing, btnWidth, btnHeight, "PSO Disk Cache", configCopy.enablePsoCache,
                        "Caches compiled Vulkan Pipeline State Objects to mitigate shader compilation micro-stutters.",
                        val -> configCopy.enablePsoCache = val);
                addToggle(col2X, contentY + rowSpacing, btnWidth, btnHeight, "Matrix Pooling", configCopy.enableMatrixPooling,
                        "Recycles Matrix4f transformation instances to minimize JVM GC pauses.",
                        val -> configCopy.enableMatrixPooling = val);

                addToggle(col1X, contentY + rowSpacing * 2, btnWidth, btnHeight, "Fast Math", configCopy.enableFastMath,
                        "Optimized trigonometric and transformation math approximations.",
                        val -> configCopy.enableFastMath = val);
                addToggle(col2X, contentY + rowSpacing * 2, btnWidth, btnHeight, "Fast Random", configCopy.enableFastRandom,
                        "High-speed pseudo-random generator for particle physics and effects.",
                        val -> configCopy.enableFastRandom = val);
            }
        }

        // 5. Bottom Navigation
        int bottomY = this.height - 26;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§e↺ Reset"),
                button -> {
                    this.configCopy = new VulkanPlusConfig();
                    this.clearAndInit();
                }
        ).dimensions(centerX - 160, bottomY, 95, 20)
         .tooltip(Tooltip.of(Text.literal("Reset all settings to default Balanced profile.")))
         .build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§a✔ Done"),
                button -> {
                    boolean leavesChanged = (ConfigManager.getConfig().opaqueLeaves != configCopy.opaqueLeaves);
                    ConfigManager.setConfig(configCopy);
                    ThreadPriorityManager.sweepAndApplyAll();

                    if (this.client != null) {
                        if (this.client.options != null) {
                            this.client.options.getCutoutLeaves().setValue(!configCopy.opaqueLeaves);
                            this.client.options.write();
                        }
                        net.minecraft.client.render.BlockRenderLayers.setCutoutLeaves(!configCopy.opaqueLeaves);
                        net.minecraft.block.LeavesBlock.setCutoutLeaves(!configCopy.opaqueLeaves);
                        if (leavesChanged) {
                            this.client.reloadResourcesConcurrently();
                            if (this.client.worldRenderer != null) {
                                this.client.worldRenderer.reload();
                            }
                        }
                        this.client.setScreen(parent);
                    }
                }
        ).dimensions(centerX - 55, bottomY, 110, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§c✖ Cancel"),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(parent);
                    }
                }
        ).dimensions(centerX + 65, bottomY, 95, 20).build());
    }

    private void addToggle(int x, int y, int width, int height, String label, boolean value, String tooltip, Consumer<Boolean> setter) {
        this.addDrawableChild(ButtonWidget.builder(
                toggleText(label, value),
                button -> {
                    setter.accept(!value);
                    this.clearAndInit();
                }
        ).dimensions(x, y, width, height)
         .tooltip(Tooltip.of(Text.literal(tooltip)))
         .build());
    }

    private void selectPreset(Preset preset) {
        configCopy.applyPreset(preset);
        this.clearAndInit();
    }

    private Text masterSwitchText(boolean enabled) {
        return Text.literal("Vulkan Plus: " + (enabled ? "§a§lENABLED" : "§c§lDISABLED"));
    }

    private Text presetButtonText(Preset preset, Preset effective) {
        boolean active = (effective == preset);
        return switch (preset) {
            case FAST -> active ? Text.literal("§a§l✔ Fast") : Text.literal("§e⚡ Fast");
            case BALANCED -> active ? Text.literal("§a§l✔ Balanced") : Text.literal("§b⚖ Balanced");
            case EXTREME -> active ? Text.literal("§a§l✔ Extreme") : Text.literal("§c🔥 Extreme");
        };
    }

    private Text tabButtonText(Tab tab) {
        boolean active = (this.currentTab == tab);
        if (active) {
            return Text.literal("§6§l" + tab.title);
        } else {
            return Text.literal("§7" + tab.title);
        }
    }

    private Text toggleText(String label, boolean value) {
        return Text.literal(label + ": " + (value ? "§aON" : "§cOFF"));
    }

    private Text presentModeText(String mode) {
        String coloredMode = switch (mode) {
            case "IMMEDIATE" -> "§eIMMEDIATE";
            case "MAILBOX" -> "§bMAILBOX";
            case "FIFO" -> "§dFIFO";
            default -> "§7" + mode;
        };
        return Text.literal("Present Mode: " + coloredMode);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int titleWidth = this.textRenderer.getWidth("Vulkan Plus Settings");
        int iconSize = 12;
        int iconX = (this.width - titleWidth) / 2 - iconSize - 4;
        int iconY = 6;
        try {
            context.drawTexturedQuad(LOGO_ICON, iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0f, 1.0f, 0.0f, 1.0f);
        } catch (Throwable ignored) {
            // Safe fallback in headless / unit test environments
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§c§lVulkan§f§l+ §7Settings"), this.width / 2, 7, 0xFFFFFF);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
