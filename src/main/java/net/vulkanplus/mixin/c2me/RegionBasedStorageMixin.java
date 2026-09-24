package net.vulkanplus.mixin.c2me;

import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.StorageKey;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/**
 * C2ME Optimization: Disables synchronous disk writes (dsync) in RegionBasedStorage.
 *
 * Vanilla Minecraft enables synchronous writes (O_SYNC / fsync) on region files,
 * causing 5-30ms disk I/O freezes on the server/client thread whenever chunks are saved.
 * Disabling dsync lets the OS page cache handle flushing asynchronously and eliminates
 * chunk saving stutters.
 */
@Mixin(RegionBasedStorage.class)
public class RegionBasedStorageMixin {

    @Mutable
    @Shadow
    @Final
    private boolean dsync;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(StorageKey storageKey, Path directory, boolean dsync, CallbackInfo ci) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableC2MeOptimizations) {
            this.dsync = false;
        }
    }
}
