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
}
