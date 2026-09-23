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

    private static final Object INIT_LOCK = new Object();
    private static volatile boolean commonInitialized = false;
    private static volatile boolean clientInitialized = false;

    public static DiagnosticHud getDiagnosticHud() {
        return DIAGNOSTIC_HUD;
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
                LOGGER.info("[{}] Initializing client optimizations and probing render backend...", MOD_NAME);
                RenderEngineBridge bridge = VulkanDetector.getBridge();
                bridge.onRenderInit();

                try {
                    ThreadPriorityManager.applyRenderThreadPriority();
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
                                boolean newState = !ConfigManager.getConfig().enabled;
                                ConfigManager.getConfig().enabled = newState;
                                ConfigManager.save();

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
                            }
                        }

                        if (toggleFpsKey != null) {
                            while (toggleFpsKey.wasPressed()) {
                                ConfigManager.getConfig().showFps = !ConfigManager.getConfig().showFps;
                                ConfigManager.save();
                            }
                        }
                    });

                    HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
                        net.vulkanplus.config.VulkanPlusConfig cfg = ConfigManager.getConfig();
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client == null || client.textRenderer == null) return;
                        if (client.options != null && client.options.hudHidden) return;

                        if (cfg.showDiagnosticsHud) {
                            DIAGNOSTIC_HUD.onFrame();
                            String[] lines = DIAGNOSTIC_HUD.getDiagnosticsLines();
                            int y = 5;
                            for (String line : lines) {
                                drawContext.drawTextWithShadow(client.textRenderer, line, 5, y, 0xFF00FF88);
                                y += 11;
                            }
                        } else if (cfg.showFps) {
                            boolean f3Enabled = client.debugHudEntryList != null && client.debugHudEntryList.isF3Enabled();
                            if (!f3Enabled) {
                                int fps = client.getCurrentFps();
                                if (fps <= 0) {
                                    DIAGNOSTIC_HUD.onFrame();
                                    fps = Math.max(1, Math.round(DIAGNOSTIC_HUD.getCurrentFps()));
                                }
                                drawContext.drawTextWithShadow(client.textRenderer, fps + " FPS", 5, 5, 0xFFFFFFFF);
                            }
                        }
                    });
                } catch (Throwable t) {
                    LOGGER.debug("[{}] Client event registration skipped or running in headless/test mode: {}", MOD_NAME, t.getMessage());
                }

                try {
                    Runtime.getRuntime().addShutdownHook(new Thread(bridge::onShutdown, "VulkanPlus-Shutdown"));
                } catch (Throwable ignored) {
                }

                clientInitialized = true;
                LOGGER.info("[{}] Client optimizations initialized successfully with {}.", MOD_NAME, bridge.getEngineName());
            }
        }
    }
}
