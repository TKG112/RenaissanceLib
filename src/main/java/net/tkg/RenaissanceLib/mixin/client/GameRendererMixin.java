package net.tkg.RenaissanceLib.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.tkg.RenaissanceLib.client.ShaderManager;
import net.tkg.RenaissanceLib.client.ScopeShaderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 500)
public abstract class GameRendererMixin {

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void renaissance$prepareScope(PoseStack pPoseStack,
                                          Camera pActiveRenderInfo,
                                          float pPartialTicks,
                                          CallbackInfo ci) {
        if (IrisApi.getInstance().isShaderPackInUse()) return;
        if (!ShaderManager.isShaderActive()) return;
        ScopeShaderRenderer.prepareForFrame(ShaderManager.getActiveShader(), pPartialTicks);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void renaissance$irisEndOfFrame(float pPartialTick,
                                            long pFinishNanoTime,
                                            PoseStack pPoseStack,
                                            CallbackInfo ci) {
        if (!IrisApi.getInstance().isShaderPackInUse()) return;
        if (!ShaderManager.isShaderActive()) return;
        ScopeShaderRenderer.endOfFrameIrisComposite(ShaderManager.getActiveShader(), pPartialTick);
    }
}
