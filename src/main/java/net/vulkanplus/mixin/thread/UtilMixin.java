package net.vulkanplus.mixin.thread;

import net.minecraft.util.Util;
import net.vulkanplus.thread.ThreadPriorityManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;

@Mixin(Util.class)
public abstract class UtilMixin {

    /**
     * Intercepts ForkJoinPool construction in Util.createWorker(String name).
     * Wraps the ForkJoinWorkerThreadFactory to deprioritize chunk/DFU worker threads to Thread.MIN_PRIORITY.
     */
    @Redirect(
            method = "createWorker",
            at = @At(
                    value = "NEW",
                    target = "java/util/concurrent/ForkJoinPool"
            )
    )
    private static ForkJoinPool redirectForkJoinPool(
            int parallelism,
            ForkJoinPool.ForkJoinWorkerThreadFactory factory,
            Thread.UncaughtExceptionHandler handler,
            boolean asyncMode) {

        ForkJoinPool.ForkJoinWorkerThreadFactory wrappedFactory = pool -> {
            ForkJoinWorkerThread thread = factory.newThread(pool);
            if (thread != null) {
                ThreadPriorityManager.configureWorkerThread(thread);
            }
            return thread;
        };

        return new ForkJoinPool(parallelism, wrappedFactory, handler, asyncMode);
    }

    /**
     * Intercepts Executors.newCachedThreadPool in Util.createIoWorker(String name, boolean daemon).
     * Wraps ThreadFactory to set I/O worker threads to priority 3.
     */
    @Redirect(
            method = "createIoWorker",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/Executors;newCachedThreadPool(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"
            )
    )
    private static ExecutorService redirectIoThreadPool(ThreadFactory factory) {
        ThreadFactory wrappedFactory = runnable -> {
            Thread thread = factory.newThread(runnable);
            if (thread != null) {
                ThreadPriorityManager.configureIoThread(thread);
            }
            return thread;
        };
        return Executors.newCachedThreadPool(wrappedFactory);
    }
}
