package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Enumerates the toggleable attachments installed on a gun (for the attachment wheel) and resolves the
 * item to show for each.
 *
 * <ul>
 *   <li>Flat top-level {@link AttachmentType} slots (muzzle, grip, …) and the scope itself → root targets.</li>
 *   <li>Rail-mounted optics under the scope, walked recursively via {@link ScopeRails} + {@link RailStorage}
 *       → nested {@link MountPath} targets. This is what lets a canted sight etc. be toggled at all.</li>
 * </ul>
 */
public final class AttachmentToggleTargets {

    /** Safety cap on rail-tree recursion. */
    private static final int MAX_DEPTH = 6;

    private AttachmentToggleTargets() {}

    /** Every toggleable attachment on the gun (flat slots + scope + rail tree), in a stable order. */
    public static List<ToggleTarget> list(ItemStack gunItem) {
        List<ToggleTarget> out = new ArrayList<>();

        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE || type == AttachmentType.SCOPE) continue;
            if (hasToggle(AttachmentStates.getStates(gunItem, type))) {
                out.add(ToggleTarget.slot(type));
            }
        }

        ItemStack scope = RailStorage.getScopeItem(gunItem);
        if (!scope.isEmpty()) {
            if (hasToggle(AttachmentStates.getStates(gunItem, AttachmentType.SCOPE))) {
                out.add(ToggleTarget.slot(AttachmentType.SCOPE));
            }
            walk(gunItem, scope, MountPath.ROOT, out, 0);
        }
        return out;
    }

    /** Only the rail-mounted (non-root) toggle targets — used by state-override application. */
    public static List<ToggleTarget> railTargets(ItemStack gunItem) {
        List<ToggleTarget> out = new ArrayList<>();
        ItemStack scope = RailStorage.getScopeItem(gunItem);
        if (!scope.isEmpty()) {
            walk(gunItem, scope, MountPath.ROOT, out, 0);
        }
        return out;
    }

    private static void walk(ItemStack gunItem, ItemStack host, MountPath hostPath,
                             List<ToggleTarget> out, int depth) {
        if (depth > MAX_DEPTH) return;
        RailsModifier.Spec spec = hostPath.isRoot()
                ? ScopeRails.getRailsSpec(gunItem)
                : ScopeRails.getRailsSpecForAttachment(host);
        if (spec == null) return;

        int slots = spec.getSlots().size();
        for (int i = 0; i < slots; i++) {
            MountPath childPath = hostPath.child(i);
            ItemStack child = RailStorage.getMountedOnGun(gunItem, childPath);
            if (child.isEmpty()) continue;
            if (hasToggle(AttachmentStates.getStatesForItem(child))) {
                out.add(ToggleTarget.of(AttachmentType.SCOPE, childPath));
            }
            walk(gunItem, child, childPath, out, depth + 1);
        }
    }

    private static boolean hasToggle(AttachmentStatesModifier.States states) {
        return states != null && states.getCycle().size() > 1;
    }

    /**
     * The installed attachment {@link ItemStack} for a target, for its wheel icon. Empty if none. For a
     * root target this is TaC:Z's stored attachment ItemStack in the gun NBT; for a rail-mounted target it
     * comes from {@link RailStorage}.
     */
    public static ItemStack getItem(ItemStack gunItem, ToggleTarget target) {
        if (target.isRoot()) {
            CompoundTag gunTag = gunItem.getTag();
            if (gunTag == null) return ItemStack.EMPTY;
            String key = GunItemDataAccessor.GUN_ATTACHMENT_BASE + target.slot().name();
            if (!gunTag.contains(key, Tag.TAG_COMPOUND)) return ItemStack.EMPTY;
            return ItemStack.of(gunTag.getCompound(key));
        }
        return RailStorage.getMountedOnGun(gunItem, target.path());
    }
}
