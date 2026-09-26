package net.vulkanplus.mixin.exordium;

import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.gui.render.state.TextGuiElementRenderState;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.vulkanplus.exordium.ExordiumManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the exact chronological sequence of GUI element and root layer additions
 * into {@link GuiRenderState} while {@link ExordiumManager} is capturing a HUD frame,
 * ensuring replayed frames build the exact same layer tree and z-ordering.
 */
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin {

    @Inject(method = "addSimpleElement", at = @At("HEAD"))
    private void vulkanplus$onAddSimpleElement(SimpleGuiElementRenderState element, CallbackInfo ci) {
        ExordiumManager manager = ExordiumManager.getInstance();
        if (manager.isCapturingState((GuiRenderState) (Object) this)) {
            manager.recordSimpleElement(element);
        }
    }

    @Inject(method = "addItem", at = @At("HEAD"))
    private void vulkanplus$onAddItem(ItemGuiElementRenderState element, CallbackInfo ci) {
        ExordiumManager manager = ExordiumManager.getInstance();
        if (manager.isCapturingState((GuiRenderState) (Object) this)) {
            manager.recordItemElement(element);
        }
    }

    @Inject(method = "addText", at = @At("HEAD"))
    private void vulkanplus$onAddText(TextGuiElementRenderState element, CallbackInfo ci) {
        ExordiumManager manager = ExordiumManager.getInstance();
        if (manager.isCapturingState((GuiRenderState) (Object) this)) {
            manager.recordTextElement(element);
        }
    }

    @Inject(method = "addSpecialElement", at = @At("HEAD"))
    private void vulkanplus$onAddSpecialElement(SpecialGuiElementRenderState element, CallbackInfo ci) {
        ExordiumManager manager = ExordiumManager.getInstance();
        if (manager.isCapturingState((GuiRenderState) (Object) this)) {
            manager.recordSpecialElement(element);
        }
    }

    @Inject(method = "createNewRootLayer", at = @At("HEAD"))
    private void vulkanplus$onCreateNewRootLayer(CallbackInfo ci) {
        ExordiumManager manager = ExordiumManager.getInstance();
        if (manager.isCapturingState((GuiRenderState) (Object) this)) {
            manager.recordCreateNewRootLayer();
        }
    }
}
