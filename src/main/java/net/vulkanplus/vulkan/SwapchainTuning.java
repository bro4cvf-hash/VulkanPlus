package net.vulkanplus.vulkan;

import java.util.List;

/**
 * Handles swapchain present mode resolution and frame latency tuning.
 */
public class SwapchainTuning {
    public static final int VK_PRESENT_MODE_IMMEDIATE_KHR = 0;
    public static final int VK_PRESENT_MODE_MAILBOX_KHR = 1;
    public static final int VK_PRESENT_MODE_FIFO_KHR = 2;
    public static final int VK_PRESENT_MODE_FIFO_RELAXED_KHR = 3;

    /**
     * Resolves the desired present mode string into Vulkan present mode enum.
     */
    public static int parsePresentMode(String modeName) {
        if (modeName == null) return VK_PRESENT_MODE_FIFO_KHR;
        return switch (modeName.toUpperCase().trim()) {
            case "IMMEDIATE" -> VK_PRESENT_MODE_IMMEDIATE_KHR;
            case "MAILBOX" -> VK_PRESENT_MODE_MAILBOX_KHR;
            case "FIFO_RELAXED" -> VK_PRESENT_MODE_FIFO_RELAXED_KHR;
            default -> VK_PRESENT_MODE_FIFO_KHR;
        };
    }

    /**
     * Selects the best supported present mode given user preference and hardware capabilities.
     * Prefers MAILBOX (triple-buffering without vsync latency), falling back to FIFO (guaranteed by Vulkan spec).
     */
    public static int selectOptimalPresentMode(int preferredMode, List<Integer> availableModes) {
        if (availableModes == null || availableModes.isEmpty()) {
            return VK_PRESENT_MODE_FIFO_KHR;
        }

        if (availableModes.contains(preferredMode)) {
            return preferredMode;
        }

        if (preferredMode == VK_PRESENT_MODE_MAILBOX_KHR && availableModes.contains(VK_PRESENT_MODE_FIFO_RELAXED_KHR)) {
            return VK_PRESENT_MODE_FIFO_RELAXED_KHR;
        }

        if (availableModes.contains(VK_PRESENT_MODE_FIFO_KHR)) {
            return VK_PRESENT_MODE_FIFO_KHR;
        }

        return availableModes.getFirst();
    }

    public static String getModeName(int mode) {
        return switch (mode) {
            case VK_PRESENT_MODE_IMMEDIATE_KHR -> "Immediate (Uncapped, Tearable)";
            case VK_PRESENT_MODE_MAILBOX_KHR -> "Mailbox (Ultra Low Latency Triple-Buffering)";
            case VK_PRESENT_MODE_FIFO_KHR -> "FIFO (VSync Locked)";
            case VK_PRESENT_MODE_FIFO_RELAXED_KHR -> "FIFO Relaxed (Adaptive VSync)";
            default -> "Unknown (" + mode + ")";
        };
    }
}
