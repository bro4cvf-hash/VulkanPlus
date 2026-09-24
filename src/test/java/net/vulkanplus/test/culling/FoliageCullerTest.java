package net.vulkanplus.test.culling;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.culling.FoliageCuller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FoliageCullerTest {

    private VulkanPlusConfig originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = ConfigManager.getConfig().copy();
        FoliageCuller.resetStats();
    }

    @AfterEach
    void tearDown() {
        ConfigManager.setConfig(originalConfig);
    }

    @Test
    @DisplayName("Verify 50% quad reduction (enableFastFoliage) and 75% quad reduction (shitFoliage)")
    void testCrossQuadReduction50And75Percent() {
        // Vanilla cross model emits 4 quads (2 double-sided diagonal planes)
        assertEquals(4, FoliageCuller.getAllowedQuadCount(false, false));
        assertEquals(0.0, FoliageCuller.getQuadReductionRatio(false, false), 1e-6);
        for (int q = 0; q < 4; q++) {
            assertTrue(FoliageCuller.shouldEmitCrossQuad(q, false, false), "Vanilla should emit all 4 quads");
        }

        // Fast Foliage (enableFastFoliage=true, shitFoliage=false): 2 quads emitted = 50% reduction
        assertEquals(2, FoliageCuller.getAllowedQuadCount(true, false));
        assertEquals(0.50, FoliageCuller.getQuadReductionRatio(true, false), 1e-6);
        assertTrue(FoliageCuller.shouldEmitCrossQuad(0, true, false));
        assertTrue(FoliageCuller.shouldEmitCrossQuad(1, true, false));
        assertFalse(FoliageCuller.shouldEmitCrossQuad(2, true, false));
        assertFalse(FoliageCuller.shouldEmitCrossQuad(3, true, false));

        // Shit Foliage (shitFoliage=true): 1 single-sided quad emitted = 75% reduction
        assertEquals(1, FoliageCuller.getAllowedQuadCount(false, true));
        assertEquals(1, FoliageCuller.getAllowedQuadCount(true, true));
        assertEquals(0.75, FoliageCuller.getQuadReductionRatio(false, true), 1e-6);
        assertEquals(0.75, FoliageCuller.getQuadReductionRatio(true, true), 1e-6);
        assertTrue(FoliageCuller.shouldEmitCrossQuad(0, true, true));
        assertFalse(FoliageCuller.shouldEmitCrossQuad(1, true, true));
        assertFalse(FoliageCuller.shouldEmitCrossQuad(2, true, true));
        assertFalse(FoliageCuller.shouldEmitCrossQuad(3, true, true));

        // Edge case: negative quad index
        assertFalse(FoliageCuller.shouldEmitCrossQuad(-1, true, false));

        // 8-quad emissive cross plants (FireflyBushBlock, open_eyeblossom using cross_emissive.json)
        // and 8-quad seagrass (template_seagrass.json):
        // Fast Foliage must emit 4 of 8 quads (0, 1 base + 4, 5 emissive overlay = 50% reduction)
        assertTrue(FoliageCuller.shouldEmitCrossQuad(4, true, false), "Emissive overlay quad 4 must be emitted in Fast Foliage");
        assertTrue(FoliageCuller.shouldEmitCrossQuad(5, true, false), "Emissive overlay quad 5 must be emitted in Fast Foliage");
        assertFalse(FoliageCuller.shouldEmitCrossQuad(6, true, false));
        assertFalse(FoliageCuller.shouldEmitCrossQuad(7, true, false));

        // Shit Foliage must emit 2 of 8 quads (0 base + 4 emissive overlay = 75% reduction)
        assertTrue(FoliageCuller.shouldEmitCrossQuad(4, true, true), "Emissive overlay quad 4 must be emitted in Shit Foliage");
        assertFalse(FoliageCuller.shouldEmitCrossQuad(5, true, true));
        assertFalse(FoliageCuller.shouldEmitCrossQuad(6, true, true));
        assertFalse(FoliageCuller.shouldEmitCrossQuad(7, true, true));

        // Multi-wall VineBlock (2 quads per attached wall/ceiling sheet):
        // In vine.json, EnumMap<Direction, ModelElementFace> orders NORTH (0, wall-facing back quad)
        // before SOUTH (1, room-facing front quad). Must emit odd indices (1, 3, 5, 7) and cull even indices (0, 2, 4, 6).
        for (int wall = 0; wall < 4; wall++) {
            assertFalse(FoliageCuller.shouldEmitCrossQuad("VineBlock", wall * 2, true, false),
                    "VineBlock wall " + wall + " wall-facing back quad must be culled in Fast Foliage");
            assertTrue(FoliageCuller.shouldEmitCrossQuad("VineBlock", wall * 2 + 1, true, false),
                    "VineBlock wall " + wall + " outward-facing front quad must be emitted in Fast Foliage");
            assertFalse(FoliageCuller.shouldEmitCrossQuad("VineBlock", wall * 2, true, true),
                    "VineBlock wall " + wall + " wall-facing back quad must be culled in Shit Foliage");
            assertTrue(FoliageCuller.shouldEmitCrossQuad("VineBlock", wall * 2 + 1, true, true),
                    "VineBlock wall " + wall + " outward-facing front quad must be emitted in Shit Foliage");
        }
    }

    @Test
    @DisplayName("Verify deterministic (x, z) density consistency for TallPlantBlock and TallFlowerBlock upper and lower halves")
    void testTallPlantDeterministicXzDensityConsistency() {
        String[] tallPlants = {
                "TallPlantBlock",
                "TallFlowerBlock",
                "TallDryGrassBlock",
                "TallSeagrassBlock"
        };
        int[] densities = {100, 75, 50, 25};

        for (String plant : tallPlants) {
            assertTrue(FoliageCuller.isTwoBlockTallPlant(plant), plant + " must be recognized as 2-block tall plant");
            assertTrue(FoliageCuller.isDecorativeClutter(plant), plant + " must be recognized as decorative clutter");

            for (int density : densities) {
                for (int x = -64; x <= 64; x += 7) {
                    for (int z = -64; z <= 64; z += 7) {
                        final int fx = x;
                        final int fz = z;
                        int baseY = 64;
                        int upperY = 65;

                        boolean lowerVisible = FoliageCuller.shouldRenderByDensity(plant, fx, baseY, fz, density, false);
                        boolean upperVisible = FoliageCuller.shouldRenderByDensity(plant, fx, upperY, fz, density, false);
                        assertEquals(lowerVisible, upperVisible,
                                () -> String.format("%s at (%d, %d) density=%d%% must have identical visibility for lower (y=%d) and upper (y=%d)",
                                        plant, fx, fz, density, baseY, upperY));

                        // Also verify in shitFoliage mode (including distance and 25% density filter)
                        boolean lowerShit = FoliageCuller.shouldRenderFoliageBlock(plant, fx, baseY, fz, 0, 64, 0, true, density, true);
                        boolean upperShit = FoliageCuller.shouldRenderFoliageBlock(plant, fx, upperY, fz, 0, 64, 0, true, density, true);
                        assertEquals(lowerShit, upperShit,
                                () -> String.format("%s in shitFoliage mode at (%d, %d) must never split lower/upper halves", plant, fx, fz));
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Verify gameplay plants are never culled by density or distance filters and render single-quad in shitFoliage mode")
    void testGameplayPlantsNeverCulledAndRenderSingleQuadInShitFoliage() {
        String[] gameplayPlants = {
                "SugarCaneBlock",
                "VineBlock",
                "SweetBerryBushBlock",
                "BambooBlock",
                "CropBlock",
                "SaplingBlock"
        };

        for (String plant : gameplayPlants) {
            assertTrue(FoliageCuller.isGameplayPlant(plant), plant + " must be classified as gameplay plant");
            assertFalse(FoliageCuller.isDecorativeClutter(plant), plant + " must NOT be classified as decorative clutter");

            // Test across coordinates, lowest density (25%, 0%), and far beyond 24-block shitFoliage distance (e.g., 100 blocks away)
            for (int x = -50; x <= 50; x += 10) {
                for (int z = -50; z <= 50; z += 10) {
                    assertTrue(FoliageCuller.shouldRenderByDensity(plant, x, 64, z, 25, true),
                            plant + " must never be culled by density thinning");
                    assertTrue(FoliageCuller.shouldRenderByDistance(plant, x + 100, 64, z + 100, 0, 64, 0, true),
                            plant + " must never be culled by shitFoliage distance filter");
                    assertTrue(FoliageCuller.shouldRenderFoliageBlock(plant, x + 100, 64, z + 100, 0, 64, 0, true, 25, true),
                            plant + " must always remain visible in shitFoliage mode");
                }
            }
        }

        // Verify SugarCaneBlock, VineBlock, and SweetBerryBushBlock render with recognizable 1-quad geometry in shitFoliage mode
        for (String crossGameplayPlant : new String[]{"SugarCaneBlock", "VineBlock", "SweetBerryBushBlock", "SaplingBlock"}) {
            assertTrue(FoliageCuller.isCrossModelPlant(crossGameplayPlant));
            assertEquals(1, FoliageCuller.getAllowedQuadCount(crossGameplayPlant, true, true),
                    crossGameplayPlant + " should render with single-quad geometry in shitFoliage mode");
            assertEquals(2, FoliageCuller.getAllowedQuadCount(crossGameplayPlant, true, false),
                    crossGameplayPlant + " should render with 2 quads (single diagonal plane) in enableFastFoliage mode");
            assertTrue(FoliageCuller.shouldForceFlatLighting(crossGameplayPlant, true),
                    crossGameplayPlant + " should use flat lighting in shitFoliage mode");
        }
    }

    @Test
    @DisplayName("Verify all 1.21.11 Pale Garden and Spring to Life foliage blocks are optimized")
    void testComplete12111FoliageBlockCoverage() {
        String[] decorative12111Blocks = {
                "DeadBushBlock",
                "DryVegetationBlock",
                "ShortDryGrassBlock",
                "TallDryGrassBlock",
                "BushBlock",
                "FireflyBushBlock",
                "CactusFlowerBlock",
                "LeafLitterBlock",
                "FlowerbedBlock",
                "HangingMossBlock",
                "PaleMossCarpetBlock",
                "EyeblossomBlock",
                "ShortPlantBlock",
                "TallPlantBlock",
                "SeagrassBlock",
                "FlowerBlock"
        };

        for (String blockName : decorative12111Blocks) {
            assertTrue(FoliageCuller.isDecorativeClutter(blockName),
                    blockName + " must be recognized as 1.21.11 decorative clutter");
            assertTrue(FoliageCuller.isFoliageOrPlant(blockName),
                    blockName + " must be recognized as foliage/plant");
            assertTrue(FoliageCuller.shouldForceFlatLighting(blockName, true),
                    blockName + " must use flat lighting in shitFoliage mode");
        }

        // Verify 24-block distance culling in shitFoliage mode for decorative clutter
        assertTrue(FoliageCuller.shouldRenderByDistance("FireflyBushBlock", 10, 64, 10, 0, 64, 0, true),
                "Decorative clutter within 24 blocks (dist ~14.8) must pass distance filter");
        assertFalse(FoliageCuller.shouldRenderByDistance("FireflyBushBlock", 25, 64, 0, 0, 64, 0, true),
                "Decorative clutter beyond 24 blocks (dist 25.5) must be culled in shitFoliage mode");
    }

    @Test
    @DisplayName("Verify stacked and ground-flush plant hidden face culling and zero model offset")
    void testStackedPlantFaceCullingAndZeroModelOffset() {
        assertEquals(Vec3d.ZERO, FoliageCuller.getOptimizedModelOffset(true, false));
        assertEquals(Vec3d.ZERO, FoliageCuller.getOptimizedModelOffset(false, true));
        assertNull(FoliageCuller.getOptimizedModelOffset(false, false));

        // Vertical interior faces between identical stacked plants must be culled
        for (String stacked : new String[]{"SugarCaneBlock", "BambooBlock", "KelpBlock", "VineBlock", "MangroveRootsBlock", "HangingMossBlock"}) {
            assertTrue(FoliageCuller.shouldCullPlantFace(stacked, stacked, Direction.UP, false),
                    stacked + " UP face against itself should be culled");
            assertTrue(FoliageCuller.shouldCullPlantFace(stacked, stacked, Direction.DOWN, false),
                    stacked + " DOWN face against itself should be culled");
        }

        // Flush bottom face resting on an opaque solid cube must be culled, while UP face must NEVER be culled
        for (String flush : new String[]{"LeafLitterBlock", "PaleMossCarpetBlock", "FlowerbedBlock"}) {
            assertTrue(FoliageCuller.shouldCullPlantFace(flush, "GrassBlock", Direction.DOWN, true),
                    flush + " DOWN face against solid ground block should be culled");
            assertFalse(FoliageCuller.shouldCullPlantFace(flush, "StoneBlock", Direction.UP, true),
                    flush + " UP face must NEVER be culled even when a solid block is at y+1");
            // Also verify direct quad-level bottom culling for JSON models that omit "cullface": "down" (template_leaf_litter_1..4, flowerbed_1..4)
            assertTrue(FoliageCuller.shouldCullFlushBottomQuad(flush, Direction.DOWN, true, false),
                    flush + " unculled Direction.DOWN quad must be culled in Fast Foliage");
            assertTrue(FoliageCuller.shouldCullFlushBottomQuad(flush, Direction.DOWN, false, true),
                    flush + " unculled Direction.DOWN quad must be culled in Shit Foliage");
            assertFalse(FoliageCuller.shouldCullFlushBottomQuad(flush, Direction.UP, true, true),
                    flush + " Direction.UP quad must NEVER be culled by shouldCullFlushBottomQuad");
        }
        for (String stacked : new String[]{"SugarCaneBlock", "BambooBlock"}) {
            assertTrue(FoliageCuller.shouldCullPlantFace(stacked, "GrassBlock", Direction.DOWN, true),
                    stacked + " DOWN face against solid ground block should be culled");
        }
    }

    @Test
    @DisplayName("Verify non-cross 3D/collision PlantBlock subclasses (AzaleaBlock, LilyPadBlock, SeaPickleBlock, SmallDripleafBlock, PitcherCropBlock, NetherWartBlock, WitherRoseBlock) and custom 3D resource pack quads are protected")
    void testNonCrossAndCollisionPlantBlockSubclassesProtected() {
        String[] protectedNonCrossPlants = {
                "AzaleaBlock",
                "LilyPadBlock",
                "SeaPickleBlock",
                "SmallDripleafBlock",
                "PitcherCropBlock",
                "NetherWartBlock",
                "StemBlock",
                "AttachedStemBlock",
                "CropBlock"
        };

        for (String plant : protectedNonCrossPlants) {
            assertTrue(FoliageCuller.isGameplayPlant(plant), plant + " must be protected as gameplay/collision plant");
            assertFalse(FoliageCuller.isDecorativeClutter(plant), plant + " must NOT be thinned/culled as decorative clutter");
            assertFalse(FoliageCuller.isCrossModelPlant(plant), plant + " must NOT have its 3D/pad/crop mesh truncated as a cross model");
            assertEquals(4, FoliageCuller.getAllowedQuadCount(plant, true, true),
                    plant + " must retain full quad allowance");
            assertTrue(FoliageCuller.shouldRenderFoliageBlock(plant, 100, 64, 100, 0, 64, 0, true, 25, true),
                    plant + " must never be hidden by density or distance culling");
        }
        assertFalse(FoliageCuller.isTwoBlockTallPlant("PitcherCropBlock"),
                "PitcherCropBlock is a 3D crop, not decorative 2-block tall clutter");
        assertFalse(FoliageCuller.isTwoBlockTallPlant("SmallDripleafBlock"),
                "SmallDripleafBlock is a 3D multi-leaf plant, not decorative 2-block tall clutter");

        // WitherRoseBlock is a lethal contact-damage hazard plant: never hidden by density/distance, still cross-model
        assertTrue(FoliageCuller.isGameplayPlant("WitherRoseBlock"), "WitherRoseBlock must be protected as a lethal hazard gameplay plant");
        assertFalse(FoliageCuller.isDecorativeClutter("WitherRoseBlock"), "WitherRoseBlock must never be culled into an invisible hazard");
        assertTrue(FoliageCuller.isCrossModelPlant("WitherRoseBlock"), "WitherRoseBlock uses a cross model");

        // Custom 3D resource pack quad guards: cullFace != null or UP/DOWN cuboid cap faces must be detected as 3D quads
        assertTrue(FoliageCuller.isCustom3DQuad(Direction.NORTH, Direction.NORTH));
        assertTrue(FoliageCuller.isCustom3DQuad(null, Direction.UP));
        assertTrue(FoliageCuller.isCustom3DQuad(null, Direction.DOWN));
        assertFalse(FoliageCuller.isCustom3DQuad(null, Direction.NORTH));
        assertFalse(FoliageCuller.isCustom3DQuad(null, Direction.EAST));
    }

    @Test
    @DisplayName("Verify FoliageBlockMixin, VulkanBlockRendererMixin, and BlockModelRendererMixin are registered in vulkanplus.mixins.json")
    void testNewMixinsRegisteredInMixinsJson() throws Exception {
        List<String> registered = new ArrayList<>();
        try (InputStream is = getClass().getResourceAsStream("/vulkanplus.mixins.json")) {
            assertNotNull(is, "vulkanplus.mixins.json must exist on classpath");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
            if (root.has("mixins")) {
                JsonArray arr = root.getAsJsonArray("mixins");
                for (JsonElement el : arr) registered.add(el.getAsString());
            }
            if (root.has("client")) {
                JsonArray arr = root.getAsJsonArray("client");
                for (JsonElement el : arr) registered.add(el.getAsString());
            }
        }

        assertTrue(registered.stream().anyMatch(s -> s.endsWith("FoliageBlockMixin")),
                "vulkanplus.mixins.json must register FoliageBlockMixin");
        assertTrue(registered.stream().anyMatch(s -> s.endsWith("VulkanBlockRendererMixin")),
                "vulkanplus.mixins.json must register VulkanBlockRendererMixin");
        assertTrue(registered.stream().anyMatch(s -> s.endsWith("BlockModelRendererMixin")),
                "vulkanplus.mixins.json must register BlockModelRendererMixin");
    }

    @Test
    @DisplayName("Verify shitFoliage config integration with isAssPcActive and JSON persistence")
    void testShitFoliageConfigIntegration() {
        VulkanPlusConfig config = new VulkanPlusConfig();
        assertFalse(config.shitFoliage);
        assertTrue(config.enableFastFoliage);
        assertEquals(100, config.foliageDensity);

        config.shitFoliage = true;
        assertTrue(config.isAssPcActive(), "shitFoliage must activate isAssPcActive()");

        config.foliageDensity = 50;
        config.enableFastFoliage = false;

        String json = ConfigManager.toJson(config);
        assertTrue(json.contains("\"shitFoliage\": true"));
        assertTrue(json.contains("\"foliageDensity\": 50"));
        assertTrue(json.contains("\"enableFastFoliage\": false"));

        VulkanPlusConfig parsed = new VulkanPlusConfig();
        ConfigManager.parseJson(json, parsed);
        assertTrue(parsed.shitFoliage);
        assertEquals(50, parsed.foliageDensity);
        assertFalse(parsed.enableFastFoliage);
        assertTrue(parsed.isAssPcActive());
    }

    @Test
    @DisplayName("Verify target classes and exact bytecode descriptors exist for FoliageBlockMixin, VulkanBlockRendererMixin, and BlockModelRendererMixin")
    void testFoliageMixinTargetsAndMethodsExist() throws Exception {
        Class<?> absStateClass = Class.forName("net.minecraft.block.AbstractBlock$AbstractBlockState", false, getClass().getClassLoader());
        assertNotNull(absStateClass);

        assertBytecodeMethodDescriptor("net.minecraft.block.AbstractBlock$AbstractBlockState", "getModelOffset",
                "(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/Vec3d;");
        assertBytecodeMethodDescriptor("net.minecraft.block.AbstractBlock$AbstractBlockState", "hasModelOffset",
                "()Z");
        assertBytecodeMethodDescriptor("net.minecraft.block.AbstractBlock$AbstractBlockState", "isSideInvisible",
                "(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Direction;)Z");
        assertBytecodeMethodDescriptor("net.minecraft.block.AbstractBlock$AbstractBlockState", "getAmbientOcclusionLightLevel",
                "(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)F");

        assertBytecodeMethodDescriptor("net.vulkanmod.render.chunk.build.renderer.BlockRenderer", "renderBlock",
                "(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lorg/joml/Vector3f;)V",
                "(Lnet/minecraft/class_2680;Lnet/minecraft/class_2338;Lorg/joml/Vector3f;)V");
        assertBytecodeMethodDescriptor("net.vulkanmod.render.chunk.build.renderer.BlockRenderer", "endRenderQuad",
                "(Lnet/vulkanmod/render/chunk/build/frapi/mesh/MutableQuadViewImpl;)V");

        assertBytecodeMethodDescriptor("net.minecraft.client.render.block.BlockModelRenderer", "render",
                "(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V");
        assertBytecodeMethodDescriptor("net.minecraft.client.render.block.BlockModelRenderer", "renderSmooth",
                "(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V");
        assertBytecodeMethodDescriptor("net.minecraft.client.render.block.BlockModelRenderer", "renderFlat",
                "(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V");
        assertBytecodeMethodDescriptor("net.minecraft.client.render.block.BlockModelRenderer", "renderQuad",
                "(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;Lnet/minecraft/client/render/block/BlockModelRenderer$LightmapCache;I)V");
    }

    private void assertBytecodeMethodDescriptor(String className, String methodName, String... expectedDescs) throws Exception {
        String resourcePath = "/" + className.replace('.', '/') + ".class";
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(is, "Classfile must exist on classpath: " + resourcePath);
            org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(is);
            org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
            cr.accept(cn, org.objectweb.asm.ClassReader.SKIP_CODE | org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);
            boolean found = cn.methods != null && cn.methods.stream().anyMatch(m -> {
                if (!methodName.equals(m.name)) return false;
                for (String desc : expectedDescs) {
                    if (desc.equals(m.desc)) return true;
                }
                return false;
            });
            assertTrue(found, "Method " + methodName + java.util.Arrays.toString(expectedDescs) + " must exist in " + className);
        }
    }
}
