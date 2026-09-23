package net.vulkanplus.mixin.thread;

import net.minecraft.util.Util;
import net.vulkanplus.thread.ThreadPriorityManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;

@Mixin(Util.class)
public abstract class UtilMixin {

    /**
     * Intercepts ForkJoinPool construction in Util.createWorker(String name).
     * Wraps the ForkJoinWorkerThreadFactory to deprioritize chunk/DFU worker threads to Thread.MIN_PRIORITY.
     */
    @ModifyArg(
            method = "createWorker",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/ForkJoinPool;<init>(ILjava/util/concurrent/ForkJoinPool$ForkJoinWorkerThreadFactory;Ljava/lang/Thread$UncaughtExceptionHandler;Z)V"
            ),
            index = 1
    )
    private static ForkJoinPool.ForkJoinWorkerThreadFactory wrapWorkerFactory(
            ForkJoinPool.ForkJoinWorkerThreadFactory factory) {

        return pool -> {
            ForkJoinWorkerThread thread = factory.newThread(pool);
            if (thread != null) {
                ThreadPriorityManager.configureWorkerThread(thread);
            }
            return thread;
        };
    }

    /**
     * Intercepts Executors.newCachedThreadPool in Util.createIoWorker(String name, boolean daemon).
     * Wraps ThreadFactory to set I/O worker threads to priority 3.
     */
    @ModifyArg(
            method = "createIoWorker",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/Executors;newCachedThreadPool(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"
            ),
            index = 0
    )
    private static ThreadFactory wrapIoThreadPool(ThreadFactory factory) {
        return runnable -> {
            Thread thread = factory.newThread(runnable);
            if (thread != null) {
                ThreadPriorityManager.configureIoThread(thread);
            }
            return thread;
        };
    }
}
