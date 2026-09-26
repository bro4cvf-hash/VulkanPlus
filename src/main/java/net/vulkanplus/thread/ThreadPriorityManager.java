package net.vulkanplus.thread;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.SharedLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class ThreadPriorityManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("VulkanPlus/ThreadPriority");

    public static final int PRIORITY_RENDER = 7; // Thread.NORM_PRIORITY + 2 (was 8/9)
    public static final int PRIORITY_IO = 3;
    public static final int PRIORITY_WORKER = Thread.NORM_PRIORITY; // 5 (was 4, originally 1)

    // Windows AVRT_PRIORITY constants
    public static final int AVRT_PRIORITY_LOW = -1;
    public static final int AVRT_PRIORITY_NORMAL = 0;
    public static final int AVRT_PRIORITY_HIGH = 1;
    public static final int AVRT_PRIORITY_CRITICAL = 2;

    private static final long SWEEP_COOLDOWN_NS = 5_000_000_000L; // 5 seconds
    private static volatile boolean initialized = false;
    private static volatile long lastSweepTimeNs = 0L;
    private static volatile int lastSweepConfigHash = 0;

    // Windows native dynamic library bindings
    private static volatile boolean windowsNativesLoaded = false;
    private static volatile boolean avrtAvailable = false;
    private static volatile boolean winmmAvailable = false;

    private static SharedLibrary avrtLibrary = null;
    private static SharedLibrary winmmLibrary = null;

    private static long pAvSetMmThreadCharacteristicsW = 0L;
    private static long pAvSetMmThreadPriority = 0L;
    private static long pAvRevertMmThreadCharacteristics = 0L;
    private static long pTimeBeginPeriod = 0L;
    private static long pTimeEndPeriod = 0L;

    private static volatile long mmcssHandle = 0L;
    private static volatile boolean timerResolutionActive = false;

    /**
     * Detects if the current host platform is Windows.
     */
    public static boolean isWindows() {
        try {
            return Platform.get() == Platform.WINDOWS;
        } catch (Throwable ignored) {
            String os = System.getProperty("os.name");
            return os != null && os.toLowerCase().contains("win");
        }
    }

    /**
     * Dynamically loads Windows native libraries (avrt.dll and winmm.dll) using LWJGL 3.
     */
    private static synchronized void loadWindowsNatives() {
        if (windowsNativesLoaded) {
            return;
        }
        windowsNativesLoaded = true;

        if (!isWindows()) {
            return;
        }

        // Dynamically load avrt.dll for MMCSS
        try {
            avrtLibrary = Library.loadNative(ThreadPriorityManager.class, "net.vulkanplus", "avrt");
            if (avrtLibrary != null) {
                pAvSetMmThreadCharacteristicsW = avrtLibrary.getFunctionAddress("AvSetMmThreadCharacteristicsW");
                pAvSetMmThreadPriority = avrtLibrary.getFunctionAddress("AvSetMmThreadPriority");
                pAvRevertMmThreadCharacteristics = avrtLibrary.getFunctionAddress("AvRevertMmThreadCharacteristics");

                if (pAvSetMmThreadCharacteristicsW != 0L && pAvSetMmThreadPriority != 0L && pAvRevertMmThreadCharacteristics != 0L) {
                    avrtAvailable = true;
                    LOGGER.info("[ThreadTweak] Windows MMCSS subsystem successfully initialized (avrt.dll)");
                } else {
                    LOGGER.warn("[ThreadTweak] avrt.dll loaded but required MMCSS entry points are missing");
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[ThreadTweak] Windows MMCSS (avrt.dll) is unavailable on this system: {}", t.getMessage());
        }

        // Dynamically load winmm.dll for high-resolution timer period
        try {
            winmmLibrary = Library.loadNative(ThreadPriorityManager.class, "net.vulkanplus", "winmm");
            if (winmmLibrary != null) {
                pTimeBeginPeriod = winmmLibrary.getFunctionAddress("timeBeginPeriod");
                pTimeEndPeriod = winmmLibrary.getFunctionAddress("timeEndPeriod");

                if (pTimeBeginPeriod != 0L) {
                    winmmAvailable = true;
                    LOGGER.info("[ThreadTweak] Windows high-resolution timer subsystem successfully initialized (winmm.dll)");
                } else {
                    LOGGER.warn("[ThreadTweak] winmm.dll loaded but timeBeginPeriod entry point is missing");
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[ThreadTweak] Windows high-resolution timer (winmm.dll) is unavailable on this system: {}", t.getMessage());
        }

        // Register shutdown hook to revert any active OS-level changes on exit
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(ThreadPriorityManager::cleanupWindowsNatives, "VulkanPlus-MMCSS-Cleanup"));
        } catch (Throwable ignored) {}
    }

    /**
     * Elevates the calling thread using Windows MMCSS (Multimedia Class Scheduler Service).
     *
     * @param profile Task name profile registered in Windows Registry (e.g. "Games" or "Pro Audio").
     */
    public static synchronized void applyMmcss(String profile) {
        if (!isWindows()) return;
        loadWindowsNatives();
        if (!avrtAvailable) return;

        if (mmcssHandle != 0L) {
            revertMmcss();
        }

        String targetProfile = (profile == null || profile.isBlank()) ? "Games" : profile.trim();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer taskNameBuf = stack.UTF16(targetProfile);
            IntBuffer taskIndexBuf = stack.ints(0);

            long handle = JNI.callPPP(
                    MemoryUtil.memAddress(taskNameBuf),
                    MemoryUtil.memAddress(taskIndexBuf),
                    pAvSetMmThreadCharacteristicsW
            );

            if (handle != 0L) {
                mmcssHandle = handle;
                int priRes = JNI.callPI(handle, AVRT_PRIORITY_NORMAL, pAvSetMmThreadPriority);
                LOGGER.info("[ThreadTweak] Registered Render thread with Windows MMCSS (profile='{}', taskIndex={}, priority=NORMAL, handle=0x{}, priResult={})",
                        targetProfile, taskIndexBuf.get(0), Long.toHexString(handle), priRes);
            } else {
                LOGGER.warn("[ThreadTweak] AvSetMmThreadCharacteristicsW returned NULL for profile '{}'", targetProfile);
            }
        } catch (Throwable t) {
            LOGGER.warn("[ThreadTweak] MMCSS registration failed for profile '{}': {}", targetProfile, t.getMessage());
        }
    }

    /**
     * Reverts MMCSS registration for the calling thread if active.
     */
    public static synchronized void revertMmcss() {
        if (mmcssHandle != 0L && pAvRevertMmThreadCharacteristics != 0L) {
            try {
                int res = JNI.callPI(mmcssHandle, pAvRevertMmThreadCharacteristics);
                LOGGER.debug("[ThreadTweak] Reverted MMCSS registration (result={})", res);
            } catch (Throwable t) {
                LOGGER.warn("[ThreadTweak] Failed to revert MMCSS registration: {}", t.getMessage());
            } finally {
                mmcssHandle = 0L;
            }
        }
    }

    /**
     * Requests a 1ms high-resolution timer resolution via timeBeginPeriod(1).
     */
    public static synchronized void enableTimerResolution() {
        if (!isWindows()) return;
        loadWindowsNatives();
        if (!winmmAvailable || timerResolutionActive) return;

        try {
            int res = JNI.callI(1, pTimeBeginPeriod);
            if (res == 0) { // TIMERR_NOERROR
                timerResolutionActive = true;
                LOGGER.info("[ThreadTweak] Enabled 1ms high-resolution timer period via timeBeginPeriod(1)");
            } else {
                LOGGER.warn("[ThreadTweak] timeBeginPeriod(1) returned error code {}", res);
            }
        } catch (Throwable t) {
            LOGGER.warn("[ThreadTweak] Failed to call timeBeginPeriod(1): {}", t.getMessage());
        }
    }

    /**
     * Cancels high-resolution timer resolution via timeEndPeriod(1).
     */
    public static synchronized void disableTimerResolution() {
        if (timerResolutionActive && pTimeEndPeriod != 0L) {
            try {
                int res = JNI.callI(1, pTimeEndPeriod);
                LOGGER.debug("[ThreadTweak] Reverted high-resolution timer period (result={})", res);
            } catch (Throwable t) {
                LOGGER.warn("[ThreadTweak] Failed to call timeEndPeriod(1): {}", t.getMessage());
            } finally {
                timerResolutionActive = false;
            }
        }
    }

    /**
     * Cleanly reverts any OS-level timer resolution adjustments and MMCSS thread handles.
     */
    public static synchronized void cleanupWindowsNatives() {
        revertMmcss();
        disableTimerResolution();
    }

    /**
     * Clamps a priority value between MIN_PRIORITY and MAX_PRIORITY.
     */
    public static int clampPriority(int priority) {
        return Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, priority));
    }

    /**
     * Elevates the client Render thread to high priority and configures Windows MMCSS & timer period.
     * Can be invoked on Render thread startup or during client initialization.
     */
    public static void applyRenderThreadPriority() {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (!config.enabled) {
            return;
        }

        if (config.enableThreadPriority) {
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

        if (isWindows()) {
            if (config.enableTimerResolution) {
                enableTimerResolution();
            }
            if (config.enableMmcss) {
                applyMmcss(config.mmcssProfile);
            }
        }
    }

    /**
     * Intercepts and assigns priority to background ForkJoin worker threads.
     */
    public static void configureWorkerThread(Thread thread) {
        if (thread == null) return;
        VulkanPlusConfig config = ConfigManager.getConfig();
        int priority = (config.enabled && config.enableThreadPriority)
                ? Math.max(Thread.NORM_PRIORITY, clampPriority(config.workerThreadPriority))
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
        int workerPri = Math.max(Thread.NORM_PRIORITY, clampPriority(config.workerThreadPriority));
        int ioPri = clampPriority(config.ioThreadPriority);

        int currentConfigHash = (active ? 1 : 0) * 31
                + renderPri * 961
                + workerPri * 29791
                + ioPri * 923521
                + (config.enableMmcss ? 1 : 0) * 28629151
                + (config.enableTimerResolution ? 1 : 0) * 887503681
                + (config.mmcssProfile != null ? config.mmcssProfile.hashCode() : 0);

        long now = System.nanoTime();
        if (!force
                && lastSweepTimeNs != 0L
                && currentConfigHash == lastSweepConfigHash
                && (now - lastSweepTimeNs) < SWEEP_COOLDOWN_NS) {
            return;
        }
        lastSweepTimeNs = now;
        lastSweepConfigHash = currentConfigHash;

        if (isWindows()) {
            if (config.enabled && config.enableTimerResolution) {
                enableTimerResolution();
            } else {
                disableTimerResolution();
            }

            boolean isRenderThread = "Render thread".equals(Thread.currentThread().getName())
                    || "Minecraft main thread".equals(Thread.currentThread().getName());
            if (isRenderThread) {
                if (config.enabled && config.enableMmcss) {
                    applyMmcss(config.mmcssProfile);
                } else {
                    revertMmcss();
                }
            }
        }

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
                } else if (name.equals("Server thread")) {
                    t.setPriority(Thread.NORM_PRIORITY);
                } else if (name.startsWith("IO-Worker-") || name.contains("IO-Worker") || name.startsWith("Download-") || name.contains("Download-")) {
                    t.setPriority(active ? ioPri : Thread.NORM_PRIORITY);
                } else if (name.contains("Server-Worker") || name.contains("Worker-") || name.contains("Builder-") || name.startsWith("Main-")) {
                    t.setPriority(active ? workerPri : Thread.NORM_PRIORITY);
                }
            } catch (SecurityException | IllegalArgumentException ignored) {}
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isMmcssActive() {
        return mmcssHandle != 0L;
    }

    public static boolean isTimerResolutionActive() {
        return timerResolutionActive;
    }

    public static long getMmcssHandle() {
        return mmcssHandle;
    }

    public static boolean isAvrtAvailable() {
        loadWindowsNatives();
        return avrtAvailable;
    }

    public static boolean isWinmmAvailable() {
        loadWindowsNatives();
        return winmmAvailable;
    }
}
