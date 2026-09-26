package net.vulkanplus.pacing;

import net.vulkanplus.thread.ThreadPriorityManager;

import java.util.concurrent.locks.LockSupport;

/**
 * High-precision frame pacer providing sub-microsecond FPS limiting accuracy.
 * Uses a hybrid approach combining parkNanos for coarse sleep and Thread.onSpinWait() (x86 PAUSE)
 * for jitter-free sub-millisecond precision, bypassing GLFW's imprecise glfwWaitEventsTimeout.
 */
public final class HighPrecisionFramePacer {
    private static long nextFrameTimeNs = 0L;

    private HighPrecisionFramePacer() {
    }

    /**
     * Paces the current thread to achieve the target maximum framerate.
     *
     * @param maxFps Target frames per second limit.
     */
    public static void pace(int maxFps) {
        if (maxFps <= 0 || maxFps >= 260) {
            return;
        }

        if (!ThreadPriorityManager.isTimerResolutionActive()) {
            ThreadPriorityManager.enableTimerResolution();
        }

        long targetIntervalNs = 1_000_000_000L / maxFps;
        long now = System.nanoTime();

        if (nextFrameTimeNs == 0L || Math.abs(now - nextFrameTimeNs) > 2 * targetIntervalNs) {
            nextFrameTimeNs = now + targetIntervalNs;
        } else {
            nextFrameTimeNs += targetIntervalNs;
        }

        long remainingNs = nextFrameTimeNs - System.nanoTime();
        if (remainingNs > 1_500_000L) { // If > 1.5ms, sleep for (remaining - 1.2ms)
            LockSupport.parkNanos(remainingNs - 1_200_000L);
        }
        while (System.nanoTime() < nextFrameTimeNs) {
            Thread.onSpinWait(); // sub-microsecond precision with x86 PAUSE
        }
    }

    public static long getNextFrameTimeNs() {
        return nextFrameTimeNs;
    }

    /**
     * Resets the pacing timer, e.g. when unpausing or switching contexts.
     */
    public static void reset() {
        nextFrameTimeNs = 0L;
    }
}
