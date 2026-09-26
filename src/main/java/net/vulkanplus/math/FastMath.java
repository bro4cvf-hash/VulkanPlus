package net.vulkanplus.math;

/**
 * High-performance, branchless, vectorized trigonometric and arithmetic utilities.
 * Uses high-precision polynomial approximations (accurate to within 1e-5 radians)
 * to eliminate expensive native CPU trigonometric instructions in hot render paths.
 */
public final class FastMath {
    public static final float PI = (float) Math.PI;
    public static final float TWO_PI = PI * 2.0f;
    public static final float HALF_PI = PI * 0.5f;
    public static final float INV_TWO_PI = 1.0f / TWO_PI;

    // Cody-Waite split of 2*PI for pure-float high-accuracy range reduction
    private static final float TWO_PI_HI = 6.283185f;
    private static final float TWO_PI_LO = 3.019916e-7f;

    // Minimax polynomial coefficients for sin(x) on [-pi/2, pi/2]
    private static final float S1 = -1.6666657e-1f;
    private static final float S2 = 8.333017e-3f;
    private static final float S3 = -1.984090e-4f;
    private static final float S4 = 2.7526e-6f;

    private FastMath() {}

    /**
     * Fast polynomial sine approximation. Accurate to within ~1e-5.
     */
    public static float sin(float rad) {
        // Pure float branchless range reduction to [-PI, PI]
        int k = (int) (rad * INV_TWO_PI + (rad >= 0.0f ? 0.5f : -0.5f));
        float x = Math.fma(-k, TWO_PI_HI, rad) - k * TWO_PI_LO;

        // Symmetry reduction to [-PI/2, PI/2]
        if (x > HALF_PI) {
            x = PI - x;
        } else if (x < -HALF_PI) {
            x = -PI - x;
        }

        float x2 = x * x;
        return x * (1.0f + x2 * (S1 + x2 * (S2 + x2 * (S3 + x2 * S4))));
    }

    public static final float DEG_TO_RAD = PI / 180.0f;
    public static final float RAD_TO_DEG = 180.0f / PI;

    /**
     * Fast polynomial cosine approximation: cos(x) = sin(x + PI/2).
     */
    public static float cos(float rad) {
        return sin(rad + HALF_PI);
    }

    /**
     * Fast polynomial sine for degree inputs (eliminates explicit Math.toRadians conversion).
     */
    public static float sinDeg(float degrees) {
        return sin(degrees * DEG_TO_RAD);
    }

    /**
     * Fast polynomial cosine for degree inputs (eliminates explicit Math.toRadians conversion).
     */
    public static float cosDeg(float degrees) {
        return cos(degrees * DEG_TO_RAD);
    }

    /**
     * Fast 2D Euclidean distance (hypot) avoiding slow Math.hypot double arithmetic.
     */
    public static float fastHypot(float x, float z) {
        return fastSqrt(x * x + z * z);
    }

    public static double fastHypot(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    /**
     * Fast 3D vector length (magnitude).
     */
    public static float fastLength3D(float x, float y, float z) {
        return fastSqrt(x * x + y * y + z * z);
    }

    /**
     * Fast 3D inverse magnitude for vector normalization without division.
     */
    public static float fastInvLength3D(float x, float y, float z) {
        return fastInvSqrt(x * x + y * y + z * z);
    }

    /**
     * Fast inverse square root (1 / sqrt(x)) using Newton-Raphson refinement.
     */
    public static float fastInvSqrt(float x) {
        float xhalf = 0.5f * x;
        int i = Float.floatToIntBits(x);
        i = 0x5f3759df - (i >> 1);
        x = Float.intBitsToFloat(i);
        x = x * (1.5f - xhalf * x * x); // 1st iteration
        return x * (1.5f - xhalf * x * x); // 2nd iteration for double precision
    }

    /**
     * Fast square root using inverse square root.
     */
    public static float fastSqrt(float x) {
        if (x <= 0.0f) return 0.0f;
        return x * fastInvSqrt(x);
    }

    /**
     * Fast branchless clamp.
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Branchless linear interpolation.
     */
    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    /**
     * Fast branchless floor for float.
     */
    public static int fastFloor(float value) {
        int i = (int) value;
        return value < (float) i ? i - 1 : i;
    }

    /**
     * Fast branchless floor for double.
     */
    public static int fastFloor(double value) {
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
    }

    /**
     * Fast branchless ceil for float.
     */
    public static int fastCeil(float value) {
        int i = (int) value;
        return value > (float) i ? i + 1 : i;
    }

    /**
     * Fast atan2 approximation using rational polynomial.
     */
    public static float atan2(float y, float x) {
        if (x == 0.0f) {
            if (y > 0.0f) return HALF_PI;
            if (y == 0.0f) return 0.0f;
            return -HALF_PI;
        }

        float absX = Math.abs(x);
        float absY = Math.abs(y);
        float a = Math.min(absX, absY) / Math.max(absX, absY);
        float s = a * a;
        float r = ((-0.0464964749f * s + 0.15931422f) * s - 0.327622764f) * s * a + a;

        if (absY > absX) r = HALF_PI - r;
        if (x < 0.0f) r = PI - r;
        if (y < 0.0f) r = -r;

        return r;
    }
}
