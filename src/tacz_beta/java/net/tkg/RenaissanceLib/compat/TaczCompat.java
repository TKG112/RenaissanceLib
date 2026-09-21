package net.tkg.RenaissanceLib.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.functional.AttachmentRender;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Thin shims over the handful of TaC:Z APIs that differ between the stable release and the unreleased beta, so
 * the shared code in {@code src/main} can call one stable signature. <b>This is the BETA variant</b> (compiled
 * for the dev's unreleased jar in {@code libs/}); the stable copy lives in {@code src/tacz_stable/java}. Build
 * with {@code -Ptacz=beta} (see {@code build.gradle}).
 */
@OnlyIn(Dist.CLIENT)
public final class TaczCompat {

    private TaczCompat() {}

    /**
     * Whether our immediate-mode GL stencil in-lens clipping (canted-rail passenger clip + gun-in-lens mask) can
     * be used. The beta rewrote rendering to a buffered/batched {@link net.minecraft.client.renderer.MultiBufferSource}
     * pipeline, so draws no longer flush in the immediate order our stencil ops assume — leaving the stencil
     * state wrong (the giant black ocular square). Disabled on beta: scopes/rail sights render plainly (no
     * multi-optic in-lens clipping) but correctly. STABLE keeps it on.
     */
    public static final boolean CLIP_IN_LENS = false;

    /**
     * Render an attachment at the current pose. The beta's {@code renderAttachment} gained an
     * {@link AttachmentType} parameter (the slot being rendered); we derive it from the attachment itself.
     */
    public static void renderAttachment(ItemStack attachment, ItemStack gun, PoseStack poseStack,
                                        ItemDisplayContext ctx, int light, int overlay) {
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachment);
        AttachmentType type = iAttachment == null ? AttachmentType.NONE : iAttachment.getType(attachment);
        AttachmentRender.renderAttachment(attachment, gun, type, poseStack, ctx, light, overlay);
    }
}
