package net.vulkanplus.test.thread;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.Preset;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.thread.ThreadPriorityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ThreadPriorityTest {

    private VulkanPlusConfig originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = ConfigManager.getConfig().copy();
        VulkanPlusConfig testConfig = new VulkanPlusConfig();
        testConfig.enabled = true;
        testConfig.enableThreadPriority = true;
        testConfig.renderThreadPriority = 8;
        testConfig.workerThreadPriority = 1;
        testConfig.ioThreadPriority = 3;
        ConfigManager.setConfig(testConfig);
    }

    @AfterEach
    void tearDown() {
        ConfigManager.setConfig(originalConfig);
    }

    @Test
    void testClampPriorityBoundaries() {
        assertEquals(Thread.MIN_PRIORITY, ThreadPriorityManager.clampPriority(-10));
        assertEquals(Thread.MIN_PRIORITY, ThreadPriorityManager.clampPriority(0));
        assertEquals(1, ThreadPriorityManager.clampPriority(1));
        assertEquals(5, ThreadPriorityManager.clampPriority(5));
        assertEquals(8, ThreadPriorityManager.clampPriority(8));
        assertEquals(Thread.MAX_PRIORITY, ThreadPriorityManager.clampPriority(10));
        assertEquals(Thread.MAX_PRIORITY, ThreadPriorityManager.clampPriority(15));
        assertEquals(Thread.MAX_PRIORITY, ThreadPriorityManager.clampPriority(100));
    }

    @Test
    void testWorkerThreadConfiguration() {
        Thread worker = new Thread(() -> {}, "Worker-Main-1");
        worker.setPriority(Thread.NORM_PRIORITY);

        ThreadPriorityManager.configureWorkerThread(worker);
        assertEquals(1, worker.getPriority(), "Worker thread should be deprioritized to Thread.MIN_PRIORITY (1)");
    }

    @Test
    void testIoThreadConfiguration() {
        Thread ioWorker = new Thread(() -> {}, "IO-Worker-1");
        ioWorker.setPriority(Thread.NORM_PRIORITY);

        ThreadPriorityManager.configureIoThread(ioWorker);
        assertEquals(3, ioWorker.getPriority(), "IO worker thread should be set to priority 3");
    }

    @Test
    void testFallbackToNormalPriorityWhenDisabled() {
        VulkanPlusConfig cfg = ConfigManager.getConfig().copy();
        cfg.enableThreadPriority = false;
        ConfigManager.setConfig(cfg);

        Thread worker = new Thread(() -> {}, "Worker-Main-TestFallback");
        worker.setPriority(Thread.MAX_PRIORITY);

        ThreadPriorityManager.configureWorkerThread(worker);
        assertEquals(Thread.NORM_PRIORITY, worker.getPriority(), "Should fall back to NORM_PRIORITY when disabled");

        Thread ioWorker = new Thread(() -> {}, "IO-Worker-TestFallback");
        ioWorker.setPriority(Thread.MIN_PRIORITY);

        ThreadPriorityManager.configureIoThread(ioWorker);
        assertEquals(Thread.NORM_PRIORITY, ioWorker.getPriority(), "Should fall back to NORM_PRIORITY when disabled");
    }

    @Test
    void testSweepAndApplyAllActiveThreads() throws InterruptedException {
        Thread renderThread = new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }, "Render thread");

        Thread workerThread = new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }, "Worker-Main-SweepTest");

        Thread ioThread = new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }, "IO-Worker-SweepTest");

        Thread builderThread = new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }, "Builder-0");

        renderThread.start();
        workerThread.start();
        ioThread.start();
        builderThread.start();

        try {
            ThreadPriorityManager.sweepAndApplyAll();

            assertEquals(8, renderThread.getPriority(), "Render thread should be elevated to 8");
            assertEquals(1, workerThread.getPriority(), "Worker thread should be deprioritized to 1");
            assertEquals(1, builderThread.getPriority(), "VulkanMod Builder-0 thread should be deprioritized to 1");
            assertEquals(3, ioThread.getPriority(), "IO worker should be set to 3");

            // Disable and sweep again
            VulkanPlusConfig cfg = ConfigManager.getConfig().copy();
            cfg.enableThreadPriority = false;
            ConfigManager.setConfig(cfg);

            ThreadPriorityManager.sweepAndApplyAll();

            assertEquals(Thread.NORM_PRIORITY, renderThread.getPriority(), "Render thread should return to 5");
            assertEquals(Thread.NORM_PRIORITY, workerThread.getPriority(), "Worker thread should return to 5");
            assertEquals(Thread.NORM_PRIORITY, builderThread.getPriority(), "Builder-0 thread should return to 5");
            assertEquals(Thread.NORM_PRIORITY, ioThread.getPriority(), "IO worker should return to 5");
        } finally {
            renderThread.interrupt();
            workerThread.interrupt();
            ioThread.interrupt();
            builderThread.interrupt();
            renderThread.join(500);
            workerThread.join(500);
            ioThread.join(500);
            builderThread.join(500);
        }
    }

    @Test
    void testPresetTuningValues() {
        VulkanPlusConfig cfg = new VulkanPlusConfig();

        cfg.applyPreset(Preset.FAST);
        assertTrue(cfg.enableThreadPriority);
        assertEquals(9, cfg.renderThreadPriority);
        assertEquals(1, cfg.workerThreadPriority);
        assertEquals(2, cfg.ioThreadPriority);

        cfg.applyPreset(Preset.BALANCED);
        assertTrue(cfg.enableThreadPriority);
        assertEquals(8, cfg.renderThreadPriority);
        assertEquals(2, cfg.workerThreadPriority);
        assertEquals(3, cfg.ioThreadPriority);

        cfg.applyPreset(Preset.EXTREME);
        assertTrue(cfg.enableThreadPriority);
        assertEquals(9, cfg.renderThreadPriority);
        assertEquals(1, cfg.workerThreadPriority);
        assertEquals(2, cfg.ioThreadPriority);
    }

    @Test
    void testConfigSerializationDeserialization() {
        VulkanPlusConfig cfg = new VulkanPlusConfig();
        cfg.enableThreadPriority = true;
        cfg.renderThreadPriority = 9;
        cfg.workerThreadPriority = 2;
        cfg.ioThreadPriority = 4;

        String json = ConfigManager.toJson(cfg);
        assertTrue(json.contains("\"enableThreadPriority\": true"));
        assertTrue(json.contains("\"renderThreadPriority\": 9"));
        assertTrue(json.contains("\"workerThreadPriority\": 2"));
        assertTrue(json.contains("\"ioThreadPriority\": 4"));

        VulkanPlusConfig parsed = new VulkanPlusConfig();
        ConfigManager.parseJson(json, parsed);

        assertTrue(parsed.enableThreadPriority);
        assertEquals(9, parsed.renderThreadPriority);
        assertEquals(2, parsed.workerThreadPriority);
        assertEquals(4, parsed.ioThreadPriority);
    }
}
