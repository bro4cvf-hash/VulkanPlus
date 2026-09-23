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
                        } else if ("targets".equals(name) && val instanceof List<?> list) {
                            for (Object item : list) {
                                if (item instanceof String s) {
                                    targets.add(s);
                                }
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
