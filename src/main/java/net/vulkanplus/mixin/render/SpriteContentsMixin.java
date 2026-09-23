package net.vulkanplus.mixin.render;

import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.resource.metadata.TextureResourceMetadata;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteDimensions;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.util.Identifier;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.render.LeafTextureHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(SpriteContents.class)
public class SpriteContentsMixin {

    @Inject(
            method = "<init>(Lnet/minecraft/util/Identifier;Lnet/minecraft/client/texture/SpriteDimensions;Lnet/minecraft/client/texture/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V",
            at = @At("HEAD")
    )
    private static void makeLeafSpriteOpaque(Identifier id, SpriteDimensions dimensions, NativeImage image,
                                             Optional<AnimationResourceMetadata> animationMetadata,
                                             List<ResourceMetadataSerializer.Value<?>> metadata,
                                             Optional<TextureResourceMetadata> textureMetadata,
                                             CallbackInfo ci) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config != null && config.enabled && config.opaqueLeaves) {
            if (LeafTextureHelper.isLeafTexture(id)) {
                LeafTextureHelper.makeOpaque(image);
            }
        }
    }
}
