package net.vulkanplus.vulkan;

import net.vulkanmod.render.shader.PipelineManager;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanplus.VulkanPlusMod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pre-warms Vulkan GraphicsPipeline state objects (PSOs) ahead of time
 * to prevent frame stutter caused by on-demand shader compilation during rendering.
 */
public final class PipelinePrewarmer {
    private static final AtomicBoolean PREWARMED = new AtomicBoolean(false);

    private PipelinePrewarmer() {
    }

    /**
     * Pre-warms terrain, early-Z, fast-blit, and clouds pipelines with common pipeline state permutations.
     * Executes only once unless {@link #reset()} is called.
     *
     * @param renderPass the active render pass to construct pipeline states with
     */
    public static void prewarm(RenderPass renderPass) {
        if (renderPass == null) {
            return;
        }

        if (!PREWARMED.compareAndSet(false, true)) {
            return;
        }

        long startTime = System.currentTimeMillis();
        int prewarmedCount = 0;

        try {
            List<PipelineState> states = createCommonPipelineStates(renderPass);

            // Iterate through all TerrainRenderType values (SOLID, CUTOUT_MIPPED, CUTOUT, TRANSLUCENT, TRIPWIRE)
            for (TerrainRenderType type : TerrainRenderType.values()) {
                try {
                    GraphicsPipeline pipeline = PipelineManager.getTerrainShader(type);
                    if (pipeline != null) {
                        prewarmedCount += prewarmPipeline(pipeline, states);
                    }
                } catch (Throwable t) {
                    VulkanPlusMod.LOGGER.debug("[VulkanPlus] Skipping terrain pipeline for {}: {}", type, t.getMessage());
                }
            }

            // Also pre-warm getTerrainShaderEarlyZ(), fastBlitPipeline, and cloudsPipeline if non-null
            GraphicsPipeline earlyZ = getTerrainShaderEarlyZ();
            if (earlyZ != null) {
                prewarmedCount += prewarmPipeline(earlyZ, states);
            }

            GraphicsPipeline fastBlit = getFastBlitPipeline();
            if (fastBlit != null) {
                prewarmedCount += prewarmPipeline(fastBlit, states);
            }

            GraphicsPipeline clouds = getCloudsPipeline();
            if (clouds != null) {
                prewarmedCount += prewarmPipeline(clouds, states);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            VulkanPlusMod.LOGGER.info("[VulkanPlus] Prewarmed {} pipeline state objects in {} ms", prewarmedCount, elapsed);
        } catch (Throwable t) {
            VulkanPlusMod.LOGGER.warn("[VulkanPlus] Pipeline pre-warming encountered an error: {}", t.getMessage());
        }
    }

    /**
     * Builds permutations of common pipeline states (blend on/off, depth test on/off, cull back/none).
     */
    private static List<PipelineState> createCommonPipelineStates(RenderPass renderPass) {
        List<PipelineState> states = new ArrayList<>();

        int blendOff = 0;
        int blendOn = PipelineState.BlendState.getState(PipelineState.defaultBlendInfo());
        int[] blendStates = new int[]{blendOff, blendOn};

        int depthOff = 0;
        int depthOn = PipelineState.DepthState.DEPTH_TEST_BIT
                | PipelineState.DepthState.DEPTH_MASK_BIT
                | PipelineState.DepthState.encodeDepthFun(515); // 515 = GL_LEQUAL
        int depthOnNoWrite = PipelineState.DepthState.DEPTH_TEST_BIT
                | PipelineState.DepthState.encodeDepthFun(515);
        int[] depthStates = new int[]{depthOn, depthOnNoWrite, depthOff};

        int cullBack = PipelineState.AssemblyRasterState.encode(true, 0, 0);
        int cullNone = PipelineState.AssemblyRasterState.encode(false, 0, 0);
        int[] cullStates = new int[]{cullBack, cullNone};

        int logicOp = 0;
        int colorMask = PipelineState.ColorMask.getColorMask(true, true, true, true);

        for (int blend : blendStates) {
            for (int depth : depthStates) {
                for (int cull : cullStates) {
                    states.add(new PipelineState(cull, blend, depth, logicOp, colorMask, renderPass));
                }
            }
        }

        return states;
    }

    private static int prewarmPipeline(GraphicsPipeline pipeline, List<PipelineState> states) {
        int count = 0;
        for (PipelineState state : states) {
            try {
                long handle = pipeline.getHandle(state);
                if (handle != 0L) {
                    count++;
                }
            } catch (Throwable ignored) {
            }
        }
        return count;
    }

    public static GraphicsPipeline getTerrainShaderEarlyZ() {
        try {
            try {
                Method m = PipelineManager.class.getDeclaredMethod("getTerrainShaderEarlyZ");
                m.setAccessible(true);
                return (GraphicsPipeline) m.invoke(null);
            } catch (NoSuchMethodException e) {
                Field f = PipelineManager.class.getDeclaredField("terrainShaderEarlyZ");
                f.setAccessible(true);
                return (GraphicsPipeline) f.get(null);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    public static GraphicsPipeline getFastBlitPipeline() {
        try {
            return PipelineManager.getFastBlitPipeline();
        } catch (Throwable t) {
            try {
                Field f = PipelineManager.class.getDeclaredField("fastBlitPipeline");
                f.setAccessible(true);
                return (GraphicsPipeline) f.get(null);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    public static GraphicsPipeline getCloudsPipeline() {
        try {
            return PipelineManager.getCloudsPipeline();
        } catch (Throwable t) {
            try {
                Field f = PipelineManager.class.getDeclaredField("cloudsPipeline");
                f.setAccessible(true);
                return (GraphicsPipeline) f.get(null);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    /**
     * Resets the prewarmer flag, allowing prewarm to be executed again on resource reloads.
     */
    public static void reset() {
        PREWARMED.set(false);
    }

    /**
     * Returns whether pipeline pre-warming has already executed.
     */
    public static boolean isPrewarmed() {
        return PREWARMED.get();
    }
}
