package net.vulkanplus.test.exordium;

import net.minecraft.client.gui.render.state.GuiRenderState;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.exordium.ExordiumManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ExordiumManagerTest {

    @BeforeEach
    public void setup() {
        ConfigManager.setConfig(new VulkanPlusConfig());
        ExordiumManager.getInstance().cleanup();
    }

    @Test
    public void testSingletonInstance() {
        ExordiumManager manager1 = ExordiumManager.getInstance();
        ExordiumManager manager2 = ExordiumManager.getInstance();
        assertNotNull(manager1);
        assertSame(manager1, manager2);
    }

    @Test
    public void testExordiumConfiguredState() {
        ExordiumManager manager = ExordiumManager.getInstance();
        VulkanPlusConfig config = ConfigManager.getConfig();

        config.enabled = true;
        config.enableExordium = true;
        assertTrue(manager.isExordiumConfigured());

        config.enableExordium = false;
        assertFalse(manager.isExordiumConfigured());

        config.enableExordium = true;
        config.enabled = false;
        assertFalse(manager.isExordiumConfigured());
    }

    @Test
    public void testVulkanModCompatibilityWithGuiRenderStateCaching() {
        ExordiumManager manager = ExordiumManager.getInstance();
        VulkanPlusConfig config = ConfigManager.getConfig();
        config.enabled = true;
        config.enableExordium = true;

        // Native 1.21.11 GuiRenderState snapshotting works seamlessly with both VulkanMod and Vanilla
        assertTrue(manager.isExordiumActive(), "Must be active when enabled on both VulkanMod and Vanilla via GuiRenderState caching");
    }

    @Test
    public void testGuiRenderStateSnapshotAndReplay() {
        ExordiumManager manager = ExordiumManager.getInstance();
        assertFalse(manager.hasValidCachedFrame());

        GuiRenderState state = new GuiRenderState();
        manager.beginHudCapture();
        assertTrue(manager.isCapturing());

        manager.endHudCapture(state);
        assertFalse(manager.isCapturing());
        assertTrue(manager.hasValidCachedFrame());

        GuiRenderState targetState = new GuiRenderState();
        assertDoesNotThrow(() -> manager.replayCachedHud(targetState));

        manager.cleanup();
        assertFalse(manager.hasValidCachedFrame());
    }

    @Test
    public void testDirtyAndInputSignals() {
        ExordiumManager manager = ExordiumManager.getInstance();

        assertDoesNotThrow(manager::markDirty);
        assertDoesNotThrow(manager::onInputActivity);
        assertDoesNotThrow(manager::cleanup);
    }

    @Test
    public void testExternalModDetectionFallback() {
        ExordiumManager manager = ExordiumManager.getInstance();
        // In test environment without external Exordium jar, detection is false
        assertFalse(manager.isExternalModDetected());
    }
}
