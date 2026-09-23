<div align="center">

# ⚡ Vulkan Plus

**The high-performance companion optimization mod for VulkanMod on Minecraft Fabric.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-2ea44f?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.16.0+-blue?style=for-the-badge&logo=fabric&logoColor=white)](https://fabricmc.net/)
[![Release](https://img.shields.io/badge/Release-v1.0.0-orange?style=for-the-badge)](https://github.com/bro4cvf-hash/vulkanplus/releases)
[![Java](https://img.shields.io/badge/Java-21+-red?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Vulkan](https://img.shields.io/badge/Vulkan-1.3-critical?style=for-the-badge&logo=vulkan&logoColor=white)](https://www.vulkan.org/)
[![License](https://img.shields.io/badge/License-MIT-purple?style=for-the-badge)](LICENSE)

<p align="center">
  Experience extreme framerates, butter-smooth frame pacing, and zero micro-stutters.
</p>

</div>

---

## 📖 Overview

**Vulkan Plus** supercharges client rendering performance when running alongside **[VulkanMod](https://github.com/xCollateral/VulkanMod)**. It eliminates CPU-side render bottlenecks, eradicates garbage collection stalls with zero-allocation math, introduces smart occlusion culling, and balances thread priorities.

### 🛡️ Dual-Tier Engine Architecture
- **Companion Mode (Active with VulkanMod)**: Injects low-level native Vulkan optimizations—persistent disk PSO caching, Reverse-Z 32-bit floating point depth, staging ring buffers, and Mailbox swapchain presentation.
- **Standalone CPU Fallback (Active without VulkanMod)**: Automatically isolates Vulkan symbols behind reflective bridges, executing CPU-side frustum culling, smart leaves, and fast math safely without crashes.

---

## ✨ Features

### 🚀 Vulkan & GPU Pipeline
- **Persistent PSO Disk Cache**: Disk-backed `VkPipelineCache` with UUID validation eliminating shader compilation micro-stutter.
- **Reverse-Z 32-Bit Float Depth**: Maps depth from 1.0 to 0.0 with `VK_FORMAT_D32_SFLOAT`, eliminating distant z-fighting and maximizing Early-Z rejection.
- **Buffer & Staging Memory Pooling**: Native transient ring buffers and slab allocators recycling staging and vertex buffers to slash driver overhead.
- **Descriptor Set Caching**: Filters redundant GPU pipeline and descriptor binding calls.
- **Swapchain Tuning & Pacing**: Configurable low-latency Mailbox presentation mode (`VK_PRESENT_MODE_MAILBOX_KHR`) and frame pacing fences.

### 👁️ Intelligent Occlusion & Culling
- **More Culling**: Skips rendering off-screen entities and mobs outside the camera frustum.
- **Block Entity Occlusion**: Culls hidden chests, signs, and skulls concealed behind solid opaque blocks.
- **Fast Item Frames**: Directional backface and wall occlusion culling to speed up massive storage halls.
- **Smart Leaves Culling**: Eliminates hidden interior foliage faces while preserving fancy transparent leaf textures.
- **Beacon Beam & Glass Culling**: Drops out-of-view beacon beams and adjoining obscured glass faces.

### 🧵 CPU, Threading & Zero-Allocation Math
- **Elevated Render Thread**: Prioritizes the main render loop (`Thread.MAX_PRIORITY - 2`) to eliminate chunk generation stutter.
- **Worker Priority Regulation**: Throttles background chunk generation and I/O tasks to prevent CPU starvation.
- **Zero-Allocation Hot Paths**: Replaces per-frame heap allocations (`Vec3d`, `Box`, matrices) with stack-based primitive pooling (`MatrixPool`).
- **Vectorized Fast Math**: High-precision polynomial approximations for trigonometric operations.
- **Fast Xoroshiro128++ PRNG**: Lock-free, high-performance pseudo-random generator replacing synchronized vanilla RNG in render loops.

---

## 🎛️ Performance Presets

Configure performance with one click in the Mod Menu settings screen:

| Preset | Target Environment | Highlights |
| :--- | :--- | :--- |
| **Fast** | Low-end & laptop hardware | Aggressive entity culling, opaque leaf optimization, low-latency present mode |
| **Balanced** | Recommended daily gaming | Full frustum culling, smart transparent leaves, PSO caching, smooth pacing |
| **Extreme** | High-end GPUs & extreme render distance | Reverse-Z float depth, maximum memory pooling, deep block occlusion culling |

---

## ⌨️ Controls & Keybinds

| Keybind | Action | Description |
| :---: | :--- | :--- |
| <kbd>P</kbd> | **Toggle Optimizations** | Real-time master toggle for all Vulkan Plus features |
| <kbd>F8</kbd> | **Diagnostics HUD** | Toggle real-time overlay showing FPS, 1% low frame times, and active culling |

*Keybinds can be customized under **Options → Controls → Key Binds**.*

---

## 📥 Installation

1. Install **Fabric Loader** (`0.16.0` or newer) for **Minecraft 1.21.11**.
2. Download and place **Fabric API** into your `.minecraft/mods` directory.
3. *(Recommended)* Download and install **[VulkanMod](https://github.com/xCollateral/VulkanMod)** into your `mods` directory.
4. Place **`vulkan-plus-1.0.0.jar`** into your `mods` directory.
5. Launch Minecraft and enjoy maximum performance!

---

## 🛠️ Building from Source

### Prerequisites
- **JDK 21** or later (Adoptium Temurin recommended)
- **Git**

### Build Steps

```bash
# Clone the repository
git clone https://github.com/bro4cvf-hash/vulkanplus.git
cd vulkanplus

# Run test suite
./gradlew test

# Compile release JAR
./gradlew build
```

The compiled mod JAR will be output to:
`build/libs/vulkan-plus-1.0.0.jar`

---

## 👥 Credits & Authorship

- **Author & Maintainer**: [bro4cvf-hash](https://github.com/bro4cvf-hash)
- **VulkanMod**: [xCollateral/VulkanMod](https://github.com/xCollateral/VulkanMod)
- **License**: [MIT](LICENSE)
