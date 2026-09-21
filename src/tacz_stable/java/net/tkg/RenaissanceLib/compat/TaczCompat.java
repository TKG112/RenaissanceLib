package net.tkg.RenaissanceLib.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.functional.AttachmentRender;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Thin shims over the handful of TaC:Z APIs that differ between the stable release and the unreleased beta, so
 * the shared code in {@code src/main} can call one stable signature. <b>This is the STABLE variant</b> (compiled
 * for the current CurseForge release); the beta copy lives in {@code src/tacz_beta/java}. Build the beta with
 * {@code -Ptacz=beta} (see {@code build.gradle}).
 */
@OnlyIn(Dist.CLIENT)
public final class TaczCompat {

    private TaczCompat() {}

    /**
     * Whether our immediate-mode GL stencil in-lens clipping (canted-rail passenger clip + gun-in-lens mask) can
     * be used. On the stable release the render pipeline is immediate-mode, so this is on; the beta's buffered
     * pipeline breaks the stencil ordering, so its copy turns this off.
     */
    public static final boolean CLIP_IN_LENS = true;

    /** Render an attachment at the current pose. Stable {@code renderAttachment} takes no attachment type. */
    public static void renderAttachment(ItemStack attachment, ItemStack gun, PoseStack poseStack,
                                        ItemDisplayContext ctx, int light, int overlay) {
        AttachmentRender.renderAttachment(attachment, gun, poseStack, ctx, light, overlay);
    }

    /** No-op on the stable release (immediate render pipeline; nothing to flush). See the beta variant. */
    public static void flushRenderBuffers() {
    }
}
