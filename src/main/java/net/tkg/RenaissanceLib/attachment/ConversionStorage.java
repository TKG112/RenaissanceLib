package net.tkg.RenaissanceLib.attachment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Stores the conversion kit installed on a gun — a full serialized {@link ItemStack} in the gun's own
 * NBT, under a {@code RenaissanceConversion} compound.
 *
 * <p>The kit lives in a RenaissanceLib <em>virtual</em> slot (there is no free native TaC:Z
 * {@link com.tacz.guns.api.item.attachment.AttachmentType}), so it isn't reachable through TaC:Z's
 * {@code Attachment<TYPE>} keys. Unlike the rail sights — which hang off a host scope attachment's NBT
 * so they travel with it — a conversion kit has no host attachment, so it is stored directly on the gun
 * (the same place the legacy rail path used). Installing/removing the kit is done by the virtual-slot
 * refit overlay and its network message; the identity redirect that actually swaps the weapon reads the
 * kit back through {@link ConversionKit}.
 */
public final class ConversionStorage {
    private static final String CONVERSION_TAG = "RenaissanceConversion";

    private ConversionStorage() {}

    /** The conversion kit installed on the gun, or {@link ItemStack#EMPTY} if none. */
    public static ItemStack getKit(ItemStack gunItem) {
        CompoundTag tag = gunItem.getTag();
        if (tag == null || !tag.contains(CONVERSION_TAG, Tag.TAG_COMPOUND)) return ItemStack.EMPTY;
        return ItemStack.of(tag.getCompound(CONVERSION_TAG));
    }

    /** Installs {@code kit} (a single copy) on the gun, or clears the slot when {@code kit} is empty/null. */
    public static void setKit(ItemStack gunItem, ItemStack kit) {
        CompoundTag tag = gunItem.getOrCreateTag();
        if (kit == null || kit.isEmpty()) {
            tag.remove(CONVERSION_TAG);
            return;
        }
        ItemStack one = kit.copy();
        one.setCount(1);
        tag.put(CONVERSION_TAG, one.save(new CompoundTag()));
    }

    /** Whether a conversion kit is present in the gun's virtual slot (cheap NBT check, no ItemStack build). */
    public static boolean hasKit(ItemStack gunItem) {
        CompoundTag tag = gunItem.getTag();
        return tag != null && tag.contains(CONVERSION_TAG, Tag.TAG_COMPOUND);
    }
}
