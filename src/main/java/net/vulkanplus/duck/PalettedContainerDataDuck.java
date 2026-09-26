package net.vulkanplus.duck;

/**
 * Duck interface for PalettedContainer$Data to support uniform palette checks and fast data retrieval.
 */
public interface PalettedContainerDataDuck<T> {
    boolean vulkanplus$isUniform();
    T vulkanplus$getUniformValue();
    T vulkanplus$getFast(int index);
}
