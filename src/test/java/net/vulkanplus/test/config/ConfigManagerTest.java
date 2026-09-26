package net.vulkanplus.test.config;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.Preset;
import net.vulkanplus.config.VulkanPlusConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigManagerTest {

    @Test
    @DisplayName("ConfigManager JSON roundtrip preserves all fields")
    public void testJsonRoundtrip() {
        VulkanPlusConfig original = new VulkanPlusConfig();
        original.enabled = false;
        original.enableBufferPooling = true;
        original.enableDescriptorCaching = false;
        original.opaqueLeaves = true;
        original.showFps = true;
        original.presentMode = "IMMEDIATE";
        original.cullingDistanceFactor = 0.75;
        original.activePreset = Preset.FAST;

        String json = ConfigManager.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"enabled\": false"));
        assertTrue(json.contains("\"opaqueLeaves\": true"));
        assertTrue(json.contains("\"showFps\": true"));
        assertTrue(json.contains("\"presentMode\": \"IMMEDIATE\""));

        VulkanPlusConfig parsed = new VulkanPlusConfig();
        ConfigManager.parseJson(json, parsed);

        assertFalse(parsed.enabled);
        assertTrue(parsed.enableBufferPooling);
        assertFalse(parsed.enableDescriptorCaching);
        assertTrue(parsed.opaqueLeaves);
        assertTrue(parsed.showFps);
        assertEquals("IMMEDIATE", parsed.presentMode);
        assertEquals(0.75, parsed.cullingDistanceFactor, 1e-5);
        assertEquals(Preset.FAST, parsed.activePreset);
    }

    @Test
    @DisplayName("Show FPS and Opaque Leaves options persist correctly across toggles")
    public void testShowFpsAndOpaqueLeavesPersistence() {
        VulkanPlusConfig cfg = new VulkanPlusConfig();
        cfg.showFps = true;
        cfg.opaqueLeaves = true;

        String jsonOn = ConfigManager.toJson(cfg);
        assertTrue(jsonOn.contains("\"showFps\": true"));
        assertTrue(jsonOn.contains("\"opaqueLeaves\": true"));

        VulkanPlusConfig parsedOn = new VulkanPlusConfig();
        ConfigManager.parseJson(jsonOn, parsedOn);
        assertTrue(parsedOn.showFps);
        assertTrue(parsedOn.opaqueLeaves);

        cfg.showFps = false;
        cfg.opaqueLeaves = false;

        String jsonOff = ConfigManager.toJson(cfg);
        assertTrue(jsonOff.contains("\"showFps\": false"));
        assertTrue(jsonOff.contains("\"opaqueLeaves\": false"));

        VulkanPlusConfig parsedOff = new VulkanPlusConfig();
        ConfigManager.parseJson(jsonOff, parsedOff);
        assertFalse(parsedOff.showFps);
        assertFalse(parsedOff.opaqueLeaves);
    }

    @Test
    @DisplayName("Preset application configures expected parameters")
    public void testPresetApplication() {
        VulkanPlusConfig config = new VulkanPlusConfig();

        config.applyPreset(Preset.FAST);
        assertEquals(Preset.FAST, config.activePreset);
        assertEquals("IMMEDIATE", config.presentMode);
        assertEquals(0.8, config.cullingDistanceFactor, 1e-5);
        assertTrue(config.opaqueLeaves);

        config.applyPreset(Preset.EXTREME);
        assertEquals(Preset.EXTREME, config.activePreset);
        assertEquals(0.6, config.cullingDistanceFactor, 1e-5);
        assertFalse(config.chestProtection);
        assertTrue(config.opaqueLeaves);

        config.applyPreset(Preset.BALANCED);
        assertEquals(Preset.BALANCED, config.activePreset);
        assertEquals("MAILBOX", config.presentMode);
        assertEquals(1.0, config.cullingDistanceFactor, 1e-5);
        assertFalse(config.opaqueLeaves);
    }

    @Test
    @DisplayName("ConfigManager handles corrupted JSON gracefully")
    public void testCorruptedJson() {
        VulkanPlusConfig fallback = new VulkanPlusConfig();
        fallback.enableBufferPooling = false;

        ConfigManager.parseJson("{ invalid: corrupted json string !!", fallback);
        // Should not crash, leaves untouched or applies valid parsed tokens
        assertNotNull(fallback);
    }

    @Test
    @DisplayName("Preset matching identifies active preset or custom deviation")
    public void testEffectivePresetMatching() {
        VulkanPlusConfig config = new VulkanPlusConfig();
        // Default new config matches balanced
        assertTrue(config.matchesPreset(Preset.BALANCED));
        assertEquals(Preset.BALANCED, config.getEffectivePreset());

        // Fast
        config.applyPreset(Preset.FAST);
        assertTrue(config.matchesPreset(Preset.FAST));
        assertFalse(config.matchesPreset(Preset.BALANCED));
        assertEquals(Preset.FAST, config.getEffectivePreset());

        // Extreme
        config.applyPreset(Preset.EXTREME);
        assertTrue(config.matchesPreset(Preset.EXTREME));
        assertEquals(Preset.EXTREME, config.getEffectivePreset());

        // Custom tweak deviates from preset
        config.opaqueLeaves = false;
        assertFalse(config.matchesPreset(Preset.EXTREME));
        assertNull(config.getEffectivePreset());
    }

    @Test
    @DisplayName("ASS PC options roundtrip and persist correctly")
    public void testAssPcPersistence() {
        VulkanPlusConfig cfg = new VulkanPlusConfig();
        cfg.noMobAnimations = true;
        cfg.noDroppedItemAnimation = true;
        cfg.staticExpAnimations = true;
        cfg.noParticles = true;
        cfg.noTextureAnimations = true;
        cfg.noEntityShadows = true;
        cfg.noItemGlint = true;
        cfg.noSky = true;
        cfg.noFog = true;
        cfg.noBlockEntityAnimations = true;
        cfg.noChunkFade = true;
        cfg.fastChest = true;
        cfg.shitFoliage = true;
        cfg.fullBright = true;
        cfg.enableC2MeOptimizations = true;
        cfg.enableMemoryLeakFix = true;

        String json = ConfigManager.toJson(cfg);
        VulkanPlusConfig parsed = new VulkanPlusConfig();
        ConfigManager.parseJson(json, parsed);

        assertTrue(parsed.noMobAnimations);
        assertTrue(parsed.noDroppedItemAnimation);
        assertTrue(parsed.staticExpAnimations);
        assertTrue(parsed.noParticles);
        assertTrue(parsed.noTextureAnimations);
        assertTrue(parsed.noEntityShadows);
        assertTrue(parsed.noItemGlint);
        assertTrue(parsed.noSky);
        assertTrue(parsed.noFog);
        assertTrue(parsed.noBlockEntityAnimations);
        assertTrue(parsed.noChunkFade);
        assertTrue(parsed.fastChest);
        assertTrue(parsed.shitFoliage);
        assertTrue(parsed.fullBright);
        assertTrue(parsed.enableC2MeOptimizations);
        assertTrue(parsed.enableMemoryLeakFix);
        assertTrue(parsed.isAssPcActive());
    }

    @Test
    @DisplayName("Master Toggle application updates and persists enabled state properly")
    public void testMasterToggleApplication() {
        VulkanPlusConfig prev = ConfigManager.getConfig().copy();
        VulkanPlusConfig updated = prev.copy();
        updated.enabled = false;
        updated.opaqueLeaves = true;

        net.vulkanplus.ui.VulkanPlusConfigScreen.applyConfigChanges(prev, updated, null);

        VulkanPlusConfig current = ConfigManager.getConfig();
        assertFalse(current.enabled);
        assertTrue(current.opaqueLeaves);

        // Turn back on
        updated.enabled = true;
        net.vulkanplus.ui.VulkanPlusConfigScreen.applyConfigChanges(current, updated, null);
        assertTrue(ConfigManager.getConfig().enabled);
    }
}
