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
        if (matrices == null || matrices.peek() == null) return;
        if (RenderOptimizer.getFrustumCuller() == null) return;

        Matrix4f posMatrix = matrices.peek().getPositionMatrix();
        if (posMatrix == null) return;
        float relX = posMatrix.m30();
        float relY = posMatrix.m31();
        float relZ = posMatrix.m32();

        float centerX = relX + 0.5f;
        float centerZ = relZ + 0.5f;
        float radius = Math.max(0.5f, outerRadius);

        float minRelX = centerX - radius;
        float maxRelX = centerX + radius;
        float minRelY = relY + yOffset;
        float maxRelY = relY + yOffset + height;
        float minRelZ = centerZ - radius;
        float maxRelZ = centerZ + radius;

        if (!RenderOptimizer.getFrustumCuller().isRelativeAabbVisible(
                minRelX, minRelY, minRelZ,
                maxRelX, maxRelY, maxRelZ)) {
            ci.cancel();
        }
    }
}
