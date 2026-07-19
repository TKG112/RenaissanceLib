package net.tkg.RenaissanceLib.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.client.ShaderManager;
import net.tkg.RenaissanceLib.client.ScopeShaderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BedrockAttachmentModel.class, remap = false)
public abstract class BedrockAttachmentModelMixin {

    private static final String RENDER_OCULAR_AND_DIVISION =
            "Lcom/tacz/guns/client/model/BedrockAttachmentModel;" +
                    "renderOcularAndDivision(" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;IIZ)V";

    @Inject(
            method = "renderScope(Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At(value = "INVOKE", target = RENDER_OCULAR_AND_DIVISION, shift = At.Shift.BEFORE),
            remap = false
    )
    private void renaissance$beforeOcularAndDivision_Scope(
            PoseStack matrixStack, ItemDisplayContext transformType,
            RenderType renderType, int light, int overlay, CallbackInfo ci) {
        if (!ShaderManager.isShaderActive()) return;
        ScopeShaderRenderer.compositeIntoLens(0);
    }

    @Inject(
            method = "renderBoth(Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At(value = "INVOKE", target = RENDER_OCULAR_AND_DIVISION, shift = At.Shift.BEFORE),
            remap = false
    )
    private void renaissance$beforeOcularAndDivision_Both(
            PoseStack matrixStack, ItemDisplayContext transformType,
            RenderType renderType, int light, int overlay, CallbackInfo ci) {
        if (!ShaderManager.isShaderActive()) return;
        ScopeShaderRenderer.compositeIntoLens(2);
    }

    @Inject(
            method = "renderOcularAndDivision(Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;IIZ)V",
            at = @At("TAIL"),
            remap = false
    )
    private void renaissance$afterOcularAndDivision(
            PoseStack matrixStack, ItemDisplayContext transformType,
            RenderType renderType, int light, int overlay, boolean isScope, CallbackInfo ci) {
        if (!ShaderManager.isShaderActive()) return;



        ScopeShaderRenderer.captureLensMaskIris();
    }

}
