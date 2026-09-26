<div align="center">

# ⚡ Vulkan Plus

**The ultimate companion performance mod for [VulkanMod](https://github.com/xCollateral/VulkanMod) on Fabric.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-2ea44f?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.18.6+-blue?style=for-the-badge&logo=fabric&logoColor=white)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21+-red?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Release](https://img.shields.io/badge/Release-v1.2.0-orange?style=for-the-badge)](https://github.com/bro4cvf-hash/VulkanPlus/releases)
[![License](https://img.shields.io/badge/License-MIT-purple?style=for-the-badge)](LICENSE)

<br/>

<img src="assets/fps_benchmark.png" alt="Benchmark Showcase" width="280" />

*Insane throughput, rock-solid frame pacing, sub-millisecond tail latencies, and zero micro-stutters.*

</div>

---

## 📊 Benchmark Telemetry & Performance Stats

Tested on **RTX 5060 (8GB, Vulkan 610.62.0.0)**, **12 CPU Cores**, **Windows 11 (MMCSS + 1ms Timer)**, **Minecraft 1.21.11 Fabric** with 54 active mods across an intensive 19-segment cinematic stress test (3,600 ticks / 180 seconds).

> 💡 **Interactive Dashboard**: A standalone telemetry visualizer is available at [[`vulkanplus_benchmark_showcase.html`](vulkanplus_benchmark_showcase.html)](vulkanplus_benchmark_showcase.html) (or on your Desktop).

### 🚀 1. Uncapped Max Throughput (Pure GPU & CPU Acceleration)

| Metric | Without Vulkan | VulkanPlus Enabled | Advantage (Δ) | Improvement |
| :--- | :--- | :--- | :--- | :--- |
| **Average FPS** | **2,203.1 FPS** | **2,785.9 FPS** | **+582.8 FPS** | **+26.45% ▲** |
| **1% Low FPS** | **460.9 FPS** | **815.8 FPS** | **+354.9 FPS** | **+77.02% ▲** |
| **0.1% Low FPS** | **206.1 FPS** | **369.7 FPS** | **+163.6 FPS** | **+79.37% ▲** |
| **Minimum FPS** | **73.5 FPS** | **127.9 FPS** | **+54.4 FPS** | **+73.97% ▲** |
| **Harmonic Average FPS** | **1,915.5 FPS** | **2,610.8 FPS** | **+695.3 FPS** | **+36.30% ▲** |
| **Average Frame Time** | **0.52 ms** | **0.38 ms** | **-0.14 ms** | **-26.92% ▼** |
| **p95 Frame Time** | **0.96 ms** | **0.54 ms** | **-0.42 ms** | **-43.75% ▼** |
| **p99 Frame Time** | **1.48 ms** | **0.81 ms** | **-0.67 ms** | **-45.27% ▼** |
| **Worst-Case Latency** | **13.60 ms** | **7.82 ms** | **-5.78 ms** | **-42.50% ▼** |
| **Client Tick Average** | **0.69 ms** | **0.56 ms** | **-0.13 ms** | **-18.84% ▼** |

---

### 🎯 2. 120 FPS Paced Benchmark (Micro-Stutter Elimination & Frame Pacing)

| Metric | Without Vulkan | VulkanPlus Enabled | Advantage (Δ) | Improvement |
| :--- | :--- | :--- | :--- | :--- |
| **1% Low FPS** | **55.75 FPS** | **88.06 FPS** | **+32.31 FPS** | **+57.95% ▲** |
| **0.1% Low FPS** | **39.28 FPS** | **44.75 FPS** | **+5.47 FPS** | **+13.93% ▲** |
| **Severe Stutter Frames (≥ 10ms)** | 2,014 frames (9.52%) | **86 frames (0.40%)** | **-1,928 frames** | **-95.73% ▼ (Eliminated)** |
| **Target Band Density (7–9ms)** | 81.90% | **99.11%** | **+17.21%** | **Near-Flawless Consistency** |
| **Frame Time Jitter (Std Dev)** | 1.93 ms | **0.81 ms** | **-1.12 ms** | **-58.17% ▼** |
| **Frame Time 99th Percentile** | 15.31 ms | **9.43 ms** | **-5.88 ms** | **-38.37% ▼** |
| **Average Client Tick** | 0.81 ms | **0.51 ms** | **-0.30 ms** | **-37.55% ▼** |
| **Max Client Tick Spike** | 7.06 ms | **1.96 ms** | **-5.10 ms** | **-72.19% ▼** |
| **GC Total Pause Duration** | 284 ms (55 pauses) | **178 ms (34 pauses)** | **-106 ms** | **-37.32% ▼** |
| **Total Frames Rendered** | 21,166 frames | **21,582 frames** | **+416 frames** | **+1.97% ▲** |

---

## ⚡ What Does Vulkan Plus Do?

Minecraft rendering can get bogged down by thousands of entities, massive chest halls, thick 1.21.11 forests/meadows, and background world generation.

**Vulkan Plus** works hand-in-hand with VulkanMod to eliminate CPU, memory, and GPU bottlenecks:

- 🏎️ **Deep VulkanMod GPU Engine**: Activates **Resizable BAR (`DeviceMappableMemory`)**, persistent **PSO disk caching**, **8-slot multi-descriptor state deduplication**, branchless dynamic state filters (117.96M ops/sec), and **visibility-invariant `SectionGraph` skips** with sub-frame chunk upload budgeting.
- ⏱️ **Windows MMCSS & High-Resolution Timer Subsystem**: Native Windows Multimedia Class Scheduler Service integration (`avrt.dll`) and 1ms timer precision (`winmm.dll`, `timeBeginPeriod(1)`) giving render and game loop threads real-time OS priority without thread starvation.
- 🎯 **Triple-Buffered Frame Pacing Queue**: Tuned triple-buffering (`frameQueueSize = 3`) that completely decouples swapchain presentation acquisition from render submissions, eliminating pipeline stalls.
- 🔮 **Pipeline State Object (PSO) Prewarmer**: Prewarms 84+ Vulkan graphics and compute pipelines at startup to eliminate runtime shader compilation judder.
- 🌿 **1.21.11 Foliage & Plant Suite**: Cuts cross-model plant geometry by **50%** (`Fast Foliage`), thins decorative ground clutter deterministically (`Foliage Density`: 100% / 75% / 50% / 25%) without splitting 2-block tall plants or hiding gameplay blocks, removes model-offset hash overhead, and culls stacked interior plant faces (Pale Garden & Spring to Life ready).
- 🥔 **ASS PC Mode (Extreme Potato Suite)**: Dedicated ultra-low-end tab featuring **Engine Fullbright** (bypasses chunk lighting & AO meshing and cancels light-update chunk rebuilds), **Shit Foliage** (**75% fewer vertices**, flat lighting, 24-block clutter cutoff), entity limb/animation freeze, flat 2D items & XP orbs, particle hard-caps, sprite animation freeze, and shadow/glint/sky/fog/chest-lid stripping.
- 🖥️ **Exordium GUI & HUD Decoupling**: Decouples 2D HUD, chat, and container screens from 3D world framerate using Minecraft 1.21.11's native `GuiRenderState` record replay — yielding zero offscreen framebuffer overhead and 0ms input latency.
- 👁️ **Smart Occlusion, Distance LOD & Frustum Culling**: Skips invisible entities, block entities behind walls, distant item frames, beacon beams, off-screen particles, and includes **distance-tiered entity animation LOD** (Tier 0–3) and **ground shadow culling** with zero per-frame heap allocations.
- 🛡️ **Memory Leak Fixes & Storage Fast-Paths**: Persistent VMA memory management, vanilla `ThreadLocal` biome & client texture leak fixes, accelerated `NbtCompound` / `RegionBasedStorage` chunk I/O, and auto-yielding to existing optimization mods (`c2me`, `ferritecore`, `memoryleakfix`, `exordium`).
- 📈 **Decoupled Telemetry HUD & FPS Counter**: Tracks real-time **FPS**, **Average**, **1% Low**, and **0.1% Low** metrics with zero-allocation `StringBuilder` formatting.

---

## 🛠️ In-Game Settings

Seamlessly integrated into **VulkanMod's Video Settings GUI** (`General`, `Culling`, `Engine`, `Exordium`, `ASS PC`) and **Mod Menu** (`General`, `Culling`, `Graphics`, `Engine`, `Exordium`, `ASS PC`):

<div align="center">
  <img src="assets/settings_menu.png" alt="Vulkan Plus In-Game Settings" width="600" />
</div>

---

## 🎛️ One-Click Presets & Modes

Pick the best profile for your hardware with a single click:

| Preset / Mode | Recommended For | What It Does |
| :--- | :--- | :--- |
| ⚡ **Fast** | Laptops & budget PCs | Maximum FPS, Fast Foliage (50% quad cut), aggressive culling, opaque leaves, distance animation LOD |
| ⚖️ **Balanced** | Everyday gaming *(Default)* | High FPS, smart transparent leaves, visibility-invariant chunk uploads, smooth pacing, balanced animation LOD |
| 🔥 **Extreme** | High-end gaming PCs | Maximum render distance, Reverse-Z 32-bit float depth, Resizable BAR, ultra caching |
| 🥔 **ASS PC Tab** | Integrated GPUs & ultra-low-end | Unlocks *Shit Foliage* (75% quad reduction + flat lighting), **Engine Fullbright** (bypasses AO and chunk light updates), frozen animations, 2D items, no shadows, no glint, and maximum stripdown |

---

## ⌨️ Controls

| Key | What It Does |
| :---: | :--- |
| <kbd>P</kbd> | Toggle Vulkan Plus on / off in real-time |
| <kbd>F8</kbd> | Toggle the Diagnostic HUD *(FPS, 1.0s rolling Average, 1% Low & 0.1% Low FPS, VRAM usage, culled entity/particle metrics, active preset & present mode)* |
| *Unbound* | Toggle minimal Show FPS counter overlay in top-left *(Configurable in Options -> Key Binds)* |

---

## 📦 How to Install

1. Ensure **Java 21+** is installed on your system.
2. Install **Fabric Loader 0.18.6+** for **Minecraft 1.21.11**.
3. Put **Fabric API** in your `.minecraft/mods` folder.
4. Put **[VulkanMod 0.6.8+](https://github.com/xCollateral/VulkanMod)** in your `.minecraft/mods` folder.
5. Put **`vulkan-plus-1.2.0.jar`** into `.minecraft/mods`.
6. Launch your game!

---

<details>
<summary><b>🔬 Deep Technical Details & Architecture (Click to expand)</b></summary>
<br/>

### GPU & VulkanMod 0.6.8 Engine Hooks (`net.vulkanplus.mixin.vulkan.*`)
- **Resizable BAR (`MemoryTypesMixin`)**: Automatically upgrades `MemoryTypes` to `DeviceMappableMemory` (`VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | HOST_VISIBLE_BIT | HOST_COHERENT_BIT`, `propertyFlags == 7`) when the backing GPU heap $\ge$ 512 MB.
- **Visibility-Invariant `SectionGraph` Elimination (`TaskDispatcherMixin`, `VulkanSectionVisibility`)**: Budgets per-frame chunk uploads (`400,000 ns`) and inspects pre/post `CompileResult` visibility bitmasks (`getVisibility()`) and emptiness (`isCompletelyEmpty()`), returning `false` when invariant to bypass redundant $O(N)$ `SectionGraph.update()` traversals in `WorldRenderer`.
- **Multi-Slot Descriptor & Dynamic State Cache (`VulkanStateCache`, `RendererMixin`, `PipelineMixin`, `VRenderSystemMixin`)**: 8-slot multi-descriptor caching across graphics and compute bind points, with branchless bitpacked dynamic state filters (benchmarked at 117.96M ops/sec) to eliminate redundant Vulkan driver dispatch calls.
- **Persistent PSO Disk Cache & Prewarming (`PipelinePrewarmer`, `PersistentPipelineCache`, `PipelineMixin`)**: Disk-backed `VkPipelineCache` with hardware/driver UUID validation and 84 prewarmed pipeline state objects.
- **Reverse-Z 32-Bit Float Depth (`ReverseZProjection`, `VkRenderPassMixin`)**: Maps depth from `1.0` (near) to `0.0` (far) with `VK_FORMAT_D32_SFLOAT`, eliminating distant z-fighting and improving Early-Z rejection.
- **Buffer & Transient Memory Pooling (`AreaBufferMixin`, `SlabSubAllocator`, `PersistentVmaManager`)**: Zero-allocation offset sub-allocator (9.27 ns/op) and persistent VMA memory pooling to prevent buffer leaks and cut allocation overhead.
- **Swapchain Tuning & Pacing (`SwapChainMixin`, `SwapchainTuning`)**: Configurable low-latency presentation with triple-buffering queue pacing (`frameQueueSize = 3`).

### Windows MMCSS & Thread Scheduling (`ThreadPriorityManager`)
- **Real-Time Thread Elevation**: Automatically interfaces with `avrt.dll` (`AvSetMmThreadCharacteristicsW("Pro Audio" / "Games")`) to boost render and tick thread priority classes in the Windows kernel.
- **High-Resolution Timer Precision**: Integrates with `winmm.dll` via `timeBeginPeriod(1)` to lock OS scheduling ticks to 1.0ms resolution, eliminating thread sleep overshoots.

### 1.21.11 Foliage & Non-Full Block Engine (`FoliageCuller`)
- **Cross-Quad Reduction (`VulkanBlockRendererMixin`, `BlockModelRendererMixin`)**: Emits a single double-sided diagonal plane (50% vertex reduction in `enableFastFoliage`) or a single one-sided flat-lit plane (75% vertex reduction in `shitFoliage`) across all 1.21.11 foliage (including Pale Garden & Spring to Life blocks: `ShortDryGrassBlock`, `TallDryGrassBlock`, `FireflyBushBlock`, `CactusFlowerBlock`, `LeafLitterBlock`, `FlowerbedBlock`, `HangingMossBlock`, `PaleMossCarpetBlock`, `EyeblossomBlock`, etc.).
- **Deterministic Clutter Thinning (`FoliageBlockMixin`)**: Position-hashed `(x, z)` filtering (`100%`, `75%`, `50%`, `25%`) guarantees upper and lower halves of `TallPlantBlock` / `TallFlowerBlock` never desync while protecting gameplay-relevant blocks.
- **Zero Offset Hashing & Stacked Face Culling**: Replaces per-block random `getModelOffset` hashing with `Vec3d.ZERO` and culls hidden interior faces between stacked foliage segments.

### Building & Testing from Source
> **Requirement**: JDK 21+ (Java 21 OpenJDK)

```bash
git clone https://github.com/bro4cvf-hash/VulkanPlus.git
cd VulkanPlus

# Linux / macOS
./gradlew test build

# Windows PowerShell / CMD
.\gradlew.bat test build
```
Output JAR: `build/libs/vulkan-plus-1.2.0.jar`

</details>

---

## 👥 Credits & Authorship

- **Author**: [bro4cvf-hash](https://github.com/bro4cvf-hash)
- Companion to the amazing [VulkanMod](https://github.com/xCollateral/VulkanMod) by xCollateral.
- Distributed under the [MIT License](LICENSE).
