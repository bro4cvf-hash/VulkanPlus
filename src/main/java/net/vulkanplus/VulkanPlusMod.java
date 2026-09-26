package net.vulkanplus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.vulkanplus.bridge.RenderEngineBridge;
import net.vulkanplus.bridge.VulkanDetector;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.thread.ThreadPriorityManager;
import net.vulkanplus.ui.DiagnosticHud;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VulkanPlusMod implements ModInitializer, ClientModInitializer {
    public static final String MOD_ID = "vulkanplus";
    public static final String MOD_NAME = "Vulkan Plus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static final DiagnosticHud DIAGNOSTIC_HUD = new DiagnosticHud();
    private static volatile KeyBinding toggleHudKey;
    private static volatile KeyBinding toggleVulkanPlusKey;
    private static volatile KeyBinding toggleFpsKey;
    private static boolean overlayRenderedThisFrame = false;

    private static final Object INIT_LOCK = new Object();
    private static volatile boolean commonInitialized = false;
    private static volatile boolean clientInitialized = false;

    public static DiagnosticHud getDiagnosticHud() {
        return DIAGNOSTIC_HUD;
    }

    public static void beginHudFrame() {
        overlayRenderedThisFrame = false;
        DIAGNOSTIC_HUD.onFrameTick();
    }

    public static void renderDiagnosticsAndFpsOverlay(net.minecraft.client.gui.DrawContext drawContext) {
        if (overlayRenderedThisFrame || drawContext == null) {
            return;
        }
        if (net.vulkanplus.exordium.ExordiumManager.getInstance().isCapturing()) {
            // Skip baking Show FPS / F8 Diagnostics HUD into Exordium's cached HUD snapshot
            // so it renders uncached at full native framerate on every frame without flickering.
            return;
        }

        net.vulkanplus.config.VulkanPlusConfig cfg = ConfigManager.getConfig();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;
        if (client.options != null && client.options.hudHidden) return;

        overlayRenderedThisFrame = true;

        if (cfg.showDiagnosticsHud) {
            DIAGNOSTIC_HUD.onHudRenderPass();
            String[] lines = DIAGNOSTIC_HUD.getDiagnosticsLines();
            int y = 5;
            for (int i = 0, n = lines.length; i < n; i++) {
                drawContext.drawTextWithShadow(client.textRenderer, lines[i], 5, y, 0xFF00FF88);
                y += 11;
            }
        } else if (cfg.showFps) {
            boolean f3Enabled = client.debugHudEntryList != null && client.debugHudEntryList.isF3Enabled();
            if (!f3Enabled) {
                DIAGNOSTIC_HUD.onHudRenderPass();
                int fps = Math.max(client.getCurrentFps(), Math.round(DIAGNOSTIC_HUD.getCurrentFps()));
                if (fps <= 0) {
                    fps = 1;
                }
                drawContext.drawTextWithShadow(client.textRenderer, DIAGNOSTIC_HUD.getFormattedFpsOverlay(fps), 5, 5, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public void onInitialize() {
        synchronized (INIT_LOCK) {
            if (!commonInitialized) {
                LOGGER.info("[{}] Loading configuration...", MOD_NAME);
                ConfigManager.load();
                commonInitialized = true;
            }
        }
    }

    @Override
    public void onInitializeClient() {
        synchronized (INIT_LOCK) {
            if (!clientInitialized) {
                ConfigManager.load();
                LOGGER.info("[{}] Initializing client optimizations and probing render backend...", MOD_NAME);
                RenderEngineBridge bridge = VulkanDetector.getBridge();
                bridge.onRenderInit();

                try {
                    net.vulkanplus.exordium.ExordiumManager.getInstance().init();
                    net.vulkanplus.exordium.ExordiumManager.getInstance().markDirty();
                } catch (Throwable ignored) {
                }

                try {
                    ThreadPriorityManager.applyRenderThreadPriority();
                    ThreadPriorityManager.sweepAndApplyAll();
                } catch (Throwable t) {
                    LOGGER.debug("[{}] Thread priority initialization skipped: {}", MOD_NAME, t.getMessage());
                }

                try {
                    net.vulkanplus.config.VulkanPlusConfig cfg = ConfigManager.getConfig();
                    if (cfg != null && cfg.enabled && cfg.opaqueLeaves) {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null && client.options != null) {
                            client.options.getCutoutLeaves().setValue(false);
                        }
                        net.minecraft.client.render.BlockRenderLayers.setCutoutLeaves(false);
                        net.minecraft.block.LeavesBlock.setCutoutLeaves(false);
                    }
                } catch (Throwable t) {
                    LOGGER.debug("[{}] Opaque leaves pre-init skipped: {}", MOD_NAME, t.getMessage());
                }

                try {
                    toggleVulkanPlusKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                            "key.vulkanplus.toggle",
                            InputUtil.Type.KEYSYM,
                            GLFW.GLFW_KEY_P,
                            KeyBinding.Category.MISC
                    ));

                    toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                            "key.vulkanplus.toggle_hud",
                            InputUtil.Type.KEYSYM,
                            GLFW.GLFW_KEY_F8,
                            KeyBinding.Category.MISC
                    ));

                    toggleFpsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                            "key.vulkanplus.toggle_fps",
                            InputUtil.Type.KEYSYM,
                            GLFW.GLFW_KEY_UNKNOWN,
                            KeyBinding.Category.MISC
                    ));

                    ClientTickEvents.END_CLIENT_TICK.register(client -> {
                        if (toggleVulkanPlusKey != null) {
                            while (toggleVulkanPlusKey.wasPressed()) {
                                net.vulkanplus.config.VulkanPlusConfig prev = ConfigManager.getConfig().copy();
                                net.vulkanplus.config.VulkanPlusConfig updated = prev.copy();
                                boolean newState = !prev.enabled;
                                updated.enabled = newState;
                                net.vulkanplus.ui.VulkanPlusConfigScreen.applyConfigChanges(prev, updated, client);

                                if (client.player != null) {
                                    Text msg = Text.literal("[Vulkan Plus] ")
                                            .formatted(Formatting.AQUA)
                                            .append(Text.literal("Optimizations: ").formatted(Formatting.GRAY))
                                            .append(Text.literal(newState ? "ENABLED" : "DISABLED")
                                                    .formatted(newState ? Formatting.GREEN : Formatting.RED));
                                    client.player.sendMessage(msg, false);
                                }
                            }
                        }

                        if (toggleHudKey != null) {
                            while (toggleHudKey.wasPressed()) {
                                ConfigManager.getConfig().showDiagnosticsHud = !ConfigManager.getConfig().showDiagnosticsHud;
                                ConfigManager.save();
                                net.vulkanplus.exordium.ExordiumManager.getInstance().markDirty();
                            }
                        }

                        if (toggleFpsKey != null) {
                            while (toggleFpsKey.wasPressed()) {
                                ConfigManager.getConfig().showFps = !ConfigManager.getConfig().showFps;
                                ConfigManager.save();
                                net.vulkanplus.exordium.ExordiumManager.getInstance().markDirty();
                            }
                        }
                    });

                    HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
                        renderDiagnosticsAndFpsOverlay(drawContext);
                    });
                } catch (Throwable t) {
                    LOGGER.debug("[{}] Client event registration skipped or running in headless/test mode: {}", MOD_NAME, t.getMessage());
                }

                try {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            ConfigManager.save();
                        } catch (Throwable ignored) {
                        }
                        bridge.onShutdown();
                    }, "VulkanPlus-Shutdown"));
                } catch (Throwable ignored) {
                }

                clientInitialized = true;
                LOGGER.info("[{}] Client optimizations initialized successfully with {}.", MOD_NAME, bridge.getEngineName());
            }
        }
    }
}
