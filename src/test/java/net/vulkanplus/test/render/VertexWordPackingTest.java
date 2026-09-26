package net.vulkanplus.test.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VertexWordPackingTest {

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    @Test
    @DisplayName("Verify putColor word-packing via BSWAP + ROR intrinsics")
    void testPutColorPacking() {
        int a = 0xAA;
        int r = 0x11;
        int g = 0x22;
        int b = 0x33;
        int argb = (a << 24) | (r << 16) | (g << 8) | b;

        long ptr = MemoryUtil.nmemAlloc(4);
        try {
            int packed = LITTLE_ENDIAN
                    ? Integer.rotateRight(Integer.reverseBytes(argb), 8)
                    : Integer.reverseBytes(Integer.rotateRight(Integer.reverseBytes(argb), 8));
            MemoryUtil.memPutInt(ptr, packed);

            assertEquals((byte) r, MemoryUtil.memGetByte(ptr));
            assertEquals((byte) g, MemoryUtil.memGetByte(ptr + 1L));
            assertEquals((byte) b, MemoryUtil.memGetByte(ptr + 2L));
            assertEquals((byte) a, MemoryUtil.memGetByte(ptr + 3L));
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    @Test
    @DisplayName("Verify color(r, g, b, a) 4-byte packing into 1 memPutInt")
    void testColorFourBytePacking() {
        int r = 0x45;
        int g = 0x67;
        int b = 0x89;
        int a = 0xAB;

        long ptr = MemoryUtil.nmemAlloc(4);
        try {
            int rgba = (r & 0xFF) | ((g & 0xFF) << 8) | ((b & 0xFF) << 16) | ((a & 0xFF) << 24);
            MemoryUtil.memPutInt(ptr, LITTLE_ENDIAN ? rgba : Integer.reverseBytes(rgba));

            assertEquals((byte) r, MemoryUtil.memGetByte(ptr));
            assertEquals((byte) g, MemoryUtil.memGetByte(ptr + 1L));
            assertEquals((byte) b, MemoryUtil.memGetByte(ptr + 2L));
            assertEquals((byte) a, MemoryUtil.memGetByte(ptr + 3L));
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    @Test
    @DisplayName("Verify texture(u, v) two float packing into 1 memPutLong")
    void testTextureTwoFloatPacking() {
        float u = 0.625f;
        float v = 0.875f;

        long ptr = MemoryUtil.nmemAlloc(8);
        try {
            int uBits = Float.floatToRawIntBits(u);
            int vBits = Float.floatToRawIntBits(v);
            long uv = LITTLE_ENDIAN
                    ? (uBits & 0xFFFFFFFFL) | (((long) vBits) << 32)
                    : (((long) uBits) << 32) | (vBits & 0xFFFFFFFFL);
            MemoryUtil.memPutLong(ptr, uv);

            assertEquals(u, MemoryUtil.memGetFloat(ptr), 1e-6f);
            assertEquals(v, MemoryUtil.memGetFloat(ptr + 4L), 1e-6f);
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    @Test
    @DisplayName("Verify uv(u, v) two short packing into 1 memPutInt")
    void testUvTwoShortPacking() {
        short u = (short) 0x1A2B;
        short v = (short) 0x3C4D;

        long ptr = MemoryUtil.nmemAlloc(4);
        try {
            int uv = LITTLE_ENDIAN
                    ? (u & 0xFFFF) | ((v & 0xFFFF) << 16)
                    : ((u & 0xFFFF) << 16) | (v & 0xFFFF);
            MemoryUtil.memPutInt(ptr, uv);

            assertEquals(u, MemoryUtil.memGetShort(ptr));
            assertEquals(v, MemoryUtil.memGetShort(ptr + 2L));
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }
}
