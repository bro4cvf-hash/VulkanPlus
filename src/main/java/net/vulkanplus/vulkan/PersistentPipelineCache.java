package net.vulkanplus.vulkan;

import net.vulkanplus.VulkanPlusMod;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

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
     * Reads and validates cached pipeline data directly into an off-heap LWJGL ByteBuffer via FileChannel,
     * avoiding intermediate heap byte[] allocations. Caller must free the returned buffer via MemoryUtil.memFree().
     */
    public ByteBuffer loadCacheDataDirect(int expectedVendorId, int expectedDeviceId, byte[] expectedUuid) {
        if (!Files.exists(CACHE_PATH)) {
            VulkanPlusMod.LOGGER.info("[VulkanPlus] No existing PSO cache found at {}. A new cache will be built.", CACHE_PATH);
            return null;
        }

        ByteBuffer directBuf = null;
        try (FileChannel channel = FileChannel.open(CACHE_PATH, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (fileSize < HEADER_SIZE || fileSize > Integer.MAX_VALUE) {
                VulkanPlusMod.LOGGER.warn("[VulkanPlus] Corrupted PSO cache header (size={} bytes). Discarding.", fileSize);
                return null;
            }

            directBuf = MemoryUtil.memAlloc((int) fileSize).order(ByteOrder.LITTLE_ENDIAN);
            while (directBuf.hasRemaining()) {
                if (channel.read(directBuf) < 0) {
                    break;
                }
            }
            if (directBuf.position() < HEADER_SIZE) {
                MemoryUtil.memFree(directBuf);
                return null;
            }
            directBuf.flip();

            int headerLength = directBuf.getInt(0);
            int headerVersion = directBuf.getInt(4);
            int vendorId = directBuf.getInt(8);
            int deviceId = directBuf.getInt(12);

            if (headerLength != HEADER_SIZE || headerVersion != 1) {
                VulkanPlusMod.LOGGER.warn("[VulkanPlus] Invalid PSO cache header format. Discarding.");
                MemoryUtil.memFree(directBuf);
                return null;
            }

            if (vendorId != expectedVendorId || deviceId != expectedDeviceId) {
                VulkanPlusMod.LOGGER.info("[VulkanPlus] Graphics hardware changed (Vendor/Device ID mismatch). Invalidating cache.");
                MemoryUtil.memFree(directBuf);
                return null;
            }

            if (expectedUuid != null && expectedUuid.length == 16) {
                for (int i = 0; i < 16; i++) {
                    if (directBuf.get(16 + i) != expectedUuid[i]) {
                        VulkanPlusMod.LOGGER.info("[VulkanPlus] Driver version or GPU UUID changed. Invalidating cache.");
                        MemoryUtil.memFree(directBuf);
                        return null;
                    }
                }
            }

            directBuf.position(0);
            isLoaded = true;
            VulkanPlusMod.LOGGER.info("[VulkanPlus] Successfully validated and loaded {} KB of persistent PSO cache (zero-copy direct).", fileSize / 1024);
            return directBuf;
        } catch (IOException e) {
            if (directBuf != null) {
                MemoryUtil.memFree(directBuf);
            }
            VulkanPlusMod.LOGGER.error("[VulkanPlus] Failed to read direct PSO cache from disk.", e);
            return null;
        }
    }

    /**
     * Persists pipeline cache binary data to disk with validation header and atomic file replacement.
     */
    public boolean saveCacheData(byte[] data) {
        if (data == null || data.length < HEADER_SIZE) {
            return false;
        }

        try {
            if (CACHE_PATH.getParent() != null && !Files.exists(CACHE_PATH.getParent())) {
                Files.createDirectories(CACHE_PATH.getParent());
            }

            Path tempPath = CACHE_PATH.resolveSibling(CACHE_FILE_NAME + ".tmp");
            Files.write(tempPath, data);
            moveAtomicallyWithFallback(tempPath, CACHE_PATH);
            VulkanPlusMod.LOGGER.info("[VulkanPlus] Saved {} KB of PSO cache data to {}.", data.length / 1024, CACHE_PATH);
            return true;
        } catch (IOException e) {
            VulkanPlusMod.LOGGER.error("[VulkanPlus] Failed to write PSO cache to disk.", e);
            return false;
        }
    }

    /**
     * Persists off-heap direct ByteBuffer pipeline cache binary data directly to disk via FileChannel
     * and atomic temporary file rename, avoiding heap byte[] copies.
     */
    public boolean saveCacheDataDirect(ByteBuffer directBuffer) {
        if (directBuffer == null || directBuffer.remaining() < HEADER_SIZE) {
            return false;
        }

        try {
            if (CACHE_PATH.getParent() != null && !Files.exists(CACHE_PATH.getParent())) {
                Files.createDirectories(CACHE_PATH.getParent());
            }

            Path tempPath = CACHE_PATH.resolveSibling(CACHE_FILE_NAME + ".tmp");
            ByteBuffer view = directBuffer.slice();
            int totalBytes = view.remaining();
            try (FileChannel channel = FileChannel.open(
                    tempPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                while (view.hasRemaining()) {
                    channel.write(view);
                }
            }
            moveAtomicallyWithFallback(tempPath, CACHE_PATH);
            VulkanPlusMod.LOGGER.info("[VulkanPlus] Saved {} KB of PSO cache data (zero-copy direct) to {}.", totalBytes / 1024, CACHE_PATH);
            return true;
        } catch (IOException e) {
            VulkanPlusMod.LOGGER.error("[VulkanPlus] Failed to write direct PSO cache to disk.", e);
            return false;
        }
    }

    private static void moveAtomicallyWithFallback(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
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
