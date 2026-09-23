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

Minecraft rendering can get bogged down by thousands of entities, massive chest halls, thick forests, and background world generation.

**Vulkan Plus** works hand-in-hand with VulkanMod to solve these bottlenecks:

- 🚀 **Extreme Framerate Boost**: Fully unleashes your GPU using native Vulkan 1.3 pipeline caching.
- 👁️ **Smart Culling**: Skips rendering things you can't see (mobs behind walls, off-screen particles, chests behind solid blocks).
- 🌲 **Lag-Free Forests & Storage**: Eliminates lag in dense jungles (Smart Leaves) and giant chest halls (Fast Item Frames).
- 🧵 **Zero Stutters**: Prioritizes smooth rendering so background chunk generation never causes frame drops.
- 🛡️ **Safety Fallback**: If you launch without VulkanMod, CPU optimizations still kick in automatically with zero crashes.

---

## 📸 In-Game Settings

Seamlessly integrated into your **Video Settings** and **Mod Menu**:

<div align="center">
  <img src="assets/settings_menu.png" alt="Vulkan Plus In-Game Settings" width="600" />
</div>

---

## 🎛️ One-Click Presets

Pick the best profile for your PC with a single click:

| Preset | Recommended For | What It Does |
| :--- | :--- | :--- |
| ⚡ **Fast** | Laptops & budget PCs | Maximum FPS, aggressive culling, opaque leaves |
| ⚖️ **Balanced** | Everyday gaming *(Default)* | High FPS, smart transparent leaves, smooth pacing |
| 🔥 **Extreme** | High-end gaming PCs | Maximum render distance, Reverse-Z depth, ultra caching |

---

## ⌨️ Controls

| Key | What It Does |
| :---: | :--- |
| <kbd>P</kbd> | Toggle Vulkan Plus on / off in real-time |
| <kbd>F8</kbd> | Toggle the Diagnostic HUD *(FPS, 1% low frame times, active culling)* |

---

## 📥 How to Install

1. Install **Fabric Loader 0.16.0+** for **Minecraft 1.21.11**.
2. Put **Fabric API** in your `.minecraft/mods` folder.
3. Put **[VulkanMod](https://github.com/xCollateral/VulkanMod)** in your `.minecraft/mods` folder.
4. Put **`vulkan-plus-1.0.0.jar`** into `.minecraft/mods`.
5. Launch your game!

---

<details>
<summary><b>🛠️ Deep Technical Details & Architecture (Click to expand)</b></summary>
<br/>

### GPU & Vulkan Pipeline
- **Persistent PSO Disk Cache**: Disk-backed `VkPipelineCache` with UUID validation eliminating shader compilation micro-stutters.
- **Reverse-Z 32-Bit Float Depth**: Maps depth from 1.0 to 0.0 with `VK_FORMAT_D32_SFLOAT`, eliminating distant z-fighting and maximizing Early-Z rejection.
- **Buffer & Staging Memory Pooling**: Native transient ring buffers and slab allocators recycling staging and vertex buffers to slash driver overhead.
- **Descriptor Set Caching**: Filters redundant GPU pipeline and descriptor binding calls.
- **Swapchain Tuning & Pacing**: Configurable low-latency Mailbox presentation mode (`VK_PRESENT_MODE_MAILBOX_KHR`) and frame pacing fences.

### CPU & Math Hot Paths
- **Elevated Render Thread**: Prioritizes the main render loop (`Thread.MAX_PRIORITY - 2`) to eliminate chunk generation stutter.
- **Worker Priority Regulation**: Throttles background chunk generation and I/O tasks to prevent CPU starvation.
- **Zero-Allocation Hot Paths**: Replaces per-frame heap allocations (`Vec3d`, `Box`, matrices) with stack-based primitive pooling (`MatrixPool`).
- **Vectorized Fast Math**: High-precision polynomial approximations for trigonometric operations.
- **Fast Xoroshiro128++ PRNG**: Lock-free, high-performance pseudo-random generator replacing synchronized vanilla RNG in render loops.

### Building from Source
```bash
git clone https://github.com/bro4cvf-hash/VulkanPlus.git
cd VulkanPlus
./gradlew build
```
Output JAR: `build/libs/vulkan-plus-1.0.0.jar`

</details>

---

## 👥 Credits & Authorship

- **Author**: [bro4cvf-hash](https://github.com/bro4cvf-hash)
- **Companion to**: [VulkanMod](https://github.com/xCollateral/VulkanMod)
- **License**: [MIT](LICENSE)
