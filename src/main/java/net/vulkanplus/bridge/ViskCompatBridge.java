package net.vulkanplus.bridge;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.vulkanplus.VulkanPlusMod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;

/**
 * Compatibility bridge for Visk Client (and similar custom Blaze3D/Vulkan UI renderers)
 * that cache {@link GpuTextureView} instances by {@link Identifier} without checking
 * {@code view.isClosed()} or {@code view.texture().isClosed()} after resource reloads.
 * <p>
 * In VulkanMod, {@code VkCommandEncoder.setupUniforms} skips binding any {@code VkGpuTexture}
 * whose {@code isClosed()} flag is true, leaving the previously bound texture (such as the
 * Font Glyph Atlas from text rendering) bound to {@code Sampler0} and causing UI icons to
 * render the squished font atlas instead of their PNG sprites.
 */
public final class ViskCompatBridge {
    private static boolean initialized = false;
    private static boolean viskPresent = false;
    private static volatile boolean cacheDirty = true;

    private static Map<Identifier, GpuTextureView> spriteTextureViewCache;
    private static Object spriteRendererInstance;
    private static Method clearGlintCacheMethod;
    private static Object bindlessTrackerInstance;
    private static Method resetBindlessFrameMethod;

    private ViskCompatBridge() {
    }

    @SuppressWarnings("unchecked")
    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Class<?> spriteRendererClass = Class.forName("dev.visk.utils.render.shader.SpriteRenderer");
            Field cacheField = spriteRendererClass.getDeclaredField("TEXTURE_VIEW_CACHE");
            cacheField.setAccessible(true);
            spriteTextureViewCache = (Map<Identifier, GpuTextureView>) cacheField.get(null);

            try {
                Field instanceField = spriteRendererClass.getDeclaredField("INSTANCE");
                instanceField.setAccessible(true);
                spriteRendererInstance = instanceField.get(null);
                clearGlintCacheMethod = spriteRendererClass.getDeclaredMethod("clearGlintCache");
                clearGlintCacheMethod.setAccessible(true);
            } catch (Throwable ignored) {
            }

            try {
                Class<?> trackerClass = Class.forName("dev.visk.utils.render.vulkan.tier3.BindlessTextureTracker");
                Field trackerInstanceField = trackerClass.getDeclaredField("INSTANCE");
                trackerInstanceField.setAccessible(true);
                bindlessTrackerInstance = trackerInstanceField.get(null);
                resetBindlessFrameMethod = trackerClass.getDeclaredMethod("resetFrame");
                resetBindlessFrameMethod.setAccessible(true);
            } catch (Throwable ignored) {
            }

            viskPresent = (spriteTextureViewCache != null);
            if (viskPresent) {
                VulkanPlusMod.LOGGER.info("[VulkanPlus] Visk Client detected — enabled GpuTextureView cache lifecycle guard.");
            }
        } catch (ClassNotFoundException ignored) {
            viskPresent = false;
        } catch (Throwable t) {
            viskPresent = false;
            VulkanPlusMod.LOGGER.debug("[VulkanPlus] Visk compatibility probe skipped: {}", t.getMessage());
        }
    }

    public static void markTextureReloaded() {
        cacheDirty = true;
    }

    public static void sanitizeTextureCachesIfDirty() {
        if (!cacheDirty) {
            return;
        }
        cacheDirty = false;
        sanitizeTextureCaches();
    }

    /**
     * Evicts or refreshes any closed or replaced {@link GpuTextureView} entries in Visk's
     * {@code SpriteRenderer.TEXTURE_VIEW_CACHE} before UI rendering occurs.
     */
    public static void sanitizeTextureCaches() {
        ensureInitialized();
        if (!viskPresent || spriteTextureViewCache == null || spriteTextureViewCache.isEmpty()) {
            return;
        }

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            TextureManager textureManager = (client != null) ? client.getTextureManager() : null;
            boolean purgedAny = false;

            Iterator<Map.Entry<Identifier, GpuTextureView>> it = spriteTextureViewCache.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Identifier, GpuTextureView> entry = it.next();
                Identifier id = entry.getKey();
                GpuTextureView cachedView = entry.getValue();

                boolean stale = (cachedView == null)
                        || cachedView.isClosed()
                        || (cachedView.texture() == null)
                        || cachedView.texture().isClosed();

                if (!stale && textureManager != null && id != null) {
                    AbstractTexture currentTex = textureManager.getTexture(id);
                    if (currentTex != null) {
                        GpuTextureView liveView = currentTex.getGlTextureView();
                        if (liveView != cachedView) {
                            stale = true;
                        }
                    }
                }

                if (stale) {
                    it.remove();
                    purgedAny = true;
                }
            }

            if (purgedAny) {
                if (spriteRendererInstance != null && clearGlintCacheMethod != null) {
                    clearGlintCacheMethod.invoke(spriteRendererInstance);
                }
                if (bindlessTrackerInstance != null && resetBindlessFrameMethod != null) {
                    resetBindlessFrameMethod.invoke(bindlessTrackerInstance);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Resolves a live {@link GpuTextureView} from {@link TextureManager} if the supplied view
     * or its underlying {@link com.mojang.blaze3d.textures.GpuTexture} has been closed by a resource reload.
     */
    public static GpuTextureView healTextureView(GpuTextureView view) {
        if (view == null) {
            return null;
        }
        com.mojang.blaze3d.textures.GpuTexture tex = view.texture();
        if (view.isClosed() || (tex != null && tex.isClosed())) {
            try {
                sanitizeTextureCaches();
                if (tex != null) {
                    String label = tex.getLabel();
                    if (label != null && !label.isEmpty()) {
                        Identifier id = Identifier.tryParse(label);
                        if (id != null) {
                            MinecraftClient client = MinecraftClient.getInstance();
                            if (client != null) {
                                TextureManager tm = client.getTextureManager();
                                if (tm != null) {
                                    AbstractTexture liveTex = tm.getTexture(id);
                                    if (liveTex != null) {
                                        GpuTextureView liveView = liveTex.getGlTextureView();
                                        if (liveView != null && !liveView.isClosed()) {
                                            if (viskPresent && spriteTextureViewCache != null) {
                                                spriteTextureViewCache.put(id, liveView);
                                            }
                                            return liveView;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return view;
    }

    /**
     * Forcefully clears all cached {@link GpuTextureView} entries when a resource reload is triggered.
     */
    public static void invalidateAll() {
        ensureInitialized();
        if (!viskPresent || spriteTextureViewCache == null) {
            return;
        }
        try {
            spriteTextureViewCache.clear();
            if (spriteRendererInstance != null && clearGlintCacheMethod != null) {
                clearGlintCacheMethod.invoke(spriteRendererInstance);
            }
            if (bindlessTrackerInstance != null && resetBindlessFrameMethod != null) {
                resetBindlessFrameMethod.invoke(bindlessTrackerInstance);
            }
        } catch (Throwable ignored) {
        }
    }
}
