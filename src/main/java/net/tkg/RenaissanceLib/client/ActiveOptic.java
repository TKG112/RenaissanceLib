package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.MountPath;
import net.tkg.RenaissanceLib.attachment.RailStorage;
import net.tkg.RenaissanceLib.attachment.RailsModifier;
import net.tkg.RenaissanceLib.attachment.ScopeRails;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves which optic the player is currently looking through, from the single shared zoom counter.
 *
 * <p>TaC:Z stores one {@code ZoomNumber} on the scope and cycles the scope's views with
 * {@code zoomNumber % scopeViews}. The rail system widens that into a <em>combined cycle</em> built by a
 * depth-first walk of the recursive mount tree: the scope's own views (if it takes part), then, for each
 * mounted optic in slot order, that optic's views followed by <em>its</em> mounts, recursively. This
 * class is the one place that maps {@code zoomNumber} onto that combined cycle so aim alignment, FOV, and
 * variable zoom all agree on the active optic.
 *
 * <p>Each stage is identified by a {@link MountPath}: {@link MountPath#ROOT} for the scope itself, and a
 * path of slot indices for each nested optic.
 */
@OnlyIn(Dist.CLIENT)
public final class ActiveOptic {
    /** Path to the active optic in the mount tree: {@link MountPath#ROOT} = the scope-slot attachment. */
    public final MountPath path;
    /** The active optic's attachment id. */
    public final ResourceLocation opticId;
    /** The item stack of the active optic (scope item, or the mounted optic item). */
    public final ItemStack opticItem;
    /** Position within the active optic's own view cycle (0-based). */
    public final int localIndex;
    /** Position within the whole combined cycle (0-based). */
    public final int globalPos;
    /** Length of the whole combined cycle. */
    public final int totalCycle;

    private ActiveOptic(MountPath path, ResourceLocation opticId, ItemStack opticItem,
                        int localIndex, int globalPos, int totalCycle) {
        this.path = path;
        this.opticId = opticId;
        this.opticItem = opticItem;
        this.localIndex = localIndex;
        this.globalPos = globalPos;
        this.totalCycle = totalCycle;
    }

    /** Whether the active optic is the scope-slot attachment itself (the root of the tree). */
    public boolean isScope() {
        return path.isRoot();
    }

    /** Whether the gun has at least one optic mounted in any rail slot (at any depth). */
    public static boolean hasMountedRailSight(ItemStack gunItem) {
        List<RailsModifier.RailSlot> slots = ScopeRails.getRailSlots(gunItem);
        for (int i = 0; i < slots.size(); i++) {
            if (!RailStorage.getRailSight(gunItem, i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** The client index of the active optic, for its views/zoom/fov/model. */
    @Nullable
    public ClientAttachmentIndex index() {
        return TimelessAPI.getClientAttachmentIndex(opticId).orElse(null);
    }

    /**
     * The active optic for the held gun, or {@code null} if there's no scope installed. When the gun has
     * no mounts this resolves to the scope, so behaviour matches vanilla TaC:Z.
     */
    @Nullable
    public static ActiveOptic resolve(ItemStack gunItem) {
        List<Stage> stages = buildStages(gunItem);
        if (stages == null || stages.isEmpty()) return null;

        int total = 0;
        for (Stage s : stages) total += s.length;
        if (total <= 0) return null;

        IGun iGun = IGun.getIGunOrNull(gunItem);
        CompoundTag scopeTag = iGun.getAttachmentTag(gunItem, AttachmentType.SCOPE);
        int zoomNumber = AttachmentItemDataAccessor.getZoomNumberFromTag(scopeTag);
        int pos = Math.floorMod(zoomNumber, total);

        int walk = pos;
        for (Stage s : stages) {
            if (walk < s.length) {
                return new ActiveOptic(s.path, s.opticId, s.opticItem, walk, pos, total);
            }
            walk -= s.length;
        }
        // Unreachable (pos < total), but fall back to the first stage.
        Stage first = stages.get(0);
        return new ActiveOptic(first.path, first.opticId, first.opticItem, 0, pos, total);
    }

    /**
     * The combined-cycle position of a given optic {@code path} at a given local view index — i.e. the
     * {@code zoomNumber} that makes {@link #resolve} select that optic at that view. Returns {@code -1} if
     * the path isn't an aim stage on this gun (e.g. a non-optic mount, or the scope when it isn't
     * {@code aim_self}). Client-only: cycle lengths come from the client attachment index.
     */
    public static int globalPosFor(ItemStack gunItem, MountPath path, int localIndex) {
        List<Stage> stages = buildStages(gunItem);
        if (stages == null) return -1;
        int base = 0;
        for (Stage s : stages) {
            if (s.path.equals(path)) {
                return base + Math.floorMod(localIndex, Math.max(1, s.length));
            }
            base += s.length;
        }
        return -1;
    }

    /** Build the combined aim cycle by a pre-order DFS of the mount tree, or {@code null} if no scope. */
    @Nullable
    private static List<Stage> buildStages(ItemStack gunItem) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return null;

        ResourceLocation scopeId = iGun.getAttachmentId(gunItem, AttachmentType.SCOPE);
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            scopeId = iGun.getBuiltInAttachmentId(gunItem, AttachmentType.SCOPE);
        }
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) return null;

        ClientAttachmentIndex scopeIndex = TimelessAPI.getClientAttachmentIndex(scopeId).orElse(null);
        if (scopeIndex == null) return null;

        ItemStack scopeItem = RailStorage.getScopeItem(gunItem);
        if (scopeItem.isEmpty()) scopeItem = iGun.getAttachment(gunItem, AttachmentType.SCOPE);

        List<Stage> stages = new ArrayList<>();
        // The scope's own view takes part only if it opts in (aim_self); a bare mount contributes none.
        RailsModifier.Spec scopeSpec = ScopeRails.getRailsSpec(gunItem);
        if (scopeSpec == null || scopeSpec.isAimSelf()) {
            stages.add(new Stage(MountPath.ROOT, scopeId, scopeItem, cycleLength(scopeIndex)));
        }
        addMountStages(scopeItem, MountPath.ROOT, scopeSpec, stages);
        return stages;
    }

    /**
     * Appends, in slot order, a stage for each optic mounted on {@code hostItem} followed (recursively) by
     * that optic's own mounts — the pre-order DFS that flattens the tree into the combined cycle.
     */
    private static void addMountStages(ItemStack hostItem, MountPath hostPath,
                                       @Nullable RailsModifier.Spec hostSpec, List<Stage> stages) {
        if (hostSpec == null) return;
        List<RailsModifier.RailSlot> slots = hostSpec.getSlots();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack mounted = RailStorage.getRailSightFromAttachment(hostItem, i);
            if (mounted.isEmpty()) continue;
            // Non-optics (e.g. a rail-mounted laser) render on the gun but aren't aimed through, so they
            // don't take part in the zoom cycle and can't host further optic mounts.
            if (!ScopeRails.isOptic(mounted)) continue;
            ResourceLocation id = attachmentId(mounted);
            if (id == null) continue;
            ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(id).orElse(null);
            if (index == null) continue;

            MountPath childPath = hostPath.child(i);
            stages.add(new Stage(childPath, id, mounted, cycleLength(index)));
            // Recurse into this optic's own mounts.
            addMountStages(mounted, childPath, ScopeRails.getRailsSpecForAttachment(mounted), stages);
        }
    }

    @Nullable
    private static ResourceLocation attachmentId(ItemStack attachment) {
        var iAttachment = com.tacz.guns.api.item.IAttachment.getIAttachmentOrNull(attachment);
        return iAttachment == null ? null : iAttachment.getAttachmentId(attachment);
    }

    /**
     * Number of cycle positions an optic contributes. TaC:Z indexes each of {@code zoom}, {@code views}
     * and {@code viewsFov} by {@code zoomNumber % thatArray.length} independently, so the effective step
     * count is the longest of them — e.g. {@code zoom: [4, 8]} with a single view still has two steps.
     */
    private static int cycleLength(ClientAttachmentIndex index) {
        int len = 1;
        float[] zoom = index.getZoom();
        if (zoom != null) len = Math.max(len, zoom.length);
        int[] views = index.getViews();
        if (views != null) len = Math.max(len, views.length);
        float[] viewsFov = index.getViewsFov();
        if (viewsFov != null) len = Math.max(len, viewsFov.length);
        return len;
    }

    private static final class Stage {
        final MountPath path;
        final ResourceLocation opticId;
        final ItemStack opticItem;
        final int length;

        Stage(MountPath path, ResourceLocation opticId, ItemStack opticItem, int length) {
            this.path = path;
            this.opticId = opticId;
            this.opticItem = opticItem;
            this.length = length;
        }
    }
}
