package net.vulkanplus.thread;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadPriorityManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("VulkanPlus/ThreadPriority");

    public static final int PRIORITY_RENDER = 8; // 8 or 9
    public static final int PRIORITY_IO = 3;
    public static final int PRIORITY_WORKER = Thread.MIN_PRIORITY; // 1

    private static final long SWEEP_COOLDOWN_NS = 5_000_000_000L; // 5 seconds
    private static volatile boolean initialized = false;
    private static volatile long lastSweepTimeNs = 0L;
    private static volatile int lastSweepConfigHash = 0;

    /**
     * Clamps a priority value between MIN_PRIORITY and MAX_PRIORITY.
     */
    public static int clampPriority(int priority) {
        return Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, priority));
    }

    /**
     * Elevates the client Render thread to high priority.
     * Can be invoked on Render thread startup or during client initialization.
     */
    public static void applyRenderThreadPriority() {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (!config.enabled || !config.enableThreadPriority) {
            return;
        }

        Thread current = Thread.currentThread();
        int target = clampPriority(config.renderThreadPriority);
        try {
            current.setPriority(target);
            LOGGER.info("[ThreadTweak] Elevated client Render thread '{}' to priority {}", current.getName(), target);
            initialized = true;
        } catch (SecurityException | IllegalArgumentException e) {
            LOGGER.warn("[ThreadTweak] Could not adjust Render thread priority: {}", e.getMessage());
        }
    }

    /**
     * Intercepts and assigns priority to background ForkJoin worker threads.
     */
    public static void configureWorkerThread(Thread thread) {
        if (thread == null) return;
        VulkanPlusConfig config = ConfigManager.getConfig();
        int priority = (config.enabled && config.enableThreadPriority)
                ? clampPriority(config.workerThreadPriority)
                : Thread.NORM_PRIORITY;
        try {
            thread.setPriority(priority);
        } catch (SecurityException | IllegalArgumentException ignored) {}
    }

    /**
     * Intercepts and assigns priority to I/O and download worker threads.
     */
    public static void configureIoThread(Thread thread) {
        if (thread == null) return;
        VulkanPlusConfig config = ConfigManager.getConfig();
        int priority = (config.enabled && config.enableThreadPriority)
                ? clampPriority(config.ioThreadPriority)
                : Thread.NORM_PRIORITY;
        try {
            thread.setPriority(priority);
        } catch (SecurityException | IllegalArgumentException ignored) {}
    }

    /**
     * Sweeps all active JVM threads and updates their priorities dynamically.
     * Invoked when settings are modified in the configuration screen or on demand.
     */
    public static void sweepAndApplyAll() {
        sweepAndApplyAll(true);
    }

    public static void sweepAndApplyAll(boolean force) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        boolean active = config.enabled && config.enableThreadPriority;

        int renderPri = clampPriority(config.renderThreadPriority);
        int workerPri = clampPriority(config.workerThreadPriority);
        int ioPri = clampPriority(config.ioThreadPriority);

        int currentConfigHash = (active ? 1 : 0) * 31
                + renderPri * 961
                + workerPri * 29791
                + ioPri * 923521;

        long now = System.nanoTime();
        if (!force
                && lastSweepTimeNs != 0L
                && currentConfigHash == lastSweepConfigHash
                && (now - lastSweepTimeNs) < SWEEP_COOLDOWN_NS) {
            return;
        }
        lastSweepTimeNs = now;
        lastSweepConfigHash = currentConfigHash;

        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        while (rootGroup.getParent() != null) {
            rootGroup = rootGroup.getParent();
        }
        Thread[] threads = new Thread[rootGroup.activeCount() * 2 + 16];
        int count = rootGroup.enumerate(threads, true);

        for (int i = 0; i < count; i++) {
            Thread t = threads[i];
            if (t == null || !t.isAlive()) continue;
            String name = t.getName();
            if (name == null) continue;

            try {
                if (name.equals("Render thread") || name.equals("Minecraft main thread")) {
                    t.setPriority(active ? renderPri : Thread.NORM_PRIORITY);
                } else if (name.startsWith("Worker-") || name.startsWith("Server-Worker") || name.startsWith("Main-") || name.startsWith("Builder-")) {
                    t.setPriority(active ? workerPri : Thread.NORM_PRIORITY);
                } else if (name.startsWith("IO-Worker-") || name.startsWith("Download-")) {
                    t.setPriority(active ? ioPri : Thread.NORM_PRIORITY);
                } else if (name.equals("Server thread")) {
                    t.setPriority(Thread.NORM_PRIORITY);
                }
            } catch (SecurityException | IllegalArgumentException ignored) {}
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
