package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Stores the optics mounted in a rail-bearing scope attachment's slots — inside <em>that attachment's
 * own ItemStack NBT</em>, so the loadout travels with the rail when it's moved between guns.
 *
 * <p>TaC:Z stores each installed attachment as a full serialized ItemStack in the gun's NBT (key
 * {@code Attachment<TYPE>}), preserving its {@code tag} (see {@code GunItemDataAccessor}). We keep the
 * mounted optics under a {@code RenaissanceRails} compound inside the scope attachment's {@code tag},
 * reached as {@code gun.tag.AttachmentSCOPE.tag.RenaissanceRails}. Removing the rail through a normal
 * TaC:Z refit returns the whole attachment ItemStack — mounted optics and all — to the player, and
 * re-installing it on another gun carries them along. Each rail slot index maps to the mounted optic's
 * saved {@link ItemStack}.
 *
 * <p>Legacy: earlier builds stored this directly on the gun NBT. Reads fall back to that location so
 * existing guns keep working; writes always go onto the attachment (so once a slot is changed, the
 * loadout starts travelling with the rail).
 */
public final class RailStorage {
    private static final String RAILS_TAG = "RenaissanceRails";
    /** TaC:Z's gun-NBT key for the installed scope attachment ItemStack, e.g. {@code AttachmentSCOPE}. */
    private static final String SCOPE_KEY = hostKey(AttachmentType.SCOPE);
    /** The ItemStack-NBT sub-key that holds an item's own tag ({@code {id, Count, tag}}). */
    private static final String ITEMSTACK_TAG = "tag";

    private RailStorage() {}

    /** The gun-NBT key for the installed attachment ItemStack in the given native slot (e.g. {@code AttachmentGRIP}). */
    private static String hostKey(AttachmentType hostType) {
        return GunItemDataAccessor.GUN_ATTACHMENT_BASE + hostType.name();
    }

    public static ItemStack getRailSight(ItemStack gunItem, int index) {
        CompoundTag gunTag = gunItem.getTag();
        if (gunTag == null) return ItemStack.EMPTY;
        String key = Integer.toString(index);

        // Preferred: stored on the rail-bearing scope attachment itself, so it travels with the rail.
        CompoundTag rails = scopeRailsTag(gunTag);
        if (rails != null && rails.contains(key, Tag.TAG_COMPOUND)) {
            return ItemStack.of(rails.getCompound(key));
        }
        // Legacy fallback: stored directly on the gun.
        CompoundTag legacy = gunTag.getCompound(RAILS_TAG);
        return legacy.contains(key, Tag.TAG_COMPOUND) ? ItemStack.of(legacy.getCompound(key)) : ItemStack.EMPTY;
    }

    public static void setRailSight(ItemStack gunItem, int index, ItemStack sight) {
        CompoundTag gunTag = gunItem.getOrCreateTag();
        if (!gunTag.contains(SCOPE_KEY, Tag.TAG_COMPOUND)) return; // no rail-bearing scope installed
        CompoundTag scopeStack = gunTag.getCompound(SCOPE_KEY);
        CompoundTag scopeItemTag = scopeStack.getCompound(ITEMSTACK_TAG); // the attachment's own NBT (created if absent)
        CompoundTag rails = scopeItemTag.getCompound(RAILS_TAG);
        String key = Integer.toString(index);
        if (sight == null || sight.isEmpty()) {
            rails.remove(key);
        } else {
            ItemStack one = sight.copy();
            one.setCount(1);
            rails.put(key, one.save(new CompoundTag()));
        }
        if (rails.isEmpty()) {
            scopeItemTag.remove(RAILS_TAG);
        } else {
            scopeItemTag.put(RAILS_TAG, rails);
        }
        scopeStack.put(ITEMSTACK_TAG, scopeItemTag);
        gunTag.put(SCOPE_KEY, scopeStack);
    }

    /**
     * The optic mounted in a rail slot, read directly from a <em>standalone</em> rail attachment's own
     * NBT ({@code attachment.tag.RenaissanceRails.{index}}) — used when the attachment is rendered as an
     * item (held/framed/dropped) rather than installed on a gun.
     */
    public static ItemStack getRailSightFromAttachment(ItemStack attachment, int index) {
        CompoundTag tag = attachment.getTag();
        if (tag == null) return ItemStack.EMPTY;
        CompoundTag rails = tag.getCompound(RAILS_TAG);
        String key = Integer.toString(index);
        return rails.contains(key, Tag.TAG_COMPOUND) ? ItemStack.of(rails.getCompound(key)) : ItemStack.EMPTY;
    }

    // ---- Recursive (path-based) access -----------------------------------------------------------

    /** The scope-slot attachment ItemStack installed on the gun (the optic rail host), or empty. */
    public static ItemStack getScopeItem(ItemStack gunItem) {
        return getHostItem(gunItem, AttachmentType.SCOPE);
    }

    /** The attachment ItemStack installed in the given native slot (a rail host), or empty. */
    public static ItemStack getHostItem(ItemStack gunItem, AttachmentType hostType) {
        CompoundTag gunTag = gunItem.getTag();
        String key = hostKey(hostType);
        if (gunTag == null || !gunTag.contains(key, Tag.TAG_COMPOUND)) return ItemStack.EMPTY;
        return ItemStack.of(gunTag.getCompound(key));
    }

    /**
     * The optic mounted at {@code path}, walked from an arbitrary root host item (e.g. the scope item).
     * {@link MountPath#ROOT} returns {@code rootHost} itself. Empty if any hop is missing.
     */
    public static ItemStack getMounted(ItemStack rootHost, MountPath path) {
        ItemStack cur = rootHost;
        for (int i = 0; i < path.depth(); i++) {
            if (cur.isEmpty()) return ItemStack.EMPTY;
            cur = getRailSightFromAttachment(cur, path.get(i));
        }
        return cur;
    }

    /** The mount at {@code path} on the gun (a host root path = the host attachment itself). */
    public static ItemStack getMountedOnGun(ItemStack gunItem, MountPath path) {
        return getMounted(getHostItem(gunItem, path.hostType()), path);
    }

    /**
     * Installs (or clears, when {@code optic} is empty/{@code null}) the optic at {@code path} on the gun,
     * re-serializing each host ItemStack back into its parent up to {@code gun.AttachmentSCOPE}. Returns
     * {@code false} (no write) if {@code path} is the root or any parent hop along it isn't mounted.
     */
    public static boolean setMounted(ItemStack gunItem, MountPath path, ItemStack optic) {
        if (path.isRoot()) return false;
        CompoundTag gunTag = gunItem.getOrCreateTag();
        String key = hostKey(path.hostType());
        if (!gunTag.contains(key, Tag.TAG_COMPOUND)) return false;
        CompoundTag hostStack = gunTag.getCompound(key);
        if (!setInHostStack(hostStack, path, 0, optic)) return false;
        gunTag.put(key, hostStack);
        return true;
    }

    /**
     * Recursively sets {@code optic} at {@code path} within the serialized host ItemStack {@code hostStack}
     * ({@code {id, Count, tag}}), descending one hop per level and re-writing each modified child back into
     * its parent's {@code RenaissanceRails}. Returns {@code false} if a parent hop is missing.
     */
    private static boolean setInHostStack(CompoundTag hostStack, MountPath path, int depth, ItemStack optic) {
        int index = path.get(depth);
        String key = Integer.toString(index);
        CompoundTag hostItemTag = hostStack.getCompound(ITEMSTACK_TAG); // host's own NBT (created if absent)
        CompoundTag rails = hostItemTag.getCompound(RAILS_TAG);

        if (depth == path.depth() - 1) {
            if (optic == null || optic.isEmpty()) {
                rails.remove(key);
            } else {
                ItemStack one = optic.copy();
                one.setCount(1);
                rails.put(key, one.save(new CompoundTag()));
            }
        } else {
            if (!rails.contains(key, Tag.TAG_COMPOUND)) return false; // parent mount not present
            CompoundTag childStack = rails.getCompound(key);
            if (!setInHostStack(childStack, path, depth + 1, optic)) return false;
            rails.put(key, childStack);
        }

        if (rails.isEmpty()) {
            hostItemTag.remove(RAILS_TAG);
        } else {
            hostItemTag.put(RAILS_TAG, rails);
        }
        hostStack.put(ITEMSTACK_TAG, hostItemTag);
        return true;
    }

    /** The {@code RenaissanceRails} compound inside the installed scope attachment's ItemStack NBT, or {@code null}. */
    private static CompoundTag scopeRailsTag(CompoundTag gunTag) {
        if (!gunTag.contains(SCOPE_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag scopeStack = gunTag.getCompound(SCOPE_KEY);
        if (!scopeStack.contains(ITEMSTACK_TAG, Tag.TAG_COMPOUND)) return null;
        CompoundTag scopeItemTag = scopeStack.getCompound(ITEMSTACK_TAG);
        if (!scopeItemTag.contains(RAILS_TAG, Tag.TAG_COMPOUND)) return null;
        return scopeItemTag.getCompound(RAILS_TAG);
    }
}
