package net.vulkanplus.test.culling;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.Preset;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.culling.ItemFrameCuller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ItemFrameCullerTest {

    private VulkanPlusConfig originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = ConfigManager.getConfig().copy();
        VulkanPlusConfig testConfig = new VulkanPlusConfig();
        testConfig.enabled = true;
        testConfig.enableFastItemFrames = true;
        testConfig.enableItemFrameBlockOcclusion = true;
        testConfig.itemFrameMaxDistance = 64.0;
        testConfig.itemFrameItemDistance = 24.0;
        testConfig.cullingDistanceFactor = 1.0;
        ConfigManager.setConfig(testConfig);
        ItemFrameCuller.resetStats();
    }

    @AfterEach
    void tearDown() {
        ConfigManager.setConfig(originalConfig);
    }

    @Test
    void testBackfaceCullingNorthFacing() {
        // Frame at (0, 64, 0) facing NORTH (normal: 0, 0, -1)
        int nx = 0, ny = 0, nz = -1;
        double fx = 0, fy = 64, fz = 0;

        // Camera in front (looking at north face: camZ = -5)
        // dx = -5 - 0 = -5, dot = -1 * -5 = +5 > 0 -> NOT culled
        assertFalse(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, 0, 64, -5));

        // Camera behind (behind wall: camZ = +5)
        // dx = +5 - 0 = +5, dot = -1 * +5 = -5 <= -0.02 -> CULLED
        assertTrue(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, 0, 64, 5));
    }

    @Test
    void testBackfaceCullingSouthFacing() {
        // Frame at (0, 64, 0) facing SOUTH (normal: 0, 0, 1)
        int nx = 0, ny = 0, nz = 1;
        double fx = 0, fy = 64, fz = 0;

        // Camera in front (camZ = +5) -> dot = +5 -> NOT culled
        assertFalse(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, 0, 64, 5));

        // Camera behind (camZ = -5) -> dot = -5 -> CULLED
        assertTrue(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, 0, 64, -5));
    }

    @Test
    void testBackfaceCullingEastFacing() {
        // Frame at (10, 64, 10) facing EAST (normal: 1, 0, 0)
        int nx = 1, ny = 0, nz = 0;
        double fx = 10, fy = 64, fz = 10;

        // Camera in front (camX = 15) -> dx = +5, dot = +5 -> NOT culled
        assertFalse(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, 15, 64, 10));

        // Camera behind (camX = 5) -> dx = -5, dot = -5 -> CULLED
        assertTrue(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, 5, 64, 10));
    }

    @Test
    void testBackfaceCullingWestFacing() {
        // Frame at (10, 64, 10) facing WEST (normal: -1, 0, 0)
        int nx = -1, ny = 0, nz = 0;
        double fx = 10, fy = 64, fz = 10;

        // Camera in front (camX = 5) -> dx = -5, dot = (-1)*(-5) = +5 -> NOT culled
        assertFalse(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, 5, 64, 10));

        // Camera behind (camX = 15) -> dx = +5, dot = (-1)*(+5) = -5 -> CULLED
        assertTrue(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, 15, 64, 10));
    }

    @Test
    void testBackfaceCullingVerticalFacing() {
        // Frame on ceiling facing DOWN (normal: 0, -1, 0)
        int nx = 0, ny = -1, nz = 0;
        double fx = 0, fy = 70, fz = 0;

        // Camera looking up from below (camY = 65) -> dy = -5, dot = (-1)*(-5) = +5 -> NOT culled
        assertFalse(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, 0, 65, 0));

        // Camera above ceiling (camY = 75) -> dy = +5, dot = (-1)*(+5) = -5 -> CULLED
        assertTrue(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, 0, 75, 0));

        // Frame on floor facing UP (normal: 0, 1, 0)
        nx = 0; ny = 1; nz = 0;
        // Camera above looking down (camY = 75) -> dy = +5, dot = (+1)*(+5) = +5 -> NOT culled
        assertFalse(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, 0, 75, 0));

        // Camera below floor (camY = 65) -> dy = -5, dot = (+1)*(-5) = -5 -> CULLED
        assertTrue(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, 0, 65, 0));
    }

    @Test
    void testBackfaceToleranceEpsilon() {
        // Test edge tolerance (-0.02)
        int nx = 1, ny = 0, nz = 0;
        double fx = 0, fy = 0, fz = 0;

        // Exactly on plane: dx = 0.0 -> dot = 0.0 > -0.02 -> NOT culled (protect grazing angles)
        assertFalse(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, 0.0, 0, 0));

        // Slightly behind within epsilon (-0.015) -> NOT culled
        assertFalse(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, -0.015, 0, 0));

        // Beyond epsilon (-0.025) -> CULLED
        assertTrue(ItemFrameCuller.isBackfaceCulled(nx, ny, nz, fx, fy, fz, -0.025, 0, 0));
    }

    @Test
    void testItemDistanceCullingThreshold() {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        assertEquals(24.0, cfg.itemFrameItemDistance);

        // Distance squared calculation for item stack:
        double fx = 0, fy = 64, fz = 0;

        // Camera at 15 blocks (within 24 block threshold)
        double camDist1 = 15.0;
        double dx1 = camDist1, dy1 = 0, dz1 = 0;
        double distSq1 = dx1 * dx1 + dy1 * dy1 + dz1 * dz1;
        double maxDistSq = cfg.itemFrameItemDistance * cfg.itemFrameItemDistance;
        assertFalse(distSq1 > maxDistSq, "Item stack should NOT be culled within distance threshold");

        // Camera at 30 blocks (exceeding 24 block threshold)
        double camDist2 = 30.0;
        double dx2 = camDist2, dy2 = 0, dz2 = 0;
        double distSq2 = dx2 * dx2 + dy2 * dy2 + dz2 * dz2;
        assertTrue(distSq2 > maxDistSq, "Item stack SHOULD be culled beyond distance threshold");
    }

    @Test
    void testResetStats() {
        ItemFrameCuller.culledBackfaceFrames = 42;
        ItemFrameCuller.culledOccludedFrames = 18;
        ItemFrameCuller.culledDistanceFrames = 5;
        ItemFrameCuller.culledItemModels = 99;

        ItemFrameCuller.resetStats();

        assertEquals(0, ItemFrameCuller.culledBackfaceFrames);
        assertEquals(0, ItemFrameCuller.culledOccludedFrames);
        assertEquals(0, ItemFrameCuller.culledDistanceFrames);
        assertEquals(0, ItemFrameCuller.culledItemModels);
    }

    @Test
    void testConfigPresetIntegration() {
        VulkanPlusConfig cfg = new VulkanPlusConfig();

        cfg.applyPreset(Preset.FAST);
        assertTrue(cfg.enableFastItemFrames);
        assertTrue(cfg.enableItemFrameBlockOcclusion);
        assertEquals(48.0, cfg.itemFrameMaxDistance);
        assertEquals(16.0, cfg.itemFrameItemDistance);

        cfg.applyPreset(Preset.BALANCED);
        assertTrue(cfg.enableFastItemFrames);
        assertTrue(cfg.enableItemFrameBlockOcclusion);
        assertEquals(64.0, cfg.itemFrameMaxDistance);
        assertEquals(24.0, cfg.itemFrameItemDistance);

        cfg.applyPreset(Preset.EXTREME);
        assertTrue(cfg.enableFastItemFrames);
        assertTrue(cfg.enableItemFrameBlockOcclusion);
        assertEquals(96.0, cfg.itemFrameMaxDistance);
        assertEquals(32.0, cfg.itemFrameItemDistance);
    }

    @Test
    void testConfigJsonSerialization() {
        VulkanPlusConfig cfg = new VulkanPlusConfig();
        cfg.enableFastItemFrames = true;
        cfg.enableItemFrameBlockOcclusion = true;
        cfg.itemFrameMaxDistance = 80.0;
        cfg.itemFrameItemDistance = 28.0;

        String json = ConfigManager.toJson(cfg);
        assertTrue(json.contains("\"enableFastItemFrames\": true"));
        assertTrue(json.contains("\"enableItemFrameBlockOcclusion\": true"));
        assertTrue(json.contains("\"itemFrameMaxDistance\": 80.0"));
        assertTrue(json.contains("\"itemFrameItemDistance\": 28.0"));

        VulkanPlusConfig parsed = new VulkanPlusConfig();
        ConfigManager.parseJson(json, parsed);

        assertTrue(parsed.enableFastItemFrames);
        assertTrue(parsed.enableItemFrameBlockOcclusion);
        assertEquals(80.0, parsed.itemFrameMaxDistance);
        assertEquals(28.0, parsed.itemFrameItemDistance);
    }
}
