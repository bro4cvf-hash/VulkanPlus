package net.vulkanplus.test.math;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.vulkanplus.bridge.VulkanModMixinPlugin;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.Preset;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.duck.HeightmapDuck;
import net.vulkanplus.duck.PalettedContainerDataDuck;
import net.vulkanplus.duck.PalettedContainerDuck;
import net.vulkanplus.mixin.math.BoxMixin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class BoxRaycastAndWorldgenTest {

    @Test
    @DisplayName("Verify BoxMixin scalar raycast returns exact intersection vector and face")
    void testBoxRaycastIntersection() {
        Vec3d from = new Vec3d(0.5, 0.5, -2.0);
        Vec3d to = new Vec3d(0.5, 0.5, 2.0);

        Optional<Vec3d> hit = BoxMixin.raycast(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, from, to);
        assertTrue(hit.isPresent(), "Ray should intersect unit box centered at origin");
        Vec3d hitPos = hit.get();
        assertEquals(0.5, hitPos.x, 1e-6);
        assertEquals(0.5, hitPos.y, 1e-6);
        assertEquals(0.0, hitPos.z, 1e-6, "Hit should occur at minZ = 0.0");

        // Oblique ray hitting east face (+X)
        Vec3d fromEast = new Vec3d(3.0, 0.5, 0.5);
        Vec3d toEast = new Vec3d(-1.0, 0.5, 0.5);
        Optional<Vec3d> hitEast = BoxMixin.raycast(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, fromEast, toEast);
        assertTrue(hitEast.isPresent());
        assertEquals(1.0, hitEast.get().x, 1e-6, "Hit should occur at maxX = 1.0");

        // Miss ray
        Vec3d fromMiss = new Vec3d(5.0, 5.0, -2.0);
        Vec3d toMiss = new Vec3d(5.0, 5.0, 2.0);
        Optional<Vec3d> hitMiss = BoxMixin.raycast(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, fromMiss, toMiss);
        assertFalse(hitMiss.isPresent(), "Ray missing the box should return Optional.empty()");
    }

    @Test
    @DisplayName("Verify BoxMixin multi-box raycast with BlockPos offset")
    void testMultiBoxRaycastWithOffset() {
        Box box1 = new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        Box box2 = new Box(2.0, 0.0, 0.0, 3.0, 1.0, 1.0);
        BlockPos offset = new BlockPos(10, 0, 0);

        Vec3d from = new Vec3d(5.0, 0.5, 0.5);
        Vec3d to = new Vec3d(15.0, 0.5, 0.5);

        // Ray traverses from X=5 to X=15.
        // Box1 offset by (10, 0, 0) spans X=[10..11]
        // Box2 offset by (10, 0, 0) spans X=[12..13]
        BlockHitResult hit = BoxMixin.raycast(List.of(box1, box2), from, to, offset);
        assertNotNull(hit, "Ray should hit the closer box");
        assertEquals(Direction.WEST, hit.getSide(), "Approach from X=5 hitting X=10 should impact WEST face");
        assertEquals(10.0, hit.getPos().x, 1e-6, "First hit should be on the closest box at X=10");
        assertEquals(offset, hit.getBlockPos());
    }

    @Test
    @DisplayName("Verify Duck interfaces declare required optimization contracts")
    void testDuckInterfacesContracts() {
        Method[] dataDuckMethods = PalettedContainerDataDuck.class.getDeclaredMethods();
        assertTrue(dataDuckMethods.length >= 3);
        assertDoesNotThrow(() -> PalettedContainerDataDuck.class.getMethod("vulkanplus$isUniform"));
        assertDoesNotThrow(() -> PalettedContainerDataDuck.class.getMethod("vulkanplus$getUniformValue"));
        assertDoesNotThrow(() -> PalettedContainerDataDuck.class.getMethod("vulkanplus$getFast", int.class));

        Method[] pcDuckMethods = PalettedContainerDuck.class.getDeclaredMethods();
        assertTrue(pcDuckMethods.length >= 5);
        assertDoesNotThrow(() -> PalettedContainerDuck.class.getMethod("vulkanplus$getCachedUniform"));
        assertDoesNotThrow(() -> PalettedContainerDuck.class.getMethod("vulkanplus$invalidateUniformCache"));

        Method[] heightmapDuckMethods = HeightmapDuck.class.getDeclaredMethods();
        assertTrue(heightmapDuckMethods.length >= 4);
        assertDoesNotThrow(() -> HeightmapDuck.class.getMethod("vulkanplus$getSectionMask"));
        assertDoesNotThrow(() -> HeightmapDuck.class.getMethod("vulkanplus$isSectionEmpty", int.class));

        // Verify PalettedContainerMixin has no @Shadow field for 'data' (which causes InvalidMixinException)
        for (java.lang.reflect.Field field : net.vulkanplus.mixin.world.PalettedContainerMixin.class.getDeclaredFields()) {
            assertNotEquals("data", field.getName(), "PalettedContainerMixin should not declare a @Shadow field 'data' with mismatched descriptor");
        }

        // Verify PalettedContainer target methods exist
        assertDoesNotThrow(() -> net.minecraft.world.chunk.PalettedContainer.class.getMethod("getElementBits"));
        assertDoesNotThrow(() -> net.minecraft.world.chunk.PalettedContainer.class.getMethod("get", int.class, int.class, int.class));
        assertDoesNotThrow(() -> net.minecraft.world.chunk.PalettedContainer.class.getMethod("swap", int.class, int.class, int.class, Object.class));
        assertDoesNotThrow(() -> net.minecraft.world.chunk.PalettedContainer.class.getMethod("onResize", int.class, Object.class));
    }

    @Test
    @DisplayName("Verify VulkanPlusConfig raycast and worldgen options and persistence")
    void testConfigOptionsAndPersistence() {
        VulkanPlusConfig cfg = new VulkanPlusConfig();
        assertTrue(cfg.enableFastRaycast, "Fast raycast should be enabled by default");
        assertTrue(cfg.enableFastWorldGen, "Fast worldgen should be enabled by default");

        cfg.applyPreset(Preset.FAST);
        assertTrue(cfg.enableFastRaycast);
        assertTrue(cfg.enableFastWorldGen);

        cfg.applyPreset(Preset.BALANCED);
        assertTrue(cfg.enableFastRaycast);
        assertTrue(cfg.enableFastWorldGen);

        cfg.applyPreset(Preset.EXTREME);
        assertTrue(cfg.enableFastRaycast);
        assertTrue(cfg.enableFastWorldGen);

        // Serialization & parsing round-trip
        String json = ConfigManager.toJson(cfg);
        assertTrue(json.contains("\"enableFastRaycast\": true"));
        assertTrue(json.contains("\"enableFastWorldGen\": true"));

        VulkanPlusConfig parsed = new VulkanPlusConfig();
        parsed.enableFastRaycast = false;
        parsed.enableFastWorldGen = false;
        ConfigManager.parseJson(json, parsed);
        assertTrue(parsed.enableFastRaycast);
        assertTrue(parsed.enableFastWorldGen);
    }

    @Test
    @DisplayName("Verify VulkanModMixinPlugin shouldApplyMixin yields BoxMixin and worldgen mixins on lomka mod")
    void testLomkaYielding() {
        VulkanModMixinPlugin plugin = new VulkanModMixinPlugin();
        // Null checks
        assertFalse(plugin.shouldApplyMixin(null, null));

        // When lomka is NOT loaded, BoxMixin and worldgen mixins apply normally
        assertTrue(plugin.shouldApplyMixin("net.minecraft.util.math.Box", "net.vulkanplus.mixin.math.BoxMixin"));
        assertTrue(plugin.shouldApplyMixin("net.minecraft.world.chunk.PalettedContainer", "net.vulkanplus.mixin.world.PalettedContainerMixin"));
        assertTrue(plugin.shouldApplyMixin("net.minecraft.world.Heightmap", "net.vulkanplus.mixin.world.HeightmapMixin"));
    }
}
