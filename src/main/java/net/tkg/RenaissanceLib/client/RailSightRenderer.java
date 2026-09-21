package net.tkg.RenaissanceLib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import net.tkg.RenaissanceLib.compat.TaczCompat;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.tkg.RenaissanceLib.attachment.RailStorage;
import net.tkg.RenaissanceLib.attachment.ScopeRails;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the optic mounted in one rail slot, at that slot's node on the host model.
 *
 * <p>Registered on every rail-capable attachment model's rail node (e.g. {@code canted_1}) by
 * {@link RailRenderRegistrar}. The <em>host</em> — the attachment whose model this node belongs to — is
 * resolved per render from the model's live {@code attachmentItem} (or {@link RailStandaloneContext} when
 * the host is being drawn as a standalone item, where TaC:Z passes no item). Because a nested optic is
 * rendered via {@code renderAttachment}, which sets {@code attachmentItem} to that optic, the same
 * renderer reads the correct level at any depth — this is what makes the rail tree recursive.
 *
 * <p>Routing:
 * <ul>
 *   <li><b>Top-level on a gun</b> (host is the gun's installed scope) → {@link RailRenderCoordinator},
 *       which orders the direct mounts and clips passengers out of the active optic's ocular.</li>
 *   <li><b>Nested, or standalone item</b> → drawn directly at the node (no aim/clip logic; recursive
 *       clipping is a later phase).</li>
 * </ul>
 *
 * <p>Any attachment that declares a {@code rails} block is a valid host ({@link ScopeRails#isRailHost}) — a
 * scope carrying optics, or a grip/handguard carrying a laser, etc. Only the gun's SCOPE optic host routes
 * through {@link RailRenderCoordinator} (view ordering + ocular clipping); other hosts draw directly. A
 * mounted laser's beam draws for free: {@code AttachmentRender.renderAttachment} runs the laser's
 * {@code BedrockAttachmentModel.render}, which itself emits the beam from the laser model's own beam bones.
 */
public class RailSightRenderer implements IFunctionalRenderer {
    private final int railIndex;
    private final BedrockAttachmentModel scopeModel;

    public RailSightRenderer(int railIndex, BedrockAttachmentModel scopeModel) {
        this.railIndex = railIndex;
        this.scopeModel = scopeModel;
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay) {
        IRailGunItemAccessor accessor = (IRailGunItemAccessor) scopeModel;

        // The host is the item whose model this node belongs to: the model's live attachmentItem, or the
        // standalone item being rendered when TaC:Z supplies none.
        ItemStack host = accessor.renaissance$getAttachmentItem();
        if (host == null || host.isEmpty()) host = RailStandaloneContext.current();
        if (host.isEmpty()) return;
        if (!ScopeRails.isRailHost(host)) return; // guardrail: only SCOPE-type hosts carry optic rails

        ItemStack mounted = RailStorage.getRailSightFromAttachment(host, railIndex);
        if (mounted.isEmpty()) return;

        ItemStack gun = accessor.renaissance$getCurrentGunItem();
        AttachmentType hostType = attachmentType(host);
        boolean topLevelOnGun = gun != null && !gun.isEmpty() && hostType != null
                && ItemStack.isSameItemSameTags(host, RailStorage.getHostItem(gun, hostType));

        // Only the gun's SCOPE optic host routes through the coordinator (view ordering + ocular clipping).
        // Non-optic hosts (a grip/handguard laser rail) and nested/standalone hosts draw directly.
        if (topLevelOnGun && hostType == AttachmentType.SCOPE) {
            RailRenderCoordinator.record(gun, railIndex, mounted, poseStack, transformType, light, overlay, scopeModel);
        } else {
            renderDirect(mounted, host, poseStack, transformType, light, overlay);
        }
    }

    /** The mounted host's own native attachment type (SCOPE/GRIP/LASER/…), or {@code null} if unresolved. */
    private static AttachmentType attachmentType(ItemStack host) {
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(host);
        return iAttachment == null ? null : iAttachment.getType(host);
    }

    private void renderDirect(ItemStack mounted, ItemStack host, PoseStack poseStack,
                              ItemDisplayContext transformType, int light, int overlay) {
        Matrix3f normal = new Matrix3f(poseStack.last().normal());
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        scopeModel.delegateRender((poseStack1, vertexBuffer1, transformType1, light1, overlay1) -> {
            PoseStack local = new PoseStack();
            local.last().normal().mul(normal);
            local.last().pose().mul(pose);
            TaczCompat.renderAttachment(mounted, host, local, transformType, light, overlay);
        });
    }
}
