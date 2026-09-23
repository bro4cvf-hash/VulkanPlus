package net.vulkanplus.vulkan;

import org.joml.Matrix4f;

/**
 * Calculates Reverse-Z floating-point projection matrices for Vulkan rendering.
 * Maps Near plane to 1.0 and Far plane to 0.0 in Vulkan's [0, 1] clip depth range.
 * Pairs with VK_COMPARE_OP_GREATER_OR_EQUAL and D32_SFLOAT to virtually eliminate z-fighting
 * and maximize Early-Z rejection at high render distances.
 */
public class ReverseZProjection {

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

        float f = 1.0f / (float) Math.tan(fovYRadians * 0.5f);

        dest.m00(f / aspectRatio);
        dest.m11(f);
        dest.m22(0.0f); // Far at infinity
        dest.m23(-1.0f);
        dest.m32(zNear); // Maps Near to 1.0
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

        float f = 1.0f / (float) Math.tan(fovYRadians * 0.5f);

        dest.m00(f / aspectRatio);
        dest.m11(f);
        dest.m22(zNear / (zFar - zNear));
        dest.m23(-1.0f);
        dest.m32((zFar * zNear) / (zFar - zNear));
        dest.m33(0.0f);

        return dest;
    }

    /**
     * Converts a standard Minecraft projection matrix into a Reverse-Z projection matrix.
     */
    public static Matrix4f convertToReverseZ(Matrix4f proj, float zNear, float zFar) {
        Matrix4f result = new Matrix4f(proj);
        result.m22(zNear / (zFar - zNear));
        result.m32((zFar * zNear) / (zFar - zNear));
        return result;
    }
}
