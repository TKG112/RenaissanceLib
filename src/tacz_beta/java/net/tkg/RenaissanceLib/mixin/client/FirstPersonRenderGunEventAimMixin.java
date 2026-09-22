package net.tkg.RenaissanceLib.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.client.RailAim;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * The first-person AIM anchor override — <b>BETA variant</b>. The beta computes the aim anchor via a new 3-arg
 * {@code getPositioningNodeInverse(List, Vector3f, int)} (ordinal 0), applying a {@code Vector3f} offset before
 * the node path. We pass that original through for ordinary/host aim (preserving the offset) and override only
 * for underbarrel iron sights / rail sights via the shared {@link RailAim#resolveAim}. The release counterpart
 * (the 1-arg overload) lives in {@code src/tacz_stable}.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = FirstPersonRenderGunEvent.class, remap = false)
public abstract class FirstPersonRenderGunEventAimMixin {

    @Shadow
    private static Matrix4f getPositioningNodeInverse(List<BedrockPart> nodePath, Vector3f offset, int viewIndex) {
        throw new AssertionError("shadow");
    }

    @Redirect(
            method = "applyFirstPersonPositioningTransform",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/client/event/FirstPersonRenderGunEvent;"
                            + "getPositioningNodeInverse(Ljava/util/List;Lorg/joml/Vector3f;I)Lorg/joml/Matrix4f;",
                    ordinal = 0),
            remap = false)
    private static Matrix4f renaissance$easeAimMatrix(
            List<BedrockPart> nodePath, Vector3f offset, int viewIndex,
            PoseStack poseStack, BedrockGunModel model, ItemStack stack,
            float aimingProgress, float refitScreenOpeningProgress) {
        return RailAim.resolveAim(model, stack, nodePath, getPositioningNodeInverse(nodePath, offset, viewIndex));
    }
}
