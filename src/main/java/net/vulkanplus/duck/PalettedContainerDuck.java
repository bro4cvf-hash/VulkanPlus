package net.vulkanplus.duck;

/**
 * Duck interface for PalettedContainer to support volatile uniform value caching and fast blockstate lookups.
 */
public interface PalettedContainerDuck<T> {
    T vulkanplus$getCachedUniform();
    void vulkanplus$setCachedUniform(T value);
    boolean vulkanplus$isUniformCached();
    void vulkanplus$invalidateUniformCache();
    T vulkanplus$fastGet(int x, int y, int z);
}
