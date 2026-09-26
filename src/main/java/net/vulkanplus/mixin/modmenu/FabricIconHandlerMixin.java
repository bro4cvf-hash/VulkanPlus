package net.vulkanplus.mixin.modmenu;

import com.terraformersmc.modmenu.ModMenu;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

@Mixin(targets = "com.terraformersmc.modmenu.util.mod.fabric.FabricIconHandler", remap = false)
public abstract class FabricIconHandlerMixin {

    @Shadow
    abstract NativeImageBackedTexture getCachedModIcon(Path path);

    @Shadow
    abstract void cacheModIcon(Path path, NativeImageBackedTexture tex);

    @Inject(method = "createIcon", at = @At("HEAD"), cancellable = true, require = 0)
    private void vulcanplus$sanitizeWindowsIconPath(ModContainer iconSource, String iconPath, CallbackInfoReturnable<NativeImageBackedTexture> cir) {
        if (iconSource == null || iconPath == null || iconPath.isEmpty() || cir == null) {
            return;
        }
        try {
            Path path = iconSource.getPath(iconPath);
            if (path == null) {
                return;
            }
            NativeImageBackedTexture cachedIcon = getCachedModIcon(path);
            if (cachedIcon != null) {
                cir.setReturnValue(cachedIcon);
                return;
            }

            try (InputStream inputStream = Files.newInputStream(path)) {
                NativeImage image = NativeImage.read(Objects.requireNonNull(inputStream));
                if (image == null) {
                    return;
                }
                Validate.validState(image.getHeight() == image.getWidth(), "Must be square icon");

                // Minecraft 1.21+ strictly validates identifier paths against [a-z0-9/._-].
                // On Windows development environments, Path.toString() produces backslashes ('\')
                // which causes InvalidIdentifierException in Mod Menu. This sanitizes the path.
                String rawPath = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                String safePath = rawPath.replaceAll("[^a-z0-9/._-]", "_");
                while (safePath.startsWith("/")) {
                    safePath = safePath.substring(1);
                }
                if (safePath.isEmpty()) {
                    safePath = "icon";
                }

                final String finalPath = safePath;
                NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> Identifier.of(ModMenu.MOD_ID, finalPath).toString(), image);
                cacheModIcon(path, tex);
                cir.setReturnValue(tex);
            }
        } catch (Throwable ignored) {
            // Fall back to original method execution if anything unexpected occurs
        }
    }
}
