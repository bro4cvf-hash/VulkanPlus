package net.vulkanplus.math;

/**
 * Ultra-fast lock-free pseudo-random number generator implementing Xoroshiro128++ 1.0.
 * Designed to replace synchronized java.util.Random and RandomSource in hot render loops
 * (particle motion, terrain jitter, bobbing, weather animation).
 */
public class FastXoroshiro128PlusPlus {
    private static final ThreadLocal<FastXoroshiro128PlusPlus> THREAD_LOCAL =
            ThreadLocal.withInitial(() -> new FastXoroshiro128PlusPlus(System.nanoTime() ^ Thread.currentThread().threadId()));

    private long s0;
    private long s1;

    public FastXoroshiro128PlusPlus(long seed) {
        setSeed(seed);
    }

    public static FastXoroshiro128PlusPlus current() {
        return THREAD_LOCAL.get();
    }

    public void setSeed(long seed) {
        // SplitMix64 initialization
        long z = seed + 0x9e3779b97f4a7c15L;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        this.s0 = z ^ (z >>> 31);

        z = seed + 0x3c6ef372fe94f82aL;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        this.s1 = z ^ (z >>> 31);

        if (this.s0 == 0 && this.s1 == 0) {
            this.s0 = 0x123456789ABCDEF0L;
            this.s1 = 0x0FEDCBA987654321L;
        }
    }

    private static long rotl(long x, int k) {
        return (x << k) | (x >>> (64 - k));
    }

    public long nextLong() {
        final long s0 = this.s0;
        long s1 = this.s1;
        final long result = rotl(s0 + s1, 17) + s0;

        s1 ^= s0;
        this.s0 = rotl(s0, 49) ^ s1 ^ (s1 << 21);
        this.s1 = rotl(s1, 28);

        return result;
    }

    public int nextInt() {
        return (int) (nextLong() >>> 32);
    }

    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        long r = nextInt() & 0xFFFFFFFFL;
        long m = r * (long) bound;
        return (int) (m >>> 32);
    }

    public float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0p-24f;
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public boolean nextBoolean() {
        return (nextLong() & 1L) != 0;
    }
}
