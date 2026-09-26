package net.vulkanplus.memory;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-local object pool for transformation matrices and vectors.
 * Eradicates temporary object creation in hot chunk meshing and entity rendering passes.
 */
public class MatrixPool {
    private static final ThreadLocal<MatrixPool> POOL = ThreadLocal.withInitial(MatrixPool::new);
    private static final int MAX_SCOPE_DEPTH = 32;
    private static final int POOL_CAPACITY = 64;

    // Fast static scratch instances for zero-allocation single-threaded passes (RenderOptimizer)
    private static final Matrix4f STATIC_MAT4 = new Matrix4f();
    private static final Matrix3f STATIC_MAT3 = new Matrix3f();
    private static final Vector3f STATIC_VEC3 = new Vector3f();
    private static final Quaternionf STATIC_QUAT = new Quaternionf();

    // Direct flat array stacks to eliminate ThreadLocal queue overhead
    private final Matrix4f[] mat4Pool = new Matrix4f[POOL_CAPACITY];
    private int mat4Top = 0;
    private final Matrix3f[] mat3Pool = new Matrix3f[POOL_CAPACITY];
    private int mat3Top = 0;
    private final Vector3f[] vec3Pool = new Vector3f[POOL_CAPACITY];
    private int vec3Top = 0;
    private final Quaternionf[] quatPool = new Quaternionf[POOL_CAPACITY];
    private int quatTop = 0;

    // Fast thread-cached scratch instances
    private final Matrix4f threadCachedMat4 = new Matrix4f();
    private final Matrix3f threadCachedMat3 = new Matrix3f();
    private final Vector3f threadCachedVec3 = new Vector3f();
    private final Quaternionf threadCachedQuat = new Quaternionf();

    private final Scope reusableScope = new Scope(this);

    public MatrixPool() {
        for (int i = 0; i < 8; i++) {
            mat4Pool[mat4Top++] = new Matrix4f();
            vec3Pool[vec3Top++] = new Vector3f();
        }
        for (int i = 0; i < 4; i++) {
            mat3Pool[mat3Top++] = new Matrix3f();
            quatPool[quatTop++] = new Quaternionf();
        }
    }

    public static MatrixPool get() {
        return POOL.get();
    }

    public static Matrix4f getStaticMatrix4f() {
        return get().getThreadCachedMatrix4f();
    }

    public static Matrix3f getStaticMatrix3f() {
        return get().getThreadCachedMatrix3f();
    }

    public static Vector3f getStaticVector3f() {
        return get().getThreadCachedVector3f();
    }

    public static Quaternionf getStaticQuaternionf() {
        return get().getThreadCachedQuaternionf();
    }

    public Matrix4f getThreadCachedMatrix4f() {
        return threadCachedMat4.identity();
    }

    public Matrix3f getThreadCachedMatrix3f() {
        return threadCachedMat3.identity();
    }

    public Vector3f getThreadCachedVector3f() {
        return threadCachedVec3.set(0.0f, 0.0f, 0.0f);
    }

    public Quaternionf getThreadCachedQuaternionf() {
        return threadCachedQuat.identity();
    }

    public Matrix4f allocMatrix4f() {
        if (mat4Top > 0) {
            return mat4Pool[--mat4Top].identity();
        }
        return new Matrix4f();
    }

    public void free(Matrix4f m) {
        if (m == null || m == threadCachedMat4 || m == STATIC_MAT4 || mat4Top >= POOL_CAPACITY) {
            return;
        }
        for (int i = 0; i < mat4Top; i++) {
            if (mat4Pool[i] == m) {
                return;
            }
        }
        mat4Pool[mat4Top++] = m;
    }

    public Matrix3f allocMatrix3f() {
        if (mat3Top > 0) {
            return mat3Pool[--mat3Top].identity();
        }
        return new Matrix3f();
    }

    public void free(Matrix3f m) {
        if (m == null || m == threadCachedMat3 || m == STATIC_MAT3 || mat3Top >= POOL_CAPACITY) {
            return;
        }
        for (int i = 0; i < mat3Top; i++) {
            if (mat3Pool[i] == m) {
                return;
            }
        }
        mat3Pool[mat3Top++] = m;
    }

    public Vector3f allocVector3f() {
        if (vec3Top > 0) {
            return vec3Pool[--vec3Top].set(0.0f, 0.0f, 0.0f);
        }
        return new Vector3f();
    }

    public void free(Vector3f v) {
        if (v == null || v == threadCachedVec3 || v == STATIC_VEC3 || vec3Top >= POOL_CAPACITY) {
            return;
        }
        for (int i = 0; i < vec3Top; i++) {
            if (vec3Pool[i] == v) {
                return;
            }
        }
        vec3Pool[vec3Top++] = v;
    }

    public Quaternionf allocQuaternionf() {
        if (quatTop > 0) {
            return quatPool[--quatTop].identity();
        }
        return new Quaternionf();
    }

    public void free(Quaternionf q) {
        if (q == null || q == threadCachedQuat || q == STATIC_QUAT || quatTop >= POOL_CAPACITY) {
            return;
        }
        for (int i = 0; i < quatTop; i++) {
            if (quatPool[i] == q) {
                return;
            }
        }
        quatPool[quatTop++] = q;
    }

    /**
     * Creates an AutoCloseable scope that automatically recycles all allocated objects on exit.
     * Uses a thread-local cursor stack to perform zero heap allocations in steady state,
     * while supporting arbitrary nested scopes.
     */
    public static Scope openScope() {
        return get().reusableScope.enter();
    }

    public static class Scope implements AutoCloseable {
        private final MatrixPool pool;

        private Matrix4f[] mat4Stack = new Matrix4f[16];
        private Matrix3f[] mat3Stack = new Matrix3f[8];
        private Vector3f[] vec3Stack = new Vector3f[16];
        private Quaternionf[] quatStack = new Quaternionf[8];

        private int mat4Top = 0;
        private int mat3Top = 0;
        private int vec3Top = 0;
        private int quatTop = 0;

        private int[] mat4Marks = new int[MAX_SCOPE_DEPTH];
        private int[] mat3Marks = new int[MAX_SCOPE_DEPTH];
        private int[] vec3Marks = new int[MAX_SCOPE_DEPTH];
        private int[] quatMarks = new int[MAX_SCOPE_DEPTH];
        private int depth = 0;

        public Scope(MatrixPool pool) {
            this.pool = pool;
        }

        Scope enter() {
            if (depth >= mat4Marks.length) {
                int newLen = mat4Marks.length << 1;
                mat4Marks = java.util.Arrays.copyOf(mat4Marks, newLen);
                mat3Marks = java.util.Arrays.copyOf(mat3Marks, newLen);
                vec3Marks = java.util.Arrays.copyOf(vec3Marks, newLen);
                quatMarks = java.util.Arrays.copyOf(quatMarks, newLen);
            }
            mat4Marks[depth] = mat4Top;
            mat3Marks[depth] = mat3Top;
            vec3Marks[depth] = vec3Top;
            quatMarks[depth] = quatTop;
            depth++;
            return this;
        }

        public Matrix4f allocMat4() {
            Matrix4f m = pool.allocMatrix4f();
            if (mat4Top >= mat4Stack.length) {
                mat4Stack = java.util.Arrays.copyOf(mat4Stack, mat4Stack.length << 1);
            }
            mat4Stack[mat4Top++] = m;
            return m;
        }

        public Matrix3f allocMat3() {
            Matrix3f m = pool.allocMatrix3f();
            if (mat3Top >= mat3Stack.length) {
                mat3Stack = java.util.Arrays.copyOf(mat3Stack, mat3Stack.length << 1);
            }
            mat3Stack[mat3Top++] = m;
            return m;
        }

        public Vector3f allocVec3() {
            Vector3f v = pool.allocVector3f();
            if (vec3Top >= vec3Stack.length) {
                vec3Stack = java.util.Arrays.copyOf(vec3Stack, vec3Stack.length << 1);
            }
            vec3Stack[vec3Top++] = v;
            return v;
        }

        public Quaternionf allocQuat() {
            Quaternionf q = pool.allocQuaternionf();
            if (quatTop >= quatStack.length) {
                quatStack = java.util.Arrays.copyOf(quatStack, quatStack.length << 1);
            }
            quatStack[quatTop++] = q;
            return q;
        }

        public Matrix4f matrix4f() {
            return allocMat4();
        }

        public Matrix3f matrix3f() {
            return allocMat3();
        }

        public Vector3f vector3f() {
            return allocVec3();
        }

        public Quaternionf quat() {
            return allocQuat();
        }

        @Override
        public void close() {
            int targetMat4 = 0;
            int targetMat3 = 0;
            int targetVec3 = 0;
            int targetQuat = 0;
            if (depth > 0) {
                depth--;
                targetMat4 = mat4Marks[depth];
                targetMat3 = mat3Marks[depth];
                targetVec3 = vec3Marks[depth];
                targetQuat = quatMarks[depth];
            }
            while (mat4Top > targetMat4) {
                Matrix4f m = mat4Stack[--mat4Top];
                mat4Stack[mat4Top] = null;
                pool.free(m);
            }
            while (mat3Top > targetMat3) {
                Matrix3f m = mat3Stack[--mat3Top];
                mat3Stack[mat3Top] = null;
                pool.free(m);
            }
            while (vec3Top > targetVec3) {
                Vector3f v = vec3Stack[--vec3Top];
                vec3Stack[vec3Top] = null;
                pool.free(v);
            }
            while (quatTop > targetQuat) {
                Quaternionf q = quatStack[--quatTop];
                quatStack[quatTop] = null;
                pool.free(q);
            }
        }
    }
}
