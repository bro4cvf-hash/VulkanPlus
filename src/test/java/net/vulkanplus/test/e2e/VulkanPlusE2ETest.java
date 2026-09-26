package net.vulkanplus.test.e2e;

import net.vulkanplus.bridge.FallbackCpuBridge;
import net.vulkanplus.bridge.RenderEngineBridge;
import net.vulkanplus.bridge.impl.VulkanModBridgeImpl;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.Preset;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.math.FastMath;
import net.vulkanplus.math.FastXoroshiro128PlusPlus;
import net.vulkanplus.memory.MatrixPool;
import net.vulkanplus.render.FrustumCuller;
import net.vulkanplus.render.ParticleCuller;
import net.vulkanplus.ui.DiagnosticHud;
import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VulkanPlusE2ETest {

    @Test
    @DisplayName("Mega-Base Chest Hall (1,000+ Block Entities Simulation)")
    public void testMegaBaseChestHallScenario() {
        FrustumCuller culler = new FrustumCuller();
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 100.0f);
        Matrix4f view = new Matrix4f().lookAt(0, 0, 0, 0, 0, -1, 0, 1, 0);
        culler.updateFrustum(new Matrix4f(proj).mul(view), 0, 0, 0);

        int totalChests = 1000;
        int renderedChests = 0;
        int culledChests = 0;

        for (int i = 0; i < totalChests; i++) {
            double x = (i % 20) - 10.0;
            double y = 0.0;
            double z = (i < 500) ? -(i % 40) - 5.0 : (i % 40) + 5.0;

            try (MatrixPool.Scope scope = MatrixPool.openScope()) {
                Matrix4f chestMatrix = scope.matrix4f();
                chestMatrix.translate((float) x, (float) y, (float) z);

                boolean visible = culler.isBlockEntityVisible(x, y, z, x + 1, y + 1, z + 1, false, true);
                if (visible) {
                    renderedChests++;
                } else {
                    culledChests++;
                }
            }
        }

        assertTrue(renderedChests > 0, "Chests in view frustum must be rendered");
        assertTrue(culledChests >= 500, "All 500 chests behind camera must be culled: " + culledChests);
    }

    @Test
    @DisplayName("Nether Mob Farm Spawn Swarm (500+ Mobs Simulation)")
    public void testMobFarmSwarmScenario() {
        FrustumCuller culler = new FrustumCuller();
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 100.0f);
        Matrix4f view = new Matrix4f().lookAt(0, 0, 0, 0, 0, -1, 0, 1, 0);
        culler.updateFrustum(new Matrix4f(proj).mul(view), 0, 0, 0);

        int totalMobs = 500;
        int culledMobs = 0;

        for (int i = 0; i < totalMobs; i++) {
            double angle = (i / (double) totalMobs) * FastMath.TWO_PI;
            double dist = 15.0;
            double x = dist * FastMath.cos((float) angle);
            double z = dist * FastMath.sin((float) angle);

            if (!culler.isAabbVisible(x - 0.5, 0, z - 0.5, x + 0.5, 2.0, z + 0.5)) {
                culledMobs++;
            }
        }

        // Circular mob distribution around camera: at least ~60% of 360-degree circle is outside 70-deg FOV
        assertTrue(culledMobs > 250, "Majority of circular mob swarm outside FOV must be culled: " + culledMobs);
    }

    @Test
    @DisplayName("Dense Particle Storm (Campfires & Explosions Simulation)")
    public void testDenseParticleStormScenario() {
        FrustumCuller culler = new FrustumCuller();
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 100.0f);
        Matrix4f view = new Matrix4f().lookAt(0, 0, 0, 0, 0, -1, 0, 1, 0);
        culler.updateFrustum(new Matrix4f(proj).mul(view), 0, 0, 0);

        ParticleCuller particleCuller = new ParticleCuller(culler);
        particleCuller.updateCamera(0, 0, 0);
        FastXoroshiro128PlusPlus rng = new FastXoroshiro128PlusPlus(42L);

        int totalParticles = 2000;
        int culledParticles = 0;

        for (int i = 0; i < totalParticles; i++) {
            double x = (rng.nextDouble() - 0.5) * 100.0;
            double y = rng.nextDouble() * 20.0;
            double z = (rng.nextDouble() - 0.5) * 100.0;

            if (!particleCuller.shouldRenderParticle(x, y, z, 0.1f)) {
                culledParticles++;
            }
        }

        assertTrue(culledParticles > 1000, "Distant and out-of-view storm particles must be culled");
    }

    @Test
    @DisplayName("Rapid Preset Switching (Fast -> Balanced -> Extreme)")
    public void testRapidPresetSwitchingScenario() {
        VulkanPlusConfig config = ConfigManager.getConfig();

        config.applyPreset(Preset.FAST);
        assertEquals(0.8, config.cullingDistanceFactor, 1e-5);
        assertEquals("MAILBOX", config.presentMode);
        assertEquals(5, config.workerThreadPriority);
        assertEquals(7, config.renderThreadPriority);
        assertFalse(config.enableMmcss);

        config.applyPreset(Preset.EXTREME);
        assertEquals(0.6, config.cullingDistanceFactor, 1e-5);
        assertEquals("MAILBOX", config.presentMode);
        assertFalse(config.chestProtection);

        config.applyPreset(Preset.BALANCED);
        assertEquals(1.0, config.cullingDistanceFactor, 1e-5);
        assertEquals("MAILBOX", config.presentMode);
        assertTrue(config.chestProtection);
    }

    @Test
    @DisplayName("Standalone and VulkanMod Dual Bridge Validation")
    public void testDualBridgeValidationScenario() {
        RenderEngineBridge cpuBridge = new FallbackCpuBridge();
        assertFalse(cpuBridge.isVulkanActive());
        assertTrue(cpuBridge.getEngineName().contains("Fallback"));
        assertTrue(cpuBridge.getVramUsed() > 0);

        VulkanModBridgeImpl vulkanBridge = new VulkanModBridgeImpl();
        assertTrue(vulkanBridge.isVulkanActive());
        assertTrue(vulkanBridge.getEngineName().contains("VulkanMod"));

        vulkanBridge.onRenderInit();
        vulkanBridge.onRenderFrameBegin();
        assertTrue(vulkanBridge.getVramUsed() >= 0);
        assertTrue(vulkanBridge.getVramAllocated() > 0);
        vulkanBridge.onRenderFrameEnd();
        vulkanBridge.onShutdown();

        try {
            java.lang.reflect.Method findLoadedClass = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            findLoadedClass.setAccessible(true);
            Object loadedMm = findLoadedClass.invoke(
                    VulkanModBridgeImpl.class.getClassLoader(),
                    "net.vulkanmod.vulkan.memory.MemoryManager");
            assertNull(loadedMm,
                    "MemoryManager must NOT be loaded/initialized while Vulkan.getAllocator() == 0L, or MemoryManager.ALLOCATOR will be permanently frozen to 0L!");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    @Test
    @DisplayName("60-Second Stress Frame-Time & 1% Low Metric Pipeline")
    public void testFrameTimingStressScenario() {
        DiagnosticHud hud = new DiagnosticHud();
        FastXoroshiro128PlusPlus rng = new FastXoroshiro128PlusPlus(1337L);

        for (int frame = 0; frame < 3600; frame++) {
            long duration;
            if (frame % 100 == 0) {
                duration = 50_000_000L;
            } else {
                duration = 16_666_667L + (long) ((rng.nextDouble() - 0.5) * 2_000_000L);
            }
            hud.recordSample(duration);
        }

        assertTrue(hud.getAverageFps() > 50.0f, "Average FPS should be around 58 FPS");
        assertTrue(hud.getOnePercentLowFps() < 30.0f, "1% low should detect the 50ms stutter spikes");
    }
}
