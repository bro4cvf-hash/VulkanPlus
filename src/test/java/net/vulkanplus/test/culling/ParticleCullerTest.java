package net.vulkanplus.test.culling;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.render.FrustumCuller;
import net.vulkanplus.render.ParticleCuller;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ParticleCullerTest {
    private ParticleCuller particleCuller;

    @BeforeEach
    public void setup() {
        FrustumCuller frustumCuller = new FrustumCuller();
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 100.0f);
        Matrix4f view = new Matrix4f().lookAt(0, 0, 0, 0, 0, -1, 0, 1, 0);
        frustumCuller.updateFrustum(new Matrix4f(proj).mul(view), 0, 0, 0);

        particleCuller = new ParticleCuller(frustumCuller);
        particleCuller.updateCamera(0, 0, 0);
        ConfigManager.getConfig().particleCullingDistance = 32.0;
        ConfigManager.getConfig().enableParticleCulling = true;
    }

    @Test
    @DisplayName("ParticleCuller: Close particle in front of camera is rendered")
    public void testCloseParticleVisible() {
        assertTrue(particleCuller.shouldRenderParticle(0, 0, -5, 0.2f));
    }

    @Test
    @DisplayName("ParticleCuller: Distant particle beyond threshold is culled")
    public void testDistantParticleCulled() {
        assertFalse(particleCuller.shouldRenderParticle(0, 0, -50, 0.2f));
    }

    @Test
    @DisplayName("ParticleCuller: Behind camera particle is culled by frustum")
    public void testBehindCameraParticleCulled() {
        assertFalse(particleCuller.shouldRenderParticle(0, 0, 5, 0.2f));
    }

    @Test
    @DisplayName("ParticleCuller: Respects enableParticleCulling toggle")
    public void testToggleDisabled() {
        ConfigManager.getConfig().enableParticleCulling = false;
        assertTrue(particleCuller.shouldRenderParticle(0, 0, -100, 0.2f));
    }

    @Test
    @DisplayName("ParticleCuller: shouldRenderInFrustumParticle scales distance by cullingDistanceFactor and skips frustum re-test")
    public void testShouldRenderInFrustumParticleDistanceScaling() {
        ConfigManager.getConfig().enableParticleCulling = true;
        ConfigManager.getConfig().particleCullingDistance = 32.0;
        ConfigManager.getConfig().cullingDistanceFactor = 0.5; // effective maxDist = 16.0
        particleCuller.updateCamera(0, 0, 0);
        particleCuller.resetStats();

        assertTrue(particleCuller.shouldRenderInFrustumParticle(0, 0, -15.0),
                "Particle at 15 blocks must pass when effective maxDist is 16");
        assertFalse(particleCuller.shouldRenderInFrustumParticle(0, 0, -20.0),
                "Particle at 20 blocks must be culled when effective maxDist is 16");
        assertEquals(1, particleCuller.getCulledParticleCount());
        ConfigManager.getConfig().cullingDistanceFactor = 1.0;
    }

    @Test
    @DisplayName("VulkanSectionVisibility: Open-sky and boundary-straddling AABBs never falsely cull")
    public void testVulkanSectionVisibilitySafety() {
        assertTrue(net.vulkanplus.culling.VulkanSectionVisibility.isPositionVisible(0, 350, -50, 0, 64, 0),
                "Open-sky position above world build height (Y=350) must never be falsely culled");
        assertTrue(net.vulkanplus.culling.VulkanSectionVisibility.isAabbVisible(
                15.2, 63.8, -20.5,
                16.8, 65.8, -19.5,
                0, 64, 0
        ), "Entity AABB straddling 16-block section boundaries must remain visible when section graph is inactive/out-of-bounds");
    }
}
