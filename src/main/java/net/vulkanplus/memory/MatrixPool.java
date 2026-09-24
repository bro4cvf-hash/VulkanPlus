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

    private final Deque<Matrix4f> matrix4fPool = new ArrayDeque<>();
    private final Deque<Matrix3f> matrix3fPool = new ArrayDeque<>();
    private final Deque<Vector3f> vector3fPool = new ArrayDeque<>();
    private final Deque<Quaternionf> quatPool = new ArrayDeque<>();

    public MatrixPool() {
        for (int i = 0; i < 4; i++) {
            matrix4fPool.offerFirst(new Matrix4f());
        }
        for (int i = 0; i < 2; i++) {
            matrix3fPool.offerFirst(new Matrix3f());
            vector3fPool.offerFirst(new Vector3f());
            quatPool.offerFirst(new Quaternionf());
        }
    }

    public static MatrixPool get() {
        return POOL.get();
    }

    public Matrix4f allocMatrix4f() {
        Matrix4f m = matrix4fPool.pollFirst();
        if (m == null) {
            return new Matrix4f();
        }
        return m.identity();
    }

    public void free(Matrix4f m) {
        if (m != null && matrix4fPool.size() < 256) {
            matrix4fPool.offerFirst(m);
        }
    }

    public Matrix3f allocMatrix3f() {
        Matrix3f m = matrix3fPool.pollFirst();
        if (m == null) {
            return new Matrix3f();
        }
        return m.identity();
    }

    public void free(Matrix3f m) {
        if (m != null && matrix3fPool.size() < 256) {
            matrix3fPool.offerFirst(m);
        }
    }

    public Vector3f allocVector3f() {
        Vector3f v = vector3fPool.pollFirst();
        if (v == null) {
            return new Vector3f();
        }
        return v.set(0.0f, 0.0f, 0.0f);
    }

    public void free(Vector3f v) {
        if (v != null && vector3fPool.size() < 256) {
            vector3fPool.offerFirst(v);
        }
    }

    public Quaternionf allocQuaternionf() {
        Quaternionf q = quatPool.pollFirst();
        if (q == null) {
            return new Quaternionf();
        }
        return q.identity();
    }

    public void free(Quaternionf q) {
        if (q != null && quatPool.size() < 256) {
            quatPool.offerFirst(q);
        }
    }

    /**
     * Creates an AutoCloseable scope that automatically recycles all allocated objects on exit.
     */
    public static Scope openScope() {
        return new Scope(get());
    }

    public static class Scope implements AutoCloseable {
        private final MatrixPool pool;
        private final Deque<Matrix4f> allocatedMat4 = new ArrayDeque<>();
        private final Deque<Matrix3f> allocatedMat3 = new ArrayDeque<>();
        private final Deque<Vector3f> allocatedVec3 = new ArrayDeque<>();
        private final Deque<Quaternionf> allocatedQuat = new ArrayDeque<>();

        public Scope(MatrixPool pool) {
            this.pool = pool;
        }

        public Matrix4f matrix4f() {
            Matrix4f m = pool.allocMatrix4f();
            allocatedMat4.push(m);
            return m;
        }

        public Matrix3f matrix3f() {
            Matrix3f m = pool.allocMatrix3f();
            allocatedMat3.push(m);
            return m;
        }

        public Vector3f vector3f() {
            Vector3f v = pool.allocVector3f();
            allocatedVec3.push(v);
            return v;
        }

        public Quaternionf quat() {
            Quaternionf q = pool.allocQuaternionf();
            allocatedQuat.push(q);
            return q;
        }

        @Override
        public void close() {
            while (!allocatedMat4.isEmpty()) pool.free(allocatedMat4.pop());
            while (!allocatedMat3.isEmpty()) pool.free(allocatedMat3.pop());
            while (!allocatedVec3.isEmpty()) pool.free(allocatedVec3.pop());
            while (!allocatedQuat.isEmpty()) pool.free(allocatedQuat.pop());
        }
    }
}
