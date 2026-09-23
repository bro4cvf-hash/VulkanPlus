package net.vulkanplus.mixin.vulkan;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.vulkanmod.config.gui.OptionBlock;
import net.vulkanmod.config.option.Option;
import net.vulkanmod.config.option.Options;
import net.vulkanmod.config.option.PerformanceImpact;
import net.vulkanmod.config.option.SwitchOption;
import net.vulkanplus.config.ConfigManager;
import net.vulkanplus.config.VulkanPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Injects the Opaque Leaves (Fast Leaves) setting into VulkanMod's Graphics options block
 * so it appears directly inside the Vulkan Video Settings GUI.
 */
@Mixin(Options.class)
public class OptionsMixin {

    @Inject(method = "getGraphicsOpts", at = @At("RETURN"), cancellable = true, remap = false)
    private static void injectOpaqueLeavesOption(CallbackInfoReturnable<OptionBlock[]> cir) {
        OptionBlock[] blocks = cir.getReturnValue();
        if (blocks == null || blocks.length < 2) return;

        SwitchOption opaqueLeavesOption = (SwitchOption) new SwitchOption(
                Text.translatable("vulkanplus.options.opaqueLeaves"),
                val -> {
                    VulkanPlusConfig cfg = ConfigManager.getConfig();
                    if (cfg.opaqueLeaves != val) {
                        cfg.opaqueLeaves = val;
                        ConfigManager.save();
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null) {
                            if (client.options != null) {
                                client.options.getCutoutLeaves().setValue(!val);
                                client.options.write();
                            }
                            net.minecraft.client.render.BlockRenderLayers.setCutoutLeaves(!val);
                            net.minecraft.block.LeavesBlock.setCutoutLeaves(!val);
                            client.reloadResourcesConcurrently();
                            if (client.worldRenderer != null) {
                                client.worldRenderer.reload();
                            }
                        }
                    }
                },
                () -> ConfigManager.getConfig().opaqueLeaves
        ).setTooltip(val -> Text.translatable("vulkanplus.options.opaqueLeaves.tooltip"))
         .setImpact(PerformanceImpact.HIGH);

        opaqueLeavesOption.setOnChange(() -> {
            boolean val = opaqueLeavesOption.getNewValue();
            VulkanPlusConfig cfg = ConfigManager.getConfig();
            if (cfg.opaqueLeaves != val) {
                cfg.opaqueLeaves = val;
                ConfigManager.save();
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) {
                    if (client.options != null) {
                        client.options.getCutoutLeaves().setValue(!val);
                        client.options.write();
                    }
                    net.minecraft.client.render.BlockRenderLayers.setCutoutLeaves(!val);
                    net.minecraft.block.LeavesBlock.setCutoutLeaves(!val);
                    client.reloadResourcesConcurrently();
                    if (client.worldRenderer != null) {
                        client.worldRenderer.reload();
                    }
                }
            }
        });

        OptionBlock targetBlock = blocks[1];
        Option<?>[] originalOptions = targetBlock.options();
        List<Option<?>> newOptionsList = new ArrayList<>(Arrays.asList(originalOptions));
        newOptionsList.add(opaqueLeavesOption);

        blocks[1] = new OptionBlock(targetBlock.title(), newOptionsList.toArray(new Option<?>[0]));
        cir.setReturnValue(blocks);
    }
}
