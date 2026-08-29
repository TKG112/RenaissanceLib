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
import org.joml.Matrix4f;

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
 *   <li>the aim <b>positioning matrix</b> — the camera aligns to {@code gun grip node + underbarrel iron_view}
 *       (the same cross-model node concatenation the rail sights use, see {@code RailAim});</li>
 *   <li>the world <b>aim zoom</b> — the underbarrel display's {@code iron_zoom} instead of the host gun's.</li>
 * </ul>
 * Model FOV ({@code zoom_model_fov}) is intentionally left to the host — iron sights don't need it.
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelAim {

    /** The gun bone the grip attachment mounts on, and the underbarrel model's iron-sight aim bone. */
    private static final String GRIP_NODE = "grip";
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
     * iron sights, or the grip / iron_view nodes can't be resolved) — in which case the caller keeps the host
     * aim.
     */
    @Nullable
    public static Matrix4f aimMatrix(BedrockGunModel gunModel, ItemStack gunItem) {
        if (gunModel == null || !ActiveWeapon.isUnderbarrelActive(gunItem)) return null;
        BedrockAttachmentModel ubModel = underbarrelModel(gunItem);
        if (ubModel == null) return null;
        BedrockPart gripNode = gunModel.getNode(GRIP_NODE);
        BedrockPart ironNode = ubModel.getNode(IRON_VIEW_NODE);
        if (gripNode == null || ironNode == null) return null;

        // gun's grip mount chain, then the underbarrel model's iron_view chain — composed the same way
        // RailAim composes a rail sight's path (getPositioningNodeInverse walks the flat list regardless of
        // which model each bone came from).
        List<BedrockPart> path = new ArrayList<>();
        appendNodePath(gripNode, path);
        appendNodePath(ironNode, path);
        return positioningNodeInverse(path);
    }

    /**
     * The underbarrel's aim zoom (world magnification) from its display's {@code iron_zoom}, or {@code <= 0} if
     * it declares none — the caller then keeps the host gun's zoom.
     */
    public static float ironZoom(ItemStack gunItem) {
        GunDisplay display = UnderbarrelClient.getUnderbarrelDisplay(Underbarrel.getInstalledUnderbarrel(gunItem));
        return display == null ? 0f : display.getIronZoom();
    }

    @Nullable
    private static BedrockAttachmentModel underbarrelModel(ItemStack gunItem) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return null;
        ResourceLocation gripId = iGun.getAttachmentId(gunItem, AttachmentType.GRIP);
        if (gripId == null) return null;
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(gripId).orElse(null);
        return index == null ? null : index.getAttachmentModel();
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

    /**
     * Reproduces TaC:Z's {@code FirstPersonRenderGunEvent.getPositioningNodeInverse} (private there): the
     * inverse of the accumulated node-path transform, used to align the camera to an aim node.
     */
    private static Matrix4f positioningNodeInverse(List<BedrockPart> nodePath) {
        Matrix4f matrix = new Matrix4f();
        for (int i = nodePath.size() - 1; i >= 0; i--) {
            BedrockPart part = nodePath.get(i);
            matrix.rotate(Axis.XN.rotation(part.xRot));
            matrix.rotate(Axis.YN.rotation(part.yRot));
            matrix.rotate(Axis.ZN.rotation(part.zRot));
            if (part.getParent() != null) {
                matrix.translate(-part.x / 16.0F, -part.y / 16.0F, -part.z / 16.0F);
            } else {
                matrix.translate(-part.x / 16.0F, (1.5F - part.y / 16.0F), -part.z / 16.0F);
            }
        }
        return matrix;
    }
}
