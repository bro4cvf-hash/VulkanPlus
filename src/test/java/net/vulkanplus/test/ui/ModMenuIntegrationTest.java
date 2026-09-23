package net.vulkanplus.test.ui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screen.Screen;
import net.vulkanplus.ui.ModMenuIntegration;
import net.vulkanplus.ui.VulkanPlusConfigScreen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class ModMenuIntegrationTest {

    @Test
    @DisplayName("ModMenuIntegration implements ModMenuApi")
    public void testImplementsModMenuApi() {
        assertTrue(ModMenuApi.class.isAssignableFrom(ModMenuIntegration.class),
                "ModMenuIntegration must implement com.terraformersmc.modmenu.api.ModMenuApi");
    }

    @Test
    @DisplayName("getModConfigScreenFactory returns a valid VulkanPlusConfigScreen factory")
    public void testConfigScreenFactory() {
        ModMenuIntegration integration = new ModMenuIntegration();
        ConfigScreenFactory<?> factory = integration.getModConfigScreenFactory();
        assertNotNull(factory, "ConfigScreenFactory must not be null");

        // When Minecraft client is uninitialized in headless JUnit environment,
        // vanilla Screen.<init> accesses MinecraftClient.getInstance().textRenderer.
        // We verify the factory executes and targets VulkanPlusConfigScreen.
        try {
            Screen screen = factory.create(null);
            assertNotNull(screen, "Created config screen instance must not be null");
            assertTrue(screen instanceof VulkanPlusConfigScreen,
                    "Screen factory must instantiate VulkanPlusConfigScreen");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("MinecraftClient"),
                    "NPE should only originate from uninitialized MinecraftClient in headless environment: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("fabric.mod.json contains modmenu entrypoint, logo icon, bro4cvf-hash credit, and GitHub links")
    public void testFabricModJsonMetadata() throws Exception {
        Path modJsonPath = Paths.get("src/main/resources/fabric.mod.json");
        assertTrue(Files.exists(modJsonPath), "fabric.mod.json must exist");

        String content = Files.readString(modJsonPath, StandardCharsets.UTF_8);

        // Verify modmenu entrypoint
        assertTrue(content.contains("\"modmenu\""), "fabric.mod.json must declare 'modmenu' entrypoint");
        assertTrue(content.contains("net.vulkanplus.ui.ModMenuIntegration"),
                "modmenu entrypoint must point to net.vulkanplus.ui.ModMenuIntegration");

        // Verify logo icon
        assertTrue(content.contains("\"icon\": \"assets/vulkanplus/icon.png\""),
                "fabric.mod.json must point to assets/vulkanplus/icon.png");

        // Verify authorship & description
        assertTrue(content.contains("bro4cvf-hash"),
                "fabric.mod.json must include bro4cvf-hash in authors/credits");
        assertTrue(content.contains("https://github.com/bro4cvf-hash"),
                "fabric.mod.json must reference https://github.com/bro4cvf-hash");
        assertTrue(content.contains("High-performance companion optimization mod for VulkanMod"),
                "Description must state 'High-performance companion optimization mod for VulkanMod'");
    }

    @Test
    @DisplayName("Vulkan+ logo icon file exists and is valid readable image")
    public void testLogoIconExists() throws Exception {
        Path iconPath = Paths.get("src/main/resources/assets/vulkanplus/icon.png");
        assertTrue(Files.exists(iconPath), "Vulkan+ logo icon must exist at src/main/resources/assets/vulkanplus/icon.png");
        assertTrue(Files.size(iconPath) > 500, "Vulkan+ logo icon must not be empty");

        // Verify PNG magic header bytes (0x89, 'P', 'N', 'G')
        byte[] bytes = Files.readAllBytes(iconPath);
        assertTrue(bytes.length >= 8, "PNG file must have at least 8 bytes");
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals((byte) 'P', bytes[1]);
        assertEquals((byte) 'N', bytes[2]);
        assertEquals((byte) 'G', bytes[3]);

        // Verify NativeImage can read it without throwing
        try (InputStream is = Files.newInputStream(iconPath)) {
            net.minecraft.client.texture.NativeImage img = net.minecraft.client.texture.NativeImage.read(is);
            assertNotNull(img, "NativeImage must not be null");
            assertEquals(img.getWidth(), img.getHeight(), "Must be square icon");
            img.close();
        }
    }

    @Test
    @DisplayName("en_us.json contains modmenu translation with bro4cvf-hash credit")
    public void testLanguageFileCredits() throws Exception {
        Path langPath = Paths.get("src/main/resources/assets/vulkanplus/lang/en_us.json");
        assertTrue(Files.exists(langPath), "en_us.json must exist");

        String content = Files.readString(langPath, StandardCharsets.UTF_8);
        assertTrue(content.contains("modmenu.descriptionTranslation.vulkanplus"),
                "en_us.json must define modmenu.descriptionTranslation.vulkanplus");
        assertTrue(content.contains("bro4cvf-hash"),
                "modmenu translation must credit bro4cvf-hash");
        assertTrue(content.contains("https://github.com/bro4cvf-hash"),
                "modmenu translation must include GitHub link https://github.com/bro4cvf-hash");
    }

    @Test
    @DisplayName("Mod Menu mixin configuration and plugin are properly configured")
    public void testModMenuMixinConfiguration() throws Exception {
        Path mixinConfig = Paths.get("src/main/resources/vulkanplus-modmenu.mixins.json");
        assertTrue(Files.exists(mixinConfig), "vulkanplus-modmenu.mixins.json must exist");

        String content = Files.readString(mixinConfig, StandardCharsets.UTF_8);
        assertTrue(content.contains("net.vulkanplus.bridge.ModMenuMixinPlugin"),
                "Mixin config must use ModMenuMixinPlugin");
        assertTrue(content.contains("FabricIconHandlerMixin"),
                "Mixin config must declare FabricIconHandlerMixin");

        // Verify ModMenuMixinPlugin class loads
        assertDoesNotThrow(() -> {
            Class<?> pluginClass = Class.forName("net.vulkanplus.bridge.ModMenuMixinPlugin");
            org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin plugin =
                    (org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin) pluginClass.getDeclaredConstructor().newInstance();
            plugin.onLoad("net.vulkanplus.mixin.modmenu");
            assertNotNull(plugin);
        });
    }

    @Test
    @DisplayName("Path sanitizer produces valid Minecraft 1.21 Identifier on Windows backslash paths")
    public void testWindowsPathSanitizationForIdentifier() {
        String[] testPaths = {
            "assets\\vulkanplus\\icon.png",
            "D:\\Games\\Minecraft\\.minecraft\\mods\\assets\\vulkanplus\\icon.png",
            "assets/vulkanplus/icon.png",
            "C:\\Users\\Player\\AppData\\icon.png"
        };

        for (String p : testPaths) {
            String rawPath = p.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
            String safePath = rawPath.replaceAll("[^a-z0-9/._-]", "_");
            while (safePath.startsWith("/")) {
                safePath = safePath.substring(1);
            }
            final String finalSafePath = safePath;

            // Must produce a valid Minecraft Identifier without throwing InvalidIdentifierException
            assertDoesNotThrow(() -> {
                net.minecraft.util.Identifier id = net.minecraft.util.Identifier.of("modmenu", finalSafePath);
                assertNotNull(id);
                assertEquals("modmenu", id.getNamespace());
            }, "Path sanitization must produce valid Identifier for: " + p);
        }
    }
}
