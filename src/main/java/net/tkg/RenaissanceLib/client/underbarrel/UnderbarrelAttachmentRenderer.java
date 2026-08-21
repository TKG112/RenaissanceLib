package net.tkg.RenaissanceLib.client.underbarrel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.functional.AttachmentRender;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAttachments;
import net.tkg.RenaissanceLib.client.IRailGunItemAccessor;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the attachment installed in one of the underbarrel's own slots, at that slot's {@code <type>_pos} node
 * on the underbarrel model — the underbarrel counterpart of TaC:Z's per-slot {@code AttachmentRender}, which
 * only runs for real gun items. Registered on each present {@code <type>_pos} node by
 * {@link UnderbarrelRenderRegistrar} (same {@code <type>_pos} bone naming TaC:Z uses on a gun).
 *
 * <p>The host gun (to read the underbarrel's stored attachments) and the underbarrel item (the render host)
 * come from the model's live accessor, so it draws the right attachment at any moment. Nothing is drawn when
 * the slot is empty.
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelAttachmentRenderer implements IFunctionalRenderer {
    private final BedrockAttachmentModel model;
    private final AttachmentType type;

    public UnderbarrelAttachmentRenderer(BedrockAttachmentModel model, AttachmentType type) {
        this.model = model;
        this.type = type;
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType,
                       int light, int overlay) {
        IRailGunItemAccessor accessor = (IRailGunItemAccessor) model;
        ItemStack gun = accessor.renaissance$getCurrentGunItem();
        if (gun == null || gun.isEmpty()) return;
        ItemStack installed = UnderbarrelAttachments.getInstalled(gun, type);
        if (installed.isEmpty()) return;
        ItemStack underbarrel = accessor.renaissance$getAttachmentItem();
        if (underbarrel == null || underbarrel.isEmpty()) return;

        // Arms/attachments share the gun vertex buffer, so defer to the end of the model render (as TaC:Z does).
        Matrix3f normal = new Matrix3f(poseStack.last().normal());
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        model.delegateRender((poseStack1, vertexBuffer1, transformType1, light1, overlay1) -> {
            PoseStack local = new PoseStack();
            local.last().normal().mul(normal);
            local.last().pose().mul(pose);
            AttachmentRender.renderAttachment(installed, underbarrel, local, transformType, light, overlay);
        });
    }
}
