package net.tkg.RenaissanceLib.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.client.refit.InteractiveRefitScreen;
import net.tkg.RenaissanceLib.client.refit.RefitOrbit;
import net.tkg.RenaissanceLib.client.refit.RefitProjection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
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
        if (!InteractiveRefitScreen.isFreeCamera()) return matrix;
        return RefitOrbit.apply(matrix, model, refitOpeningProgress);
    }

    /**
     * Keeps TaC:Z's refit camera on the overview framing ({@code refit_view}) while the interactive refit screen is
     * open: focusing a card frames its slot through {@link RefitOrbit#focus} instead — the same view, but reached by
     * rotating/zooming about the gun's centre, so orbiting while focused still turns the gun about its middle rather
     * than about TaC:Z's per-slot camera. (TaC:Z still records the focused slot type; only the framing is ours.)
     */
    @Redirect(
            method = "applyFirstPersonPositioningTransform",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/client/model/BedrockGunModel;getRefitAttachmentViewPath("
                            + "Lcom/tacz/guns/api/item/attachment/AttachmentType;)Ljava/util/List;"),
            remap = false)
    private static List<BedrockPart> renaissance$overviewFraming(BedrockGunModel model, AttachmentType type) {
        return model.getRefitAttachmentViewPath(
                InteractiveRefitScreen.isFreeCamera() ? AttachmentType.NONE : type);
    }

    /**
     * Once the gun is positioned, capture the gun→screen projection ({@link RefitProjection}) that the slot callouts
     * and the debug overlay place themselves with.
     */
    @Inject(method = "applyFirstPersonPositioningTransform", at = @At("TAIL"), remap = false)
    private static void renaissance$refitProjectionCapture(CallbackInfo ci,
                                                           @Local(argsOnly = true) PoseStack poseStack,
                                                           @Local(argsOnly = true) BedrockGunModel model) {
        if (!InteractiveRefitScreen.isOpenOrClosing()) return;
        RefitProjection.capture(poseStack, model);
    }
}
