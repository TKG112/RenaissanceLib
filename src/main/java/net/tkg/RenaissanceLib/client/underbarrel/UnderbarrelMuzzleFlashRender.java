package net.tkg.RenaissanceLib.client.underbarrel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Draws the underbarrel's muzzle flash at its {@code muzzle_flash_underbarrel} bone, mirroring how TaC:Z
 * renders a gun's flash: a textured quad ({@link SlotModel}), full-bright, scaled by the display's flash
 * scale, with a random spin so repeated shots don't look identical. Registered on the bone by
 * {@link UnderbarrelRenderRegistrar}, so it runs at that bone's transform during the attachment render;
 * it only draws inside the brief post-shot window ({@link UnderbarrelEffects#isFlashing()}).
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelMuzzleFlashRender implements IFunctionalRenderer {
    private static final SlotModel QUAD = new SlotModel();

    private final MuzzleFlash muzzleFlash;

    public UnderbarrelMuzzleFlashRender(MuzzleFlash muzzleFlash) {
        this.muzzleFlash = muzzleFlash;
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexConsumer, ItemDisplayContext ctx,
                       int light, int overlay) {
        if (muzzleFlash == null || muzzleFlash.getTexture() == null) return;
        if (!UnderbarrelEffects.isFlashing()) return;

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(muzzleFlash.getTexture()));

        poseStack.pushPose();
        float scale = muzzleFlash.getScale();
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.ZP.rotationDegrees((float) (Math.random() * 360.0)));
        QUAD.renderToBuffer(poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();
    }
}
