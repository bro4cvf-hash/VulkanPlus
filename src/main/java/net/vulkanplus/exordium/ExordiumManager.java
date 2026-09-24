package net.vulkanplus.exordium;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.gui.render.state.TextGuiElementRenderState;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.vulkanplus.VulkanPlusMod;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Core engine for Exordium GUI & HUD framerate decoupling in Vulkan Plus.
 * Uses Minecraft 1.21.11's native GuiRenderState record snapshotting & replay to decouple
 * HUD CPU extraction and layout from 3D world rendering at the exact target FPS (e.g. 15-120 FPS)
 * with zero offscreen framebuffer blits and 100% compatibility with VulkanMod and Vanilla.
 */
public class ExordiumManager {
    private static final ExordiumManager INSTANCE = new ExordiumManager();

    private SimpleFramebuffer guiFramebuffer;
    private boolean isCapturing = false;
    private boolean hasValidCache = false;
    private long lastHudFrameTime = 0;
    private int lastCapturedWidth = -1;
    private int lastCapturedHeight = -1;
    private volatile boolean dirty = true;
    private volatile boolean inputActive = false;

    private final List<SimpleGuiElementRenderState> cachedSimpleElements = new ArrayList<>(256);
    private final List<ItemGuiElementRenderState> cachedItemElements = new ArrayList<>(64);
    private final List<TextGuiElementRenderState> cachedTextElements = new ArrayList<>(128);
    private final List<SpecialGuiElementRenderState> cachedSpecialElements = new ArrayList<>(16);

    private float lastHealth = -1.0f;

    private boolean externalModDetected = false;
    private boolean initialized = false;

    private ExordiumManager() {
    }

    public static ExordiumManager getInstance() {
        return INSTANCE;
    }

    public synchronized void init() {
        if (initialized) return;
        try {
            if (FabricLoader.getInstance().isModLoaded("exordium")) {
                this.externalModDetected = true;
                VulkanPlusMod.LOGGER.info("[VulkanPlus] External Exordium mod detected! Yielding built-in Exordium engine to external mod.");
            }
        } catch (Throwable ignored) {
        }
        initialized = true;
    }

    public boolean isExternalModDetected() {
        return externalModDetected;
    }

    public boolean isExordiumConfigured() {
        VulkanPlusConfig config = ConfigManager.getConfig();
        return config.enabled && config.enableExordium;
    }

    public boolean isVulkanModConflict() {
        return net.vulkanplus.bridge.VulkanDetector.isVulkanModLoaded();
    }

    public boolean isExordiumActive() {
        if (!initialized) init();
        if (externalModDetected) return false;
        return isExordiumConfigured();
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void onInputActivity() {
        this.inputActive = true;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            VulkanPlusConfig config = ConfigManager.getConfig();
            // Only bypass pacing for input when an interactive screen (Inventory/Menu) is open.
            // Never mark dirty during 3D camera movement or WASD walking so HUD pacing stays locked at hudTargetFps!
            if (client != null && client.currentScreen != null && config.instantInputResponsiveness) {
                this.dirty = true;
            }
        } catch (Throwable ignored) {
        }
    }

    public SimpleFramebuffer getGuiFramebuffer() {
        return guiFramebuffer;
    }

    public boolean isCapturing() {
        return isCapturing;
    }

    public void ensureFramebuffer(int width, int height) {
        int safeW = Math.max(1, width);
        int safeH = Math.max(1, height);
        if (lastCapturedWidth != safeW || lastCapturedHeight != safeH) {
            lastCapturedWidth = safeW;
            lastCapturedHeight = safeH;
            dirty = true;
            hasValidCache = false;
        }
    }

    public boolean shouldRenderHud(RenderTickCounter tickCounter) {
        if (!isExordiumActive()) return true;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden) return true;

        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config.bypassInDebugScreen && client.getDebugHud() != null && client.getDebugHud().shouldShowDebugHud()) {
            return true;
        }

        // If in a screen and screen pacing is disabled, render unpaced
        if (client.currentScreen != null && !config.enableScreenPacing) {
            return true;
        }

        Window window = client.getWindow();
        if (window == null) return true;

        int width = window.getFramebufferWidth();
        int height = window.getFramebufferHeight();
        if (lastCapturedWidth != width || lastCapturedHeight != height) {
            ensureFramebuffer(width, height);
            return true;
        }

        if (!hasValidCache) {
            return true;
        }

        // Dynamic reactive trigger only when taking health damage so hotbar scrolling & animations visibly pace at target FPS
        if (config.dynamicHudUpdates && client.player != null) {
            float health = client.player.getHealth();
            if (lastHealth >= 0.0f && health < lastHealth) {
                dirty = true;
            }
            lastHealth = health;
        }

        if (dirty) {
            dirty = false;
            return true;
        }

        long now = System.nanoTime();
        int targetFps = (client.currentScreen != null && config.enableScreenPacing)
                ? config.screenTargetFps
                : config.hudTargetFps;
        long intervalNs = 1_000_000_000L / Math.max(1, targetFps);
        return (now - lastHudFrameTime >= intervalNs);
    }

    public boolean hasValidCachedFrame() {
        return hasValidCache;
    }

    public void beginHudCapture() {
        this.isCapturing = true;
    }

    public void endHudCapture() {
        this.isCapturing = false;
        this.lastHudFrameTime = System.nanoTime();
    }

    public void endHudCapture(GuiRenderState state) {
        this.isCapturing = false;
        this.lastHudFrameTime = System.nanoTime();
        if (state == null) {
            return;
        }

        cachedSimpleElements.clear();
        cachedItemElements.clear();
        cachedTextElements.clear();
        cachedSpecialElements.clear();

        state.forEachSimpleElement(cachedSimpleElements::add, GuiRenderState.LayerFilter.ALL);
        state.forEachItemElement(cachedItemElements::add);
        state.forEachTextElement(cachedTextElements::add);
        state.forEachSpecialElement(cachedSpecialElements::add);

        this.hasValidCache = true;
    }

    public void replayCachedHud(GuiRenderState state) {
        if (state == null || !hasValidCache) {
            return;
        }

        for (int i = 0, size = cachedSimpleElements.size(); i < size; i++) {
            state.addSimpleElement(cachedSimpleElements.get(i));
        }
        if (!cachedItemElements.isEmpty()) {
            state.createNewRootLayer();
            for (int i = 0, size = cachedItemElements.size(); i < size; i++) {
                state.addItem(cachedItemElements.get(i));
            }
        }
        if (!cachedTextElements.isEmpty()) {
            state.createNewRootLayer();
            for (int i = 0, size = cachedTextElements.size(); i < size; i++) {
                state.addText(cachedTextElements.get(i));
            }
        }
        if (!cachedSpecialElements.isEmpty()) {
            state.createNewRootLayer();
            for (int i = 0, size = cachedSpecialElements.size(); i < size; i++) {
                state.addSpecialElement(cachedSpecialElements.get(i));
            }
        }
        state.createNewRootLayer();
    }

    public void renderCachedHud() {
        // Legacy no-op kept for API compatibility
    }

    public void cleanup() {
        cachedSimpleElements.clear();
        cachedItemElements.clear();
        cachedTextElements.clear();
        cachedSpecialElements.clear();
        hasValidCache = false;
        if (guiFramebuffer != null) {
            try {
                guiFramebuffer.delete();
            } catch (Throwable ignored) {
            }
            guiFramebuffer = null;
        }
    }
}
