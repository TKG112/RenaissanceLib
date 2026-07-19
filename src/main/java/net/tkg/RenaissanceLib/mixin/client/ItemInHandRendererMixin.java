package net.tkg.RenaissanceLib.mixin.client;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.client.ScopeShaderRenderer;
import net.tkg.RenaissanceLib.client.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void renaissance$snapshotPreViewmodelDepth(float partialTicks,
                                                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                                                       net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource,
                                                       net.minecraft.client.player.LocalPlayer player,
                                                       int combinedLight,
                                                       CallbackInfo ci) {
        if (!ShaderManager.isShaderActive()) return;
        ScopeShaderRenderer.snapshotPreViewmodelDepth();
    }
}
