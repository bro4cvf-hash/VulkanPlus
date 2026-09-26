package net.vulkanplus.exordium;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
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
import java.util.function.Consumer;

/**
 * Core engine for Exordium GUI & HUD framerate decoupling in Vulkan Plus.
 * Uses Minecraft 1.21.11's native GuiRenderState record snapshotting & replay to decouple
 * HUD CPU extraction and layout from 3D world rendering at the exact target FPS (e.g. 15-120 FPS)
 * with zero offscreen framebuffer blits and 100% compatibility with VulkanMod and Vanilla.
 */
public class ExordiumManager {
    private static final ExordiumManager INSTANCE = new ExordiumManager();

    private static final byte OP_SIMPLE = 0;
    private static final byte OP_ITEM = 1;
    private static final byte OP_TEXT = 2;
    private static final byte OP_SPECIAL = 3;
    private static final byte OP_NEW_ROOT_LAYER = 4;

    private boolean isCapturing = false;
    private GuiRenderState capturingState = null;
    private boolean hasValidCache = false;
    private long lastHudFrameTime = 0;
    private long hudFrameAccumulatorNs = 0;
    private int lastCapturedWidth = -1;
    private int lastCapturedHeight = -1;
    private volatile boolean dirty = true;
    private volatile boolean inputActive = false;

    // Pre-allocated chronological operation log for exact layer-preserving replay (zero per-frame heap allocations)
    private byte[] recordedOps = new byte[512];
    private Object[] recordedElements = new Object[512];
    private int recordedCount = 0;

    private final List<SimpleGuiElementRenderState> cachedSimpleElements = new ArrayList<>(256);
    private final List<ItemGuiElementRenderState> cachedItemElements = new ArrayList<>(64);
    private final List<TextGuiElementRenderState> cachedTextElements = new ArrayList<>(128);
    private final List<SpecialGuiElementRenderState> cachedSpecialElements = new ArrayList<>(16);

    // Pre-allocated Consumer references to eliminate 240 lambda allocations/sec on the render thread
    private final Consumer<SimpleGuiElementRenderState> simpleConsumer = cachedSimpleElements::add;
    private final Consumer<ItemGuiElementRenderState> itemConsumer = cachedItemElements::add;
    private final Consumer<TextGuiElementRenderState> textConsumer = cachedTextElements::add;
    private final Consumer<SpecialGuiElementRenderState> specialConsumer = cachedSpecialElements::add;

    private float lastHealth = -1.0f;
    private int lastSelectedSlot = -1;
    private Object lastScreen = null;

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

    public void onHotbarInput() {
        this.inputActive = true;
        this.dirty = true;
    }

    public void onInputActivity() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            VulkanPlusConfig config = ConfigManager.getConfig();
            // Only bypass pacing for cursor/movement input when an interactive screen (Inventory/Menu) is open,
            // or in headless test environments where client is null. Hotbar/scroll events use onHotbarInput().
            if (client == null || (client.currentScreen != null && config.instantInputResponsiveness)) {
                this.inputActive = true;
                this.dirty = true;
            }
        } catch (Throwable ignored) {
            this.inputActive = true;
            this.dirty = true;
        }
    }

    public boolean isCapturing() {
        return isCapturing;
    }

    public boolean isCapturingState(GuiRenderState state) {
        return isCapturing && state != null && (capturingState == null || capturingState == state);
    }

    private void appendRecordedOp(byte op, Object element) {
        int idx = this.recordedCount;
        if (idx >= this.recordedOps.length) {
            int newCap = this.recordedOps.length * 2;
            this.recordedOps = java.util.Arrays.copyOf(this.recordedOps, newCap);
            this.recordedElements = java.util.Arrays.copyOf(this.recordedElements, newCap);
        }
        this.recordedOps[idx] = op;
        this.recordedElements[idx] = element;
        this.recordedCount = idx + 1;
    }

    public void recordSimpleElement(SimpleGuiElementRenderState element) {
        if (isCapturing && element != null) {
            appendRecordedOp(OP_SIMPLE, element);
        }
    }

    public void recordItemElement(ItemGuiElementRenderState element) {
        if (isCapturing && element != null) {
            appendRecordedOp(OP_ITEM, element);
        }
    }

    public void recordTextElement(TextGuiElementRenderState element) {
        if (isCapturing && element != null) {
            appendRecordedOp(OP_TEXT, element);
        }
    }

    public void recordSpecialElement(SpecialGuiElementRenderState element) {
        if (isCapturing && element != null) {
            appendRecordedOp(OP_SPECIAL, element);
        }
    }

    public void recordCreateNewRootLayer() {
        if (isCapturing) {
            appendRecordedOp(OP_NEW_ROOT_LAYER, null);
        }
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

        if (client.currentScreen != lastScreen) {
            lastScreen = client.currentScreen;
            dirty = true;
            hasValidCache = false;
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

        // Dynamic reactive trigger when taking health damage or switching hotbar slots
        if (client.player != null) {
            if (config.dynamicHudUpdates) {
                float health = client.player.getHealth();
                if (lastHealth >= 0.0f && health < lastHealth) {
                    dirty = true;
                }
                lastHealth = health;
            }
            int slot = client.player.getInventory().getSelectedSlot();
            if (lastSelectedSlot >= 0 && slot != lastSelectedSlot) {
                this.inputActive = true;
                this.dirty = true;
            }
            lastSelectedSlot = slot;
        }

        if (this.inputActive) {
            this.inputActive = false;
            this.dirty = false;
            this.hudFrameAccumulatorNs = 0;
            this.lastHudFrameTime = System.nanoTime();
            return true;
        }

        if (dirty) {
            dirty = false;
            this.hudFrameAccumulatorNs = 0;
            this.lastHudFrameTime = System.nanoTime();
            return true;
        }

        long now = System.nanoTime();
        int targetFps = (client.currentScreen != null && config.enableScreenPacing)
                ? config.screenTargetFps
                : config.hudTargetFps;
        long intervalNs = 1_000_000_000L / Math.max(1, targetFps);
        if (lastHudFrameTime == 0L) {
            lastHudFrameTime = now;
            hudFrameAccumulatorNs = intervalNs;
            return true;
        }
        long elapsed = Math.max(0L, now - lastHudFrameTime);
        lastHudFrameTime = now;
        hudFrameAccumulatorNs += Math.min(elapsed, intervalNs * 4L);
        return hudFrameAccumulatorNs >= intervalNs;
    }

    public boolean hasValidCachedFrame() {
        return hasValidCache;
    }

    public void beginHudCapture() {
        beginHudCapture(null);
    }

    public void beginHudCapture(GuiRenderState state) {
        if (this.recordedCount > 0) {
            java.util.Arrays.fill(this.recordedElements, 0, this.recordedCount, null);
            this.recordedCount = 0;
        }
        this.capturingState = state;
        this.isCapturing = true;
    }

    private void consumeHudFrameInterval() {
        VulkanPlusConfig config = ConfigManager.getConfig();
        int targetFps = config != null ? config.hudTargetFps : 60;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.currentScreen != null && config != null && config.enableScreenPacing) {
                targetFps = config.screenTargetFps;
            }
        } catch (Throwable ignored) {
        }
        long intervalNs = 1_000_000_000L / Math.max(1, targetFps);
        if (hudFrameAccumulatorNs >= intervalNs) {
            hudFrameAccumulatorNs %= intervalNs;
        }
        if (lastHudFrameTime == 0L) {
            lastHudFrameTime = System.nanoTime();
        }
    }

    public void endHudCapture() {
        this.isCapturing = false;
        this.capturingState = null;
        consumeHudFrameInterval();
    }

    public void endHudCapture(GuiRenderState state) {
        this.isCapturing = false;
        this.capturingState = null;
        consumeHudFrameInterval();
        if (state == null) {
            return;
        }

        cachedSimpleElements.clear();
        cachedItemElements.clear();
        cachedTextElements.clear();
        cachedSpecialElements.clear();

        if (this.recordedCount == 0) {
            state.forEachSimpleElement(this.simpleConsumer, GuiRenderState.LayerFilter.ALL);
            state.forEachItemElement(this.itemConsumer);
            state.forEachTextElement(this.textConsumer);
            state.forEachSpecialElement(this.specialConsumer);
        }

        this.hasValidCache = true;
    }

    public void replayCachedState(GuiRenderState state) {
        replayCachedHud(state);
    }

    public void replayCachedHud(GuiRenderState state) {
        if (state == null || !hasValidCache) {
            return;
        }

        if (this.recordedCount > 0) {
            for (int i = 0, n = this.recordedCount; i < n; i++) {
                byte op = this.recordedOps[i];
                Object elem = this.recordedElements[i];
                switch (op) {
                    case OP_SIMPLE -> state.addSimpleElement((SimpleGuiElementRenderState) elem);
                    case OP_ITEM -> state.addItem((ItemGuiElementRenderState) elem);
                    case OP_TEXT -> state.addText((TextGuiElementRenderState) elem);
                    case OP_SPECIAL -> state.addSpecialElement((SpecialGuiElementRenderState) elem);
                    case OP_NEW_ROOT_LAYER -> state.createNewRootLayer();
                }
            }
            return;
        }

        for (int i = 0, n = cachedSimpleElements.size(); i < n; i++) {
            state.addSimpleElement(cachedSimpleElements.get(i));
        }
        for (int i = 0, n = cachedItemElements.size(); i < n; i++) {
            state.addItem(cachedItemElements.get(i));
        }
        for (int i = 0, n = cachedTextElements.size(); i < n; i++) {
            state.addText(cachedTextElements.get(i));
        }
        for (int i = 0, n = cachedSpecialElements.size(); i < n; i++) {
            state.addSpecialElement(cachedSpecialElements.get(i));
        }
    }

    public void reset() {
        cleanup();
    }

    public void cleanup() {
        if (this.recordedCount > 0) {
            java.util.Arrays.fill(this.recordedElements, 0, this.recordedCount, null);
            this.recordedCount = 0;
        }
        cachedSimpleElements.clear();
        cachedItemElements.clear();
        cachedTextElements.clear();
        cachedSpecialElements.clear();
        hasValidCache = false;
        isCapturing = false;
        capturingState = null;
        dirty = true;
        inputActive = false;
        lastHudFrameTime = 0L;
        hudFrameAccumulatorNs = 0L;
        lastHealth = -1.0f;
        lastSelectedSlot = -1;
        lastScreen = null;
    }
}
