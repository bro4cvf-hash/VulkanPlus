package net.vulkanplus.mixin.vulkan;

import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.build.task.CompileResult;
import net.vulkanmod.render.chunk.build.task.TaskDispatcher;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.thread.ThreadPriorityManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Queue;

/**
 * Frame-time chunk upload budgeting, visibility-invariant SectionGraph traversal elimination,
 * and builder thread priority enforcement for VulkanMod's TaskDispatcher.
 */
@Mixin(value = TaskDispatcher.class, remap = false)
public abstract class TaskDispatcherMixin {

    @Unique
    private static final long UPLOAD_BUDGET_NANOS = 400_000L; // 0.4ms strict frame-time budget

    @Shadow
    @Final
    private Queue<CompileResult> compileResults;

    @Shadow
    private void doSectionUpdate(CompileResult compileResult) {
    }

    @Inject(method = "updateSections", at = @At("HEAD"), cancellable = true)
    private void vulkanplus$budgetedUpdateSections(CallbackInfoReturnable<Boolean> cir) {
        VulkanPlusConfig cfg = ConfigManager.getConfig();
        if (cfg == null || !cfg.enabled) {
            return;
        }

        CompileResult item = this.compileResults.poll();
        if (item == null) {
            cir.setReturnValue(false);
            return;
        }

        int backlog = this.compileResults.size();
        final long budgetNanos;
        if (backlog > 64) {
            budgetNanos = 1_500_000L; // 1.5ms under heavy chunk backlog
        } else if (backlog > 16) {
            budgetNanos = 800_000L;   // 0.8ms under moderate chunk backlog
        } else {
            budgetNanos = UPLOAD_BUDGET_NANOS; // 0.4ms default smooth budget
        }

        final long startTime = System.nanoTime();
        int uploadedCount = 0;
        boolean graphDirty = false;

        do {
            if (!item.fullUpdate) {
                // Translucent quad sorting (SortTransparencyTask) only updates the translucent index buffer
                // and never modifies chunk visibility, emptiness, or block entities.
                this.doSectionUpdate(item);
            } else {
                RenderSection section = item.renderSection;
                if (section == null) {
                    this.doSectionUpdate(item);
                    continue;
                }
                long prevVis = section.getVisibility();
                boolean prevEmpty = section.isCompletelyEmpty();
                boolean prevBE = section.containsBlockEntities();

                this.doSectionUpdate(item);

                if (section.getVisibility() != prevVis
                        || section.isCompletelyEmpty() != prevEmpty
                        || section.containsBlockEntities() != prevBE) {
                    graphDirty = true;
                }
            }

            if ((++uploadedCount & 3) == 0 && (System.nanoTime() - startTime >= budgetNanos)) {
                break;
            }
        } while ((item = this.compileResults.poll()) != null);

        cir.setReturnValue(graphDirty);
    }

    @Inject(method = "createThreads(I)V", at = @At("RETURN"))
    private void vulkanplus$onCreateBuilderThreads(int count, CallbackInfo ci) {
        ThreadPriorityManager.sweepAndApplyAll();
    }
}
