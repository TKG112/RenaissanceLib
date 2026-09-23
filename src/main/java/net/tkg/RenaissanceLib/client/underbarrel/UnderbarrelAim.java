package net.tkg.RenaissanceLib.client.underbarrel;

import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.compat.TaczCompat;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Optional aim-down-sights for the underbarrel weapon.
 *
 * <p>Iron sights are <b>opt-in</b>: an underbarrel gets its own ADS only if its model declares an
 * {@code iron_view} node (the same bone TaC:Z uses for a gun's iron sight). If it doesn't, aiming while the
 * underbarrel is active falls through to TaC:Z's normal (host-gun) aim, exactly as before.
 *
 * <p>When active it drives two things (wired from {@code FirstPersonRenderGunEventMixin} and
 * {@code CameraSetupEventMixin}):
 * <ul>
 *   <li>the aim <b>positioning matrix</b> — the camera aligns to {@code gun grip_pos + underbarrel iron_view}
 *       (the same cross-model node concatenation the rail sights use, see {@code RailAim});</li>
 *   <li>the world <b>aim zoom</b> — the underbarrel display's {@code iron_zoom} instead of the host gun's.</li>
 * </ul>
 * and the aimed <b>model FOV</b> — the underbarrel display's {@code zoom_model_fov}, not the host gun's (each host
 * gun has its own, which drew the same underbarrel sight bigger or smaller depending on the gun).
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelAim {

    /**
     * The gun bone TaC:Z mounts the grip attachment on ({@code <type>_pos}), and the underbarrel model's
     * iron-sight aim bone. (Not {@code grip}: on most guns that's the pistol-grip bone, or absent.)
     */
    private static final String GRIP_MOUNT_NODE = "grip_pos";
    private static final String IRON_VIEW_NODE = "iron_view";

    private UnderbarrelAim() {}

    /** True when the underbarrel is the active weapon and it declares its own iron sights (an {@code iron_view}). */
    public static boolean isIronAimActive(ItemStack gunItem) {
        if (!ActiveWeapon.isUnderbarrelActive(gunItem)) return false;
        BedrockAttachmentModel model = underbarrelModel(gunItem);
        return model != null && model.getNode(IRON_VIEW_NODE) != null;
    }

    /**
     * The aim-alignment matrix for the active underbarrel's iron sights, or {@code null} if unavailable (no
     * iron sights, or the grip_pos / iron_view nodes can't be resolved) — in which case the caller keeps the host
     * aim.
     *
     * <p>This is the inverse of the exact chain the underbarrel is drawn with: the gun's {@code grip_pos} bone
     * chain, TaC:Z's attachment {@code -1.5} origin shift, TaC:Z's per-gun mount offset (beta slot adapter /
     * variant, see {@link TaczCompat#attachmentMountOffset}), our own {@code mount_offset} nudge (translate +
     * rotation, applied in {@code BedrockAttachmentModelMixin}), then the drawn model's {@code iron_view} chain.
     * Leaving any of those out puts the camera in the wrong place on guns that use them.
     */
    @Nullable
    public static Matrix4f aimMatrix(BedrockGunModel gunModel, ItemStack gunItem) {
        if (gunModel == null || !ActiveWeapon.isUnderbarrelActive(gunItem)) return null;
        BedrockAttachmentModel ubModel = underbarrelModel(gunItem);
        if (ubModel == null) return null;
        BedrockPart mountNode = gunModel.getNode(GRIP_MOUNT_NODE);
        BedrockPart ironNode = ubModel.getNode(IRON_VIEW_NODE);
        if (mountNode == null || ironNode == null) return null;

        List<BedrockPart> gunPath = new ArrayList<>();
        appendNodePath(mountNode, gunPath);
        List<BedrockPart> ubPath = new ArrayList<>();
        appendNodePath(ironNode, ubPath);

        ItemStack underbarrel = Underbarrel.getInstalledUnderbarrel(gunItem);
        IGun iGun = IGun.getIGunOrNull(gunItem);
        Vector3f taczOffset = iGun == null ? null : TaczCompat.attachmentMountOffset(
                gunItem, underbarrel, AttachmentType.GRIP, iGun.getAttachmentId(gunItem, AttachmentType.GRIP));
        float[] ourOffset = UnderbarrelClient.getMountOffset(underbarrel);

        // Built back-to-front (inverse order), matching TaC:Z's getPositioningNodeInverse: undo the underbarrel
        // bones, then our nudge, then TaC:Z's mount offset and the attachment origin shift, then the gun bones.
        Matrix4f matrix = new Matrix4f();
        for (int i = ubPath.size() - 1; i >= 0; i--) {
            BedrockPart part = ubPath.get(i);
            invertPart(matrix, part);
            // The attachment root's origin shift is re-added below (after our nudge), not folded in here.
            matrix.translate(-part.x / 16.0F, -part.y / 16.0F, -part.z / 16.0F);
        }
        if (ourOffset != null) {
            // Forward: translate(pos/16), then rotate X, Y, Z (degrees). Inverse: Z, Y, X negated, then -pos.
            matrix.rotate(Axis.ZP.rotationDegrees(-ourOffset[5]));
            matrix.rotate(Axis.YP.rotationDegrees(-ourOffset[4]));
            matrix.rotate(Axis.XP.rotationDegrees(-ourOffset[3]));
            matrix.translate(-ourOffset[0] / 16.0F, -ourOffset[1] / 16.0F, -ourOffset[2] / 16.0F);
        }
        if (taczOffset != null) {
            // TaC:Z draws it as translate(x/16, -y/16, z/16).
            matrix.translate(-taczOffset.x() / 16.0F, taczOffset.y() / 16.0F, -taczOffset.z() / 16.0F);
        }
        matrix.translate(0.0F, 1.5F, 0.0F); // undo AttachmentRender's translate(0, -1.5, 0)
        for (int i = gunPath.size() - 1; i >= 0; i--) {
            BedrockPart part = gunPath.get(i);
            invertPart(matrix, part);
            if (part.getParent() != null) {
                matrix.translate(-part.x / 16.0F, -part.y / 16.0F, -part.z / 16.0F);
            } else {
                matrix.translate(-part.x / 16.0F, (1.5F - part.y / 16.0F), -part.z / 16.0F);
            }
        }
        return matrix;
    }

    /** The rotation half of TaC:Z's per-bone inverse (the translation depends on where the bone sits). */
    private static void invertPart(Matrix4f matrix, BedrockPart part) {
        matrix.rotate(Axis.XN.rotation(part.xRot));
        matrix.rotate(Axis.YN.rotation(part.yRot));
        matrix.rotate(Axis.ZN.rotation(part.zRot));
    }

    /**
     * The underbarrel's aim zoom (world magnification) from its display's {@code iron_zoom}, or {@code <= 0} if
     * it declares none — the caller then keeps the host gun's zoom.
     */
    public static float ironZoom(ItemStack gunItem) {
        GunDisplay display = UnderbarrelClient.getUnderbarrelDisplay(Underbarrel.getInstalledUnderbarrel(gunItem));
        return display == null ? 0f : display.getIronZoom();
    }

    /** The underbarrel model TaC:Z actually draws (a beta variant can replace the index model). */
    /**
     * The underbarrel's aimed model FOV from its display's {@code zoom_model_fov} (TaC:Z defaults it to 70), or
     * {@code <= 0} if there's no underbarrel display — the caller then keeps the host gun's value.
     */
    public static float modelFov(ItemStack gunItem) {
        GunDisplay display = UnderbarrelClient.getUnderbarrelDisplay(Underbarrel.getInstalledUnderbarrel(gunItem));
        return display == null ? 0f : display.getZoomModelFov();
    }

    @Nullable
    private static BedrockAttachmentModel underbarrelModel(ItemStack gunItem) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return null;
        ResourceLocation gripId = iGun.getAttachmentId(gunItem, AttachmentType.GRIP);
        if (gripId == null) return null;
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(gripId).orElse(null);
        return index == null ? null
                : TaczCompat.renderedAttachmentModel(Underbarrel.getInstalledUnderbarrel(gunItem), index);
    }

    /** Appends the root-to-node bone chain (root first, node last), matching TaC:Z's path ordering. */
    private static void appendNodePath(BedrockPart node, List<BedrockPart> out) {
        Deque<BedrockPart> stack = new ArrayDeque<>();
        for (BedrockPart p = node; p != null; p = p.getParent()) {
            stack.push(p);
        }
        while (!stack.isEmpty()) {
            out.add(stack.pop());
        }
    }
}
