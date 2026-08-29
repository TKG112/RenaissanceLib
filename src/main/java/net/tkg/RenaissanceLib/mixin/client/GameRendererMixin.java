package net.tkg.RenaissanceLib.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.tkg.RenaissanceLib.client.ScopeShaderRenderer;
import net.tkg.RenaissanceLib.client.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the frame render for the in-lens scope post-shader. Two compositing routes:
 * <ul>
 *   <li><b>Mid-frame</b> (no shaderpack): prepare + composite during the scope render, since the world is already
 *       on the main target by the hand pass.</li>
 *   <li><b>End-of-frame</b> (Iris/Oculus shaderpack): the world isn't on the main target mid-frame, so capture the
 *       lens mask mid-frame and composite at {@code renderLevel} TAIL. {@link ScopeShaderRenderer#useEndOfFramePath()}
 *       selects this route.</li>
 * </ul>
 */
@Mixin(value = GameRenderer.class, priority = 500)
public abstract class GameRendererMixin {

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void renaissance$prepareScope(PoseStack pPoseStack,
                                          Camera pActiveRenderInfo,
                                          float pPartialTicks,
                                          CallbackInfo ci) {
        if (ScopeShaderRenderer.useEndOfFramePath()) return; // Iris shaderpack: deferred to end-of-frame
        if (!ShaderManager.isShaderActive()) return;
        ScopeShaderRenderer.prepareForFrame(ShaderManager.getActiveShader(), pPartialTicks);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void renaissance$endOfFrameComposite(float pPartialTick,
                                                 long pFinishNanoTime,
                                                 PoseStack pPoseStack,
                                                 CallbackInfo ci) {
        if (!ScopeShaderRenderer.useEndOfFramePath()) return; // mid-frame route handled it
        if (!ShaderManager.isShaderActive()) return;
        ScopeShaderRenderer.endOfFrameIrisComposite(ShaderManager.getActiveShader(), pPartialTick);
    }
}
