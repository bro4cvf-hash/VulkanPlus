package net.vulkanplus.test.ui;

import net.vulkanplus.ui.DiagnosticHud;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DiagnosticHudTest {

    @Test
    @DisplayName("DiagnosticHud computes correct average FPS and 1% low metrics")
    public void testMetricsCalculation() {
        DiagnosticHud hud = new DiagnosticHud();

        long sixtyFpsNanos = 16_666_667L;
        for (int i = 0; i < 99; i++) {
            hud.recordSample(sixtyFpsNanos);
        }

        hud.recordSample(100_000_000L);

        assertEquals(100, hud.getSamplesCount());
        assertTrue(hud.getAverageFps() > 50.0f && hud.getAverageFps() < 65.0f,
                "Average FPS must reflect samples: " + hud.getAverageFps());

        assertEquals(10.0f, hud.getOnePercentLowFps(), 1.0f,
                "1% low FPS must accurately capture worst 1% frame duration");
    }

    @Test
    @DisplayName("DiagnosticHud generates non-empty formatted diagnostic lines")
    public void testDiagnosticLines() {
        DiagnosticHud hud = new DiagnosticHud();
        hud.recordSample(16_666_667L);

        String[] lines = hud.getDiagnosticsLines();
        assertNotNull(lines);
        assertTrue(lines.length >= 4);
        assertTrue(lines[0].contains("Vulkan Plus"));
        assertTrue(lines[1].contains("FPS:"));
    }

    @Test
    @DisplayName("DiagnosticHud circular buffer wraps around at capacity 300")
    public void testCircularBuffer() {
        DiagnosticHud hud = new DiagnosticHud();
        for (int i = 0; i < 500; i++) {
            hud.recordSample(16_666_667L);
        }
        assertEquals(DiagnosticHud.BUFFER_CAPACITY, hud.getSamplesCount());
    }

    @Test
    @DisplayName("DiagnosticHud reuses cached String[5] array across frames without reallocating")
    public void testZeroAllocationCachedLines() {
        DiagnosticHud hud = new DiagnosticHud();
        hud.recordSample(16_666_667L);

        String[] firstCall = hud.getDiagnosticsLines();
        String[] secondCall = hud.getDiagnosticsLines();
        assertSame(firstCall, secondCall, "getDiagnosticsLines must reuse the same String[] array reference");
        assertSame(firstCall[0], secondCall[0], "Cached line 0 String instance must be reused when clean");
        assertSame(firstCall[1], secondCall[1], "Cached line 1 String instance must be reused when clean");

        net.vulkanplus.bridge.impl.VulkanModBridgeImpl bridge = new net.vulkanplus.bridge.impl.VulkanModBridgeImpl();
        bridge.onRenderInit();
        assertTrue(bridge.getVramUsed() <= bridge.getVramAllocated(),
                "VRAM used must never exceed VRAM allocated (fixes 699 MB / 48 MB bug)");

        // Verify null activePreset and non-interned presentMode String do not throw or falsely invalidate
        net.vulkanplus.config.VulkanPlusConfig cfg = net.vulkanplus.config.ConfigManager.getConfig();
        net.vulkanplus.config.Preset savedPreset = cfg.activePreset;
        String savedMode = cfg.presentMode;
        try {
            cfg.activePreset = null;
            cfg.presentMode = new String("FIFO_RELAXED");
            String[] customLines = hud.getDiagnosticsLines();
            assertTrue(customLines[4].contains("Preset: Custom | Present: FIFO_RELAXED"));
        } finally {
            cfg.activePreset = savedPreset;
            cfg.presentMode = savedMode;
        }
    }

    @Test
    @DisplayName("DiagnosticHud 1.0-second rolling window prunes samples older than 1_000_000_000ns and computes 1% & 0.1% lows")
    public void testOneSecondRollingWindowPruningAndPointOnePercentLow() {
        DiagnosticHud hud = new DiagnosticHud();

        // Simulate 1,000 frames over >1.0 second:
        // 1 frame at 20ms (50 FPS) at start, 9 frames at 5ms (200 FPS), 990 frames at 1ms (1000 FPS)
        long timestamp = 0L;
        for (int i = 0; i < 1000; i++) {
            long duration;
            if (i == 0) {
                duration = 20_000_000L; // 50 FPS spike at start of window (t = 20ms)
            } else if (i < 10) {
                duration = 5_000_000L;  // 200 FPS for 9 frames
            } else {
                duration = 1_000_000L;  // 1000 FPS for 990 frames
            }
            timestamp += duration;
            hud.recordTimedSample(duration, timestamp);
        }

        // At timestamp = 1_055_000_000ns (1.055s), the first frame at t = 20ms (20_000_000ns)
        // has age 1_055_000_000 - 20_000_000 = 1_035_000_000ns >= 1_000_000_000ns, so it MUST be pruned!
        assertTrue(hud.getLiveWindowCount() < 1000,
                "Samples older than 1.0s must be pruned from the rolling window");
        assertEquals(200.0f, hud.getPointOnePercentLowFps(), 5.0f,
                "After 20ms spike ages out past 1.0s, 0.1% low must recover to ~200 FPS");

        // Now test exact 1,000-sample window within 1.0s where 1% low != 0.1% low
        hud.reset();
        long t = 0L;
        for (int i = 0; i < 1000; i++) {
            long d;
            if (i == 999) {
                d = 10_000_000L; // 1 frame at 10ms (100 FPS) -> 0.1% low (worst 1 of 1000)
            } else if (i >= 990) {
                d = 2_000_000L;  // 9 frames at 2ms (500 FPS) -> 1% low averages 1x10ms + 9x2ms = 2.8ms (~357.1 FPS)
            } else {
                d = 500_000L;    // 990 frames at 0.5ms (2000 FPS)
            }
            t += d; // Total elapsed time = 495ms + 18ms + 10ms = 523ms (< 1.0s, no pruning)
            hud.recordTimedSample(d, t);
        }

        assertEquals(1000, hud.getLiveWindowCount(), "All 1,000 frames within 523ms must remain in window");
        assertEquals(100.0f, hud.getPointOnePercentLowFps(), 1.0f, "0.1% low must equal worst 1/1000 frame (100 FPS)");
        assertEquals(1_000_000_000.0f / 2_800_000.0f, hud.getOnePercentLowFps(), 1.0f, "1% low must average worst 10/1000 frames (~357.1 FPS)");
        assertTrue(hud.getPointOnePercentLowFps() < hud.getOnePercentLowFps(), "0.1% low must be strictly lower than 1% low");
        assertTrue(hud.getDiagnosticsLines()[1].contains("0.1% Low:"), "HUD line 1 must display 0.1% Low FPS");
    }

    @Test
    @DisplayName("DiagnosticHud onFrameAt maintains 2,500 FPS 1.0s rolling window in 4,096-entry ring buffer")
    public void testHighFpsRollingWindowCapacity() {
        DiagnosticHud hud = new DiagnosticHud();
        assertEquals(4096, DiagnosticHud.LIVE_WINDOW_CAPACITY);
        assertEquals(4096, hud.getLiveSortBuffer().length);

        long t = 1_000_000_000L;
        hud.onFrameAt(t); // seed initial timestamp
        long frameStep = 400_000L; // 0.4ms = 2,500 FPS
        for (int i = 0; i < 3000; i++) {
            t += frameStep;
            hud.onFrameAt(t);
        }

        // 1.0 second at 2,500 FPS = 2,500 frames retained (not 300!)
        assertEquals(2500, hud.getLiveWindowCount(),
                "At 2,500 FPS, 1.0-second rolling window must retain exactly 2,500 samples");
        assertEquals(2500.0f, hud.getAverageFps(), 5.0f);
        assertEquals(2500.0f, hud.getOnePercentLowFps(), 5.0f);
        assertEquals(2500.0f, hud.getPointOnePercentLowFps(), 5.0f);
    }
}
