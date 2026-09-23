package net.vulkanplus.mixin.culling;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.render.entity.state.ItemFrameEntityRenderState;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.math.Vec3d;
import net.vulkanplus.culling.ItemFrameCuller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFrameEntityRenderer.class)
public abstract class ItemFrameEntityRendererMixin<T extends ItemFrameEntity> {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/decoration/ItemFrameEntity;Lnet/minecraft/client/render/entity/state/ItemFrameEntityRenderState;F)V", at = @At("RETURN"))
    private void onUpdateRenderState(T entity, ItemFrameEntityRenderState state, float tickDelta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
            Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
            if (camPos != null) {
                // If the entire frame is culled (backface, block occlusion, or frame distance),
                // make invisible and clear item/map state for 0 draw calls.
                if (ItemFrameCuller.shouldCullFrame(entity, camPos.x, camPos.y, camPos.z)) {
                    state.invisible = true;
                    state.itemRenderState.clear();
                    state.mapId = null;
                    return;
                }

                // If only the contained 3D item stack exceeds distance, clear item/map model state.
                if (ItemFrameCuller.shouldCullContainedItem(entity, camPos.x, camPos.y, camPos.z)) {
                    state.itemRenderState.clear();
                    state.mapId = null;
                }
            }
        }
    }
}
