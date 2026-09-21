package net.tkg.RenaissanceLib.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.functional.LeftHandRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.compat.TaczDescriptors;
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

    // The support-hand functional renderer. TaC:Z calls the IFunctionalRenderer 6-arg (BufferSource) overload,
    // which LeftHandRender overrides, so the beta must hook that exact overload (a name-only selector didn't
    // take). Per-variant descriptor: 5-arg on stable, 6-arg on beta. See TaczDescriptors.HAND_RENDER.
    @Inject(
            method = TaczDescriptors.HAND_RENDER,
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void renaissance$hideHostLeftHandForUnderbarrel(
            CallbackInfo ci,
            @Local(argsOnly = true, ordinal = 0) PoseStack poseStack,
            @Local(argsOnly = true, ordinal = 0) ItemDisplayContext transformType) {
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
