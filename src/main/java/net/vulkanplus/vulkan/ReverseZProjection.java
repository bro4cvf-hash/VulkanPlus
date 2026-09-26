package net.vulkanplus.vulkan;

import org.joml.Matrix4f;

/**
 * Calculates Reverse-Z floating-point projection matrices for Vulkan rendering.
 * Maps Near plane to 1.0 and Far plane to 0.0 in Vulkan's [0, 1] clip depth range.
 * Pairs with VK_COMPARE_OP_GREATER_OR_EQUAL and D32_SFLOAT to virtually eliminate z-fighting
 * and maximize Early-Z rejection at high render distances.
 */
public class ReverseZProjection {
    private static final float DEFAULT_FOV_RADIANS = (float) Math.toRadians(70.0);
    private static final float MIN_FOV_RADIANS = 1.0e-4f;
    private static final float MAX_FOV_RADIANS = (float) Math.PI - 1.0e-4f;
    private static final float DEFAULT_ASPECT_RATIO = 16.0f / 9.0f;
    private static final float MIN_ASPECT_RATIO = 1.0e-4f;
    private static final float DEFAULT_Z_NEAR = 0.05f;
    private static final float MIN_Z_NEAR = 1.0e-5f;
    private static final float DEFAULT_DEPTH_SPAN = 4096.0f;
    private static final float MIN_DEPTH_SPAN = 1.0e-4f;

    private static float sanitizeFovFocalLength(float fovYRadians) {
        float safeFov = (!Float.isFinite(fovYRadians) || fovYRadians <= MIN_FOV_RADIANS || fovYRadians >= MAX_FOV_RADIANS)
                ? DEFAULT_FOV_RADIANS
                : fovYRadians;
        float tanHalf = (float) Math.tan(safeFov * 0.5f);
        if (!Float.isFinite(tanHalf) || Math.abs(tanHalf) < MIN_FOV_RADIANS) {
            return 1.0f / (float) Math.tan(DEFAULT_FOV_RADIANS * 0.5f);
        }
        return 1.0f / tanHalf;
    }

    private static float sanitizeAspectRatio(float aspectRatio) {
        if (!Float.isFinite(aspectRatio) || Math.abs(aspectRatio) < MIN_ASPECT_RATIO) {
            return DEFAULT_ASPECT_RATIO;
        }
        return aspectRatio;
    }

    private static float sanitizeZNear(float zNear) {
        if (!Float.isFinite(zNear) || zNear <= MIN_Z_NEAR) {
            return DEFAULT_Z_NEAR;
        }
        return zNear;
    }

    private static float sanitizeDepthSpan(float safeNear, float zFar) {
        if (!Float.isFinite(zFar)) {
            return DEFAULT_DEPTH_SPAN;
        }
        float span = zFar - safeNear;
        if (!Float.isFinite(span) || Math.abs(span) < MIN_DEPTH_SPAN) {
            return DEFAULT_DEPTH_SPAN;
        }
        return span;
    }

    /**
     * Constructs a Reverse-Z perspective projection matrix with an infinite far plane.
     *
     * @param fovYRadians Vertical field of view in radians
     * @param aspectRatio Viewport width / height
     * @param zNear Near clipping plane (e.g. 0.05f)
     * @param dest Destination matrix
     */
    public static Matrix4f createInfinitePerspective(float fovYRadians, float aspectRatio, float zNear, Matrix4f dest) {
        if (dest == null) dest = new Matrix4f();
        dest.zero();

        float f = sanitizeFovFocalLength(fovYRadians);
        float safeAspect = sanitizeAspectRatio(aspectRatio);
        float safeNear = sanitizeZNear(zNear);

        dest.m00(f / safeAspect);
        dest.m11(f);
        dest.m22(0.0f); // Far at infinity
        dest.m23(-1.0f);
        dest.m32(safeNear); // Maps Near to 1.0
        dest.m33(0.0f);

        return dest;
    }

    /**
     * Constructs a Reverse-Z perspective projection matrix with a finite far plane.
     *
     * @param fovYRadians Vertical field of view in radians
     * @param aspectRatio Viewport width / height
     * @param zNear Near clipping plane
     * @param zFar Far clipping plane
     * @param dest Destination matrix
     */
    public static Matrix4f createFinitePerspective(float fovYRadians, float aspectRatio, float zNear, float zFar, Matrix4f dest) {
        if (dest == null) dest = new Matrix4f();
        dest.zero();

        float f = sanitizeFovFocalLength(fovYRadians);
        float safeAspect = sanitizeAspectRatio(aspectRatio);
        float safeNear = sanitizeZNear(zNear);
        float span = sanitizeDepthSpan(safeNear, zFar);
        float safeFar = safeNear + span;

        dest.m00(f / safeAspect);
        dest.m11(f);
        dest.m22(safeNear / span);
        dest.m23(-1.0f);
        dest.m32((safeFar * safeNear) / span);
        dest.m33(0.0f);

        return dest;
    }

    /**
     * Converts a standard Minecraft projection matrix into a Reverse-Z projection matrix without heap allocation.
     */
    public static Matrix4f convertToReverseZ(Matrix4f proj, float zNear, float zFar, Matrix4f dest) {
        if (dest == null) dest = new Matrix4f();
        if (proj == null) {
            return createFinitePerspective(DEFAULT_FOV_RADIANS, DEFAULT_ASPECT_RATIO, zNear, zFar, dest);
        }
        if (dest != proj) {
            dest.set(proj);
        }
        float safeNear = sanitizeZNear(zNear);
        float span = sanitizeDepthSpan(safeNear, zFar);
        float safeFar = safeNear + span;

        if (!Float.isFinite(dest.m00()) || dest.m00() == 0.0f) {
            dest.m00(sanitizeFovFocalLength(DEFAULT_FOV_RADIANS) / DEFAULT_ASPECT_RATIO);
        }
        if (!Float.isFinite(dest.m11()) || dest.m11() == 0.0f) {
            dest.m11(sanitizeFovFocalLength(DEFAULT_FOV_RADIANS));
        }

        dest.m22(safeNear / span);
        dest.m32((safeFar * safeNear) / span);
        return dest;
    }

    /**
     * Converts a standard Minecraft projection matrix into a Reverse-Z projection matrix.
     */
    public static Matrix4f convertToReverseZ(Matrix4f proj, float zNear, float zFar) {
        return convertToReverseZ(proj, zNear, zFar, new Matrix4f());
    }

    /**
     * Fast 2-instruction projection converter for Vulkan Reverse-Z projection.
     * Transforms standard projection matrix into Reverse-Z space without trigonometry.
     *
     * @param proj Source projection matrix
     * @param dest Destination projection matrix
     */
    public static void convertToReverseZFast(Matrix4f proj, Matrix4f dest) {
        if (dest == null) {
            return;
        }
        if (proj == null) {
            createInfinitePerspective(DEFAULT_FOV_RADIANS, DEFAULT_ASPECT_RATIO, DEFAULT_Z_NEAR, dest);
            return;
        }
        if (dest != proj) {
            dest.set(proj);
        }
        float m00 = proj.m00();
        float m11 = proj.m11();
        float m22 = proj.m22();
        float m32 = proj.m32();
        if (!Float.isFinite(m00) || m00 == 0.0f) {
            dest.m00(sanitizeFovFocalLength(DEFAULT_FOV_RADIANS) / DEFAULT_ASPECT_RATIO);
        }
        if (!Float.isFinite(m11) || m11 == 0.0f) {
            dest.m11(sanitizeFovFocalLength(DEFAULT_FOV_RADIANS));
        }
        dest.m22(Float.isFinite(m22) ? (-1.0f - m22) : 0.0f);
        dest.m32((Float.isFinite(m32) && m32 != 0.0f) ? (-m32) : DEFAULT_Z_NEAR);
    }
}
