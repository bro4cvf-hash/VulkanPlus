package net.vulkanplus.test.render;

import net.minecraft.client.texture.NativeImage;
import net.vulkanplus.render.LeafTextureHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LeafTextureHelperTest {

    @Test
    @DisplayName("LeafTextureHelper correctly identifies leaf texture paths")
    public void testLeafPathIdentification() {
        assertTrue(LeafTextureHelper.isLeafPath("block/oak_leaves"));
        assertTrue(LeafTextureHelper.isLeafPath("block/birch_leaves"));
        assertTrue(LeafTextureHelper.isLeafPath("block/spruce_leaves"));
        assertTrue(LeafTextureHelper.isLeafPath("block/dark_oak_leaves"));
        assertTrue(LeafTextureHelper.isLeafPath("block/jungle_leaves"));
        assertTrue(LeafTextureHelper.isLeafPath("block/acacia_leaves"));
        assertTrue(LeafTextureHelper.isLeafPath("block/mangrove_leaves"));
        assertTrue(LeafTextureHelper.isLeafPath("block/cherry_leaves"));
        assertTrue(LeafTextureHelper.isLeafPath("block/azalea_leaves"));
        assertTrue(LeafTextureHelper.isLeafPath("block/flowering_azalea_leaves"));
        assertTrue(LeafTextureHelper.isLeafPath("block/pale_oak_leaves"));

        assertTrue(LeafTextureHelper.isLeafPath("textures/block/maple_leaves"));
        assertTrue(LeafTextureHelper.isLeafPath("block/fir_leaves"));

        assertFalse(LeafTextureHelper.isLeafPath("block/stone"));
        assertFalse(LeafTextureHelper.isLeafPath("block/oak_log"));
        assertFalse(LeafTextureHelper.isLeafPath("block/oak_planks"));
        assertFalse(LeafTextureHelper.isLeafPath("item/oak_leaves"));
        assertFalse(LeafTextureHelper.isLeafPath(null));
    }

    @Test
    @DisplayName("makeOpaque eliminates all transparency and sets 50% brightness foliage color on NativeImage")
    public void testMakeOpaqueTransformation() {
        int width = 16;
        int height = 16;

        int leafColor = (0xFF << 24) | (100 << 16) | (180 << 8) | 80;
        int transparentColor = 0x00000000;

        try (NativeImage img = new NativeImage(width, height, false)) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((x + y) % 2 == 0) {
                        img.setColorArgb(x, y, leafColor);
                    } else {
                        img.setColorArgb(x, y, transparentColor);
                    }
                }
            }

            LeafTextureHelper.makeOpaque(img);

            int expectedFillR = (int) (100 * 0.50f);
            int expectedFillG = (int) (180 * 0.50f);
            int expectedFillB = (int) (80 * 0.50f);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = img.getColorArgb(x, y);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;

                    assertEquals(255, a, "Pixel (" + x + "," + y + ") must be completely opaque");

                    if ((x + y) % 2 == 0) {
                        assertEquals(100, r);
                        assertEquals(180, g);
                        assertEquals(80, b);
                    } else {
                        assertEquals(expectedFillR, r);
                        assertEquals(expectedFillG, g);
                        assertEquals(expectedFillB, b);
                    }
                }
            }
        }
    }
}
