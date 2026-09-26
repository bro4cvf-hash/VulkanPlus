package net.vulkanplus.test.pacing;

import net.vulkanplus.pacing.HighPrecisionFramePacer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HighPrecisionFramePacerTest {

    @Test
    @DisplayName("Pacer interval calculation and reset handling")
    void testIntervalCalculation() {
        HighPrecisionFramePacer.reset();
        assertEquals(0L, HighPrecisionFramePacer.getNextFrameTimeNs());

        // Test pacing at 120 FPS
        long start = System.nanoTime();
        HighPrecisionFramePacer.pace(120);
        long elapsed = System.nanoTime() - start;

        // Pacer should establish a future target and execute without throwing
        assertTrue(HighPrecisionFramePacer.getNextFrameTimeNs() > 0L);

        HighPrecisionFramePacer.reset();
        assertEquals(0L, HighPrecisionFramePacer.getNextFrameTimeNs());
    }

    @Test
    @DisplayName("Pacer ignores invalid or uncapped FPS limits")
    void testInvalidLimits() {
        HighPrecisionFramePacer.reset();
        HighPrecisionFramePacer.pace(0);
        assertEquals(0L, HighPrecisionFramePacer.getNextFrameTimeNs());

        HighPrecisionFramePacer.pace(-10);
        assertEquals(0L, HighPrecisionFramePacer.getNextFrameTimeNs());

        HighPrecisionFramePacer.pace(300); // Exceeds 260 limit
        assertEquals(0L, HighPrecisionFramePacer.getNextFrameTimeNs());
    }
}
