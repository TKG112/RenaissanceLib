package net.tkg.RenaissanceLib.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.functional.AttachmentRender;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.client.resource.index.ClientSlotAdapterIndex;
import com.tacz.guns.util.SlotAdapterHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import javax.annotation.Nullable;

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

    /**
     * Flush the shared buffer now. The beta defers the scope ocular draws into the shared MultiBufferSource and
     * only flushes at end of frame; when we render a rail optic out-of-band that leaves its ocular flushing with
     * the wrong stencil state (the giant black square). Draining here forces those draws to happen in sequence
     * with the render we just did. No-op on stable (immediate pipeline).
     */
    public static void flushRenderBuffers() {
        net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    /**
     * The model TaC:Z actually draws for an attachment: a replace-mode variant's model when one applies (the beta
     * lets an attachment swap its model per variant), else the index model. Mirrors
     * {@code AttachmentRender.renderAttachment}.
     */
    @Nullable
    public static BedrockAttachmentModel renderedAttachmentModel(ItemStack attachment, ClientAttachmentIndex index) {
        ClientAttachmentVariantIndex variant = TimelessAPI.getClientAttachmentVariantIndex(attachment).orElse(null);
        if (variant != null && variant.isReplaceMode()
                && variant.getAttachmentModel() != null && variant.getModelTexture() != null) {
            return variant.getAttachmentModel();
        }
        return index.getAttachmentModel();
    }

    /**
     * The extra translation TaC:Z applies to an attachment after its mount node, in model units (pixels, the
     * authored JSON sense): the gun's slot adapter's {@code mount_offset}, plus an overlay-mode variant's. Mirrors
     * {@code AttachmentRender.renderAttachment}, which draws with {@code translate(x/16, -y/16, z/16)} of each.
     * {@code null} when neither applies.
     */
    @Nullable
    public static Vector3f attachmentMountOffset(ItemStack gun, ItemStack attachment, AttachmentType type,
                                                 ResourceLocation attachmentId) {
        Vector3f sum = null;
        ResourceLocation adapterId = SlotAdapterHelper.getEffectiveSlotAdapter(gun, type, attachmentId);
        if (adapterId != null) {
            Vector3f off = TimelessAPI.getClientSlotAdapterIndex(adapterId)
                    .map(ClientSlotAdapterIndex::getMountOffset).orElse(null);
            if (off != null) sum = new Vector3f(off);
        }
        ClientAttachmentVariantIndex variant = TimelessAPI.getClientAttachmentVariantIndex(attachment).orElse(null);
        if (variant != null && variant.isOverlayMode() && variant.getMountOffset() != null) {
            sum = sum == null ? new Vector3f(variant.getMountOffset()) : sum.add(variant.getMountOffset());
        }
        return sum;
    }
}
