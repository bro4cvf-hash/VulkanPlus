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

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Unique
    private boolean vulkanplus$renderingUncachedCrosshair = false;

    @Shadow
    protected abstract void renderCrosshair(DrawContext context, RenderTickCounter tickCounter);

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshairHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ExordiumManager manager = ExordiumManager.getInstance();
        if (manager.isExordiumActive()
                && manager.isCapturing()
                && !this.vulkanplus$renderingUncachedCrosshair
                && ConfigManager.getConfig().separateCrosshair) {
            // Skip baking the crosshair into the cached HUD state so it renders at full native FPS
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
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

        if (!manager.shouldRenderHud(tickCounter)) {
            if (manager.hasValidCachedFrame()) {
                GuiRenderState state = ((DrawContextAccessor) context).vulkanplus$getState();
                manager.replayCachedHud(state);
                if (ConfigManager.getConfig().separateCrosshair) {
                    this.vulkanplus$renderingUncachedCrosshair = true;
                    try {
                        this.renderCrosshair(context, tickCounter);
                    } finally {
                        this.vulkanplus$renderingUncachedCrosshair = false;
                    }
                }
                ci.cancel();
            }
            return;
        }

        manager.beginHudCapture();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ExordiumManager manager = ExordiumManager.getInstance();
        if (manager.isCapturing()) {
            GuiRenderState state = ((DrawContextAccessor) context).vulkanplus$getState();
            manager.endHudCapture(state);
            if (ConfigManager.getConfig().separateCrosshair) {
                this.vulkanplus$renderingUncachedCrosshair = true;
                try {
                    this.renderCrosshair(context, tickCounter);
                } finally {
                    this.vulkanplus$renderingUncachedCrosshair = false;
                }
            }
        }
    }
}
