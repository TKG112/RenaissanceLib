package net.tkg.RenaissanceLib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.BedrockGunModel;
import net.tkg.RenaissanceLib.compat.TaczCompat;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import net.tkg.RenaissanceLib.attachment.ScopeRails;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Orders the draw of all mounted rail sights in one pass so a passenger optic can be clipped out of the
 * <em>active</em> optic's ocular.
 *
 * <p>Each {@link RailSightRenderer} (one per rail node) is reached at a fixed point in the scope model's
 * traversal, and TaC:Z drains functional delegates in enqueue order — which is node order, not "active
 * first". But to clip a passenger against the active optic's lens, the active optic must render
 * <em>first</em> (its {@code renderScope}/{@code renderSight} writes the ocular mask into the shared
 * stencil buffer), and only then can the passengers draw clipped against that mask.
 *
 * <p>So instead of each node enqueuing its own draw, every node {@link #record records} itself here
 * during traversal, and the first record enqueues a single coordinator delegate. When that delegate
 * drains (after the whole traversal, so every node has been recorded regardless of node order), it:
 * <ol>
 *   <li>if the player is aiming through a masking optic, renders that optic normally (writing its mask),
 *       then renders every other mounted sight as a {@link RailPassengerClip clipped passenger};</li>
 *   <li>otherwise renders every mounted sight normally (vanilla behaviour).</li>
 * </ol>
 *
 * <p>Client render thread only; plain statics suffice.
 */
@OnlyIn(Dist.CLIENT)
public final class RailRenderCoordinator {
    private RailRenderCoordinator() {}

    /** Aiming progress past which the passenger clip runs (matches the gun-body clip). */
    private static final float CLIP_AIM = 0.05f;

    private static final List<Entry> pending = new ArrayList<>();
    private static ItemStack gun = ItemStack.EMPTY;

    private static final class Entry {
        final int railIndex;
        final ItemStack sight;
        final Matrix4f pose;
        final Matrix3f normal;
        final ItemDisplayContext transformType;
        final int light;
        final int overlay;

        Entry(int railIndex, ItemStack sight, Matrix4f pose, Matrix3f normal,
              ItemDisplayContext transformType, int light, int overlay) {
            this.railIndex = railIndex;
            this.sight = sight;
            this.pose = pose;
            this.normal = normal;
            this.transformType = transformType;
            this.light = light;
            this.overlay = overlay;
        }
    }

    /**
     * Records one mounted rail sight to be drawn this frame, capturing its node pose. The first record of
     * the batch enqueues the single coordinator delegate on the scope model.
     */
    public static void record(ItemStack gun, int railIndex, ItemStack sight, PoseStack poseStack,
                              ItemDisplayContext transformType, int light, int overlay,
                              BedrockAttachmentModel scopeModel) {
        RailRenderCoordinator.gun = gun;
        pending.add(new Entry(railIndex, sight,
                new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal()),
                transformType, light, overlay));
        if (pending.size() == 1) {
            scopeModel.delegateRender((ps, vb, tt, l, o) -> drain());
        }
    }

    private static void drain() {
        try {
            ActiveOptic active = ActiveOptic.resolve(gun);
            boolean masking = aimingProgress() > CLIP_AIM
                    && active != null && RailAim.masksOptic(active.index());

            if (!masking) {
                for (Entry e : pending) renderEntry(e, null);
                return;
            }

            ClientAttachmentIndex activeIndex = active.index();
            RailPassengerClip.Mask mask =
                    new RailPassengerClip.Mask(RailAim.maskFunc(activeIndex), RailAim.maskRef(activeIndex));

            if (active.isScope()) {
                // The active optic is the scope-slot attachment itself: it has already rendered natively
                // (before this delegate drains) and its ocular mask is in the buffer. Clip every mounted
                // sight against it.
                for (Entry e : pending) renderEntry(e, mask);
            } else if (active.path.depth() == 1) {
                // The active optic is a direct child of the scope: render it first so its ocular mask is
                // written, then clip every other direct mount against it.
                int activeSlot = active.path.last();
                Entry activeEntry = null;
                for (Entry e : pending) {
                    if (e.railIndex == activeSlot) {
                        activeEntry = e;
                        break;
                    }
                }
                if (activeEntry != null) renderEntry(activeEntry, null);
                for (Entry e : pending) {
                    if (e != activeEntry) renderEntry(e, mask);
                }
            } else {
                // The active optic is nested deeper than the coordinator's (top-level) view; correct
                // clipping against a deep optic's ocular is a later phase. Render the direct mounts
                // normally so nothing is wrongly clipped meanwhile.
                for (Entry e : pending) renderEntry(e, null);
            }
        } finally {
            pending.clear();
            gun = ItemStack.EMPTY;
        }
    }

    private static void renderEntry(Entry e, @Nullable RailPassengerClip.Mask clip) {
        // Hoist non-optic mounts (lasers) onto the gun model's own delegate so they render in the gun's
        // pass — where the scope's ocular stencil test is active — and their beam gets clipped out of the
        // lens like a native-slot laser. Nested inside the scope render (the default) that clip isn't set
        // up yet, so the beam bleeds into the ocular. Capture the gun into the lambda: the gun delegate
        // drains after this coordinator pass has cleared the shared `gun` field. Optics stay inline.
        if (!ScopeRails.isOptic(e.sight)) {
            BedrockGunModel gunModel = RailGunModelContext.current();
            if (gunModel != null) {
                final ItemStack gunItem = gun;
                gunModel.delegateRender((ps, vb, tt, l, o) -> renderAttachmentAt(e, gunItem, null));
                return;
            }
        }
        renderAttachmentAt(e, gun, clip);
    }

    private static void renderAttachmentAt(Entry e, ItemStack gunItem, @Nullable RailPassengerClip.Mask clip) {
        PoseStack local = new PoseStack();
        local.last().normal().mul(e.normal);
        local.last().pose().mul(e.pose);
        if (clip != null) RailPassengerClip.begin(clip);
        try {
            TaczCompat.renderAttachment(e.sight, gunItem, local, e.transformType, e.light, e.overlay);
            // Drain the shared buffer now so an out-of-band optic's ocular flushes in sequence with this render
            // (beta's deferred pipeline otherwise flushes it at end-of-frame with the wrong stencil). No-op on
            // stable.
            TaczCompat.flushRenderBuffers();
        } finally {
            if (clip != null) RailPassengerClip.end();
        }
    }

    private static float aimingProgress() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0f;
        try {
            return IClientPlayerGunOperator.fromLocalPlayer(player)
                    .getClientAimingProgress(Minecraft.getInstance().getFrameTime());
        } catch (Throwable t) {
            return 0f;
        }
    }
}
