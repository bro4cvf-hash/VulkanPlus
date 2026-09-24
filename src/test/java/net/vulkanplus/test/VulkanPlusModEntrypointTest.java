package net.vulkanplus.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.vulkanplus.VulkanPlusMod;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanPlusModEntrypointTest {

    @BeforeEach
    void setUp() {
        VulkanPlusConfig cfg = new VulkanPlusConfig();
        cfg.enabled = true;
        cfg.enableThreadPriority = true;
        ConfigManager.setConfig(cfg);
    }

    @Test
    @DisplayName("VulkanPlusMod implements ModInitializer interface required by fabric.mod.json main entrypoint")
    public void testImplementsModInitializer() {
        assertTrue(ModInitializer.class.isAssignableFrom(VulkanPlusMod.class),
                "VulkanPlusMod must implement net.fabricmc.api.ModInitializer");
    }

    @Test
    @DisplayName("VulkanPlusMod implements ClientModInitializer interface required by fabric.mod.json client entrypoint")
    public void testImplementsClientModInitializer() {
        assertTrue(ClientModInitializer.class.isAssignableFrom(VulkanPlusMod.class),
                "VulkanPlusMod must implement net.fabricmc.api.ClientModInitializer to prevent client boot crash");
    }

    @Test
    @DisplayName("VulkanPlusMod entrypoints initialize cleanly without throwing exceptions")
    public void testEntrypointExecution() {
        VulkanPlusMod mod = new VulkanPlusMod();
        assertDoesNotThrow(mod::onInitialize, "onInitialize() should execute without throwing");
        assertDoesNotThrow(mod::onInitializeClient, "onInitializeClient() should execute without throwing");
        assertTrue(net.vulkanplus.thread.ThreadPriorityManager.isInitialized(),
                "ThreadPriorityManager must be initialized during onInitializeClient()");
    }
}
