package net.vulkanplus.render;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

/**
 * Utility for detecting leaf block textures and removing transparency by filling
 * cutout areas with complementary foliage shading, matching FastLeaves pack aesthetics.
 */
public class LeafTextureHelper {

    /**
     * Determines whether the given texture identifier belongs to a leaf block.
     */
    public static boolean isLeafTexture(Identifier id) {
        if (id == null) return false;
        String path = id.getPath();
        return isLeafPath(path);
    }

    /**
     * Determines whether the texture path corresponds to leaves.
     */
    public static boolean isLeafPath(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        return (lower.startsWith("block/") || lower.startsWith("textures/block/")) &&
                (lower.contains("leaves") || lower.endsWith("_leaf") || lower.contains("_leaves"));
    }

    /**
     * Modifies a NativeImage in-place to remove transparency:
     * - Transparent cutout pixels (alpha <= 32) are replaced with 50% brightness foliage color at 100% opacity.
     * - Semi-transparent and opaque pixels are forced to 100% opacity (alpha = 255).
     */
    public static void makeOpaque(NativeImage image) {
        if (image == null) return;
        int width = image.getWidth();
        int height = image.getHeight();

        long rSum = 0, gSum = 0, bSum = 0;
        int count = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getColorArgb(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a > 32) {
                    rSum += (argb >> 16) & 0xFF;
                    gSum += (argb >> 8) & 0xFF;
                    bSum += argb & 0xFF;
                    count++;
                }
            }
        }

        int fillR = count > 0 ? (int) ((rSum / count) * 0.50f) : 0x48;
        int fillG = count > 0 ? (int) ((gSum / count) * 0.50f) : 0x48;
        int fillB = count > 0 ? (int) ((bSum / count) * 0.50f) : 0x48;
        int fillColor = (0xFF << 24) | (fillR << 16) | (fillG << 8) | fillB;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getColorArgb(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a <= 32) {
                    image.setColorArgb(x, y, fillColor);
                } else {
                    image.setColorArgb(x, y, (0xFF << 24) | (argb & 0x00FFFFFF));
                }
            }
        }
    }
}
