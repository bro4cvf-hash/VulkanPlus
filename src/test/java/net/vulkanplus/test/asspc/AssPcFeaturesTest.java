package net.vulkanplus.test.asspc;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.Preset;
import net.vulkanplus.config.VulkanPlusConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AssPcFeaturesTest {

    @Test
    @DisplayName("ASS PC options all default to false")
    void testDefaultsAreFalse() {
        VulkanPlusConfig config = new VulkanPlusConfig();
        assertFalse(config.noMobAnimations);
        assertFalse(config.noDroppedItemAnimation);
        assertFalse(config.staticExpAnimations);
        assertFalse(config.noParticles);
        assertFalse(config.noTextureAnimations);
        assertFalse(config.noEntityShadows);
        assertFalse(config.noItemGlint);
        assertFalse(config.noSky);
        assertFalse(config.noFog);
        assertFalse(config.noBlockEntityAnimations);
        assertFalse(config.shitFoliage);
        assertFalse(config.isAssPcActive());
    }

    @Test
    @DisplayName("isAssPcActive returns true if any ASS PC option is enabled")
    void testIsAssPcActive() {
        VulkanPlusConfig config = new VulkanPlusConfig();
        assertFalse(config.isAssPcActive());

        config.noMobAnimations = true;
        assertTrue(config.isAssPcActive());
        config.noMobAnimations = false;

        config.noDroppedItemAnimation = true;
        assertTrue(config.isAssPcActive());
        config.noDroppedItemAnimation = false;

        config.staticExpAnimations = true;
        assertTrue(config.isAssPcActive());
        config.staticExpAnimations = false;

        config.noParticles = true;
        assertTrue(config.isAssPcActive());
        config.noParticles = false;

        config.noTextureAnimations = true;
        assertTrue(config.isAssPcActive());
        config.noTextureAnimations = false;

        config.noEntityShadows = true;
        assertTrue(config.isAssPcActive());
        config.noEntityShadows = false;

        config.noItemGlint = true;
        assertTrue(config.isAssPcActive());
        config.noItemGlint = false;

        config.noSky = true;
        assertTrue(config.isAssPcActive());
        config.noSky = false;

        config.noFog = true;
        assertTrue(config.isAssPcActive());
        config.noFog = false;

        config.noBlockEntityAnimations = true;
        assertTrue(config.isAssPcActive());
        config.noBlockEntityAnimations = false;

        config.shitFoliage = true;
        assertTrue(config.isAssPcActive());
        config.shitFoliage = false;

        assertFalse(config.isAssPcActive());
    }

    @Test
    @DisplayName("Presets do not enable ASS PC options")
    void testPresetsKeepAssPcDisabled() {
        VulkanPlusConfig config = new VulkanPlusConfig();

        config.applyPreset(Preset.FAST);
        assertFalse(config.isAssPcActive());

        config.applyPreset(Preset.BALANCED);
        assertFalse(config.isAssPcActive());

        config.applyPreset(Preset.EXTREME);
        assertFalse(config.isAssPcActive());
    }

    @Test
    @DisplayName("Config copy preserves ASS PC toggles")
    void testCopyPreservesAssPc() {
        VulkanPlusConfig config = new VulkanPlusConfig();
        config.noMobAnimations = true;
        config.noDroppedItemAnimation = true;
        config.staticExpAnimations = true;
        config.noParticles = true;
        config.noTextureAnimations = true;
        config.noEntityShadows = true;
        config.noItemGlint = true;
        config.noSky = true;
        config.noFog = true;
        config.noBlockEntityAnimations = true;

        VulkanPlusConfig copy = config.copy();
        assertTrue(copy.noMobAnimations);
        assertTrue(copy.noDroppedItemAnimation);
        assertTrue(copy.staticExpAnimations);
        assertTrue(copy.noParticles);
        assertTrue(copy.noTextureAnimations);
        assertTrue(copy.noEntityShadows);
        assertTrue(copy.noItemGlint);
        assertTrue(copy.noSky);
        assertTrue(copy.noFog);
        assertTrue(copy.noBlockEntityAnimations);
        assertTrue(copy.isAssPcActive());
    }

    @Test
    @DisplayName("ConfigManager JSON roundtrip preserves all 10 ASS PC options")
    void testJsonRoundtrip() {
        VulkanPlusConfig original = new VulkanPlusConfig();
        original.noMobAnimations = true;
        original.noDroppedItemAnimation = true;
        original.staticExpAnimations = true;
        original.noParticles = true;
        original.noTextureAnimations = true;
        original.noEntityShadows = true;
        original.noItemGlint = true;
        original.noSky = true;
        original.noFog = true;
        original.noBlockEntityAnimations = true;

        String json = ConfigManager.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"noMobAnimations\": true"));
        assertTrue(json.contains("\"noDroppedItemAnimation\": true"));
        assertTrue(json.contains("\"staticExpAnimations\": true"));
        assertTrue(json.contains("\"noParticles\": true"));
        assertTrue(json.contains("\"noTextureAnimations\": true"));
        assertTrue(json.contains("\"noEntityShadows\": true"));
        assertTrue(json.contains("\"noItemGlint\": true"));
        assertTrue(json.contains("\"noSky\": true"));
        assertTrue(json.contains("\"noFog\": true"));
        assertTrue(json.contains("\"noBlockEntityAnimations\": true"));

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
        assertTrue(parsed.isAssPcActive());
    }
}
