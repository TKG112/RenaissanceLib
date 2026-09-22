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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * The first-person AIM anchor override — <b>STABLE variant</b>. The TaC:Z release computes the aim anchor via
 * the 1-arg {@code getPositioningNodeInverse(List)} (the first such call, ordinal 0). The beta moved it to a new
 * 3-arg overload, so its copy lives in {@code src/tacz_beta}. The aim decision (underbarrel iron sights / rail
 * sights vs. the host aim) is shared in {@link RailAim#resolveAim}.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = FirstPersonRenderGunEvent.class, remap = false)
public abstract class FirstPersonRenderGunEventAimMixin {

    @Shadow
    private static Matrix4f getPositioningNodeInverse(List<BedrockPart> nodePath) {
        throw new AssertionError("shadow");
    }

    @Redirect(
            method = "applyFirstPersonPositioningTransform",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/client/event/FirstPersonRenderGunEvent;"
                            + "getPositioningNodeInverse(Ljava/util/List;)Lorg/joml/Matrix4f;",
                    ordinal = 0),
            remap = false)
    private static Matrix4f renaissance$easeAimMatrix(
            List<BedrockPart> nodePath,
            PoseStack poseStack, BedrockGunModel model, ItemStack stack,
            float aimingProgress, float refitScreenOpeningProgress) {
        return RailAim.resolveAim(model, stack, nodePath, getPositioningNodeInverse(nodePath));
    }
}
