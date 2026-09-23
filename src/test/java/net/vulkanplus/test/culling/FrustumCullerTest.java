package net.vulkanplus.test.culling;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.render.FrustumCuller;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FrustumCullerTest {
    private FrustumCuller culler;

    @BeforeEach
    public void setup() {
        culler = new FrustumCuller();
        // Setup perspective matrix: 70 deg FOV, aspect 1.0, near 0.1, far 100.0
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 100.0f);
        // Camera at origin (0, 0, 0) looking down -Z
        Matrix4f view = new Matrix4f().lookAt(0, 0, 0, 0, 0, -1, 0, 1, 0);
        Matrix4f viewProj = new Matrix4f(proj).mul(view);

        culler.updateFrustum(viewProj, 0, 0, 0);
    }

    @Test
    @DisplayName("FrustumCuller: Object directly in front of camera is visible")
    public void testObjectInFront() {
        // Box at (0, 0, -10)
        assertTrue(culler.isAabbVisible(-1, -1, -11, 1, 1, -9), "Object in front must be visible");
    }

    @Test
    @DisplayName("FrustumCuller: Object behind camera is culled")
    public void testObjectBehind() {
        // Box at (0, 0, +10) behind camera
        assertFalse(culler.isAabbVisible(-1, -1, 9, 1, 1, 11), "Object behind camera must be culled");
    }

    @Test
    @DisplayName("FrustumCuller: Object far to the right is culled")
    public void testObjectFarRight() {
        assertFalse(culler.isAabbVisible(50, -1, -10, 52, 1, -8), "Object far right must be culled");
    }

    @Test
    @DisplayName("FrustumCuller: Object far to the left is culled")
    public void testObjectFarLeft() {
        assertFalse(culler.isAabbVisible(-52, -1, -10, -50, 1, -8), "Object far left must be culled");
    }

    @Test
    @DisplayName("FrustumCuller: Object beyond far plane is culled")
    public void testObjectBeyondFarPlane() {
        assertFalse(culler.isAabbVisible(-1, -1, -200, 1, 1, -150), "Object beyond far plane must be culled");
    }

    @Test
    @DisplayName("FrustumCuller: Bounding sphere visibility test")
    public void testSphereVisibility() {
        assertTrue(culler.isSphereVisible(0, 0, -10, 2.0f));
        assertFalse(culler.isSphereVisible(0, 0, 20, 2.0f));
    }

    @Test
    @DisplayName("FrustumCuller: Beacon protection extends vertical boundary")
    public void testBeaconProtection() {
        ConfigManager.getConfig().beaconProtection = true;
        // Beacon on ground below camera view frustum, but beam extends up into view
        // If not protected, culled; with protection, marked visible
        boolean visible = culler.isBlockEntityVisible(0, -50, -10, 1, -49, -9, true, false);
        assertTrue(visible, "Beacon beam extension must keep beacon visible");
    }

    @Test
    @DisplayName("FrustumCuller: Stats track total tested and culled entities")
    public void testStatsTracking() {
        culler.resetStats();
        assertEquals(0, culler.getTotalEntitiesTested());
        assertEquals(0, culler.getCulledEntitiesCount());

        culler.isAabbVisible(0, 0, -10, 1, 1, -9); // visible
        culler.isAabbVisible(0, 0, 10, 1, 1, 11);  // culled

        assertEquals(2, culler.getTotalEntitiesTested());
        assertEquals(1, culler.getCulledEntitiesCount());
    }

    @Test
    @DisplayName("FrustumCuller: Dropped item on ground is visible when camera tilts/looks down")
    public void testCameraPitchLookingDownAtGroundDropItem() {
        // Player at (0, 0, 0), camera eye at (0, 1.62, 0) looking down at the ground at pitch ~60 deg
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 100.0f);
        // Looking from eye (0, 1.62, 0) toward ground item at (0, 0, -1)
        Matrix4f view = new Matrix4f().lookAt(0, 1.62f, 0, 0, 0, -1.0f, 0, 1, 0);
        Matrix4f viewProj = new Matrix4f(proj).mul(view);

        FrustumCuller pitchCuller = new FrustumCuller();
        pitchCuller.updateFrustum(viewProj, 0, 1.62, 0);

        // Typical ItemEntity bounding box on ground: x in [-0.125, 0.125], y in [0, 0.25], z in [-1.125, -0.875]
        // Even without expansion:
        assertTrue(pitchCuller.isAabbVisible(-0.125, 0.0, -1.125, 0.125, 0.25, -0.875),
                "Dropped item on ground directly in front of and below player must be visible when looking down");

        // With standard 0.5 expansion:
        assertTrue(pitchCuller.isAabbVisible(-0.625, -0.5, -1.625, 0.625, 0.75, -0.375),
                "Expanded dropped item box must be visible when looking down");

        // Object behind the player (+Z) must be culled
        assertFalse(pitchCuller.isAabbVisible(-0.5, 0.0, 4.5, 0.5, 1.0, 5.5),
                "Object behind player must be culled when looking down forward");
    }

    @Test
    @DisplayName("FrustumCuller: Camera rotation facing South (+Z) correctly identifies front and back")
    public void testCameraFacingSouth() {
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 100.0f);
        // Camera at (0, 0, 0) looking toward +Z (South)
        Matrix4f view = new Matrix4f().lookAt(0, 0, 0, 0, 0, 1, 0, 1, 0);
        Matrix4f viewProj = new Matrix4f(proj).mul(view);

        FrustumCuller southCuller = new FrustumCuller();
        southCuller.updateFrustum(viewProj, 0, 0, 0);

        // Object at +10 Z (in front)
        assertTrue(southCuller.isAabbVisible(-1, -1, 9, 1, 1, 11), "Object to South must be visible when looking South");
        // Object at -10 Z (behind)
        assertFalse(southCuller.isAabbVisible(-1, -1, -11, 1, 1, -9), "Object to North must be culled when looking South");
    }

    @Test
    @DisplayName("FrustumCuller: Camera rotation facing East (+X) correctly identifies front and back")
    public void testCameraFacingEast() {
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 100.0f);
        // Camera at (0, 0, 0) looking toward +X (East)
        Matrix4f view = new Matrix4f().lookAt(0, 0, 0, 1, 0, 0, 0, 1, 0);
        Matrix4f viewProj = new Matrix4f(proj).mul(view);

        FrustumCuller eastCuller = new FrustumCuller();
        eastCuller.updateFrustum(viewProj, 0, 0, 0);

        // Object at +10 X (in front)
        assertTrue(eastCuller.isAabbVisible(9, -1, -1, 11, 1, 1), "Object to East must be visible when looking East");
        // Object at -10 X (behind)
        assertFalse(eastCuller.isAabbVisible(-11, -1, -1, -9, 1, 1), "Object to West must be culled when looking East");
    }
}
