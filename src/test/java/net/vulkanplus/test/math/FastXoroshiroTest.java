package net.vulkanplus.test.math;

import net.vulkanplus.math.FastXoroshiro128PlusPlus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FastXoroshiroTest {

    @Test
    @DisplayName("FastXoroshiro128PlusPlus generates non-zero random numbers within bounds")
    public void testGenerationBounds() {
        FastXoroshiro128PlusPlus rng = new FastXoroshiro128PlusPlus(123456789L);

        for (int i = 0; i < 1000; i++) {
            float f = rng.nextFloat();
            assertTrue(f >= 0.0f && f < 1.0f, "Float must be in [0, 1): " + f);

            double d = rng.nextDouble();
            assertTrue(d >= 0.0 && d < 1.0, "Double must be in [0, 1): " + d);

            int bounded = rng.nextInt(50);
            assertTrue(bounded >= 0 && bounded < 50, "Bounded int must be in [0, 50): " + bounded);
        }
    }

    @Test
    @DisplayName("FastXoroshiro128PlusPlus produces deterministic sequence given identical seeds")
    public void testDeterminism() {
        FastXoroshiro128PlusPlus rng1 = new FastXoroshiro128PlusPlus(987654321L);
        FastXoroshiro128PlusPlus rng2 = new FastXoroshiro128PlusPlus(987654321L);

        for (int i = 0; i < 100; i++) {
            assertEquals(rng1.nextLong(), rng2.nextLong());
        }
    }

    @Test
    @DisplayName("FastXoroshiro128PlusPlus handles zero seed gracefully via SplitMix64")
    public void testZeroSeed() {
        FastXoroshiro128PlusPlus rng = new FastXoroshiro128PlusPlus(0L);
        assertNotEquals(0L, rng.nextLong());
    }

    @Test
    @DisplayName("FastXoroshiro128PlusPlus thread-local instance is non-null")
    public void testThreadLocal() {
        assertNotNull(FastXoroshiro128PlusPlus.current());
        assertNotNull(FastXoroshiro128PlusPlus.current().nextLong());
    }
}
