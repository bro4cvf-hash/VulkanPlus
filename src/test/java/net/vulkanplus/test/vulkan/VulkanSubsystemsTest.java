package net.vulkanplus.test.vulkan;

import net.vulkanplus.vulkan.PersistentPipelineCache;
import net.vulkanplus.vulkan.ReverseZProjection;
import net.vulkanplus.vulkan.SwapchainTuning;
import net.vulkanplus.vulkan.VulkanStateCache;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VulkanSubsystemsTest {

    @Test
    @DisplayName("PersistentPipelineCache rejects data with invalid size")
    public void testPsoCacheHeaderValidation() {
        PersistentPipelineCache cache = new PersistentPipelineCache();
        assertFalse(cache.saveCacheData(new byte[10]), "Must reject cache smaller than 32-byte header");
    }

    @Test
    @DisplayName("ReverseZProjection maps near plane to 1.0 and far plane to 0.0")
    public void testReverseZMapping() {
        float zNear = 0.1f;
        float zFar = 100.0f;
        Matrix4f revZ = ReverseZProjection.createFinitePerspective(
                (float) Math.toRadians(70.0), 1.0f, zNear, zFar, new Matrix4f()
        );

        // Near point in view space (0, 0, -zNear, 1)
        Vector4f nearClip = new Vector4f(0, 0, -zNear, 1.0f).mul(revZ);
        float nearNdcZ = nearClip.z / nearClip.w;
        assertEquals(1.0f, nearNdcZ, 1e-4f, "Near plane must map to 1.0 in Reverse-Z");

        // Far point in view space (0, 0, -zFar, 1)
        Vector4f farClip = new Vector4f(0, 0, -zFar, 1.0f).mul(revZ);
        float farNdcZ = farClip.z / farClip.w;
        assertEquals(0.0f, farNdcZ, 1e-4f, "Far plane must map to 0.0 in Reverse-Z");
    }

    @Test
    @DisplayName("VulkanStateCache filters duplicate pipeline and descriptor set bindings")
    public void testStateCacheFiltering() {
        VulkanStateCache cache = new VulkanStateCache();

        assertTrue(cache.checkAndBindPipeline(1001L));
        assertFalse(cache.checkAndBindPipeline(1001L));
        assertTrue(cache.checkAndBindPipeline(1002L));

        assertTrue(cache.checkAndBindDescriptorSet(501L));
        assertFalse(cache.checkAndBindDescriptorSet(501L));

        assertTrue(cache.checkViewport(0, 0, 1920, 1080));
        assertFalse(cache.checkViewport(0, 0, 1920, 1080));
        assertTrue(cache.checkViewport(0, 0, 1280, 720));

        assertTrue(cache.getRedundantBindsPrevented() >= 3);
        assertTrue(cache.getFilteringRatio() > 0.0f);
    }

    @Test
    @DisplayName("SwapchainTuning parses mode strings and selects optimal present mode")
    public void testSwapchainTuning() {
        assertEquals(SwapchainTuning.VK_PRESENT_MODE_MAILBOX_KHR, SwapchainTuning.parsePresentMode("MAILBOX"));
        assertEquals(SwapchainTuning.VK_PRESENT_MODE_IMMEDIATE_KHR, SwapchainTuning.parsePresentMode("IMMEDIATE"));
        assertEquals(SwapchainTuning.VK_PRESENT_MODE_FIFO_KHR, SwapchainTuning.parsePresentMode("FIFO"));

        List<Integer> available = List.of(
                SwapchainTuning.VK_PRESENT_MODE_FIFO_KHR,
                SwapchainTuning.VK_PRESENT_MODE_MAILBOX_KHR
        );

        int selected = SwapchainTuning.selectOptimalPresentMode(SwapchainTuning.VK_PRESENT_MODE_MAILBOX_KHR, available);
        assertEquals(SwapchainTuning.VK_PRESENT_MODE_MAILBOX_KHR, selected);

        List<Integer> fifoOnly = List.of(SwapchainTuning.VK_PRESENT_MODE_FIFO_KHR);
        int fallback = SwapchainTuning.selectOptimalPresentMode(SwapchainTuning.VK_PRESENT_MODE_MAILBOX_KHR, fifoOnly);
        assertEquals(SwapchainTuning.VK_PRESENT_MODE_FIFO_KHR, fallback);
    }
}
