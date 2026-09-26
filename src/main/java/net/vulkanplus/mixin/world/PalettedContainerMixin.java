package net.vulkanplus.mixin.world;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.world.chunk.PalettedContainer;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.duck.PalettedContainerDuck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin<T> implements PalettedContainerDuck<T> {
    @Shadow public abstract T get(int x, int y, int z);
    @Shadow public abstract int getElementBits();

    @Unique
    private volatile T vulkanplus$cachedUniform;
    @Unique
    private volatile boolean vulkanplus$uniformDetermined = false;

    @Override
    public T vulkanplus$getCachedUniform() {
        if (!this.vulkanplus$uniformDetermined) {
            vulkanplus$updateUniformCache();
        }
        return this.vulkanplus$cachedUniform;
    }

    @Override
    public void vulkanplus$setCachedUniform(T value) {
        this.vulkanplus$cachedUniform = value;
        this.vulkanplus$uniformDetermined = (value != null);
    }

    @Override
    public boolean vulkanplus$isUniformCached() {
        if (!this.vulkanplus$uniformDetermined) {
            vulkanplus$updateUniformCache();
        }
        return this.vulkanplus$cachedUniform != null;
    }

    @Override
    public void vulkanplus$invalidateUniformCache() {
        this.vulkanplus$cachedUniform = null;
        this.vulkanplus$uniformDetermined = false;
    }

    @Unique
    private static final ThreadLocal<Boolean> VULKANPLUS$UPDATING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Unique
    private void vulkanplus$updateUniformCache() {
        if (Boolean.TRUE.equals(VULKANPLUS$UPDATING.get())) {
            return;
        }
        VULKANPLUS$UPDATING.set(Boolean.TRUE);
        try {
            if (this.getElementBits() == 0) {
                this.vulkanplus$uniformDetermined = true;
                this.vulkanplus$cachedUniform = this.get(0, 0, 0);
            } else {
                this.vulkanplus$cachedUniform = null;
                this.vulkanplus$uniformDetermined = true;
            }
        } finally {
            VULKANPLUS$UPDATING.set(Boolean.FALSE);
        }
    }

    @Inject(method = "get(III)Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$fastGet3D(int x, int y, int z, CallbackInfoReturnable<T> cir) {
        if (Boolean.TRUE.equals(VULKANPLUS$UPDATING.get())) {
            return;
        }
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableFastWorldGen) {
            if (!this.vulkanplus$uniformDetermined) {
                vulkanplus$updateUniformCache();
            }
            T uniform = this.vulkanplus$cachedUniform;
            if (uniform != null) {
                cir.setReturnValue(uniform);
            }
        }
    }

    @Inject(method = "get(I)Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$fastGet1D(int index, CallbackInfoReturnable<T> cir) {
        if (Boolean.TRUE.equals(VULKANPLUS$UPDATING.get())) {
            return;
        }
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg != null && cfg.enabled && cfg.enableFastWorldGen) {
            if (!this.vulkanplus$uniformDetermined) {
                vulkanplus$updateUniformCache();
            }
            T uniform = this.vulkanplus$cachedUniform;
            if (uniform != null) {
                cir.setReturnValue(uniform);
            }
        }
    }

    @Inject(method = "set(IIILjava/lang/Object;)V", at = @At("HEAD"))
    private void vulkanplus$onSet3D(int x, int y, int z, T value, CallbackInfo ci) {
        vulkanplus$invalidateUniformCache();
    }

    @Inject(method = "set(ILjava/lang/Object;)V", at = @At("HEAD"))
    private void vulkanplus$onSet1D(int index, T value, CallbackInfo ci) {
        vulkanplus$invalidateUniformCache();
    }

    @Inject(method = "swap(IIILjava/lang/Object;)Ljava/lang/Object;", at = @At("HEAD"))
    private void vulkanplus$onSwap3D(int x, int y, int z, T value, CallbackInfoReturnable<T> cir) {
        vulkanplus$invalidateUniformCache();
    }

    @Inject(method = "swap(ILjava/lang/Object;)Ljava/lang/Object;", at = @At("HEAD"))
    private void vulkanplus$onSwap1D(int index, T value, CallbackInfoReturnable<T> cir) {
        vulkanplus$invalidateUniformCache();
    }

    @Inject(method = "onResize", at = @At("HEAD"))
    private void vulkanplus$onResize(int newBits, T value, CallbackInfoReturnable<Integer> cir) {
        vulkanplus$invalidateUniformCache();
    }

    @Inject(method = "readPacket", at = @At("RETURN"))
    private void vulkanplus$onReadPacket(PacketByteBuf buf, CallbackInfo ci) {
        vulkanplus$invalidateUniformCache();
    }

    @Override
    public T vulkanplus$fastGet(int x, int y, int z) {
        if (!this.vulkanplus$uniformDetermined) {
            vulkanplus$updateUniformCache();
        }
        T uniform = this.vulkanplus$cachedUniform;
        if (uniform != null) {
            return uniform;
        }
        return this.get(x, y, z);
    }
}
