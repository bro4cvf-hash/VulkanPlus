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
}
