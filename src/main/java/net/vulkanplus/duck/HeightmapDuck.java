package net.vulkanplus.duck;

/**
 * Duck interface for Heightmap to support non-empty section bitmask tracking and section-skipping queries.
 */
public interface HeightmapDuck {
    long vulkanplus$getSectionMask();
    void vulkanplus$setSectionMask(long mask);
    void vulkanplus$updateSectionMask();
    boolean vulkanplus$isSectionEmpty(int sectionIndex);
}
