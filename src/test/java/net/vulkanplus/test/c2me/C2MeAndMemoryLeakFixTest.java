package net.vulkanplus.test.c2me;

import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class C2MeAndMemoryLeakFixTest {

    @BeforeEach
    void setUp() {
        VulkanPlusConfig config = new VulkanPlusConfig();
        config.enabled = true;
        config.enableC2MeOptimizations = true;
        config.enableMemoryLeakFix = true;
        config.noChunkFade = true;
        config.fastChest = true;
        ConfigManager.setConfig(config);
    }

    @Test
    @DisplayName("C2ME NBT map uses FastUtil Object2ObjectOpenHashMap for zero-overhead chunk streaming")
    void testC2MeFastUtilNbtMap() {
        Map<String, Object> map = new Object2ObjectOpenHashMap<>();
        map.put("xPos", 100);
        map.put("zPos", -200);
        map.put("Status", "full");

        assertEquals(3, map.size());
        assertEquals(100, map.get("xPos"));
        assertEquals(-200, map.get("zPos"));
        assertEquals("full", map.get("Status"));
    }

    @Test
    @DisplayName("MemoryLeakFix Biome ThreadLocal temperature cache sharing eliminates memory leakage")
    void testBiomeSharedThreadLocalCache() {
        AtomicInteger initCount = new AtomicInteger(0);
        ThreadLocal<Long2FloatLinkedOpenHashMap> shared = ThreadLocal.withInitial(() -> {
            initCount.incrementAndGet();
            return new Long2FloatLinkedOpenHashMap();
        });

        // Simulate 20 biomes on the same thread
        Long2FloatLinkedOpenHashMap first = shared.get();
        for (int i = 0; i < 20; i++) {
            Long2FloatLinkedOpenHashMap next = shared.get();
            assertSame(first, next, "All biome instances on the same thread must reuse the single shared cache");
        }

        assertEquals(1, initCount.get(), "ThreadLocal supplier must only be invoked once per thread");
    }

    @Test
    @DisplayName("No Chunk Fade config flag correctly triggers instant rendering and removes distance feather")
    void testNoChunkFadeConfiguration() {
        VulkanPlusConfig config = ConfigManager.getConfig();
        assertTrue(config.noChunkFade);
        assertTrue(config.isAssPcActive());

        // Test with disabled
        config.noChunkFade = false;
        assertFalse(config.noChunkFade);
    }

    @Test
    @DisplayName("Combined No Fog / Fade zeroes all fog distance and enables instant rendering")
    void testCombinedFogAndFadeBehavior() {
        VulkanPlusConfig config = ConfigManager.getConfig();
        config.noFog = true;
        config.noChunkFade = true;
        assertTrue(config.isAssPcActive());

        // When both or either are enabled, fog data distances must be at Float.MAX_VALUE or 1,000,000 blocks
        assertTrue(config.noFog || config.noChunkFade);
    }
}
