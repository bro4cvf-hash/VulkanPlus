package net.vulkanplus.mixin.exordium;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.vulkanplus.exordium.ExordiumManager;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void onKey(long window, int key, KeyInput input, CallbackInfo ci) {
        if (vulkanplus$isHudRelevantKey(key, input)) {
            ExordiumManager.getInstance().onHotbarInput();
        } else {
            ExordiumManager.getInstance().onInputActivity();
        }
    }

    @Unique
    private static boolean vulkanplus$isHudRelevantKey(int key, KeyInput input) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.options != null && input != null) {
                GameOptions opts = client.options;
                if (opts.hotbarKeys != null) {
                    for (KeyBinding hotbarKey : opts.hotbarKeys) {
                        if (hotbarKey != null && hotbarKey.matchesKey(input)) {
                            return true;
                        }
                    }
                }
                if ((opts.swapHandsKey != null && opts.swapHandsKey.matchesKey(input))
                        || (opts.dropKey != null && opts.dropKey.matchesKey(input))
                        || (opts.inventoryKey != null && opts.inventoryKey.matchesKey(input))
                        || (opts.chatKey != null && opts.chatKey.matchesKey(input))
                        || (opts.commandKey != null && opts.commandKey.matchesKey(input))
                        || (opts.playerListKey != null && opts.playerListKey.matchesKey(input))) {
                    return true;
                }
                // Ignore continuous movement keys so walking/running doesn't bypass HUD pacing;
                // treat any other custom or modded keybind press as HUD-relevant.
                if ((opts.forwardKey != null && opts.forwardKey.matchesKey(input))
                        || (opts.backKey != null && opts.backKey.matchesKey(input))
                        || (opts.leftKey != null && opts.leftKey.matchesKey(input))
                        || (opts.rightKey != null && opts.rightKey.matchesKey(input))
                        || (opts.jumpKey != null && opts.jumpKey.matchesKey(input))
                        || (opts.sneakKey != null && opts.sneakKey.matchesKey(input))
                        || (opts.sprintKey != null && opts.sprintKey.matchesKey(input))) {
                    return false;
                }
                return true;
            }
        } catch (Throwable ignored) {
        }
        return (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9)
                || key == GLFW.GLFW_KEY_F
                || key == GLFW.GLFW_KEY_Q
                || key == GLFW.GLFW_KEY_E;
    }
}
