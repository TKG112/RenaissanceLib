package net.tkg.RenaissanceLib.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.client.refit.InteractiveRefitScreen;
import net.tkg.RenaissanceLib.client.refit.RefitDebug;
import net.tkg.RenaissanceLib.client.refit.RefitOrbit;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the interactive refit screen's turntable ({@link RefitOrbit}) to the first-person gun. TaC:Z positions the
 * gun with a single {@code poseStack.mulPoseMatrix(matrix)} at the end of {@code applyFirstPersonPositioningTransform}
 * (after blending idle / aim / refit views — and after our own aim and rail redirects); we rotate/zoom that final
 * matrix, weighted by the refit opening progress TaC:Z passed in, so the orbit fades in and out with the screen.
 * Same call on the release and the beta.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = FirstPersonRenderGunEvent.class, remap = false)
public abstract class RefitOrbitMixin {

    @ModifyArg(
            method = "applyFirstPersonPositioningTransform",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPoseMatrix(Lorg/joml/Matrix4f;)V",
                    remap = true),
            index = 0)
    private static Matrix4f renaissance$refitOrbit(Matrix4f matrix,
                                                   @Local(argsOnly = true) BedrockGunModel model,
                                                   @Local(argsOnly = true, ordinal = 1) float refitOpeningProgress) {
        if (!InteractiveRefitScreen.isOpenOrClosing()) return matrix;
        return RefitOrbit.apply(matrix, model, refitOpeningProgress);
    }

    /** Debug overlay (P in the refit screen): capture the projected pivot + bounding box once the gun is positioned. */
    @Inject(method = "applyFirstPersonPositioningTransform", at = @At("TAIL"), remap = false)
    private static void renaissance$refitDebugCapture(CallbackInfo ci,
                                                      @Local(argsOnly = true) PoseStack poseStack,
                                                      @Local(argsOnly = true) BedrockGunModel model) {
        if (!RefitDebug.isEnabled() || !InteractiveRefitScreen.isOpenOrClosing()) return;
        RefitDebug.capture(poseStack, model);
    }
}
