package net.tkg.RenaissanceLib.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.functional.LeftHandRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelHandAnchor;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelTransition;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Manages the host gun's left (support) hand for the underbarrel switch. Every frame it records the host
 * grip transform ({@link UnderbarrelHandAnchor}) so {@code UnderbarrelLeftHandRender} can interpolate the
 * arm between the host and underbarrel grips. While a switch is in progress <em>or</em> the underbarrel is
 * active (transition factor &gt; 0) it cancels the host's own hand, so only the underbarrel renderer's
 * (interpolated) arm draws; at rest in main-gun mode (factor 0) the host hand renders normally. The right
 * hand ({@code RightHandRender}) is untouched — it stays on the host grip. First person only.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = LeftHandRender.class, remap = false)
public abstract class LeftHandRenderMixin {

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;II)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void renaissance$hideHostLeftHandForUnderbarrel(
            PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType,
            int light, int overlay, CallbackInfo ci) {
        if (!transformType.firstPerson()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack gun = player.getMainHandItem();
        if (!Underbarrel.hasUnderbarrel(gun)) return;

        // Record the host support-hand grip transform (raw bone, before the hand's own ZP-180 flip) so the
        // underbarrel renderer can glide the arm from here to the underbarrel grip during a switch.
        UnderbarrelHandAnchor.setHostHand(new Matrix4f(poseStack.last().pose()));

        // Anytime the switch is under way or complete, the underbarrel renderer owns the (interpolated) hand.
        if (UnderbarrelTransition.factor() > 0f) {
            ci.cancel();
        }
    }
}
