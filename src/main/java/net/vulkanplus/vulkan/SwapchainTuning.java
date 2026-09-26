package net.vulkanplus.vulkan;

import java.nio.IntBuffer;
import java.util.ArrayList;
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
     * Strictly prefers: Preferred Mode -> MAILBOX -> FIFO_RELAXED -> FIFO -> IMMEDIATE.
     */
    public static int selectOptimalPresentMode(int preferredMode, List<Integer> availableModes) {
        if (availableModes == null || availableModes.isEmpty()) {
            return VK_PRESENT_MODE_FIFO_KHR;
        }

        if (availableModes.contains(preferredMode)) {
            return preferredMode;
        }

        if (availableModes.contains(VK_PRESENT_MODE_MAILBOX_KHR)) {
            return VK_PRESENT_MODE_MAILBOX_KHR;
        }

        if (availableModes.contains(VK_PRESENT_MODE_FIFO_RELAXED_KHR)) {
            return VK_PRESENT_MODE_FIFO_RELAXED_KHR;
        }

        if (availableModes.contains(VK_PRESENT_MODE_FIFO_KHR)) {
            return VK_PRESENT_MODE_FIFO_KHR;
        }

        if (availableModes.contains(VK_PRESENT_MODE_IMMEDIATE_KHR)) {
            return VK_PRESENT_MODE_IMMEDIATE_KHR;
        }

        return availableModes.get(0);
    }

    /**
     * Overload for IntBuffer representing available present modes.
     */
    public static int selectOptimalPresentMode(int preferredMode, IntBuffer availableModes) {
        if (availableModes == null || availableModes.limit() == 0) {
            return VK_PRESENT_MODE_FIFO_KHR;
        }
        List<Integer> list = new ArrayList<>(availableModes.limit());
        for (int i = 0; i < availableModes.limit(); i++) {
            list.add(availableModes.get(i));
        }
        return selectOptimalPresentMode(preferredMode, list);
    }

    /**
     * Determines the optimal swapchain image count to prevent starvation.
     * When MAILBOX or FIFO is used, returns Math.max(3, minImageCount + 1)
     * (clamped to maxImageCount if maxImageCount > 0), preventing AMD Mailbox 2-image starvation.
     */
    public static int getOptimalImageCount(int presentMode, int minImageCount, int maxImageCount) {
        int count;
        if (presentMode == VK_PRESENT_MODE_MAILBOX_KHR || presentMode == VK_PRESENT_MODE_FIFO_KHR || presentMode == VK_PRESENT_MODE_FIFO_RELAXED_KHR) {
            count = Math.max(3, minImageCount + 1);
        } else {
            count = minImageCount + 1;
        }

        if (maxImageCount > 0 && count > maxImageCount) {
            count = maxImageCount;
        }

        return Math.max(count, minImageCount);
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
