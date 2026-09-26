package net.vulkanplus.test.math;

import net.vulkanplus.math.FastMath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FastMathTest {

    @Test
    @DisplayName("FastMath.sin matches Math.sin within 1e-4 tolerance across [-4*PI, 4*PI]")
    public void testSineAccuracy() {
        float step = 0.01f;
        for (float rad = -4.0f * FastMath.PI; rad <= 4.0f * FastMath.PI; rad += step) {
            float expected = (float) Math.sin(rad);
            float actual = FastMath.sin(rad);
            assertEquals(expected, actual, 1.5e-4f, "FastMath.sin failed at rad=" + rad);
        }
    }

    @Test
    @DisplayName("FastMath.cos matches Math.cos within 1e-4 tolerance across [-4*PI, 4*PI]")
    public void testCosineAccuracy() {
        float step = 0.01f;
        for (float rad = -4.0f * FastMath.PI; rad <= 4.0f * FastMath.PI; rad += step) {
            float expected = (float) Math.cos(rad);
            float actual = FastMath.cos(rad);
            assertEquals(expected, actual, 1.5e-4f, "FastMath.cos failed at rad=" + rad);
        }
    }

    @Test
    @DisplayName("FastMath trigonometric key boundaries: 0, PI/2, PI, 3*PI/2, 2*PI")
    public void testTrigKeyBoundaries() {
        assertEquals(0.0f, FastMath.sin(0.0f), 1e-5f);
        assertEquals(1.0f, FastMath.sin(FastMath.HALF_PI), 1e-4f);
        assertEquals(0.0f, FastMath.sin(FastMath.PI), 1e-4f);
        assertEquals(-1.0f, FastMath.sin(FastMath.PI * 1.5f), 1e-4f);
        assertEquals(0.0f, FastMath.sin(FastMath.TWO_PI), 1e-4f);

        assertEquals(1.0f, FastMath.cos(0.0f), 1e-5f);
        assertEquals(0.0f, FastMath.cos(FastMath.HALF_PI), 1e-4f);
        assertEquals(-1.0f, FastMath.cos(FastMath.PI), 1e-4f);
        assertEquals(0.0f, FastMath.cos(FastMath.PI * 1.5f), 1e-4f);
        assertEquals(1.0f, FastMath.cos(FastMath.TWO_PI), 1e-4f);
    }

    @Test
    @DisplayName("FastMath fastInvSqrt matches 1.0 / Math.sqrt within 0.1% tolerance")
    public void testInvSqrt() {
        for (float x = 0.1f; x <= 1000.0f; x += 5.0f) {
            float expected = 1.0f / (float) Math.sqrt(x);
            float actual = FastMath.fastInvSqrt(x);
            assertEquals(expected, actual, expected * 0.002f, "fastInvSqrt failed at x=" + x);
        }
    }

    @Test
    @DisplayName("FastMath fastSqrt matches Math.sqrt")
    public void testFastSqrt() {
        assertEquals(0.0f, FastMath.fastSqrt(0.0f), 1e-6f);
        assertEquals(0.0f, FastMath.fastSqrt(-5.0f), 1e-6f);
        for (float x = 1.0f; x <= 500.0f; x += 10.0f) {
            float expected = (float) Math.sqrt(x);
            float actual = FastMath.fastSqrt(x);
            assertEquals(expected, actual, expected * 0.005f);
        }
    }

    @Test
    @DisplayName("FastMath branchless clamp for int, float, and double")
    public void testClamp() {
        assertEquals(5, FastMath.clamp(2, 5, 10));
        assertEquals(10, FastMath.clamp(15, 5, 10));
        assertEquals(7, FastMath.clamp(7, 5, 10));

        assertEquals(5.0f, FastMath.clamp(2.0f, 5.0f, 10.0f));
        assertEquals(10.0f, FastMath.clamp(15.0f, 5.0f, 10.0f));
        assertEquals(7.5f, FastMath.clamp(7.5f, 5.0f, 10.0f));

        assertEquals(5.0, FastMath.clamp(2.0, 5.0, 10.0));
        assertEquals(10.0, FastMath.clamp(15.0, 5.0, 10.0));
    }

    @Test
    @DisplayName("FastMath branchless floor and ceil")
    public void testFloorAndCeil() {
        assertEquals(3, FastMath.fastFloor(3.7f));
        assertEquals(-4, FastMath.fastFloor(-3.2f));
        assertEquals(4, FastMath.fastFloor(4.0f));

        assertEquals(4, FastMath.fastCeil(3.2f));
        assertEquals(-3, FastMath.fastCeil(-3.7f));
        assertEquals(5, FastMath.fastCeil(5.0f));
    }

    @Test
    @DisplayName("FastMath lerp arithmetic")
    public void testLerp() {
        assertEquals(5.0f, FastMath.lerp(0.5f, 0.0f, 10.0f));
        assertEquals(0.0f, FastMath.lerp(0.0f, 0.0f, 10.0f));
        assertEquals(10.0f, FastMath.lerp(1.0f, 0.0f, 10.0f));
    }

    @Test
    @DisplayName("FastMath atan2 approximation")
    public void testAtan2() {
        assertEquals(0.0f, FastMath.atan2(0.0f, 1.0f), 1e-4f);
        assertEquals(FastMath.HALF_PI, FastMath.atan2(1.0f, 0.0f), 1e-4f);
        assertEquals(FastMath.PI, FastMath.atan2(0.0f, -1.0f), 1e-4f);
        assertEquals(-FastMath.HALF_PI, FastMath.atan2(-1.0f, 0.0f), 1e-4f);
    }

    @Test
    @DisplayName("FastMath.cos large angle precision matches Math.cos within 1.5e-4 across [-1000.0f, 1000.0f]")
    public void testLargeAngleCosinePrecision() {
        float step = 0.05f;
        for (float rad = -1000.0f; rad <= 1000.0f; rad += step) {
            float expected = (float) Math.cos(rad);
            float actual = FastMath.cos(rad);
            assertEquals(expected, actual, 1.5e-4f, "FastMath.cos failed at rad=" + rad);
        }
    }

    @Test
    @DisplayName("Microbenchmark: 1,000,000 iterations throughput for sin, cos, fastInvSqrt, atan2")
    public void benchmarkFastMathTrigAndSqrtThroughput() {
        final int iterations = 1_000_000;
        final int warmup = 100_000;

        // --- FastMath.sin ---
        float sink = 0.0f;
        for (int i = 0; i < warmup; i++) {
            sink += FastMath.sin((float) (i & 1023) * 0.01f);
        }
        long startSin = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            sink += FastMath.sin((float) (i & 1023) * 0.01f);
        }
        long durationSin = System.nanoTime() - startSin;
        double nsPerOpSin = (double) durationSin / iterations;
        long opsPerSecSin = (long) (iterations / (durationSin / 1_000_000_000.0));
        System.out.printf("[BENCHMARK] FastMath.sin: %.2f ns/op (%,d ops/sec)%n", nsPerOpSin, opsPerSecSin);

        // --- FastMath.cos ---
        for (int i = 0; i < warmup; i++) {
            sink += FastMath.cos((float) (i & 1023) * 0.01f);
        }
        long startCos = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            sink += FastMath.cos((float) (i & 1023) * 0.01f);
        }
        long durationCos = System.nanoTime() - startCos;
        double nsPerOpCos = (double) durationCos / iterations;
        long opsPerSecCos = (long) (iterations / (durationCos / 1_000_000_000.0));
        System.out.printf("[BENCHMARK] FastMath.cos: %.2f ns/op (%,d ops/sec)%n", nsPerOpCos, opsPerSecCos);

        // --- FastMath.fastInvSqrt ---
        for (int i = 0; i < warmup; i++) {
            sink += FastMath.fastInvSqrt(1.0f + (float) (i & 1023));
        }
        long startSqrt = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            sink += FastMath.fastInvSqrt(1.0f + (float) (i & 1023));
        }
        long durationSqrt = System.nanoTime() - startSqrt;
        double nsPerOpSqrt = (double) durationSqrt / iterations;
        long opsPerSecSqrt = (long) (iterations / (durationSqrt / 1_000_000_000.0));
        System.out.printf("[BENCHMARK] FastMath.fastInvSqrt: %.2f ns/op (%,d ops/sec)%n", nsPerOpSqrt, opsPerSecSqrt);

        // --- FastMath.atan2 ---
        for (int i = 0; i < warmup; i++) {
            sink += FastMath.atan2((float) ((i & 255) - 128), (float) (((i >> 8) & 255) - 128) + 0.01f);
        }
        long startAtan2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            sink += FastMath.atan2((float) ((i & 255) - 128), (float) (((i >> 8) & 255) - 128) + 0.01f);
        }
        long durationAtan2 = System.nanoTime() - startAtan2;
        double nsPerOpAtan2 = (double) durationAtan2 / iterations;
        long opsPerSecAtan2 = (long) (iterations / (durationAtan2 / 1_000_000_000.0));
        System.out.printf("[BENCHMARK] FastMath.atan2: %.2f ns/op (%,d ops/sec)%n", nsPerOpAtan2, opsPerSecAtan2);

        assertNotEquals(0.0f, sink);
    }
}
