package net.vulkanplus.render;

/**
 * Pure, headless-safe, zero-allocation evaluator for Distance-Tiered Entity Animation LOD.
 */
public final class AnimationLodEvaluator {

    private AnimationLodEvaluator() {
    }

    /**
     * Computes the animation tick interval (quantization tier) based on squared distance to camera.
     *
     * @param distSq      Squared distance from the entity to the camera
     * @param baseLodDist Base LOD distance threshold in blocks/meters (default ~32m)
     * @return 1 (Tier 0: uncapped), 2 (Tier 1: ~30 Hz), 4 (Tier 2: ~15 Hz), or 8 (Tier 3: ~8 Hz / static horizon)
     */
    public static int getTickInterval(double distSq, double baseLodDist) {
        if (!Double.isFinite(distSq) || distSq < 0.0) {
            return 1;
        }
        double d1 = Math.max(8.0, baseLodDist * 0.5);
        double d2 = Math.max(16.0, baseLodDist);
        double d3 = Math.max(24.0, baseLodDist * 1.5);

        if (distSq < d1 * d1) {
            return 1;
        }
        if (distSq < d2 * d2) {
            return 2;
        }
        if (distSq < d3 * d3) {
            return 4;
        }
        return 8;
    }

    /**
     * Determines whether an entity should update its animation frame on the given frame index.
     * Uses unsigned bitmasks so negative entity IDs never produce negative modulo results.
     */
    public static boolean shouldUpdateFrame(long frameIndex, int entityId, int interval) {
        if (interval <= 1) {
            return true;
        }
        return ((frameIndex & 0x7FFFFFFFL) + (entityId & 0x7FFFFFFFL)) % interval == 0;
    }

    /**
     * Quantizes a floating-point angle or animation progress value to the nearest step.
     */
    public static float quantizeAngle(float value, float step) {
        if (!Float.isFinite(value) || step <= 0.0f) {
            return value;
        }
        return Math.round(value / step) * step;
    }
}
