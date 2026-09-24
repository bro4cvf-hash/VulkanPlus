<div align="center">

# ⚡ Vulkan Plus

**The ultimate companion performance mod for [VulkanMod](https://github.com/xCollateral/VulkanMod) on Fabric.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-2ea44f?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.16.0+-blue?style=for-the-badge&logo=fabric&logoColor=white)](https://fabricmc.net/)
[![Release](https://img.shields.io/badge/Release-v1.0.0-orange?style=for-the-badge)](https://github.com/bro4cvf-hash/VulkanPlus/releases)
[![License](https://img.shields.io/badge/License-MIT-purple?style=for-the-badge)](LICENSE)

<br/>

<img src="assets/fps_benchmark.png" alt="3400 FPS Benchmark" width="280" />

*Insane FPS boost, rock-solid frame pacing, and zero micro-stutters.*

</div>

---

## 🎯 What Does This Mod Do?

Minecraft rendering can get bogged down by thousands of entities, massive chest halls, thick 1.21.11 forests/meadows, and background world generation.

**Vulkan Plus** works hand-in-hand with VulkanMod 0.6.8 to eliminate CPU and GPU bottlenecks:

- 🚀 **Deep VulkanMod GPU Engine**: Activates **Resizable BAR (`DeviceMappableMemory`)**, persistent **PSO disk caching**, **pipeline/descriptor state deduplication**, and **visibility-invariant `SectionGraph` skips** with sub-frame chunk upload budgeting.
- 🌿 **1.21.11 Foliage & Plant Suite**: Cuts cross-model plant geometry by **50%** (`Fast Foliage`), thins decorative ground clutter deterministically (`Foliage Density`: 100% / 75% / 50% / 25%) without splitting 2-block tall plants or hiding gameplay blocks, removes model-offset hash overhead, and culls stacked interior plant faces (Pale Garden & Spring to Life ready).
- 🥔 **ASS PC Mode (Extreme Potato Suite)**: Dedicated ultra-low-end tab featuring **Shit Foliage** (**75% fewer vertices**, flat lighting, 24-block clutter cutoff), entity limb/animation freeze, flat 2D items & XP orbs, particle hard-caps, sprite animation freeze, and shadow/glint/sky/fog/chest-lid stripping.
- 🖥️ **Exordium HUD Frame-Skip Buffering**: Decouples 2D HUD & chat rendering from your 3D framerate—caching GUI passes and instantly waking on mouse, keyboard, or chat updates.
- 👁️ **Smart Occlusion & Frustum Culling**: Skips invisible entities, block entities behind walls, distant item frames, beacon beams, and off-screen particles with zero per-frame heap allocations.
- 🧹 **Memory Leak Fixes & C2ME Fast-Paths**: Fixes vanilla `ThreadLocal` biome & client texture leaks, accelerates `NbtCompound` / `RegionBasedStorage` chunk I/O, and includes built-in **VISK** compatibility (`ViskCompatBridge`).
- 📊 **1s Rolling-Window Telemetry HUD**: Tracks real-time **FPS**, **1% Low**, and **0.1% Low** frame times using a 4,096-sample nanosecond circular buffer with zero GC pressure.

---

## 📸 In-Game Settings

Seamlessly integrated into **VulkanMod's Video Settings GUI** and **Mod Menu** across dedicated tabs (`Culling`, `Graphics`, `Vulkan GPU`, `Performance`, `Exordium HUD`, and `ASS PC`):

<div align="center">
  <img src="assets/settings_menu.png" alt="Vulkan Plus In-Game Settings" width="600" />
</div>

---

## 🎛️ One-Click Presets & Modes

Pick the best profile for your hardware with a single click:

| Preset / Mode | Recommended For | What It Does |
| :--- | :--- | :--- |
| ⚡ **Fast** | Laptops & budget PCs | Maximum FPS, Fast Foliage (50% quad cut), aggressive culling, opaque leaves |
| ⚖️ **Balanced** | Everyday gaming *(Default)* | High FPS, smart transparent leaves, visibility-invariant chunk uploads, smooth pacing |
| 🔥 **Extreme** | High-end gaming PCs | Maximum render distance, Reverse-Z 32-bit float depth, Resizable BAR, ultra caching |
| 🥔 **ASS PC Tab** | Integrated GPUs & ultra-low-end | Unlocks *Shit Foliage* (75% quad reduction + flat lighting), frozen animations, 2D items/orbs, and zero decorative FX |

---

## ⌨️ Controls

| Key | What It Does |
| :---: | :--- |
| <kbd>P</kbd> | Toggle Vulkan Plus on / off in real-time |
| <kbd>F8</kbd> | Toggle the Diagnostic HUD *(FPS, 1.0s rolling 1% Low & 0.1% Low FPS, frame-time graph, active subsystems)* |

---

## 📥 How to Install

1. Install **Fabric Loader 0.16.0+** for **Minecraft 1.21.11**.
2. Put **Fabric API** in your `.minecraft/mods` folder.
3. Put **[VulkanMod 0.6.8+](https://github.com/xCollateral/VulkanMod)** in your `.minecraft/mods` folder.
4. Put **`vulkan-plus-1.0.0.jar`** into `.minecraft/mods`.
5. Launch your game!

---

<details>
<summary><b>🛠️ Deep Technical Details & Architecture (Click to expand)</b></summary>
<br/>

### GPU & VulkanMod 0.6.8 Engine Hooks (`net.vulkanplus.mixin.vulkan.*`)
- **Resizable BAR (`MemoryTypesMixin`)**: Automatically upgrades `MemoryTypes` to `DeviceMappableMemory` (`VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | HOST_VISIBLE_BIT | HOST_COHERENT_BIT`, `propertyFlags == 7`) when the backing GPU heap $\ge$ 512 MB.
- **Visibility-Invariant `SectionGraph` Elimination (`TaskDispatcherMixin`, `VulkanSectionVisibility`)**: Budgets per-frame chunk uploads (`400,000 ns`) and inspects pre/post `CompileResult` visibility bitmasks (`getVisibility()`) and emptiness (`isCompletelyEmpty()`), returning `false` when invariant to bypass redundant $O(N)$ `SectionGraph.update()` traversals in `WorldRenderer`.
- **Pipeline & Descriptor State Cache (`RendererMixin`, `PipelineMixin`, `VRenderSystemMixin`)**: Intercepts `Renderer.bindGraphicsPipeline(...)` and state transitions via `VulkanStateCache` to eliminate duplicate Vulkan command buffer binds.
- **Persistent PSO Disk Cache (`PipelineMixin`)**: Disk-backed `VkPipelineCache` with hardware/driver UUID validation to eliminate shader compilation micro-stutters.
- **Reverse-Z 32-Bit Float Depth (`ReverseZProjection`, `VkRenderPassMixin`)**: Maps depth from `1.0` (near) to `0.0` (far) with `VK_FORMAT_D32_SFLOAT`, eliminating distant z-fighting and improving Early-Z rejection.
- **Buffer & Staging Memory Pooling (`AreaBufferMixin`, `VulkanBufferPool`, `TransientRingBuffer`)**: Recycles staging and vertex buffers with slab allocation to cut `vkAllocateMemory` overhead.
- **Swapchain Tuning (`SwapChainMixin`)**: Configurable low-latency `VK_PRESENT_MODE_MAILBOX_KHR` presentation and frame-pacing fences.

### 1.21.11 Foliage & Non-Full Block Engine (`FoliageCuller`)
- **Cross-Quad Reduction (`VulkanBlockRendererMixin`, `BlockModelRendererMixin`)**: Emits a single double-sided diagonal plane (50% vertex reduction in `enableFastFoliage`) or a single one-sided flat-lit plane (75% vertex reduction in `shitFoliage`) across all 1.21.11 foliage (including Pale Garden & Spring to Life blocks: `ShortDryGrassBlock`, `TallDryGrassBlock`, `FireflyBushBlock`, `CactusFlowerBlock`, `LeafLitterBlock`, `FlowerbedBlock`, `HangingMossBlock`, `PaleMossCarpetBlock`, `EyeblossomBlock`, etc.).
- **Deterministic Clutter Thinning (`FoliageBlockMixin`)**: Position-hashed `(x, z)` filtering (`100%`, `75%`, `50%`, `25%`) guarantees upper and lower halves of `TallPlantBlock` / `TallFlowerBlock` never desync while protecting gameplay-relevant blocks (`SugarCaneBlock`, `VineBlock`, `SweetBerryBushBlock`, `BambooBlock`, `CropBlock`, `SaplingBlock`).
- **Zero Offset Hashing & Stacked Face Culling**: Replaces per-block random `getModelOffset` hashing with `Vec3d.ZERO` and culls hidden interior faces between stacked `SugarCaneBlock`, `BambooBlock`, `KelpBlock`, `VineBlock`, and `MangroveRootsBlock` segments.

### CPU, Exordium HUD, C2ME & Memory Leak Fixes
- **Exordium HUD Frame-Skip (`ExordiumManager`)**: Renders `InGameHud` and `ChatHud` at a configurable frame interval while immediately invalidating on `MouseMixin`, `KeyboardMixin`, or chat events.
- **Elevated Render Thread & Worker Regulation (`ThreadPriorityManager`, `UtilMixin`)**: Boosts the main render thread (`Thread.MAX_PRIORITY - 2`) and throttles background worker threads to prevent chunk-gen frame drops.
- **Zero-Allocation Hot Paths (`MatrixPool`, `FastXoroshiro128PlusPlus`)**: Stack-allocated `Matrix4f`/`Vector4f` pooling and lock-free `Xoroshiro128++` PRNG for particle jitter and frustum/occlusion culling.
- **Memory Leak & Storage Fixes (`BiomeMixin`, `MinecraftClientMixin`, `ReloadableTextureMixin`, `RegionBasedStorageMixin`, `NbtCompoundMixin`)**: Prevents `ThreadLocal` retention leaks across world reloads and optimizes NBT map lookups and region cache eviction.

### Building & Testing from Source
```bash
git clone https://github.com/bro4cvf-hash/VulkanPlus.git
cd VulkanPlus
./gradlew test build
```
Output JAR: `build/libs/vulkan-plus-1.0.0.jar`

</details>

---

## 👥 Credits & Authorship

- **Author**: [bro4cvf-hash](https://github.com/bro4cvf-hash)
- **Companion to**: [VulkanMod](https://github.com/xCollateral/VulkanMod)
- **License**: [MIT](LICENSE)
