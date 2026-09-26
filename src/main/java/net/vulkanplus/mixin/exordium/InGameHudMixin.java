package net.vulkanplus.mixin.exordium;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.RenderTickCounter;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.exordium.ExordiumManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InGameHud.class, priority = 2000)
public abstract class InGameHudMixin {

    @Unique
    private boolean vulkanplus$renderingUncachedCrosshair = false;

    @Shadow
    protected abstract void renderCrosshair(DrawContext context, RenderTickCounter tickCounter);

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshairHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ExordiumManager manager = ExordiumManager.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        if (manager.isExordiumActive()
                && manager.isCapturing()
                && !this.vulkanplus$renderingUncachedCrosshair
                && this.vulkanplus$shouldRenderDecoupledCrosshair(client)) {
            // Skip baking the crosshair into the cached HUD state so it renders at full native FPS
            ci.cancel();
        }
    }

    @Unique
    private boolean vulkanplus$shouldRenderDecoupledCrosshair(MinecraftClient client) {
        if (!ConfigManager.getConfig().separateCrosshair) {
            return false;
        }
        if (client == null || client.options == null || client.options.hudHidden) {
            return false;
        }
        if (client.currentScreen != null) {
            return false;
        }
        if (client.inGameHud != null && client.inGameHud.getDebugHud() != null
                && client.inGameHud.getDebugHud().shouldShowDebugHud()) {
            return false;
        }
        return true;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        // Always record 3D world frame timing and reset per-frame overlay guard before Exordium HUD throttling
        net.vulkanplus.VulkanPlusMod.beginHudFrame();

        ExordiumManager manager = ExordiumManager.getInstance();
        if (!manager.isExordiumActive()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden) {
            return;
        }

        if (ConfigManager.getConfig().bypassInDebugScreen && client.getDebugHud() != null && client.getDebugHud().shouldShowDebugHud()) {
            return;
        }

        GuiRenderState state = ((DrawContextAccessor) context).vulkanplus$getState();
        if (!manager.shouldRenderHud(tickCounter)) {
            if (manager.hasValidCachedFrame()) {
                manager.replayCachedHud(state);
                if (this.vulkanplus$shouldRenderDecoupledCrosshair(client)) {
                    this.vulkanplus$renderingUncachedCrosshair = true;
                    try {
                        this.renderCrosshair(context, tickCounter);
                    } finally {
                        this.vulkanplus$renderingUncachedCrosshair = false;
                    }
                }
                net.vulkanplus.VulkanPlusMod.renderDiagnosticsAndFpsOverlay(context);
                ci.cancel();
            }
            return;
        }

        manager.beginHudCapture(state);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderReturn(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ExordiumManager manager = ExordiumManager.getInstance();
        if (manager.isCapturing()) {
            GuiRenderState state = ((DrawContextAccessor) context).vulkanplus$getState();
            manager.endHudCapture(state);
            MinecraftClient client = MinecraftClient.getInstance();
            if (this.vulkanplus$shouldRenderDecoupledCrosshair(client)) {
                this.vulkanplus$renderingUncachedCrosshair = true;
                try {
                    this.renderCrosshair(context, tickCounter);
                } finally {
                    this.vulkanplus$renderingUncachedCrosshair = false;
                }
            }
        }
        net.vulkanplus.VulkanPlusMod.renderDiagnosticsAndFpsOverlay(context);
    }
}
