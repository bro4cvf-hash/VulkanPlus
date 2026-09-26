package net.vulkanplus.test.thread;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.Preset;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.thread.ThreadPriorityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ThreadPriorityManagerTest {

    @AfterEach
    public void cleanup() {
        if (ThreadPriorityManager.isWindows()) {
            ThreadPriorityManager.cleanupWindowsNatives();
        }
    }

    @Test
    @DisplayName("ThreadPriorityManager constants have expected values")
    public void testPriorityConstants() {
        assertEquals(7, ThreadPriorityManager.PRIORITY_RENDER);
        assertEquals(5, ThreadPriorityManager.PRIORITY_WORKER);
        assertEquals(3, ThreadPriorityManager.PRIORITY_IO);
    }

    @Test
    @DisplayName("VulkanPlusConfig default values and copy/clone include MMCSS and timer resolution")
    public void testConfigDefaultsAndCopy() {
        VulkanPlusConfig config = new VulkanPlusConfig();
        assertFalse(config.enableMmcss, "enableMmcss should be false by default");
        assertEquals("Games", config.mmcssProfile, "mmcssProfile should default to 'Games'");
        assertTrue(config.enableTimerResolution, "enableTimerResolution should be true by default");

        VulkanPlusConfig copy = config.copy();
        assertFalse(copy.enableMmcss);
        assertEquals("Games", copy.mmcssProfile);
        assertTrue(copy.enableTimerResolution);

        copy.enableMmcss = true;
        copy.mmcssProfile = "Pro Audio";
        copy.enableTimerResolution = false;

        VulkanPlusConfig target = new VulkanPlusConfig();
        target.copyFrom(copy);
        assertTrue(target.enableMmcss);
        assertEquals("Pro Audio", target.mmcssProfile);
        assertFalse(target.enableTimerResolution);
    }

    @Test
    @DisplayName("Presets correctly configure and match MMCSS and timer resolution settings")
    public void testPresetsWithMmcss() {
        for (Preset preset : Preset.values()) {
            VulkanPlusConfig config = new VulkanPlusConfig();
            config.applyPreset(preset);
            assertFalse(config.enableMmcss, "enableMmcss should be false for preset " + preset);
            assertEquals("Games", config.mmcssProfile, "mmcssProfile should be 'Games' for preset " + preset);
            assertTrue(config.enableTimerResolution, "enableTimerResolution should be true for preset " + preset);
            assertTrue(config.matchesPreset(preset), "Config should match preset " + preset);
        }
    }

    @Test
    @DisplayName("ConfigManager JSON serialization roundtrip includes MMCSS properties")
    public void testJsonRoundtrip() {
        VulkanPlusConfig original = new VulkanPlusConfig();
        original.enableMmcss = false;
        original.mmcssProfile = "Pro Audio";
        original.enableTimerResolution = false;

        String json = ConfigManager.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"enableMmcss\": false"));
        assertTrue(json.contains("\"mmcssProfile\": \"Pro Audio\""));
        assertTrue(json.contains("\"enableTimerResolution\": false"));

        VulkanPlusConfig parsed = new VulkanPlusConfig();
        ConfigManager.parseJson(json, parsed);
        assertFalse(parsed.enableMmcss);
        assertEquals("Pro Audio", parsed.mmcssProfile);
        assertFalse(parsed.enableTimerResolution);
    }

    @Test
    @DisplayName("Windows MMCSS and high-resolution timer period can be dynamically applied and reverted")
    public void testWindowsMmcssAndTimerResolution() {
        if (!ThreadPriorityManager.isWindows()) {
            return;
        }

        assertTrue(ThreadPriorityManager.isAvrtAvailable(), "avrt.dll should be dynamically available on Windows");
        assertTrue(ThreadPriorityManager.isWinmmAvailable(), "winmm.dll should be dynamically available on Windows");

        // Test Timer Resolution
        ThreadPriorityManager.enableTimerResolution();
        assertTrue(ThreadPriorityManager.isTimerResolutionActive(), "Timer resolution should be active");

        ThreadPriorityManager.disableTimerResolution();
        assertFalse(ThreadPriorityManager.isTimerResolutionActive(), "Timer resolution should be inactive after disable");

        // Test MMCSS with "Games" profile
        ThreadPriorityManager.applyMmcss("Games");
        assertTrue(ThreadPriorityManager.isMmcssActive(), "MMCSS should be active with Games profile");
        assertNotEquals(0L, ThreadPriorityManager.getMmcssHandle(), "MMCSS handle should be non-zero");

        ThreadPriorityManager.revertMmcss();
        assertFalse(ThreadPriorityManager.isMmcssActive(), "MMCSS should be inactive after revert");
        assertEquals(0L, ThreadPriorityManager.getMmcssHandle());

        // Test MMCSS with "Pro Audio" profile
        ThreadPriorityManager.applyMmcss("Pro Audio");
        assertTrue(ThreadPriorityManager.isMmcssActive(), "MMCSS should be active with Pro Audio profile");

        ThreadPriorityManager.revertMmcss();
        assertFalse(ThreadPriorityManager.isMmcssActive());

        // Test applyRenderThreadPriority integration
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        cfg.enabled = true;
        cfg.enableThreadPriority = true;
        cfg.enableMmcss = true;
        cfg.mmcssProfile = "Games";
        cfg.enableTimerResolution = true;

        ThreadPriorityManager.applyRenderThreadPriority();
        assertTrue(ThreadPriorityManager.isMmcssActive(), "applyRenderThreadPriority should activate MMCSS");
        assertTrue(ThreadPriorityManager.isTimerResolutionActive(), "applyRenderThreadPriority should activate Timer Resolution");

        ThreadPriorityManager.cleanupWindowsNatives();
        assertFalse(ThreadPriorityManager.isMmcssActive());
        assertFalse(ThreadPriorityManager.isTimerResolutionActive());
    }
}
