package net.vulkanplus.culling;

import net.minecraft.block.AbstractPlantPartBlock;
import net.minecraft.block.AttachedStemBlock;
import net.minecraft.block.AzaleaBlock;
import net.minecraft.block.BambooBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BushBlock;
import net.minecraft.block.CactusFlowerBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.DryVegetationBlock;
import net.minecraft.block.EyeblossomBlock;
import net.minecraft.block.FireflyBushBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.FlowerbedBlock;
import net.minecraft.block.FungusBlock;
import net.minecraft.block.HangingMossBlock;
import net.minecraft.block.KelpBlock;
import net.minecraft.block.KelpPlantBlock;
import net.minecraft.block.LeafLitterBlock;
import net.minecraft.block.LilyPadBlock;
import net.minecraft.block.MangroveRootsBlock;
import net.minecraft.block.MushroomPlantBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.PaleMossCarpetBlock;
import net.minecraft.block.PitcherCropBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.RootsBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.SeaPickleBlock;
import net.minecraft.block.SeagrassBlock;
import net.minecraft.block.ShortDryGrassBlock;
import net.minecraft.block.ShortPlantBlock;
import net.minecraft.block.SmallDripleafBlock;
import net.minecraft.block.SproutsBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.block.TallDryGrassBlock;
import net.minecraft.block.TallFlowerBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.TallSeagrassBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.block.WitherRoseBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;

import java.util.List;
import java.util.Set;

/**
 * Foliage & Non-Full Block Optimization Suite for Minecraft 1.21.11 + VulkanMod 0.6.8.
 *
 * Implements:
 * - 50% single-plane cross-quad reduction (enableFastFoliage: 2 quads instead of 4)
 * - 75% extreme potato cross-quad reduction (shitFoliage: 1 single-sided quad instead of 4)
 * - Deterministic (x, z) ground clutter density thinning (100%, 75%, 50%, 25%) that keeps
 *   2-block tall plants (TallPlantBlock / TallFlowerBlock / TallDryGrassBlock / TallSeagrassBlock)
 *   100% consistent between lower and upper halves while never hiding gameplay plants
 * - 24-block decorative clutter distance culling and flat lighting (no AO) in shitFoliage mode
 * - Zero random model offset hashing (Vec3d.ZERO) and hidden interior/bottom face culling
 *   for stacked and flush plants (SugarCaneBlock, BambooBlock, KelpBlock, VineBlock,
 *   MangroveRootsBlock, LeafLitterBlock, PaleMossCarpetBlock, FlowerbedBlock).
 */
public final class FoliageCuller {

    public static final int VANILLA_CROSS_QUAD_COUNT = 4;
    public static final int FAST_FOLIAGE_QUAD_COUNT = 2;
    public static final int SHIT_FOLIAGE_QUAD_COUNT = 1;

    public static final int SHIT_FOLIAGE_DENSITY_PERCENT = 25;
    public static final double SHIT_FOLIAGE_MAX_DISTANCE = 24.0;
    public static final double SHIT_FOLIAGE_MAX_DISTANCE_SQ = SHIT_FOLIAGE_MAX_DISTANCE * SHIT_FOLIAGE_MAX_DISTANCE;

    private static volatile double cameraX = 0.0;
    private static volatile double cameraY = 0.0;
    private static volatile double cameraZ = 0.0;
    private static volatile boolean cameraInitialized = false;

    public static volatile long culledFoliageBlocks = 0;
    public static volatile long culledFoliageQuads = 0;
    public static final java.util.concurrent.atomic.LongAdder culledStackedPlantFaces = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.Map<String, String> NORMALIZED_CACHE = new java.util.concurrent.ConcurrentHashMap<>(128);

    private static final Set<String> GAMEPLAY_PLANT_NAMES = Set.of(
            "SugarCaneBlock", "sugar_cane", "minecraft:sugar_cane",
            "VineBlock", "vine", "minecraft:vine",
            "SweetBerryBushBlock", "sweet_berry_bush", "minecraft:sweet_berry_bush",
            "WitherRoseBlock", "wither_rose", "minecraft:wither_rose",
            "BambooBlock", "bamboo", "minecraft:bamboo", "BambooShootBlock", "bamboo_sapling",
            "CropBlock", "crop", "wheat", "carrots", "potatoes", "beetroots",
            "StemBlock", "AttachedStemBlock", "PitcherCropBlock", "pitcher_crop", "TorchflowerBlock", "NetherWartBlock", "nether_wart", "CocoaBlock",
            "SaplingBlock", "sapling", "oak_sapling", "spruce_sapling", "birch_sapling",
            "jungle_sapling", "acacia_sapling", "dark_oak_sapling", "cherry_sapling", "pale_oak_sapling",
            "AzaleaBlock", "azalea", "flowering_azalea",
            "LilyPadBlock", "lily_pad",
            "SeaPickleBlock", "sea_pickle",
            "SmallDripleafBlock", "small_dripleaf", "minecraft:small_dripleaf",
            "BigDripleafBlock", "big_dripleaf", "BigDripleafStemBlock", "big_dripleaf_stem",
            "MangrovePropaguleBlock", "mangrove_propagule",
            "MangroveRootsBlock", "mangrove_roots", "minecraft:mangrove_roots",
            "KelpBlock", "KelpPlantBlock", "kelp", "kelp_plant"
    );

    private static final Set<String> DECORATIVE_CLUTTER_NAMES = Set.of(
            "DeadBushBlock", "DryVegetationBlock", "dead_bush", "minecraft:dead_bush",
            "ShortDryGrassBlock", "short_dry_grass", "minecraft:short_dry_grass",
            "TallDryGrassBlock", "tall_dry_grass", "minecraft:tall_dry_grass",
            "BushBlock", "bush", "minecraft:bush",
            "FireflyBushBlock", "firefly_bush", "minecraft:firefly_bush",
            "CactusFlowerBlock", "cactus_flower", "minecraft:cactus_flower",
            "LeafLitterBlock", "leaf_litter", "minecraft:leaf_litter",
            "FlowerbedBlock", "pink_petals", "wildflowers", "minecraft:pink_petals", "minecraft:wildflowers",
            "HangingMossBlock", "pale_hanging_moss", "minecraft:pale_hanging_moss",
            "PaleMossCarpetBlock", "pale_moss_carpet", "minecraft:pale_moss_carpet",
            "EyeblossomBlock", "open_eyeblossom", "closed_eyeblossom", "minecraft:open_eyeblossom", "minecraft:closed_eyeblossom",
            "ShortPlantBlock", "short_grass", "fern", "minecraft:short_grass", "minecraft:fern",
            "TallPlantBlock", "tall_grass", "large_fern", "minecraft:tall_grass", "minecraft:large_fern",
            "TallFlowerBlock", "sunflower", "lilac", "rose_bush", "peony", "pitcher_plant",
            "SeagrassBlock", "TallSeagrassBlock", "seagrass", "tall_seagrass", "minecraft:seagrass", "minecraft:tall_seagrass",
            "FlowerBlock", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
            "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley"
    );

    private static final Set<String> CROSS_MODEL_PLANT_NAMES = Set.of(
            "DeadBushBlock", "DryVegetationBlock", "dead_bush",
            "ShortDryGrassBlock", "short_dry_grass",
            "TallDryGrassBlock", "tall_dry_grass",
            "BushBlock", "bush",
            "FireflyBushBlock", "firefly_bush",
            "CactusFlowerBlock", "cactus_flower",
            "HangingMossBlock", "pale_hanging_moss",
            "EyeblossomBlock", "open_eyeblossom", "closed_eyeblossom",
            "ShortPlantBlock", "short_grass", "fern",
            "TallPlantBlock", "tall_grass", "large_fern",
            "TallFlowerBlock", "sunflower", "lilac", "rose_bush", "peony", "pitcher_plant",
            "SugarCaneBlock", "sugar_cane",
            "VineBlock", "vine",
            "KelpBlock", "KelpPlantBlock", "kelp", "kelp_plant",
            "SeagrassBlock", "TallSeagrassBlock", "seagrass", "tall_seagrass",
            "FlowerBlock", "WitherRoseBlock", "wither_rose", "dandelion", "poppy",
            "SaplingBlock", "sapling",
            "SweetBerryBushBlock", "sweet_berry_bush"
    );

    private static final Set<String> STACKED_OR_FLUSH_PLANT_NAMES = Set.of(
            "SugarCaneBlock", "sugar_cane",
            "BambooBlock", "bamboo",
            "KelpBlock", "KelpPlantBlock", "kelp", "kelp_plant",
            "VineBlock", "vine",
            "MangroveRootsBlock", "mangrove_roots",
            "LeafLitterBlock", "leaf_litter",
            "PaleMossCarpetBlock", "pale_moss_carpet",
            "FlowerbedBlock", "pink_petals", "wildflowers",
            "HangingMossBlock", "pale_hanging_moss"
    );

    private static final Set<String> TWO_BLOCK_TALL_PLANT_NAMES = Set.of(
            "TallPlantBlock", "tall_grass", "large_fern",
            "TallFlowerBlock", "sunflower", "lilac", "rose_bush", "peony", "pitcher_plant",
            "TallDryGrassBlock", "tall_dry_grass",
            "TallSeagrassBlock", "tall_seagrass"
    );

    private FoliageCuller() {
    }

    public static void resetStats() {
        culledFoliageBlocks = 0;
        culledFoliageQuads = 0;
        culledStackedPlantFaces.reset();
    }

    public static long getCulledStackedPlantFaces() {
        return culledStackedPlantFaces.sum();
    }

    public static void updateCameraPosition(double x, double y, double z) {
        cameraX = x;
        cameraY = y;
        cameraZ = z;
        cameraInitialized = true;
    }

    public static double getCameraX() {
        return cameraX;
    }

    public static double getCameraY() {
        return cameraY;
    }

    public static double getCameraZ() {
        return cameraZ;
    }

    private static void syncCameraFromClientIfPossible() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.gameRenderer != null) {
                Camera cam = client.gameRenderer.getCamera();
                if (cam != null) {
                    Vec3d pos = cam.getCameraPos();
                    if (pos != null) {
                        cameraX = pos.x;
                        cameraY = pos.y;
                        cameraZ = pos.z;
                        cameraInitialized = true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Safe fallback in headless unit test environments
        }
    }

    private static String normalizeName(String rawName) {
        if (rawName == null || rawName.isEmpty()) return "";
        return NORMALIZED_CACHE.computeIfAbsent(rawName, FoliageCuller::doNormalizeName);
    }

    private static String doNormalizeName(String rawName) {
        String trimmed = rawName.trim();
        int dot = trimmed.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < trimmed.length()) {
            trimmed = trimmed.substring(dot + 1);
        }
        if (trimmed.startsWith("minecraft:")) {
            trimmed = trimmed.substring("minecraft:".length());
        }
        return trimmed;
    }

    /**
     * Returns true if the block is a gameplay-relevant plant that must NEVER be hidden by
     * decorative density thinning or distance culling.
     */
    public static boolean isGameplayPlant(String blockName) {
        String norm = normalizeName(blockName);
        if (norm.isEmpty()) return false;
        if (GAMEPLAY_PLANT_NAMES.contains(norm) || GAMEPLAY_PLANT_NAMES.contains(blockName)) {
            return true;
        }
        String lower = norm.toLowerCase();
        return lower.contains("sugarcane") || lower.contains("sugar_cane")
                || lower.contains("vine")
                || lower.contains("sweetberry") || lower.contains("sweet_berry")
                || lower.contains("witherrose") || lower.contains("wither_rose")
                || lower.contains("bamboo")
                || lower.contains("crop") || lower.contains("sapling")
                || lower.contains("azalea") || lower.contains("lilypad") || lower.contains("lily_pad")
                || lower.contains("seapickle") || lower.contains("sea_pickle")
                || lower.contains("dripleaf")
                || lower.contains("netherwart") || lower.contains("nether_wart")
                || lower.contains("mangroveroots") || lower.contains("mangrove_roots")
                || lower.contains("kelp");
    }

    private static final int FLAG_GAMEPLAY = 1;
    private static final int FLAG_DECORATIVE = 1 << 1;
    private static final int FLAG_STACKED_FLUSH = 1 << 2;
    private static final int FLAG_CROSS_MODEL = 1 << 3;
    private static final int FLAG_TWO_BLOCK_TALL = 1 << 4;

    private static final ClassValue<Integer> BLOCK_CLASS_FLAGS = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> type) {
            if (type == Block.class) {
                return 0;
            }
            String simpleName = type.getSimpleName();
            int flags = 0;
            if ( SugarCaneBlock.class.isAssignableFrom(type)
                    || VineBlock.class.isAssignableFrom(type)
                    || SweetBerryBushBlock.class.isAssignableFrom(type)
                    || WitherRoseBlock.class.isAssignableFrom(type)
                    || BambooBlock.class.isAssignableFrom(type)
                    || CropBlock.class.isAssignableFrom(type)
                    || PitcherCropBlock.class.isAssignableFrom(type)
                    || NetherWartBlock.class.isAssignableFrom(type)
                    || StemBlock.class.isAssignableFrom(type)
                    || AttachedStemBlock.class.isAssignableFrom(type)
                    || AzaleaBlock.class.isAssignableFrom(type)
                    || LilyPadBlock.class.isAssignableFrom(type)
                    || SeaPickleBlock.class.isAssignableFrom(type)
                    || SmallDripleafBlock.class.isAssignableFrom(type)
                    || SaplingBlock.class.isAssignableFrom(type)
                    || MangroveRootsBlock.class.isAssignableFrom(type)
                    || KelpBlock.class.isAssignableFrom(type)
                    || KelpPlantBlock.class.isAssignableFrom(type)
                    || isGameplayPlant(simpleName)) {
                flags |= FLAG_GAMEPLAY;
            }
            if ((flags & FLAG_GAMEPLAY) == 0 && (
                    DryVegetationBlock.class.isAssignableFrom(type)
                    || ShortDryGrassBlock.class.isAssignableFrom(type)
                    || TallDryGrassBlock.class.isAssignableFrom(type)
                    || BushBlock.class.isAssignableFrom(type)
                    || FireflyBushBlock.class.isAssignableFrom(type)
                    || CactusFlowerBlock.class.isAssignableFrom(type)
                    || LeafLitterBlock.class.isAssignableFrom(type)
                    || FlowerbedBlock.class.isAssignableFrom(type)
                    || HangingMossBlock.class.isAssignableFrom(type)
                    || PaleMossCarpetBlock.class.isAssignableFrom(type)
                    || EyeblossomBlock.class.isAssignableFrom(type)
                    || ShortPlantBlock.class.isAssignableFrom(type)
                    || TallPlantBlock.class.isAssignableFrom(type)
                    || TallFlowerBlock.class.isAssignableFrom(type)
                    || SeagrassBlock.class.isAssignableFrom(type)
                    || TallSeagrassBlock.class.isAssignableFrom(type)
                    || FlowerBlock.class.isAssignableFrom(type)
                    || RootsBlock.class.isAssignableFrom(type)
                    || SproutsBlock.class.isAssignableFrom(type)
                    || FungusBlock.class.isAssignableFrom(type)
                    || MushroomPlantBlock.class.isAssignableFrom(type)
                    || isDecorativeClutter(simpleName))) {
                flags |= FLAG_DECORATIVE;
            }
            if (SugarCaneBlock.class.isAssignableFrom(type)
                    || BambooBlock.class.isAssignableFrom(type)
                    || KelpBlock.class.isAssignableFrom(type)
                    || KelpPlantBlock.class.isAssignableFrom(type)
                    || VineBlock.class.isAssignableFrom(type)
                    || MangroveRootsBlock.class.isAssignableFrom(type)
                    || LeafLitterBlock.class.isAssignableFrom(type)
                    || PaleMossCarpetBlock.class.isAssignableFrom(type)
                    || FlowerbedBlock.class.isAssignableFrom(type)
                    || HangingMossBlock.class.isAssignableFrom(type)
                    || isStackedOrFlushPlant(simpleName)) {
                flags |= FLAG_STACKED_FLUSH;
            }
            if (!LeafLitterBlock.class.isAssignableFrom(type)
                    && !PaleMossCarpetBlock.class.isAssignableFrom(type)
                    && !FlowerbedBlock.class.isAssignableFrom(type)
                    && !MangroveRootsBlock.class.isAssignableFrom(type)
                    && !BambooBlock.class.isAssignableFrom(type)
                    && !CropBlock.class.isAssignableFrom(type)
                    && !PitcherCropBlock.class.isAssignableFrom(type)
                    && !NetherWartBlock.class.isAssignableFrom(type)
                    && !StemBlock.class.isAssignableFrom(type)
                    && !AttachedStemBlock.class.isAssignableFrom(type)
                    && !AzaleaBlock.class.isAssignableFrom(type)
                    && !LilyPadBlock.class.isAssignableFrom(type)
                    && !SeaPickleBlock.class.isAssignableFrom(type)
                    && !SmallDripleafBlock.class.isAssignableFrom(type)
                    && (DryVegetationBlock.class.isAssignableFrom(type)
                        || ShortDryGrassBlock.class.isAssignableFrom(type)
                        || TallDryGrassBlock.class.isAssignableFrom(type)
                        || BushBlock.class.isAssignableFrom(type)
                        || FireflyBushBlock.class.isAssignableFrom(type)
                        || CactusFlowerBlock.class.isAssignableFrom(type)
                        || HangingMossBlock.class.isAssignableFrom(type)
                        || EyeblossomBlock.class.isAssignableFrom(type)
                        || ShortPlantBlock.class.isAssignableFrom(type)
                        || TallPlantBlock.class.isAssignableFrom(type)
                        || TallFlowerBlock.class.isAssignableFrom(type)
                        || SugarCaneBlock.class.isAssignableFrom(type)
                        || VineBlock.class.isAssignableFrom(type)
                        || KelpBlock.class.isAssignableFrom(type)
                        || KelpPlantBlock.class.isAssignableFrom(type)
                        || AbstractPlantPartBlock.class.isAssignableFrom(type)
                        || SeagrassBlock.class.isAssignableFrom(type)
                        || TallSeagrassBlock.class.isAssignableFrom(type)
                        || FlowerBlock.class.isAssignableFrom(type)
                        || WitherRoseBlock.class.isAssignableFrom(type)
                        || SaplingBlock.class.isAssignableFrom(type)
                        || SweetBerryBushBlock.class.isAssignableFrom(type)
                        || RootsBlock.class.isAssignableFrom(type)
                        || SproutsBlock.class.isAssignableFrom(type)
                        || FungusBlock.class.isAssignableFrom(type)
                        || MushroomPlantBlock.class.isAssignableFrom(type)
                        || isCrossModelPlant(simpleName))) {
                flags |= FLAG_CROSS_MODEL;
            }
            if (!PitcherCropBlock.class.isAssignableFrom(type)
                    && !SmallDripleafBlock.class.isAssignableFrom(type)
                    && (TallPlantBlock.class.isAssignableFrom(type)
                        || TallFlowerBlock.class.isAssignableFrom(type)
                        || TallDryGrassBlock.class.isAssignableFrom(type)
                        || TallSeagrassBlock.class.isAssignableFrom(type)
                        || isTwoBlockTallPlant(simpleName))) {
                flags |= FLAG_TWO_BLOCK_TALL;
            }
            return flags;
        }
    };

    public static boolean isGameplayPlant(Block block) {
        if (block == null) return false;
        return (BLOCK_CLASS_FLAGS.get(block.getClass()) & FLAG_GAMEPLAY) != 0;
    }

    public static boolean isGameplayPlant(BlockState state) {
        return state != null && isGameplayPlant(state.getBlock());
    }

    /**
     * Returns true if the block is purely decorative ground clutter / foliage eligible for
     * deterministic (x, z) density thinning and shitFoliage 24-block distance culling.
     */
    public static boolean isDecorativeClutter(String blockName) {
        String norm = normalizeName(blockName);
        if (norm.isEmpty() || isGameplayPlant(norm)) {
            return false;
        }
        if (DECORATIVE_CLUTTER_NAMES.contains(norm) || DECORATIVE_CLUTTER_NAMES.contains(blockName)) {
            return true;
        }
        String lower = norm.toLowerCase();
        if (lower.equals("grassblock") || lower.equals("grass_block")) {
            return false;
        }
        return lower.contains("deadbush") || lower.contains("dead_bush") || lower.contains("dryvegetation")
                || lower.contains("drygrass") || lower.contains("dry_grass")
                || lower.contains("fireflybush") || lower.contains("firefly_bush")
                || lower.contains("cactusflower") || lower.contains("cactus_flower")
                || lower.contains("leaflitter") || lower.contains("leaf_litter")
                || lower.contains("flowerbed") || lower.contains("wildflower") || lower.contains("pink_petals")
                || lower.contains("hangingmoss") || lower.contains("hanging_moss")
                || lower.contains("palemosscarpet") || lower.contains("pale_moss_carpet")
                || lower.contains("eyeblossom")
                || lower.contains("shortplant") || lower.contains("short_grass") || lower.contains("fern")
                || lower.contains("tallplant") || lower.contains("tall_grass") || lower.contains("tallflower")
                || lower.contains("seagrass") || lower.contains("flower") || lower.equals("bushblock") || lower.equals("bush");
    }

    public static boolean isDecorativeClutter(Block block) {
        if (block == null) return false;
        return (BLOCK_CLASS_FLAGS.get(block.getClass()) & FLAG_DECORATIVE) != 0;
    }

    public static boolean isDecorativeClutter(BlockState state) {
        return state != null && isDecorativeClutter(state.getBlock());
    }

    /**
     * Returns true if the block is a 2-block tall plant (TallPlantBlock, TallFlowerBlock,
     * TallDryGrassBlock, TallSeagrassBlock) whose upper and lower halves must always match visibility.
     */
    public static boolean isTwoBlockTallPlant(String blockName) {
        String norm = normalizeName(blockName);
        if (norm.equals("PitcherCropBlock") || norm.equals("pitcher_crop")
                || norm.equals("SmallDripleafBlock") || norm.equals("small_dripleaf")) {
            return false;
        }
        return TWO_BLOCK_TALL_PLANT_NAMES.contains(norm) || TWO_BLOCK_TALL_PLANT_NAMES.contains(blockName);
    }

    public static boolean isTwoBlockTallPlant(Block block) {
        if (block == null) return false;
        return (BLOCK_CLASS_FLAGS.get(block.getClass()) & FLAG_TWO_BLOCK_TALL) != 0;
    }

    public static boolean isTwoBlockTallPlant(BlockState state) {
        return state != null && isTwoBlockTallPlant(state.getBlock());
    }

    /**
     * Returns true if the plant uses cross-plane geometry eligible for 50% (enableFastFoliage)
     * or 75% (shitFoliage) quad emission reduction.
     */
    public static boolean isCrossModelPlant(String blockName) {
        String norm = normalizeName(blockName);
        if (norm.isEmpty()) return false;
        if (norm.equals("AzaleaBlock") || norm.equals("azalea") || norm.equals("flowering_azalea")
                || norm.equals("LilyPadBlock") || norm.equals("lily_pad")
                || norm.equals("SeaPickleBlock") || norm.equals("sea_pickle")
                || norm.equals("SmallDripleafBlock") || norm.equals("small_dripleaf")
                || norm.equals("BigDripleafBlock") || norm.equals("big_dripleaf")
                || norm.equals("BigDripleafStemBlock") || norm.equals("big_dripleaf_stem")
                || norm.equals("PitcherCropBlock") || norm.equals("pitcher_crop")
                || norm.equals("NetherWartBlock") || norm.equals("nether_wart")
                || norm.equals("StemBlock") || norm.equals("AttachedStemBlock")
                || norm.equals("CropBlock") || norm.equals("BambooBlock") || norm.equals("MangroveRootsBlock")
                || norm.equals("LeafLitterBlock") || norm.equals("leaf_litter")
                || norm.equals("PaleMossCarpetBlock") || norm.equals("pale_moss_carpet")
                || norm.equals("FlowerbedBlock") || norm.equals("pink_petals") || norm.equals("wildflowers")) {
            return false;
        }
        if (CROSS_MODEL_PLANT_NAMES.contains(norm) || CROSS_MODEL_PLANT_NAMES.contains(blockName)) {
            return true;
        }
        return isDecorativeClutter(norm);
    }

    public static boolean isCrossModelPlant(Block block) {
        if (block == null) return false;
        return (BLOCK_CLASS_FLAGS.get(block.getClass()) & FLAG_CROSS_MODEL) != 0;
    }

    public static boolean isCrossModelPlant(BlockState state) {
        return state != null && isCrossModelPlant(state.getBlock());
    }

    /**
     * Returns true if a quad comes from a custom 3D cuboid model (e.g. a third-party resource pack
     * with directional cullFace or horizontal UP/DOWN cuboid cap faces) rather than a vertical cross plane.
     */
    public static boolean isCustom3DQuad(Direction cullFace, Direction nominalFace) {
        if (cullFace != null) {
            return true;
        }
        return nominalFace != null && nominalFace.getAxis().isVertical();
    }

    public static boolean isCustom3DQuad(Block block, Direction cullFace, Direction nominalFace) {
        if (cullFace != null) {
            return true;
        }
        if (block instanceof VineBlock) {
            return false;
        }
        return nominalFace != null && nominalFace.getAxis().isVertical();
    }

    public static boolean isCustom3DQuad(BlockState state, Direction cullFace, Direction nominalFace) {
        if (cullFace != null) {
            return true;
        }
        if (state != null && state.getBlock() instanceof VineBlock) {
            // Ceiling vines ("up": "true") rotate vine.json by x=270 so their 2 quads have UP/DOWN nominalFaces
            return false;
        }
        return nominalFace != null && nominalFace.getAxis().isVertical();
    }

    /**
     * Returns true if a quad is a hidden downward-facing underside quad on a ground-flush plant
     * (LeafLitterBlock, FlowerbedBlock, PaleMossCarpetBlock) whose vanilla JSON model omits "cullface": "down".
     */
    public static boolean shouldCullFlushBottomQuad(String blockName, Direction nominalFace, boolean enableFastFoliage, boolean shitFoliage) {
        if (!enableFastFoliage && !shitFoliage) return false;
        if (nominalFace != Direction.DOWN) return false;
        String norm = normalizeName(blockName);
        if (norm.equals("LeafLitterBlock") || norm.equals("leaf_litter")
                || norm.equals("FlowerbedBlock") || norm.equals("pink_petals") || norm.equals("wildflowers")
                || norm.equals("PaleMossCarpetBlock") || norm.equals("pale_moss_carpet")) {
            culledStackedPlantFaces.increment();
            return true;
        }
        return false;
    }

    public static boolean shouldCullFlushBottomQuad(Block block, Direction nominalFace, boolean enableFastFoliage, boolean shitFoliage) {
        if (!enableFastFoliage && !shitFoliage) return false;
        if (block == null || nominalFace != Direction.DOWN) return false;
        if (block instanceof LeafLitterBlock || block instanceof FlowerbedBlock || block instanceof PaleMossCarpetBlock) {
            culledStackedPlantFaces.increment();
            return true;
        }
        return false;
    }

    public static boolean shouldCullFlushBottomQuad(BlockState state, Direction nominalFace, boolean enableFastFoliage, boolean shitFoliage) {
        if (!enableFastFoliage && !shitFoliage) return false;
        if (state == null || nominalFace != Direction.DOWN) return false;
        return shouldCullFlushBottomQuad(state.getBlock(), nominalFace, enableFastFoliage, shitFoliage);
    }

    /**
     * Returns true if the given quad on a block state is part of a cross-plane geometry group
     * eligible for 50% (enableFastFoliage) or 75% (shitFoliage) cross-quad reduction:
     * - Standard cross-model plants (non-3D quads)
     * - VineBlock wall and ceiling sheets (2 quads per attached face)
     * - BambooBlock tinted cross-leaf quads (bamboo_small_leaves / bamboo_large_leaves have tintIndex >= 0, while 3D stalk has tintIndex == -1)
     * - FlowerbedBlock vertical cross-stem quads (top UP petal quads are preserved via isCustom3DQuad)
     * - MangroveRootsBlock internal unculled cross-plane quads (outer 6 cube shell faces have cullFace != null and are preserved via isCustom3DQuad)
     */
    public static boolean shouldReduceCrossQuad(Block block, Direction cullFace, Direction nominalFace, int tintIndex) {
        if (block == null) return false;
        if (isCustom3DQuad(block, cullFace, nominalFace)) {
            return false;
        }
        if (block instanceof BambooBlock) {
            return tintIndex >= 0;
        }
        if (block instanceof FlowerbedBlock || block instanceof MangroveRootsBlock) {
            return true;
        }
        return isCrossModelPlant(block);
    }

    public static boolean shouldReduceCrossQuad(BlockState state, Direction cullFace, Direction nominalFace, int tintIndex) {
        if (state == null) return false;
        return shouldReduceCrossQuad(state.getBlock(), cullFace, nominalFace, tintIndex);
    }

    public static boolean isCustom3DModel(List<BlockModelPart> parts) {
        if (parts == null || parts.isEmpty()) return false;
        for (BlockModelPart part : parts) {
            if (part == null) continue;
            for (Direction dir : Direction.values()) {
                List<BakedQuad> sideQuads = part.getQuads(dir);
                if (sideQuads != null && !sideQuads.isEmpty()) {
                    return true;
                }
            }
            List<BakedQuad> unculled = part.getQuads(null);
            if (unculled != null) {
                if (unculled.size() > 8) {
                    return true;
                }
                for (BakedQuad quad : unculled) {
                    if (quad != null && quad.face() != null && quad.face().getAxis().isVertical()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the block is a stacked or ground-flush plant whose interior/bottom faces can be culled.
     */
    public static boolean isStackedOrFlushPlant(String blockName) {
        String norm = normalizeName(blockName);
        return STACKED_OR_FLUSH_PLANT_NAMES.contains(norm) || STACKED_OR_FLUSH_PLANT_NAMES.contains(blockName);
    }

    public static boolean isStackedOrFlushPlant(Block block) {
        if (block == null) return false;
        return (BLOCK_CLASS_FLAGS.get(block.getClass()) & FLAG_STACKED_FLUSH) != 0;
    }

    public static boolean isStackedOrFlushPlant(BlockState state) {
        return state != null && isStackedOrFlushPlant(state.getBlock());
    }

    /**
     * Returns true if the block is any foliage or non-full plant block managed by FoliageCuller.
     */
    public static boolean isFoliageOrPlant(String blockName) {
        return isDecorativeClutter(blockName) || isGameplayPlant(blockName) || isStackedOrFlushPlant(blockName);
    }

    public static boolean isFoliageOrPlant(Block block) {
        if (block == null) return false;
        return (BLOCK_CLASS_FLAGS.get(block.getClass()) & (FLAG_DECORATIVE | FLAG_GAMEPLAY | FLAG_STACKED_FLUSH)) != 0;
    }

    public static boolean isFoliageOrPlant(BlockState state) {
        return state != null && isFoliageOrPlant(state.getBlock());
    }

    /**
     * Returns the number of cross-model quads emitted per 4-quad cross group:
     * - shitFoliage = true        -> 1 quad (75% vertex/overdraw reduction)
     * - enableFastFoliage = true  -> 2 quads (50% single-plane reduction)
     * - both false                -> 4 quads (vanilla 2 double-sided diagonal planes)
     */
    public static int getAllowedQuadCount(boolean enableFastFoliage, boolean shitFoliage) {
        if (shitFoliage) {
            return SHIT_FOLIAGE_QUAD_COUNT;
        }
        if (enableFastFoliage) {
            return FAST_FOLIAGE_QUAD_COUNT;
        }
        return VANILLA_CROSS_QUAD_COUNT;
    }

    public static int getAllowedQuadCount(String blockName, boolean enableFastFoliage, boolean shitFoliage) {
        if (!isCrossModelPlant(blockName)) {
            return VANILLA_CROSS_QUAD_COUNT;
        }
        return getAllowedQuadCount(enableFastFoliage, shitFoliage);
    }

    public static double getQuadReductionRatio(boolean enableFastFoliage, boolean shitFoliage) {
        int allowed = getAllowedQuadCount(enableFastFoliage, shitFoliage);
        return 1.0 - ((double) allowed / (double) VANILLA_CROSS_QUAD_COUNT);
    }

    /**
     * Evaluates whether quad at quadIndex should be emitted for a 4-quad cross group.
     * Uses (quadIndex % 4) < allowed so 8-quad emissive cross plants (FireflyBushBlock,
     * open_eyeblossom using cross_emissive.json) and 8-quad seagrass (template_seagrass.json)
     * preserve their matching emissive overlay / second plane at exact 50% (4/8) and 75% (2/8) reduction.
     */
    public static boolean shouldEmitCrossQuad(int quadIndex, boolean enableFastFoliage, boolean shitFoliage) {
        if (quadIndex < 0) return false;
        int allowed = getAllowedQuadCount(enableFastFoliage, shitFoliage);
        if (allowed >= VANILLA_CROSS_QUAD_COUNT) {
            return true;
        }
        boolean emit = (quadIndex % VANILLA_CROSS_QUAD_COUNT) < allowed;
        if (!emit) {
            culledFoliageQuads++;
        }
        return emit;
    }

    /**
     * Block-aware cross quad filter.
     * In vine.json, ModelElement.Deserializer stores faces in Direction enum order (NORTH ordinal 2, SOUTH ordinal 3):
     * - Even quadIndex (0, 2, 4, 6, 8) is the NORTH face pointing INTO the attached solid wall/ceiling.
     * - Odd quadIndex (1, 3, 5, 7, 9) is the SOUTH face pointing OUTWARD into the room toward the player.
     * Emitting (quadIndex % 2) == 1 keeps the visible outward-facing front quad on every attached wall/ceiling
     * and culls the hidden wall-facing back quad (preventing backface culling from turning vines invisible).
     */
    public static boolean shouldEmitCrossQuad(String blockName, int quadIndex, boolean enableFastFoliage, boolean shitFoliage) {
        if (quadIndex < 0) return false;
        if (!enableFastFoliage && !shitFoliage) return true;
        String norm = normalizeName(blockName);
        if (norm.equals("VineBlock") || norm.equals("vine")) {
            boolean emit = (quadIndex % 2) == 1;
            if (!emit) {
                culledFoliageQuads++;
            }
            return emit;
        }
        return shouldEmitCrossQuad(quadIndex, enableFastFoliage, shitFoliage);
    }

    public static boolean shouldEmitCrossQuad(Block block, int quadIndex, boolean enableFastFoliage, boolean shitFoliage) {
        if (quadIndex < 0) return false;
        if (!enableFastFoliage && !shitFoliage) return true;
        if (block instanceof VineBlock) {
            boolean emit = (quadIndex & 1) == 1;
            if (!emit) {
                culledFoliageQuads++;
            }
            return emit;
        }
        return shouldEmitCrossQuad(quadIndex, enableFastFoliage, shitFoliage);
    }

    public static boolean shouldEmitCrossQuad(BlockState state, int quadIndex, boolean enableFastFoliage, boolean shitFoliage) {
        if (quadIndex < 0) return false;
        if (!enableFastFoliage && !shitFoliage) return true;
        if (state != null) {
            return shouldEmitCrossQuad(state.getBlock(), quadIndex, enableFastFoliage, shitFoliage);
        }
        return shouldEmitCrossQuad(quadIndex, enableFastFoliage, shitFoliage);
    }

    public static boolean shouldEmitCrossQuad(int quadIndex) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled) {
            return quadIndex >= 0;
        }
        return shouldEmitCrossQuad(quadIndex, config.enableFastFoliage, config.shitFoliage);
    }

    /**
     * Computes a deterministic [0, 99] density bucket strictly from horizontal (x, z) coordinates.
     * Because y is excluded, lower (x, y, z) and upper (x, y+1, z) blocks of 2-block tall plants
     * always produce the exact same bucket.
     */
    public static int computeXzDensityBucket(int x, int z) {
        int h = x * 0x45d9f3b ^ z * 0x119de1f3;
        h = ((h >>> 16) ^ h) * 0x45d9f3b;
        h = ((h >>> 16) ^ h) * 0x45d9f3b;
        h = (h >>> 16) ^ h;
        return (int) (((h & 0xFFFFFFFFL) * 100L) >>> 32);
    }

    public static int getEffectiveDensity(int configuredDensity, boolean shitFoliage) {
        int clamped = Math.max(0, Math.min(100, configuredDensity));
        if (shitFoliage) {
            return Math.min(clamped, SHIT_FOLIAGE_DENSITY_PERCENT);
        }
        return clamped;
    }

    /**
     * Evaluates deterministic (x, z) density thinning.
     * Gameplay plants always return true (never hidden by density thinning).
     */
    public static boolean shouldRenderByDensity(String blockName, int x, int y, int z, int foliageDensity, boolean shitFoliage) {
        if (isGameplayPlant(blockName) || !isDecorativeClutter(blockName)) {
            return true;
        }
        int effectiveDensity = getEffectiveDensity(foliageDensity, shitFoliage);
        if (effectiveDensity >= 100) {
            return true;
        }
        if (effectiveDensity <= 0) {
            return false;
        }
        return computeXzDensityBucket(x, z) < effectiveDensity;
    }

    public static boolean shouldRenderByDensity(Block block, int x, int y, int z, int foliageDensity, boolean shitFoliage) {
        if (block == null || isGameplayPlant(block) || !isDecorativeClutter(block)) {
            return true;
        }
        int effectiveDensity = getEffectiveDensity(foliageDensity, shitFoliage);
        if (effectiveDensity >= 100) {
            return true;
        }
        if (effectiveDensity <= 0) {
            return false;
        }
        return computeXzDensityBucket(x, z) < effectiveDensity;
    }

    public static boolean shouldRenderByDensity(BlockState state, int x, int y, int z, int foliageDensity, boolean shitFoliage) {
        if (state == null) return true;
        return shouldRenderByDensity(state.getBlock(), x, y, z, foliageDensity, shitFoliage);
    }

    /**
     * Evaluates distance culling in shitFoliage mode (24 blocks from camera).
     * Uses horizontal (x, z) distance so 2-block tall plants never split at the 24-block boundary.
     * Gameplay plants are never distance-culled.
     */
    public static boolean shouldRenderByDistance(String blockName, double blockX, double blockY, double blockZ,
                                                 double camX, double camY, double camZ, boolean shitFoliage) {
        if (!shitFoliage) {
            return true;
        }
        if (isGameplayPlant(blockName) || !isDecorativeClutter(blockName)) {
            return true;
        }
        double dx = (blockX + 0.5) - camX;
        double dz = (blockZ + 0.5) - camZ;
        double horizDistSq = dx * dx + dz * dz;
        return horizDistSq <= SHIT_FOLIAGE_MAX_DISTANCE_SQ;
    }

    public static boolean shouldRenderByDistance(Block block, double blockX, double blockY, double blockZ,
                                                 double camX, double camY, double camZ, boolean shitFoliage) {
        if (!shitFoliage || block == null || isGameplayPlant(block) || !isDecorativeClutter(block)) {
            return true;
        }
        double dx = (blockX + 0.5) - camX;
        double dz = (blockZ + 0.5) - camZ;
        double horizDistSq = dx * dx + dz * dz;
        return horizDistSq <= SHIT_FOLIAGE_MAX_DISTANCE_SQ;
    }

    public static boolean shouldRenderByDistance(BlockState state, double blockX, double blockY, double blockZ,
                                                 double camX, double camY, double camZ, boolean shitFoliage) {
        if (state == null) return true;
        return shouldRenderByDistance(state.getBlock(), blockX, blockY, blockZ, camX, camY, camZ, shitFoliage);
    }

    /**
     * Combined visibility check for any foliage/plant block at (x, y, z) given camera coordinates and config settings.
     */
    public static boolean shouldRenderFoliageBlock(String blockName, int x, int y, int z,
                                                   double camX, double camY, double camZ,
                                                   boolean enableFastFoliage, int foliageDensity, boolean shitFoliage) {
        if (isGameplayPlant(blockName)) {
            return true;
        }
        if (!isDecorativeClutter(blockName)) {
            return true;
        }
        if (!shouldRenderByDistance(blockName, x, y, z, camX, camY, camZ, shitFoliage)) {
            culledFoliageBlocks++;
            return false;
        }
        if (!shouldRenderByDensity(blockName, x, y, z, foliageDensity, shitFoliage)) {
            culledFoliageBlocks++;
            return false;
        }
        return true;
    }

    public static boolean shouldRenderFoliageBlock(Block block, int x, int y, int z,
                                                   double camX, double camY, double camZ,
                                                   boolean enableFastFoliage, int foliageDensity, boolean shitFoliage) {
        if (block == null || isGameplayPlant(block) || !isDecorativeClutter(block)) {
            return true;
        }
        if (!shouldRenderByDistance(block, x, y, z, camX, camY, camZ, shitFoliage)) {
            culledFoliageBlocks++;
            return false;
        }
        if (!shouldRenderByDensity(block, x, y, z, foliageDensity, shitFoliage)) {
            culledFoliageBlocks++;
            return false;
        }
        return true;
    }

    public static boolean shouldRenderFoliageBlock(BlockState state, int x, int y, int z,
                                                   double camX, double camY, double camZ,
                                                   boolean enableFastFoliage, int foliageDensity, boolean shitFoliage) {
        if (state == null) return true;
        return shouldRenderFoliageBlock(state.getBlock(), x, y, z, camX, camY, camZ, enableFastFoliage, foliageDensity, shitFoliage);
    }

    /**
     * Runtime visibility check used by VulkanBlockRendererMixin, SodiumBlockRendererMixin, and BlockModelRendererMixin.
     * Never hides foliage blocks when shitFoliage is disabled so normal/Fast/Balanced/Extreme presets never have
     * invisible foliage blocks. In ASS PC shitFoliage mode, uses zero-allocation deterministic (x, z) thinning.
     */
    public static boolean shouldRenderBlockAt(BlockState state, BlockPos pos) {
        if (state == null || pos == null) return true;
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || (!config.shitFoliage && config.foliageDensity >= 100)) {
            return true;
        }

        Block block = state.getBlock();
        if (block == null) return true;
        int flags = BLOCK_CLASS_FLAGS.get(block.getClass());
        if ((flags & FLAG_GAMEPLAY) != 0 || (flags & FLAG_DECORATIVE) == 0) {
            return true;
        }

        int effectiveDensity = getEffectiveDensity(config.foliageDensity, config.shitFoliage);
        if (effectiveDensity < 100 && computeXzDensityBucket(pos.getX(), pos.getZ()) >= effectiveDensity) {
            culledFoliageBlocks++;
            return false;
        }

        return true;
    }

    /**
     * Determines whether a face of a stacked or ground-flush plant should be culled against its neighbor.
     */
    public static boolean shouldCullPlantFace(String blockName, String neighborBlockName, Direction direction, boolean neighborOpaqueFullCube) {
        if (direction == null) return false;
        String self = normalizeName(blockName);
        String neighbor = normalizeName(neighborBlockName);
        if (self.isEmpty() || !isStackedOrFlushPlant(self)) {
            return false;
        }

        // Cull hidden bottom face when resting on an opaque full cube or identical plant
        if (direction == Direction.DOWN && (neighborOpaqueFullCube || self == neighbor || self.equals(neighbor))) {
            culledStackedPlantFaces.increment();
            return true;
        }

        // Full-cube mangrove roots can cull any face shared with another mangrove root or opaque full cube
        if (self.equals("MangroveRootsBlock") || self.equals("mangrove_roots")) {
            if (self == neighbor || self.equals(neighbor) || neighborOpaqueFullCube) {
                culledStackedPlantFaces.increment();
                return true;
            }
            return false;
        }

        // Ground-flush carpets/litter (1-3 pixels tall): ONLY cull DOWN (handled above) or identical adjacent carpet, NEVER against opaque walls
        if (self.equals("LeafLitterBlock") || self.equals("leaf_litter")
                || self.equals("PaleMossCarpetBlock") || self.equals("pale_moss_carpet")
                || self.equals("FlowerbedBlock") || self.equals("pink_petals") || self.equals("wildflowers")) {
            return false;
        }

        // Vertical interior face culling for stacked columns (SugarCane, Bamboo, Kelp, Vine, HangingMoss)
        if (direction.getAxis().isVertical()) {
            if (self == neighbor || self.equals(neighbor)
                    || (self.startsWith("Kelp") && neighbor.startsWith("Kelp"))
                    || (self.startsWith("kelp") && neighbor.startsWith("kelp"))) {
                culledStackedPlantFaces.increment();
                return true;
            }
        }

        return false;
    }

    public static boolean shouldCullPlantFace(Block selfBlock, Block neighborBlock, Direction direction, boolean neighborOpaqueFullCube) {
        if (selfBlock == null || neighborBlock == null || direction == null) return false;
        if (!isStackedOrFlushPlant(selfBlock)) {
            return false;
        }
        if (selfBlock == neighborBlock && direction.getAxis().isVertical()) {
            culledStackedPlantFaces.increment();
            return true;
        }
        if ((selfBlock instanceof KelpBlock || selfBlock instanceof KelpPlantBlock)
                && (neighborBlock instanceof KelpBlock || neighborBlock instanceof KelpPlantBlock)
                && direction.getAxis().isVertical()) {
            culledStackedPlantFaces.increment();
            return true;
        }
        if (direction == Direction.DOWN && neighborOpaqueFullCube && !(selfBlock instanceof VineBlock) && !(selfBlock instanceof HangingMossBlock)) {
            culledStackedPlantFaces.increment();
            return true;
        }
        if (selfBlock instanceof MangroveRootsBlock && (selfBlock == neighborBlock || neighborOpaqueFullCube)) {
            culledStackedPlantFaces.increment();
            return true;
        }
        return false;
    }

    public static boolean shouldCullPlantFace(BlockState state, BlockState neighborState, Direction direction) {
        if (state == null || neighborState == null || direction == null) return false;
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || (!config.enableFastFoliage && !config.shitFoliage)) {
            return false;
        }
        Block selfBlock = state.getBlock();
        if (!isStackedOrFlushPlant(selfBlock)) {
            return false;
        }
        Block neighborBlock = neighborState.getBlock();
        boolean neighborOpaque = false;
        try {
            neighborOpaque = neighborState.isOpaqueFullCube();
        } catch (Throwable ignored) {
        }
        return shouldCullPlantFace(selfBlock, neighborBlock, direction, neighborOpaque);
    }

    /**
     * Returns Vec3d.ZERO when Fast Foliage or Shit Foliage is enabled to eliminate per-block random coordinate hashing.
     */
    public static Vec3d getOptimizedModelOffset(boolean enableFastFoliage, boolean shitFoliage) {
        if (enableFastFoliage || shitFoliage) {
            return Vec3d.ZERO;
        }
        return null;
    }

    /**
     * Returns true when smooth Ambient Occlusion must be disabled (flat lighting) for foliage.
     */
    public static boolean shouldForceFlatLighting(String blockName, boolean shitFoliage) {
        return shitFoliage && isFoliageOrPlant(blockName);
    }

    public static boolean shouldForceFlatLighting(Block block, boolean shitFoliage) {
        return shitFoliage && isFoliageOrPlant(block);
    }

    public static boolean shouldForceFlatLighting(BlockState state) {
        if (state == null) return false;
        VulkanPlusConfig config = ConfigManager.getConfig();
        return config != null && config.enabled && (config.enableFastFoliage || config.shitFoliage) && isFoliageOrPlant(state.getBlock());
    }
}
