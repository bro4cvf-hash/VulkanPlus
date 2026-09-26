package net.vulkanplus.mixin.world;

import net.minecraft.util.collection.PaletteStorage;
import net.minecraft.world.chunk.Palette;
import net.minecraft.world.chunk.SingularPalette;
import net.vulkanplus.duck.PalettedContainerDataDuck;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.chunk.PalettedContainer$Data")
public abstract class PalettedContainerDataMixin<T> implements PalettedContainerDataDuck<T> {
    @Shadow @Final PaletteStorage storage;
    @Shadow @Final Palette<T> palette;

    @Override
    public boolean vulkanplus$isUniform() {
        return this.storage.getElementBits() == 0
                || this.palette instanceof SingularPalette
                || this.palette.getSize() <= 1;
    }

    @Override
    public T vulkanplus$getUniformValue() {
        if (vulkanplus$isUniform()) {
            return this.palette.get(0);
        }
        return null;
    }

    @Override
    public T vulkanplus$getFast(int index) {
        if (vulkanplus$isUniform()) {
            return this.palette.get(0);
        }
        return this.palette.get(this.storage.get(index));
    }
}
