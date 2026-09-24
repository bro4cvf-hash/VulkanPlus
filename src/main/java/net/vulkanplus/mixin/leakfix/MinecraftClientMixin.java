package net.vulkanplus.mixin.leakfix;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MemoryLeakFix: In vanilla Minecraft, MinecraftClient.targetedEntity and crosshairTarget
 * retain references to the last hovered entity. When screens open (inventory, pause menu)
 * or when disconnecting, this reference prevents the entity, its world, and its chunk
 * from being garbage-collected.
 *
 * Clearing targetedEntity and crosshairTarget on screen change and disconnect ensures
 * immediate garbage collection of dead/unloaded entities.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow
    @Nullable
    public Entity targetedEntity;

    @Shadow
    @Nullable
    public HitResult crosshairTarget;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableMemoryLeakFix) {
            this.targetedEntity = null;
            this.crosshairTarget = null;
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V", at = @At("HEAD"))
    private void onDisconnect(Screen screen, boolean transferring, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableMemoryLeakFix) {
            this.targetedEntity = null;
            this.crosshairTarget = null;
        }
    }
}
