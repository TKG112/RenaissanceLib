package net.tkg.RenaissanceLib.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.item.AttachmentItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.client.RailStandaloneContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes the attachment stack being rendered as a standalone item so {@link RailStandaloneContext}
 * can hand it to the rail renderer — letting a held/framed/dropped rail-bearing attachment show the
 * optics mounted on it. TaC:Z renders the model with no gun, so without this the mounted optics have no
 * item to be read from and don't appear.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(AttachmentItemRenderer.class)
public abstract class AttachmentItemRendererMixin {

    // renderByItem is a GeckoLib override; the TaC:Z beta reshaped it enough that the mixin AP can't pin its
    // descriptor. require = 0 so this cosmetic hook (rail optics on a standalone attachment item) degrades
    // gracefully instead of crashing if it can't be applied on a given TaC:Z version.
    @Inject(method = "renderByItem", at = @At("HEAD"), require = 0)
    private void renaissance$beginRailStandalone(ItemStack stack, ItemDisplayContext transformType,
                                                 PoseStack poseStack, MultiBufferSource buffer,
                                                 int light, int overlay, CallbackInfo ci) {
        RailStandaloneContext.begin(stack);
    }

    @Inject(method = "renderByItem", at = @At("RETURN"), require = 0)
    private void renaissance$endRailStandalone(ItemStack stack, ItemDisplayContext transformType,
                                               PoseStack poseStack, MultiBufferSource buffer,
                                               int light, int overlay, CallbackInfo ci) {
        RailStandaloneContext.end();
    }
}
