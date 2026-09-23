package net.vulkanplus.mixin.culling;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import net.vulkanplus.render.RenderOptimizer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconBlockEntityRenderer.class)
public class BeaconBlockEntityRendererMixin {

    @Inject(
            method = "renderBeam(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/util/Identifier;FFIIIFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void cullBeaconBeamSegment(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            Identifier texture,
            float beamScale,
            float beamRotationDegrees,
            int yOffset,
            int height,
            int color,
            float innerRadius,
            float outerRadius,
            CallbackInfo ci
    ) {
        VulkanPlusConfig config = ConfigManager.getConfig();
        if (config == null || !config.enabled || !config.enableBeaconBeamCulling) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return;
        Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
        if (camPos == null) return;

        Matrix4f posMatrix = matrices.peek().getPositionMatrix();
        float relX = posMatrix.m30();
        float relY = posMatrix.m31();
        float relZ = posMatrix.m32();

        float minRelX = relX - 0.5f;
        float maxRelX = relX + 0.5f;
        float minRelY = relY + yOffset;
        float maxRelY = relY + yOffset + height;
        float minRelZ = relZ - 0.5f;
        float maxRelZ = relZ + 0.5f;

        if (!RenderOptimizer.getFrustumCuller().isAabbVisible(
                camPos.x + minRelX, camPos.y + minRelY, camPos.z + minRelZ,
                camPos.x + maxRelX, camPos.y + maxRelY, camPos.z + maxRelZ)) {
            ci.cancel();
        }
    }
}
