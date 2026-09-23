package net.tkg.RenaissanceLib.client.refit;

import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.MountPath;
import net.tkg.RenaissanceLib.attachment.RailStorage;
import net.tkg.RenaissanceLib.attachment.RailsModifier;
import net.tkg.RenaissanceLib.attachment.ScopeRails;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelClient;
import net.tkg.RenaissanceLib.compat.TaczCompat;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Where each refit card's part is, in pivot space ({@link RefitOrbit}), so {@link RefitProjection} can put the
 * card's dot on it. Native slots use the gun's {@code <type>_pos} bone (the magazine for the extended mag). Our
 * slots live inside installed attachments, reached through the same chain the attachment is drawn with: the gun's
 * mount bone → TaC:Z's attachment origin ({@code translate(0,-1.5,0)}, the beta's slot-adapter / variant offset, and
 * our {@code mount_offset} for an underbarrel) → the bone inside that attachment's model. A rail mount is the rail
 * slot's node on its host (one level deeper per nested mount, each hung the same way); an underbarrel slot is the
 * underbarrel model's {@code <type>_pos} bone; the conversion slot sits on the magazine.
 */
@OnlyIn(Dist.CLIENT)
public final class RefitAnchors {
    private static final int MAX_RAIL_DEPTH = 4;

    private RefitAnchors() {}

    /** The slot's anchor in pivot space, or {@code null} (the card goes to the dock). */
    @Nullable
    public static Vector3f anchor(RefitSlot slot, BedrockGunModel gunModel, ItemStack gun) {
        if (slot instanceof RefitSlot.Native n) return RefitOrbit.slotAnchor(gunModel, n.type());
        if (slot instanceof RefitSlot.Conversion) return RefitOrbit.slotAnchor(gunModel, AttachmentType.EXTENDED_MAG);
        if (slot instanceof RefitSlot.UnderbarrelSlot u) return underbarrelAnchor(gunModel, gun, u.type());
        if (slot instanceof RefitSlot.Rail r) return railAnchor(gunModel, gun, r.path());
        return null;
    }

    @Nullable
    private static Vector3f underbarrelAnchor(BedrockGunModel gunModel, ItemStack gun, AttachmentType type) {
        ItemStack underbarrel = Underbarrel.getInstalledUnderbarrel(gun);
        if (underbarrel.isEmpty()) return null;
        Matrix4f root = attachmentRoot(gunModel, gun, AttachmentType.GRIP, underbarrel, true);
        if (root == null) return null;
        Matrix4f node = RefitOrbit.findNode(modelOf(underbarrel), type.name().toLowerCase() + "_pos", root);
        return (node != null ? node : root).transformPosition(new Vector3f());
    }

    @Nullable
    private static Vector3f railAnchor(BedrockGunModel gunModel, ItemStack gun, MountPath path) {
        AttachmentType hostType = path.hostType();
        ItemStack host = RailStorage.getHostItem(gun, hostType);
        if (host.isEmpty() || path.depth() == 0 || path.depth() > MAX_RAIL_DEPTH) return null;
        Matrix4f frame = attachmentRoot(gunModel, gun, hostType, host, false);
        RailsModifier.Spec spec = ScopeRails.getRailsSpecForType(gun, hostType);
        BedrockAttachmentModel model = modelOf(host);
        MountPath walked = MountPath.root(hostType);
        for (int d = 0; d < path.depth(); d++) {
            int index = path.get(d);
            if (frame == null || spec == null || model == null || index < 0 || index >= spec.getSlots().size()) {
                return null;
            }
            Matrix4f node = RefitOrbit.findNode(model, spec.getSlots().get(index).getNode(), frame);
            if (node == null) return null;
            if (d == path.depth() - 1) return node.transformPosition(new Vector3f());
            // Descend: the optic mounted here is drawn at this node the same way (TaC:Z's -1.5 origin).
            walked = walked.child(index);
            ItemStack mounted = RailStorage.getMountedOnGun(gun, walked);
            frame = new Matrix4f(node).translate(0f, -1.5f, 0f);
            model = modelOf(mounted);
            spec = ScopeRails.getRailsSpecForAttachment(mounted);
        }
        return null;
    }

    /**
     * Pivot-space frame of an attachment model's root as TaC:Z draws it on the gun's {@code slotType} mount bone:
     * the bone, TaC:Z's {@code translate(0,-1.5,0)}, the beta's slot-adapter / variant offset
     * ({@code translate(x/16, -y/16, z/16)}), and — for an underbarrel — our {@code mount_offset} nudge.
     */
    @Nullable
    private static Matrix4f attachmentRoot(BedrockGunModel gunModel, ItemStack gun, AttachmentType slotType,
                                           ItemStack attachment, boolean underbarrel) {
        Matrix4f slot = RefitOrbit.findNode(gunModel, slotType.name().toLowerCase() + "_pos");
        if (slot == null) return null;
        Matrix4f frame = new Matrix4f(slot).translate(0f, -1.5f, 0f);
        IGun iGun = IGun.getIGunOrNull(gun);
        Vector3f offset = iGun == null ? null
                : TaczCompat.attachmentMountOffset(gun, attachment, slotType, iGun.getAttachmentId(gun, slotType));
        if (offset != null) frame.translate(offset.x() / 16f, -offset.y() / 16f, offset.z() / 16f);
        if (underbarrel) {
            float[] nudge = UnderbarrelClient.getMountOffset(attachment);
            if (nudge != null) {
                frame.translate(nudge[0] / 16f, nudge[1] / 16f, nudge[2] / 16f)
                        .rotate(Axis.XP.rotationDegrees(nudge[3]))
                        .rotate(Axis.YP.rotationDegrees(nudge[4]))
                        .rotate(Axis.ZP.rotationDegrees(nudge[5]));
            }
        }
        return frame;
    }

    /** The model TaC:Z draws for an attachment item (variant-aware), or {@code null}. */
    @Nullable
    private static BedrockAttachmentModel modelOf(ItemStack attachment) {
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachment);
        if (iAttachment == null) return null;
        ClientAttachmentIndex index =
                TimelessAPI.getClientAttachmentIndex(iAttachment.getAttachmentId(attachment)).orElse(null);
        return index == null ? null : TaczCompat.renderedAttachmentModel(attachment, index);
    }
}
