package net.vulkanplus.test.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.Type;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MixinTargetAdversarialTest {

    @Test
    @DisplayName("Verify all declared mixin classes and targets exist")
    void testAllMixinClassesAndTargetsExist() throws Exception {
        List<String> mixinClasses = loadMixinClassesFromConfig("/vulkanplus.mixins.json");
        assertFalse(mixinClasses.isEmpty(), "vulkanplus.mixins.json must declare mixin classes");

        for (String mixinClassName : mixinClasses) {
            String resourcePath = "/" + mixinClassName.replace('.', '/') + ".class";
            try (InputStream classStream = getClass().getResourceAsStream(resourcePath)) {
                assertNotNull(classStream, "Mixin classfile must exist: " + resourcePath);
                ClassReader cr = new ClassReader(classStream);
                ClassNode cn = new ClassNode();
                cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

                AnnotationNode mixinAnno = null;
                if (cn.invisibleAnnotations != null) {
                    for (AnnotationNode an : cn.invisibleAnnotations) {
                        if ("Lorg/spongepowered/asm/mixin/Mixin;".equals(an.desc)) {
                            mixinAnno = an;
                            break;
                        }
                    }
                }
                assertNotNull(mixinAnno, "Mixin class must have @Mixin annotation: " + mixinClassName);

                List<String> targets = new ArrayList<>();
                if (mixinAnno.values != null) {
                    for (int i = 0; i < mixinAnno.values.size(); i += 2) {
                        String name = (String) mixinAnno.values.get(i);
                        Object val = mixinAnno.values.get(i + 1);
                        if ("value".equals(name) && val instanceof List<?> list) {
                            for (Object item : list) {
                                if (item instanceof Type t) {
                                    targets.add(t.getClassName());
                                }
                            }
                        } else if ("targets".equals(name)) {
                            if (val instanceof List<?> list) {
                                for (Object item : list) {
                                    if (item instanceof String s) {
                                        targets.add(s);
                                    }
                                }
                            } else if (val instanceof String s) {
                                targets.add(s);
                            }
                        }
                    }
                }

                assertFalse(targets.isEmpty(), "Mixin must declare at least one target: " + mixinClassName);
                for (String target : targets) {
                    assertDoesNotThrow(() -> Class.forName(target, false, getClass().getClassLoader()),
                            "Target class must exist on classpath: " + target + " for mixin " + mixinClassName);
                }
            }
        }
    }

    @Test
    @DisplayName("BeaconBlockMixin targets AbstractBlock which declares isSideInvisible")
    void testBeaconBlockMixinMethodDeclaration() throws Exception {
        String resourcePath = "/net/vulkanplus/mixin/culling/BeaconBlockMixin.class";
        List<String> targets = new ArrayList<>();
        try (InputStream classStream = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(classStream, "Mixin classfile must exist: " + resourcePath);
            ClassReader cr = new ClassReader(classStream);
            ClassNode cn = new ClassNode();
            cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            if (cn.invisibleAnnotations != null) {
                for (AnnotationNode an : cn.invisibleAnnotations) {
                    if ("Lorg/spongepowered/asm/mixin/Mixin;".equals(an.desc)) {
                        if (an.values != null) {
                            for (int i = 0; i < an.values.size(); i += 2) {
                                String name = (String) an.values.get(i);
                                Object val = an.values.get(i + 1);
                                if ("value".equals(name) && val instanceof List<?> list) {
                                    for (Object item : list) {
                                        if (item instanceof Type t) {
                                            targets.add(t.getClassName());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        assertFalse(targets.isEmpty(), "BeaconBlockMixin must declare target class in @Mixin");
        assertTrue(targets.contains("net.minecraft.block.AbstractBlock"),
                "BeaconBlockMixin must target AbstractBlock because BeaconBlock does not directly declare isSideInvisible");

        Class<?> abstractBlockClass = Class.forName("net.minecraft.block.AbstractBlock");
        assertNotNull(abstractBlockClass);

        boolean declaredInAbstractBlock = false;
        for (Method m : abstractBlockClass.getDeclaredMethods()) {
            if (m.getName().equals("isSideInvisible")) {
                declaredInAbstractBlock = true;
                break;
            }
        }

        System.out.println("AbstractBlock declares isSideInvisible: " + declaredInAbstractBlock);
        assertTrue(declaredInAbstractBlock,
                "AbstractBlock must declare isSideInvisible for BeaconBlockMixin to inject successfully");
    }

    @Test
    @DisplayName("BeaconBlockEntityRendererMixin vs renderBeam signature")
    void testBeaconBlockEntityRendererMixinSignature() throws Exception {
        Class<?> rendererClass = Class.forName("net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer");
        assertNotNull(rendererClass);

        List<Method> renderBeamMethods = new ArrayList<>();
        for (Method m : rendererClass.getDeclaredMethods()) {
            if (m.getName().equals("renderBeam")) {
                renderBeamMethods.add(m);
            }
        }

        assertFalse(renderBeamMethods.isEmpty(), "BeaconBlockEntityRenderer must declare renderBeam");

        Class<?> mixinClass = Class.forName("net.vulkanplus.mixin.culling.BeaconBlockEntityRendererMixin");
        Method cullMethod = null;
        for (Method m : mixinClass.getDeclaredMethods()) {
            if (m.getName().equals("cullBeaconBeamSegment")) {
                cullMethod = m;
                break;
            }
        }
        assertNotNull(cullMethod, "cullBeaconBeamSegment method must exist in mixin");

        Parameter[] params = cullMethod.getParameters();
        assertEquals(11, params.length,
                "cullBeaconBeamSegment must have 11 parameters (10 renderBeam arguments + CallbackInfo)");
        Parameter queueParam = params[1];
        System.out.println("BeaconBlockEntityRendererMixin param 1 type: " + queueParam.getType().getName());
        assertEquals("net.minecraft.client.render.command.OrderedRenderCommandQueue", queueParam.getType().getName(),
                "Param 1 must be OrderedRenderCommandQueue instead of Object");
        assertEquals("net.minecraft.util.Identifier", params[2].getType().getName(),
                "Param 2 must be Identifier");
    }

    @Test
    @DisplayName("ItemFrameEntityRenderState declares mapId and ItemFrameEntityRendererMixin method exists")
    void testItemFrameEntityRendererMixinTargetAndFields() throws Exception {
        Class<?> renderStateClass = Class.forName("net.minecraft.client.render.entity.state.ItemFrameEntityRenderState");
        assertNotNull(renderStateClass);
        java.lang.reflect.Field mapIdField = renderStateClass.getField("mapId");
        assertNotNull(mapIdField, "ItemFrameEntityRenderState must declare mapId field for framed map culling");
        assertEquals("net.minecraft.component.type.MapIdComponent", mapIdField.getType().getName());

        Class<?> mixinClass = Class.forName("net.vulkanplus.mixin.culling.ItemFrameEntityRendererMixin");
        assertNotNull(mixinClass);
        boolean foundMethod = false;
        for (Method m : mixinClass.getDeclaredMethods()) {
            if (m.getName().equals("onUpdateRenderState")) {
                foundMethod = true;
                break;
            }
        }
        assertTrue(foundMethod, "ItemFrameEntityRendererMixin must declare onUpdateRenderState method");
    }

    @Test
    @DisplayName("Verify exact VulkanMod bytecode descriptors for all 6 Phase 2 Mixin targets")
    void testVulkanModMixinMethodDescriptors() throws Exception {
        // 1. Exact bytecode descriptor verification via ASM ClassReader on vulkanmod-0.6.8+1.21.11.jar
        assertBytecodeMethodDescriptor("net.vulkanmod.vulkan.memory.MemoryTypes", "createMemoryTypes", "()V");
        assertBytecodeMethodDescriptor("net.vulkanmod.vulkan.Renderer", "bindGraphicsPipeline", "(Lnet/vulkanmod/vulkan/shader/GraphicsPipeline;)V");
        assertBytecodeMethodDescriptor("net.vulkanmod.render.chunk.build.task.TaskDispatcher", "updateSections", "()Z");
        assertBytecodeMethodDescriptor("net.vulkanmod.render.chunk.buffer.AreaBuffer", "reallocate", "(I)Lnet/vulkanmod/render/chunk/buffer/AreaBuffer$Segment;");
        assertBytecodeMethodDescriptor("net.vulkanmod.vulkan.framebuffer.SwapChain", "getPresentMode", "(Ljava/nio/IntBuffer;)I");
        assertBytecodeMethodDescriptor("net.vulkanmod.vulkan.shader.Pipeline", "createPipelineCache", "()J");
        assertBytecodeMethodDescriptor("net.vulkanmod.vulkan.shader.Pipeline", "destroyPipelineCache", "()V");

        // 2. Reflective verification of declared methods
        Class<?> memoryTypesClass = Class.forName("net.vulkanmod.vulkan.memory.MemoryTypes", false, getClass().getClassLoader());
        assertNotNull(memoryTypesClass.getDeclaredMethod("createMemoryTypes"));

        Class<?> graphicsPipelineClass = Class.forName("net.vulkanmod.vulkan.shader.GraphicsPipeline", false, getClass().getClassLoader());
        Class<?> rendererClass = Class.forName("net.vulkanmod.vulkan.Renderer", false, getClass().getClassLoader());
        assertNotNull(rendererClass.getDeclaredMethod("bindGraphicsPipeline", graphicsPipelineClass));

        Class<?> taskDispatcherClass = Class.forName("net.vulkanmod.render.chunk.build.task.TaskDispatcher", false, getClass().getClassLoader());
        assertNotNull(taskDispatcherClass.getDeclaredMethod("updateSections"));
        assertNotNull(taskDispatcherClass.getDeclaredMethod("createThreads", int.class));

        Class<?> areaBufferClass = Class.forName("net.vulkanmod.render.chunk.buffer.AreaBuffer", false, getClass().getClassLoader());
        assertNotNull(areaBufferClass.getDeclaredMethod("reallocate", int.class));

        Class<?> swapChainClass = Class.forName("net.vulkanmod.vulkan.framebuffer.SwapChain", false, getClass().getClassLoader());
        assertNotNull(swapChainClass.getDeclaredMethod("getPresentMode", java.nio.IntBuffer.class));

        Class<?> pipelineClass = Class.forName("net.vulkanmod.vulkan.shader.Pipeline", false, getClass().getClassLoader());
        assertNotNull(pipelineClass.getDeclaredMethod("createPipelineCache"));
        assertNotNull(pipelineClass.getDeclaredMethod("destroyPipelineCache"));
    }

    @Test
    @DisplayName("Verify MemoryTypesMixin, TaskDispatcherMixin, and RendererMixin structure and plugin registration")
    void testPhase2VulkanMixinsStructureAndRegistration() throws Exception {
        List<String> mixinClasses = loadMixinClassesFromConfig("/vulkanplus.mixins.json");
        assertTrue(mixinClasses.contains("net.vulkanplus.mixin.vulkan.MemoryTypesMixin"),
                "vulkanplus.mixins.json must register vulkan.MemoryTypesMixin");
        assertTrue(mixinClasses.contains("net.vulkanplus.mixin.vulkan.TaskDispatcherMixin"),
                "vulkanplus.mixins.json must register vulkan.TaskDispatcherMixin");
        assertTrue(mixinClasses.contains("net.vulkanplus.mixin.vulkan.RendererMixin"),
                "vulkanplus.mixins.json must register vulkan.RendererMixin");

        assertBytecodeMethodDescriptor("net.vulkanplus.mixin.vulkan.MemoryTypesMixin",
                "vulkanplus$enableReBarDeviceMappableMemory",
                "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        assertBytecodeMethodDescriptor("net.vulkanplus.mixin.vulkan.TaskDispatcherMixin",
                "vulkanplus$budgetedUpdateSections",
                "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V");
        assertBytecodeMethodDescriptor("net.vulkanplus.mixin.vulkan.RendererMixin",
                "vulkanplus$deduplicateBindGraphicsPipeline",
                "(Lnet/vulkanmod/vulkan/shader/GraphicsPipeline;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        assertBytecodeMethodDescriptor("net.vulkanplus.mixin.vulkan.RendererMixin",
                "vulkanplus$onBeginMainRenderPass",
                "(Lorg/lwjgl/system/MemoryStack;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
    }

    @Test
    @DisplayName("Verify Exordium MinecraftClientMixin onReloadResources descriptor matches 1.21.11 no-arg reloadResources")
    void testExordiumMinecraftClientMixinReloadResourcesDescriptor() throws Exception {
        assertBytecodeMethodDescriptor("net.vulkanplus.mixin.exordium.MinecraftClientMixin",
                "onReloadResources",
                "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V");
    }

    private void assertBytecodeMethodDescriptor(String className, String methodName, String expectedDescriptor) throws Exception {
        String resourcePath = "/" + className.replace('.', '/') + ".class";
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(is, "Class resource must exist on test classpath: " + resourcePath);
            ClassReader cr = new ClassReader(is);
            ClassNode cn = new ClassNode();
            cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            boolean matched = false;
            if (cn.methods != null) {
                for (org.objectweb.asm.tree.MethodNode mn : cn.methods) {
                    if (methodName.equals(mn.name) && expectedDescriptor.equals(mn.desc)) {
                        matched = true;
                        break;
                    }
                }
            }
            assertTrue(matched, () -> String.format(
                    "Expected bytecode method %s.%s%s not found in %s",
                    className, methodName, expectedDescriptor, resourcePath));
        }
    }

    private List<String> loadMixinClassesFromConfig(String resourcePath) throws Exception {
        List<String> list = new ArrayList<>();
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(is, "Resource not found: " + resourcePath);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
            String pkg = json.get("package").getAsString();

            if (json.has("mixins")) {
                JsonArray mixins = json.getAsJsonArray("mixins");
                for (JsonElement el : mixins) {
                    list.add(pkg + "." + el.getAsString());
                }
            }
            if (json.has("client")) {
                JsonArray clientMixins = json.getAsJsonArray("client");
                for (JsonElement el : clientMixins) {
                    list.add(pkg + "." + el.getAsString());
                }
            }
        }
        return list;
    }
}
