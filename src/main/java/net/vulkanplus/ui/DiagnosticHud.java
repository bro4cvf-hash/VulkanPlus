package net.vulkanplus.ui;

import net.vulkanplus.bridge.VulkanDetector;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.render.RenderOptimizer;

import java.util.Arrays;

/**
 * High-precision frame time and performance diagnostics tracker.
 * Uses a 4,096-entry nanosecond-timestamped circular ring buffer pruned to a 1.0-second
 * rolling window for live frame telemetry (computing Average, 1% Low, and 0.1% Low FPS
 * with zero per-frame heap allocations), while preserving the 300-sample deterministic
 * buffer for backward-compatible unit testing via {@link #recordSample(long)}.
 */
public class DiagnosticHud {
    public static final int BUFFER_CAPACITY = 300;
    public static final int LIVE_WINDOW_CAPACITY = 4096;
    public static final long ROLLING_WINDOW_NANOS = 1_000_000_000L; // 1.0s rolling window
    public static final long RECALCULATE_INTERVAL_NANOS = 200_000_000L; // ~200ms throttle

    // Legacy 300-sample deterministic ring buffer (for recordSample backward compatibility)
    private final long[] frameTimesNanos = new long[BUFFER_CAPACITY];
    private final long[] sortBuffer = new long[BUFFER_CAPACITY];
    private int bufferIndex = 0;
    private int samplesCount = 0;

    // Live 4,096-entry nanosecond-timestamped 1.0s rolling window ring buffer
    private final long[] liveDurationsNanos = new long[LIVE_WINDOW_CAPACITY];
    private final long[] liveTimestampsNanos = new long[LIVE_WINDOW_CAPACITY];
    private final long[] liveSortBuffer = new long[LIVE_WINDOW_CAPACITY];
    private int liveHead = 0;
    private int liveTail = 0;
    private int liveCount = 0;
    private boolean usingLiveWindow = false;
    private boolean liveMetricsDirty = false;

    private final String[] cachedLines = new String[5];
    private final StringBuilder lineBuilder = new StringBuilder(96);
    private String cachedFpsOverlayText = "0 FPS";
    private int lastOverlayFpsValue = -1;
    private boolean linesDirty = true;
    private boolean lastEnabledState = false;
    private Object lastPresetState = null;
    private String lastPresentModeState = null;

    private long lastFrameTimeNanos = System.nanoTime();
    private long lastRecalculateTimeNanos = 0;
    private boolean frameRecordedThisPass = false;

    private float currentFps = 0.0f;
    private float averageFps = 0.0f;
    private float onePercentLowFps = 0.0f;
    private float pointOnePercentLowFps = 0.0f;

    /**
     * Called once per 3D world frame (even when Exordium throttles InGameHud.render)
     * so 3D frame deltas and 1% / 0.1% lows never freeze at the HUD target framerate.
     */
    public void onFrameTick() {
        onFrameAt(System.nanoTime());
        frameRecordedThisPass = true;
    }

    /**
     * Called during the HUD render callback; records a frame sample only if
     * {@link #onFrameTick()} did not already record the current 3D render pass.
     */
    public void onHudRenderPass() {
        if (frameRecordedThisPass) {
            frameRecordedThisPass = false;
            return;
        }
        onFrameAt(System.nanoTime());
    }

    /**
     * Decoupled 3D frame delta recorder that accepts an explicit frame delta in nanoseconds.
     */
    public void recordFrameTime(long frameDeltaNs) {
        if (frameDeltaNs <= 0) return;
        long now = lastFrameTimeNanos + frameDeltaNs;
        lastFrameTimeNanos = now;
        appendLiveSample(frameDeltaNs, now);
        currentFps = 1_000_000_000.0f / frameDeltaNs;
        if (lastRecalculateTimeNanos == 0
                || now - lastRecalculateTimeNanos >= RECALCULATE_INTERVAL_NANOS
                || now < lastRecalculateTimeNanos) {
            recalculateLiveMetrics();
            lastRecalculateTimeNanos = now;
        } else {
            liveMetricsDirty = true;
        }
    }

    public void onFrame() {
        onFrameAt(System.nanoTime());
    }

    /**
     * Records a live frame completion at the specified nanosecond timestamp,
     * pruning samples outside the 1.0-second rolling window.
     */
    public void onFrameAt(long timestampNanos) {
        long duration = timestampNanos - lastFrameTimeNanos;
        lastFrameTimeNanos = timestampNanos;

        if (duration <= 0) return;

        appendLiveSample(duration, timestampNanos);
        currentFps = 1_000_000_000.0f / duration;

        // Throttle sorting/recalculation to ~200ms in live rendering to eliminate per-frame churn
        if (lastRecalculateTimeNanos == 0
                || timestampNanos - lastRecalculateTimeNanos >= RECALCULATE_INTERVAL_NANOS
                || timestampNanos < lastRecalculateTimeNanos) {
            recalculateLiveMetrics();
            lastRecalculateTimeNanos = timestampNanos;
        } else {
            liveMetricsDirty = true;
        }
    }

    /**
     * Deterministically records a frame duration at an explicit nanosecond timestamp
     * into the 1.0-second rolling window and immediately recalculates all metrics.
     */
    public void recordTimedSample(long durationNanos, long timestampNanos) {
        if (durationNanos <= 0) return;
        lastFrameTimeNanos = timestampNanos;
        appendLiveSample(durationNanos, timestampNanos);
        currentFps = 1_000_000_000.0f / durationNanos;
        recalculateLiveMetrics();
        lastRecalculateTimeNanos = timestampNanos;
    }

    private void appendLiveSample(long durationNanos, long timestampNanos) {
        usingLiveWindow = true;
        if (liveCount == LIVE_WINDOW_CAPACITY) {
            liveHead = (liveHead + 1) & (LIVE_WINDOW_CAPACITY - 1);
            liveCount--;
        }
        liveDurationsNanos[liveTail] = durationNanos;
        liveTimestampsNanos[liveTail] = timestampNanos;
        liveTail = (liveTail + 1) & (LIVE_WINDOW_CAPACITY - 1);
        liveCount++;

        pruneExpiredSamples(timestampNanos);
    }

    private void pruneExpiredSamples(long currentTimestampNanos) {
        while (liveCount > 1 && (currentTimestampNanos - liveTimestampsNanos[liveHead]) >= ROLLING_WINDOW_NANOS) {
            liveHead = (liveHead + 1) & (LIVE_WINDOW_CAPACITY - 1);
            liveCount--;
        }
    }

    public void recordSample(long durationNanos) {
        if (durationNanos <= 0) return;
        usingLiveWindow = false;
        frameTimesNanos[bufferIndex] = durationNanos;
        bufferIndex = (bufferIndex + 1) % BUFFER_CAPACITY;
        if (samplesCount < BUFFER_CAPACITY) {
            samplesCount++;
        }
        currentFps = 1_000_000_000.0f / durationNanos;
        recalculateMetrics();
    }

    private void recalculateMetrics() {
        if (samplesCount == 0) return;

        long sum = 0;
        System.arraycopy(frameTimesNanos, 0, sortBuffer, 0, samplesCount);
        for (int i = 0; i < samplesCount; i++) {
            sum += sortBuffer[i];
        }

        recalculateFromBuffer(sortBuffer, samplesCount, sum);
    }

    private void recalculateLiveMetrics() {
        if (liveCount == 0) return;

        long sum = 0;
        int idx = liveHead;
        for (int i = 0; i < liveCount; i++) {
            long d = liveDurationsNanos[idx];
            liveSortBuffer[i] = d;
            sum += d;
            idx = (idx + 1) & (LIVE_WINDOW_CAPACITY - 1);
        }

        recalculateFromBuffer(liveSortBuffer, liveCount, sum);
        liveMetricsDirty = false;
    }

    private void recalculateFromBuffer(long[] buffer, int count, long sum) {
        averageFps = 1_000_000_000.0f / ((float) sum / count);

        // Sort only the active slice in the preallocated buffer (zero heap allocations)
        Arrays.sort(buffer, 0, count);

        int onePercentCount = Math.max(1, count / 100);
        long worstDurationSum = 0;
        for (int i = count - onePercentCount; i < count; i++) {
            worstDurationSum += buffer[i];
        }
        float avgWorstDuration = (float) worstDurationSum / onePercentCount;
        onePercentLowFps = 1_000_000_000.0f / avgWorstDuration;

        int pointOnePercentCount = Math.max(1, count / 1000);
        long worstPointOneDurationSum = 0;
        for (int i = count - pointOnePercentCount; i < count; i++) {
            worstPointOneDurationSum += buffer[i];
        }
        float avgWorstPointOneDuration = (float) worstPointOneDurationSum / pointOnePercentCount;
        pointOnePercentLowFps = 1_000_000_000.0f / avgWorstPointOneDuration;

        linesDirty = true;
    }

    private static void appendOneDecimal(StringBuilder sb, float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0.0f) {
            sb.append("0.0");
            return;
        }
        int scaled = Math.round(value * 10.0f);
        sb.append(scaled / 10).append('.').append(scaled % 10);
    }

    public String getFormattedFpsOverlay(int fps) {
        if (fps != lastOverlayFpsValue) {
            lastOverlayFpsValue = fps;
            lineBuilder.setLength(0);
            lineBuilder.append(fps).append(" FPS");
            cachedFpsOverlayText = lineBuilder.toString();
        }
        return cachedFpsOverlayText;
    }

    public String[] getDiagnosticsLines() {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null) {
            config = new VulkanPlusConfig();
        }
        String presentModeDisplay = config.presentMode != null ? config.presentMode : "MAILBOX";
        boolean configChanged = (config.enabled != lastEnabledState)
                || (config.activePreset != lastPresetState)
                || !java.util.Objects.equals(presentModeDisplay, lastPresentModeState);

        if (linesDirty || configChanged || cachedLines[0] == null) {
            var bridge = VulkanDetector.getBridge();
            long vramUsedMb = bridge != null ? Math.max(0L, bridge.getVramUsed()) / (1024 * 1024) : 0L;
            long vramAllocMb = bridge != null ? Math.max(0L, bridge.getVramAllocated()) / (1024 * 1024) : 0L;
            String engineName = (bridge != null && bridge.getEngineName() != null) ? bridge.getEngineName() : "Unknown";
            String presetDisplay = config.activePreset != null ? config.activePreset.getDisplayName() : "Custom";

            lineBuilder.setLength(0);
            lineBuilder.append("Vulkan Plus [1.21.11] - ")
                    .append(engineName)
                    .append(" [").append(config.enabled ? "ACTIVE" : "PAUSED").append(']');
            cachedLines[0] = lineBuilder.toString();

            lineBuilder.setLength(0);
            lineBuilder.append("FPS: ");
            appendOneDecimal(lineBuilder, currentFps);
            lineBuilder.append(" | Avg: ");
            appendOneDecimal(lineBuilder, averageFps);
            lineBuilder.append(" | 1% Low: ");
            appendOneDecimal(lineBuilder, onePercentLowFps);
            lineBuilder.append(" | 0.1% Low: ");
            appendOneDecimal(lineBuilder, pointOnePercentLowFps);
            cachedLines[1] = lineBuilder.toString();

            lineBuilder.setLength(0);
            lineBuilder.append("VRAM: ").append(vramUsedMb).append(" MB / ").append(vramAllocMb).append(" MB");
            cachedLines[2] = lineBuilder.toString();

            long culledEntities = RenderOptimizer.getFrustumCuller() != null
                    ? RenderOptimizer.getFrustumCuller().getCulledEntitiesCount() : 0L;
            long culledParticles = RenderOptimizer.getParticleCuller() != null
                    ? RenderOptimizer.getParticleCuller().getCulledParticleCount() : 0L;

            lineBuilder.setLength(0);
            lineBuilder.append("Culled: ").append(culledEntities)
                    .append(" entities | ").append(culledParticles)
                    .append(" particles");
            cachedLines[3] = lineBuilder.toString();

            lineBuilder.setLength(0);
            lineBuilder.append("Preset: ").append(presetDisplay).append(" | Present: ").append(presentModeDisplay);
            cachedLines[4] = lineBuilder.toString();

            lastEnabledState = config.enabled;
            lastPresetState = config.activePreset;
            lastPresentModeState = presentModeDisplay;
            linesDirty = false;
        }
        return cachedLines;
    }

    public float getCurrentFps() {
        return currentFps;
    }

    public float getAverageFps() {
        if (usingLiveWindow && liveMetricsDirty) {
            recalculateLiveMetrics();
        }
        return averageFps;
    }

    public float getOnePercentLowFps() {
        if (usingLiveWindow && liveMetricsDirty) {
            recalculateLiveMetrics();
        }
        return onePercentLowFps;
    }

    public float getPointOnePercentLowFps() {
        if (usingLiveWindow && liveMetricsDirty) {
            recalculateLiveMetrics();
        }
        return pointOnePercentLowFps;
    }

    public float getZeroPointOnePercentLowFps() {
        return getPointOnePercentLowFps();
    }

    public int getSamplesCount() {
        return usingLiveWindow ? liveCount : samplesCount;
    }

    public int getLiveWindowCount() {
        return liveCount;
    }

    public long[] getSortBuffer() {
        return sortBuffer;
    }

    public long[] getLiveSortBuffer() {
        return liveSortBuffer;
    }

    public void reset() {
        bufferIndex = 0;
        samplesCount = 0;
        liveHead = 0;
        liveTail = 0;
        liveCount = 0;
        usingLiveWindow = false;
        liveMetricsDirty = false;
        currentFps = 0.0f;
        averageFps = 0.0f;
        onePercentLowFps = 0.0f;
        pointOnePercentLowFps = 0.0f;
        lastFrameTimeNanos = System.nanoTime();
        lastRecalculateTimeNanos = 0;
        frameRecordedThisPass = false;
        linesDirty = true;
        Arrays.fill(frameTimesNanos, 0);
        Arrays.fill(sortBuffer, 0);
        Arrays.fill(liveDurationsNanos, 0);
        Arrays.fill(liveTimestampsNanos, 0);
        Arrays.fill(liveSortBuffer, 0);
    }
}
