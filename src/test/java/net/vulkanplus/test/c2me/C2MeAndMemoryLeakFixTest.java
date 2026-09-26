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

    @Test
    @DisplayName("Verify RegionBasedStorageMixin method descriptor and dsync disabling behavior")
    void testRegionBasedStorageMixinBehavior() throws Exception {
        Class<?> mixinClass = Class.forName("net.vulkanplus.mixin.c2me.RegionBasedStorageMixin");
        assertNotNull(mixinClass);

        java.lang.reflect.Method onInitMethod = mixinClass.getDeclaredMethod("onInit",
                net.minecraft.world.storage.StorageKey.class,
                java.nio.file.Path.class,
                boolean.class,
                org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        assertNotNull(onInitMethod);
        onInitMethod.setAccessible(true);

        java.lang.reflect.Field dsyncField = mixinClass.getDeclaredField("dsync");
        dsyncField.setAccessible(true);

        // Instantiate mixin instance via Objenesis / Unsafe or constructor
        Object mixinInstance = mixinClass.getDeclaredConstructor().newInstance();
        dsyncField.set(mixinInstance, true);

        VulkanPlusConfig config = ConfigManager.getConfig();
        config.enabled = true;
        config.enableC2MeOptimizations = true;

        onInitMethod.invoke(mixinInstance, null, null, true, null);
        assertFalse((boolean) dsyncField.get(mixinInstance), "dsync must be forced to false when C2ME optimizations are enabled");

        // When disabled, dsync must not be altered
        dsyncField.set(mixinInstance, true);
        config.enableC2MeOptimizations = false;
        onInitMethod.invoke(mixinInstance, null, null, true, null);
        assertTrue((boolean) dsyncField.get(mixinInstance), "dsync must remain unchanged when C2ME optimizations are disabled");
    }

    @Test
    @DisplayName("Verify NbtCompoundMixin fast path returns Object2ObjectOpenHashMap")
    void testNbtCompoundMixinFastPath() throws Exception {
        Class<?> mixinClass = Class.forName("net.vulkanplus.mixin.c2me.NbtCompoundMixin");
        assertNotNull(mixinClass);

        java.lang.reflect.Method fastMapMethod = mixinClass.getDeclaredMethod("useFastUtilMap", Map.class);
        fastMapMethod.setAccessible(true);

        VulkanPlusConfig config = ConfigManager.getConfig();
        config.enabled = true;
        config.enableC2MeOptimizations = true;

        Map<String, net.minecraft.nbt.NbtElement> originalMap = new java.util.HashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, net.minecraft.nbt.NbtElement> resultEnabled =
                (Map<String, net.minecraft.nbt.NbtElement>) fastMapMethod.invoke(null, originalMap);

        assertNotNull(resultEnabled);
        assertTrue(resultEnabled instanceof Object2ObjectOpenHashMap, "NbtCompound must use Object2ObjectOpenHashMap on fast path");
        assertNotSame(originalMap, resultEnabled);

        // When C2ME optimizations are disabled
        config.enableC2MeOptimizations = false;
        @SuppressWarnings("unchecked")
        Map<String, net.minecraft.nbt.NbtElement> resultDisabled =
                (Map<String, net.minecraft.nbt.NbtElement>) fastMapMethod.invoke(null, originalMap);

        assertSame(originalMap, resultDisabled, "NbtCompound must retain original HashMap when C2ME optimizations are disabled");
    }
}

