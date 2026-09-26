package net.vulkanplus.test.culling;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.Preset;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.culling.BlockEntityOcclusionCuller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class MoreCullingTest {

    private VulkanPlusConfig originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = ConfigManager.getConfig().copy();
        VulkanPlusConfig testConfig = new VulkanPlusConfig();
        testConfig.enabled = true;
        testConfig.enableMoreCulling = true;
        testConfig.enableEntityCulling = true;
        testConfig.enableBlockEntityOcclusion = true;
        testConfig.enableSmartLeaves = true;
        testConfig.enableBeaconBeamCulling = true;
        testConfig.enableExtraGlassCulling = true;
        ConfigManager.setConfig(testConfig);
        BlockEntityOcclusionCuller.resetStats();
    }

    @AfterEach
    void tearDown() {
        ConfigManager.setConfig(originalConfig);
    }

    @Test
    void testMoreCullingRenamingAndBackwardCompatibility() {
        VulkanPlusConfig cfg = new VulkanPlusConfig();
        cfg.enableMoreCulling = true;
        cfg.enableEntityCulling = true;

        String json = ConfigManager.toJson(cfg);
        assertTrue(json.contains("\"enableMoreCulling\": true"), "JSON should contain enableMoreCulling");
        assertTrue(json.contains("\"enableEntityCulling\": true"), "JSON should preserve enableEntityCulling alias");

        // Parse legacy JSON that only has enableEntityCulling
        String legacyJson = "{\n  \"enabled\": true,\n  \"enableEntityCulling\": true\n}";
        VulkanPlusConfig parsedLegacy = new VulkanPlusConfig();
        ConfigManager.parseJson(legacyJson, parsedLegacy);
        assertTrue(parsedLegacy.enableMoreCulling, "enableMoreCulling should be populated from legacy enableEntityCulling");
        assertTrue(parsedLegacy.enableEntityCulling);

        // Parse modern JSON that has enableMoreCulling
        String modernJson = "{\n  \"enabled\": true,\n  \"enableMoreCulling\": false\n}";
        VulkanPlusConfig parsedModern = new VulkanPlusConfig();
        ConfigManager.parseJson(modernJson, parsedModern);
        assertFalse(parsedModern.enableMoreCulling);
        assertFalse(parsedModern.enableEntityCulling);
    }

    @Test
    void testProtectedBlockEntityTypes() {
        // Beacon, End Gateway, End Portal, Piston, and Bell must never be culled by occlusion
        assertTrue(BlockEntityOcclusionCuller.isProtectedTypeName("minecraft:beacon"));
        assertTrue(BlockEntityOcclusionCuller.isProtectedTypeName("minecraft:end_gateway"));
        assertTrue(BlockEntityOcclusionCuller.isProtectedTypeName("minecraft:end_portal"));
        assertTrue(BlockEntityOcclusionCuller.isProtectedTypeName("minecraft:piston"));
        assertTrue(BlockEntityOcclusionCuller.isProtectedTypeName("minecraft:bell"));
        assertTrue(BlockEntityOcclusionCuller.isProtectedTypeName("beacon"));
        assertTrue(BlockEntityOcclusionCuller.isProtectedTypeName("end_gateway"));
        assertTrue(BlockEntityOcclusionCuller.isProtectedTypeName("end_portal"));
        assertTrue(BlockEntityOcclusionCuller.isProtectedTypeName("piston"));
        assertTrue(BlockEntityOcclusionCuller.isProtectedTypeName("bell"));

        // Normal block entities must be eligible for occlusion culling
        assertFalse(BlockEntityOcclusionCuller.isProtectedTypeName("minecraft:chest"));
        assertFalse(BlockEntityOcclusionCuller.isProtectedTypeName("minecraft:sign"));
        assertFalse(BlockEntityOcclusionCuller.isProtectedTypeName("minecraft:skull"));
        assertFalse(BlockEntityOcclusionCuller.isProtectedTypeName("minecraft:barrel"));
        assertFalse(BlockEntityOcclusionCuller.isProtectedTypeName(null));

        assertFalse(BlockEntityOcclusionCuller.isProtectedType(null));
    }

    @Test
    void testPresetTuningForCullingFeatures() {
        VulkanPlusConfig cfg = new VulkanPlusConfig();

        cfg.applyPreset(Preset.FAST);
        assertTrue(cfg.enableMoreCulling);
        assertTrue(cfg.enableBlockEntityOcclusion);
        assertTrue(cfg.enableSmartLeaves);
        assertTrue(cfg.enableBeaconBeamCulling);
        assertTrue(cfg.enableExtraGlassCulling);
        assertTrue(cfg.opaqueLeaves);

        cfg.applyPreset(Preset.BALANCED);
        assertTrue(cfg.enableMoreCulling);
        assertTrue(cfg.enableBlockEntityOcclusion);
        assertTrue(cfg.enableSmartLeaves);
        assertTrue(cfg.enableBeaconBeamCulling);
        assertTrue(cfg.enableExtraGlassCulling);
        assertFalse(cfg.opaqueLeaves, "Balanced mode keeps transparent leaves with Smart Leaves culling");

        cfg.applyPreset(Preset.EXTREME);
        assertTrue(cfg.enableMoreCulling);
        assertTrue(cfg.enableBlockEntityOcclusion);
        assertTrue(cfg.enableSmartLeaves);
        assertTrue(cfg.enableBeaconBeamCulling);
        assertTrue(cfg.enableExtraGlassCulling);
        assertTrue(cfg.opaqueLeaves);
    }

    @Test
    void testSerializationOfCullingToggles() {
        VulkanPlusConfig cfg = new VulkanPlusConfig();
        cfg.enableMoreCulling = true;
        cfg.enableBlockEntityOcclusion = true;
        cfg.enableSmartLeaves = true;
        cfg.enableBeaconBeamCulling = true;
        cfg.enableExtraGlassCulling = true;

        String json = ConfigManager.toJson(cfg);
        assertTrue(json.contains("\"enableMoreCulling\": true"));
        assertTrue(json.contains("\"enableBlockEntityOcclusion\": true"));
        assertTrue(json.contains("\"enableSmartLeaves\": true"));
        assertTrue(json.contains("\"enableBeaconBeamCulling\": true"));
        assertTrue(json.contains("\"enableExtraGlassCulling\": true"));

        VulkanPlusConfig parsed = new VulkanPlusConfig();
        ConfigManager.parseJson(json, parsed);

        assertTrue(parsed.enableMoreCulling);
        assertTrue(parsed.enableBlockEntityOcclusion);
        assertTrue(parsed.enableSmartLeaves);
        assertTrue(parsed.enableBeaconBeamCulling);
        assertTrue(parsed.enableExtraGlassCulling);
    }

    @Test
    void testLangTranslationsPresence() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/assets/vulkanplus/lang/en_us.json")) {
            assertNotNull(is, "en_us.json must be present in classpath");
            String langJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(langJson.contains("\"vulkanplus.options.moreCulling\""));
            assertTrue(langJson.contains("\"vulkanplus.options.blockEntityOcclusion\""));
            assertTrue(langJson.contains("\"vulkanplus.options.smartLeaves\""));
            assertTrue(langJson.contains("\"vulkanplus.options.beaconBeamCulling\""));
            assertTrue(langJson.contains("\"vulkanplus.options.extraGlassCulling\""));
            assertTrue(langJson.contains("\"vulkanplus.options.threadPriority\""));
            assertTrue(langJson.contains("\"vulkanplus.options.fastItemFrames\""));
        }
    }

    @Test
    void testResetStats() {
        BlockEntityOcclusionCuller.culledOccludedBlockEntities = 55;
        BlockEntityOcclusionCuller.resetStats();
        assertEquals(0, BlockEntityOcclusionCuller.culledOccludedBlockEntities);
    }
}
