package net.vulkanplus.vulkan;

import net.vulkanplus.VulkanPlusMod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages persistent on-disk storage and validation for Vulkan Pipeline State Objects (PSO).
 * Saves VkPipelineCache data across game sessions to eliminate first-time shader compilation stutter.
 */
public class PersistentPipelineCache {
    public static final String CACHE_FILE_NAME = "vulkanplus_pso_cache.bin";
    private static final Path CACHE_PATH = Paths.get("config", CACHE_FILE_NAME);
    public static final int HEADER_SIZE = 32;

    private boolean isLoaded = false;
    private int cachedPipelineCount = 0;

    /**
     * Reads and validates cached pipeline data from disk.
     * Header layout:
     * - [0..3]: Header length (32 bytes)
     * - [4..7]: Header version (VK_PIPELINE_CACHE_HEADER_VERSION_ONE = 1)
     * - [8..11]: Vendor ID
     * - [12..15]: Device ID
     * - [16..31]: Pipeline cache UUID (16 bytes)
     */
    public ByteBuffer loadCacheData(int expectedVendorId, int expectedDeviceId, byte[] expectedUuid) {
        if (!Files.exists(CACHE_PATH)) {
            VulkanPlusMod.LOGGER.info("[VulkanPlus] No existing PSO cache found at {}. A new cache will be built.", CACHE_PATH);
            return null;
        }

        try {
            byte[] bytes = Files.readAllBytes(CACHE_PATH);
            if (bytes.length < HEADER_SIZE) {
                VulkanPlusMod.LOGGER.warn("[VulkanPlus] Corrupted PSO cache header (size < 32 bytes). Discarding.");
                return null;
            }

            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int headerLength = buffer.getInt(0);
            int headerVersion = buffer.getInt(4);
            int vendorId = buffer.getInt(8);
            int deviceId = buffer.getInt(12);

            if (headerLength != HEADER_SIZE || headerVersion != 1) {
                VulkanPlusMod.LOGGER.warn("[VulkanPlus] Invalid PSO cache header format. Discarding.");
                return null;
            }

            if (vendorId != expectedVendorId || deviceId != expectedDeviceId) {
                VulkanPlusMod.LOGGER.info("[VulkanPlus] Graphics hardware changed (Vendor/Device ID mismatch). Invalidating cache.");
                return null;
            }

            if (expectedUuid != null && expectedUuid.length == 16) {
                byte[] fileUuid = new byte[16];
                buffer.position(16);
                buffer.get(fileUuid);
                for (int i = 0; i < 16; i++) {
                    if (fileUuid[i] != expectedUuid[i]) {
                        VulkanPlusMod.LOGGER.info("[VulkanPlus] Driver version or GPU UUID changed. Invalidating cache.");
                        return null;
                    }
                }
            }

            buffer.position(0);
            isLoaded = true;
            VulkanPlusMod.LOGGER.info("[VulkanPlus] Successfully validated and loaded {} KB of persistent PSO cache.", bytes.length / 1024);
            return buffer;
        } catch (IOException e) {
            VulkanPlusMod.LOGGER.error("[VulkanPlus] Failed to read PSO cache from disk.", e);
            return null;
        }
    }

    /**
     * Persists pipeline cache binary data to disk with validation header.
     */
    public boolean saveCacheData(byte[] data) {
        if (data == null || data.length < HEADER_SIZE) {
            return false;
        }

        try {
            if (CACHE_PATH.getParent() != null && !Files.exists(CACHE_PATH.getParent())) {
                Files.createDirectories(CACHE_PATH.getParent());
            }

            Files.write(CACHE_PATH, data);
            VulkanPlusMod.LOGGER.info("[VulkanPlus] Saved {} KB of PSO cache data to {}.", data.length / 1024, CACHE_PATH);
            return true;
        } catch (IOException e) {
            VulkanPlusMod.LOGGER.error("[VulkanPlus] Failed to write PSO cache to disk.", e);
            return false;
        }
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public int getCachedPipelineCount() {
        return cachedPipelineCount;
    }

    public void setCachedPipelineCount(int count) {
        this.cachedPipelineCount = count;
    }
}
