package net.vulkanplus.ui;

import net.vulkanplus.bridge.VulkanDetector;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.render.RenderOptimizer;

import java.util.Arrays;

/**
 * High-precision frame time and performance diagnostics tracker.
 * Maintains a 300-frame circular buffer to compute average FPS and 1% low frame times.
 */
public class DiagnosticHud {
    public static final int BUFFER_CAPACITY = 300;
    public static final long RECALCULATE_INTERVAL_NANOS = 200_000_000L; // ~200ms throttle

    private final long[] frameTimesNanos = new long[BUFFER_CAPACITY];
    private final long[] sortBuffer = new long[BUFFER_CAPACITY];
    private int bufferIndex = 0;
    private int samplesCount = 0;

    private long lastFrameTimeNanos = System.nanoTime();
    private long lastRecalculateTimeNanos = 0;

    private float currentFps = 0.0f;
    private float averageFps = 0.0f;
    private float onePercentLowFps = 0.0f;

    public void onFrame() {
        long now = System.nanoTime();
        long duration = now - lastFrameTimeNanos;
        lastFrameTimeNanos = now;

        if (duration <= 0) return;

        frameTimesNanos[bufferIndex] = duration;
        bufferIndex = (bufferIndex + 1) % BUFFER_CAPACITY;
        if (samplesCount < BUFFER_CAPACITY) {
            samplesCount++;
        }

        currentFps = 1_000_000_000.0f / duration;

        // Throttle recalculation to ~200ms in onFrame() to eliminate per-frame sort churn
        if (now - lastRecalculateTimeNanos >= RECALCULATE_INTERVAL_NANOS) {
            recalculateMetrics();
            lastRecalculateTimeNanos = now;
        }
    }

    public void recordSample(long durationNanos) {
        if (durationNanos <= 0) return;
        frameTimesNanos[bufferIndex] = durationNanos;
        bufferIndex = (bufferIndex + 1) % BUFFER_CAPACITY;
        if (samplesCount < BUFFER_CAPACITY) {
            samplesCount++;
        }
        recalculateMetrics();
    }

    private void recalculateMetrics() {
        if (samplesCount == 0) return;

        long sum = 0;
        System.arraycopy(frameTimesNanos, 0, sortBuffer, 0, samplesCount);
        for (int i = 0; i < samplesCount; i++) {
            sum += sortBuffer[i];
        }

        averageFps = 1_000_000_000.0f / ((float) sum / samplesCount);

        // Sort only the active slice in the preallocated buffer
        Arrays.sort(sortBuffer, 0, samplesCount);
        int onePercentCount = Math.max(1, samplesCount / 100);
        long worstDurationSum = 0;
        for (int i = samplesCount - onePercentCount; i < samplesCount; i++) {
            worstDurationSum += sortBuffer[i];
        }
        float avgWorstDuration = (float) worstDurationSum / onePercentCount;
        onePercentLowFps = 1_000_000_000.0f / avgWorstDuration;
    }

    public String[] getDiagnosticsLines() {
        VulkanPlusConfig config = ConfigManager.getConfig();
        long vramUsedMb = VulkanDetector.getBridge().getVramUsed() / (1024 * 1024);
        long vramAllocMb = VulkanDetector.getBridge().getVramAllocated() / (1024 * 1024);

        return new String[]{
                String.format("Vulkan Plus [1.21.11] - %s [%s]", VulkanDetector.getBridge().getEngineName(), config.enabled ? "ACTIVE" : "PAUSED"),
                String.format("FPS: %.1f | Avg: %.1f | 1%% Low: %.1f", currentFps, averageFps, onePercentLowFps),
                String.format("VRAM: %d MB / %d MB", vramUsedMb, vramAllocMb),
                String.format("Culled: %d entities | %d particles",
                        RenderOptimizer.getFrustumCuller().getCulledEntitiesCount(),
                        RenderOptimizer.getParticleCuller().getCulledParticleCount()),
                String.format("Preset: %s | Present: %s", config.activePreset.getDisplayName(), config.presentMode)
        };
    }

    public float getCurrentFps() {
        return currentFps;
    }

    public float getAverageFps() {
        return averageFps;
    }

    public float getOnePercentLowFps() {
        return onePercentLowFps;
    }

    public int getSamplesCount() {
        return samplesCount;
    }

    public long[] getSortBuffer() {
        return sortBuffer;
    }

    public void reset() {
        bufferIndex = 0;
        samplesCount = 0;
        currentFps = 0.0f;
        averageFps = 0.0f;
        onePercentLowFps = 0.0f;
        lastRecalculateTimeNanos = 0;
        Arrays.fill(frameTimesNanos, 0);
        Arrays.fill(sortBuffer, 0);
    }
}
