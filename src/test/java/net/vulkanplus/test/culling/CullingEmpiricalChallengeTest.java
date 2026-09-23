package net.vulkanplus.test.culling;

import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.culling.ItemFrameCuller;
import net.vulkanplus.render.FrustumCuller;
import net.vulkanplus.render.RenderOptimizer;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification suite for:
 * - Framed maps bytecode modifications in ItemFrameEntityRendererMixin
 * - Vertical beam segment frustum culling
 */
public class CullingEmpiricalChallengeTest {

    private VulkanPlusConfig originalConfig;

    @BeforeEach
    public void setUp() {
        originalConfig = ConfigManager.getConfig().copy();
        VulkanPlusConfig cfg = new VulkanPlusConfig();
        cfg.enabled = true;
        cfg.enableMoreCulling = true;
        cfg.enableFastItemFrames = true;
        cfg.enableItemFrameBlockOcclusion = true;
        cfg.enableBeaconBeamCulling = true;
        cfg.itemFrameMaxDistance = 64.0;
        cfg.itemFrameItemDistance = 24.0;
        cfg.cullingDistanceFactor = 1.0;
        ConfigManager.setConfig(cfg);
        ItemFrameCuller.resetStats();
        RenderOptimizer.resetFrameStats();
    }

    @AfterEach
    public void tearDown() {
        ConfigManager.setConfig(originalConfig);
    }

    @Test
    @DisplayName("Framed maps: bytecode verification of mapId clearance in ItemFrameEntityRendererMixin")
    public void testItemFrameMixinBytecodeClearsMapId() throws Exception {
        String resourcePath = "/net/vulkanplus/mixin/culling/ItemFrameEntityRendererMixin.class";
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(is, "Mixin classfile must exist: " + resourcePath);
            ClassReader cr = new ClassReader(is);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            MethodNode updateRenderStateMethod = null;
            for (MethodNode mn : cn.methods) {
                if (mn.name.equals("onUpdateRenderState")) {
                    updateRenderStateMethod = mn;
                    break;
                }
            }
            assertNotNull(updateRenderStateMethod, "onUpdateRenderState method must exist in ItemFrameEntityRendererMixin");

            int mapIdPutfieldCount = 0;
            int itemClearCount = 0;
            for (AbstractInsnNode insn : updateRenderStateMethod.instructions) {
                if (insn instanceof FieldInsnNode fin) {
                    if (fin.name.equals("mapId") && fin.owner.equals("net/minecraft/client/render/entity/state/ItemFrameEntityRenderState")) {
                        mapIdPutfieldCount++;
                    }
                } else if (insn instanceof MethodInsnNode min) {
                    if (min.name.equals("clear") && min.owner.equals("net/minecraft/client/render/item/ItemRenderState")) {
                        itemClearCount++;
                    }
                }
            }

            assertTrue(mapIdPutfieldCount >= 2,
                    "ItemFrameEntityRendererMixin must assign state.mapId in at least 2 branches (whole frame culling & item distance culling). Found: " + mapIdPutfieldCount);
            assertTrue(itemClearCount >= 2,
                    "ItemFrameEntityRendererMixin must call state.itemRenderState.clear() in at least 2 branches. Found: " + itemClearCount);
        }
    }

    @Test
    @DisplayName("Beacon beam: frustum culling across vertical segment tower")
    public void testBeaconVerticalSegmentTowerCulling() {
        FrustumCuller culler = new FrustumCuller();
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 100.0f);
        Matrix4f view = new Matrix4f().lookAt(0, 0, 0, 0, 0, -1, 0, 1, 0);
        culler.updateFrustum(new Matrix4f(proj).mul(view), 0, 64, 0);

        boolean seg0 = culler.isAabbVisible(-0.5, 64.0, -20.5, 0.5, 80.0, -19.5);
        assertTrue(seg0, "Segment 0 (eye level) MUST be visible");

        boolean segTop = culler.isAabbVisible(-0.5, 224.0, -20.5, 0.5, 240.0, -19.5);
        assertFalse(segTop, "Segment 10 (Y=224..240) MUST be culled when camera looks forward at Y=64");

        boolean segSky = culler.isAabbVisible(-0.5, 304.0, -20.5, 0.5, 320.0, -19.5);
        assertFalse(segSky, "Segment 15 (Y=304..320) MUST be culled when camera looks forward at Y=64");

        Matrix4f viewUp = new Matrix4f().lookAt(0, 0, 0, 0, 1, 0, 0, 0, -1);
        culler.updateFrustum(new Matrix4f(proj).mul(viewUp), 0, 64, 0);

        boolean segOverheadNear = culler.isAabbVisible(-0.5, 100.0, -2.5, 0.5, 116.0, -1.5);
        assertTrue(segOverheadNear, "Overhead segment within far plane MUST be visible when looking upwards");

        boolean segOverheadExceedsFar = culler.isAabbVisible(-0.5, 224.0, -2.5, 0.5, 240.0, -1.5);
        assertFalse(segOverheadExceedsFar, "Overhead segment beyond far plane (160 > 100) MUST be culled");

        Matrix4f projFar = new Matrix4f().perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 300.0f);
        culler.updateFrustum(new Matrix4f(projFar).mul(viewUp), 0, 64, 0);
        boolean segSkyWithExtendedFar = culler.isAabbVisible(-0.5, 224.0, -2.5, 0.5, 240.0, -1.5);
        assertTrue(segSkyWithExtendedFar, "Overhead segment at Y=224 MUST be visible when far plane is extended to 300");
    }
}
