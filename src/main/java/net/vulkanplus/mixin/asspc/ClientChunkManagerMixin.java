package net.vulkanplus.mixin.asspc;

import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Eliminates light-triggered chunk section mesh rebuilds when Engine Fullbright is enabled.
 * Since all terrain quads are baked at maximum light (0xF000F0), sky and block light propagation
 * updates (torches, pistons, flowing fluids, redstone, day/night transitions) never require
 * rebuilding chunk geometry.
 */
@Mixin(ClientChunkManager.class)
public class ClientChunkManagerMixin {

    @Inject(method = "onLightUpdate", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$cancelLightUpdateChunkRebuild(LightType type, ChunkSectionPos pos, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.fullBright) {
            ci.cancel();
        }
    }
}
