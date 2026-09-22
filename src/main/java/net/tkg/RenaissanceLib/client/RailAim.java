package net.tkg.RenaissanceLib.client;

import com.mojang.math.Axis;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.util.math.MathUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.MountPath;
import net.tkg.RenaissanceLib.attachment.RailStorage;
import net.tkg.RenaissanceLib.attachment.RailsModifier;
import net.tkg.RenaissanceLib.attachment.ScopeRails;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelAim;
import org.lwjgl.opengl.GL11;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Builds the camera aim-alignment path for looking through a mounted rail sight.
 *
 * <p>TaC:Z aligns the aim by inverting the transform of a bone path: {@code gun scope-mount node +
 * scope model's scope_view node}. For a rail sight the path nests one level deeper —
 * {@code gun scope-mount + scope model's canted_N node + the sight model's scope_view node} — so the
 * camera ends up looking straight down the canted optic. {@code getPositioningNodeInverse} walks any
 * flat bone list regardless of which model each bone came from, so we just concatenate the pieces.
 *
 * <p>This produces only the <em>suffix</em> after the gun's scope-mount node (which TaC:Z already
 * prepends): {@code [canted_N ... ] + [ sight scope_view ... ]}.
 */
@OnlyIn(Dist.CLIENT)
public final class RailAim {
    private RailAim() {}

    /**
     * The aim-path suffix for the active rail sight, or {@code null} if the active optic is the scope
     * (let TaC:Z handle it) or anything can't be resolved.
     */
    @Nullable
    public static List<BedrockPart> buildViewSuffix(ItemStack gunItem, ActiveOptic optic) {
        if (optic == null || optic.isScope()) return null;

        BedrockAttachmentModel hostModel = scopeModel(gunItem);
        ItemStack hostItem = RailStorage.getScopeItem(gunItem);
        RailsModifier.Spec hostSpec = ScopeRails.getRailsSpec(gunItem);
        if (hostModel == null || hostItem.isEmpty()) return null;

        MountPath mount = optic.path;
        List<BedrockPart> path = new ArrayList<>();

        // Walk the mount path: at each hop append the slot node's bone chain (in the current host model),
        // then descend into the mounted optic's model. getPositioningNodeInverse multiplies the flat bone
        // list regardless of which model each bone came from, so the nested transforms compose.
        for (int d = 0; d < mount.depth(); d++) {
            int slot = mount.get(d);
            if (hostSpec == null || slot < 0 || slot >= hostSpec.getSlots().size()) return null;
            BedrockPart node = hostModel.getNode(hostSpec.getSlots().get(slot).getNode());
            if (node == null) return null;
            appendNodePath(node, path);

            ItemStack mounted = RailStorage.getRailSightFromAttachment(hostItem, slot);
            if (mounted.isEmpty()) return null;
            ClientAttachmentIndex mountedIndex = TimelessAPI.getClientAttachmentIndex(attachmentId(mounted)).orElse(null);
            if (mountedIndex == null) return null;
            BedrockAttachmentModel mountedModel = mountedIndex.getAttachmentModel();
            if (mountedModel == null) return null;

            if (d == mount.depth() - 1) {
                // Deepest hop: append the active optic's own scope_view for the current cycle position.
                int[] views = mountedIndex.getViews();
                if (views != null && views.length > 0) {
                    int viewIndex = views[Math.floorMod(optic.localIndex, views.length)] - 1;
                    List<BedrockPart> viewPath = mountedModel.getScopeViewPath(viewIndex);
                    if (viewPath != null) path.addAll(viewPath);
                }
            } else {
                hostModel = mountedModel;
                hostItem = mounted;
                hostSpec = ScopeRails.getRailsSpecForAttachment(mounted);
            }
        }
        return path;
    }

    @Nullable
    private static ResourceLocation attachmentId(ItemStack attachment) {
        com.tacz.guns.api.item.IAttachment iAttachment =
                com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(attachment);
        return iAttachment == null ? null : iAttachment.getAttachmentId(attachment);
    }

    /**
     * The first-person aim matrix accounting for our two aim overrides — underbarrel iron sights and mounted
     * rail sights — or {@code original} (TaC:Z's own value) when neither applies. Shared by the per-variant
     * aim-anchor mixins (the aim {@code getPositioningNodeInverse} overload differs between the TaC:Z release
     * and the beta), so the decision lives in one place.
     */
    public static Matrix4f resolveAim(com.tacz.guns.client.model.BedrockGunModel model, ItemStack stack,
                                      List<BedrockPart> nodePath, Matrix4f original) {
        if (UnderbarrelAim.isIronAimActive(stack)) {
            Matrix4f ubAim = UnderbarrelAim.aimMatrix(model, stack);
            if (ubAim != null) return ubAim;
        }
        if (ActiveOptic.hasMountedRailSight(stack)) {
            return easedAimMatrix(nodePath, stack);
        }
        return original;
    }

    /** Persisted eased aim matrix while a rail sight is in play; reset when the cycle isn't combined. */
    private static Matrix4f easedMatrix = null;
    private static long lastEaseNanos = 0L;

    /** Time constant (seconds) for easing the aim between combined-cycle positions. */
    private static final float AIM_EASE_TAU = 0.09f;

    /**
     * The aim-alignment matrix for the given node path, eased between combined-cycle positions when a
     * rail sight is mounted. For guns with no rail sight it returns TaC:Z's exact value unchanged (and
     * clears the easing state), so their aim keeps using TaC:Z's own smoother.
     */
    public static Matrix4f easedAimMatrix(List<BedrockPart> nodePath, ItemStack gunItem) {
        Matrix4f target = positioningNodeInverse(nodePath);
        if (!ActiveOptic.hasMountedRailSight(gunItem)) {
            easedMatrix = null;
            lastEaseNanos = 0L;
            return target;
        }
        long now = System.nanoTime();
        float dt = lastEaseNanos == 0L ? 0f : (now - lastEaseNanos) / 1_000_000_000f;
        lastEaseNanos = now;
        if (easedMatrix == null) {
            easedMatrix = new Matrix4f(target);
            return new Matrix4f(target);
        }
        float alpha = dt <= 0f ? 1f : 1f - (float) Math.exp(-Math.min(dt, 0.1f) / AIM_EASE_TAU);
        // Eases easedMatrix toward target in place (translation lerp + rotation slerp).
        MathUtil.applyMatrixLerp(easedMatrix, target, easedMatrix, alpha);
        return new Matrix4f(easedMatrix);
    }

    /**
     * Reproduces TaC:Z's {@code FirstPersonRenderGunEvent.getPositioningNodeInverse}: the inverse of the
     * accumulated node-path transform, used to align the camera to an aim node. Replicated because that
     * method is private; kept byte-for-byte equivalent.
     */
    private static Matrix4f positioningNodeInverse(@Nullable List<BedrockPart> nodePath) {
        Matrix4f matrix = new Matrix4f();
        if (nodePath != null) {
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
        }
        return matrix;
    }

    /**
     * Whether the active optic removes the gun and other mounted sights from its ocular in the rail
     * system — only a true <b>scope</b> ({@code isScope}: a long scope or a scope/sight combination)
     * does. A mounted <em>sight</em> (red dot, holo, even a magnified one) is a secondary optic you look
     * <em>at</em>, not a tube that replaces the view, so it never clips: aiming through a canted sight
     * leaves the scope and gun barrel rendering normally. Only the scope's ocular clips.
     */
    public static boolean masksOptic(@Nullable ClientAttachmentIndex index) {
        return index != null && index.isScope();
    }

    /** The stencil compare function to clip the gun out of an optic's lens (matches TaC:Z per kind). */
    public static int maskFunc(ClientAttachmentIndex index) {
        return (index.isScope() && index.isSight()) ? GL11.GL_GREATER : GL11.GL_EQUAL;
    }

    /** The stencil reference value paired with {@link #maskFunc}. */
    public static int maskRef(ClientAttachmentIndex index) {
        return (index.isScope() && index.isSight()) ? 127 : 0;
    }

    /** The installed scope's attachment model (which owns the canted rail nodes), or {@code null}. */
    @Nullable
    private static BedrockAttachmentModel scopeModel(ItemStack gunItem) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return null;
        ResourceLocation scopeId = iGun.getAttachmentId(gunItem, AttachmentType.SCOPE);
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            scopeId = iGun.getBuiltInAttachmentId(gunItem, AttachmentType.SCOPE);
        }
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) return null;
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(scopeId).orElse(null);
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
}
