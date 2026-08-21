package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The underbarrel's <em>own</em> installed attachments — a muzzle, extended mag, etc. mounted on the sub-gun.
 *
 * <p>Stored on the grip attachment's own NBT under the standard {@code Attachment<TYPE>} keys — so they read
 * back through TaC:Z's attachment index like any attachment and travel with the underbarrel between guns —
 * just nested one level deeper than a gun's: reached as {@code gun.tag.AttachmentGRIP.tag.Attachment<TYPE>}.
 * The allowed slots are the underbarrel's own {@code allow_attachment_types} ({@link GunData#getAllowAttachments}).
 *
 * <p>This is only storage + the slot gate; the stat effects of these attachments are applied by
 * {@link UnderbarrelCache} (mirroring TaC:Z's modifier pipeline over the sub-gun's data).
 */
public final class UnderbarrelAttachments {
    private static final String GRIP_KEY = GunItemDataAccessor.GUN_ATTACHMENT_BASE + AttachmentType.GRIP.name();
    private static final String ITEMSTACK_TAG = "tag";

    private UnderbarrelAttachments() {}

    /** The attachment types the underbarrel accepts (its own {@code allow_attachment_types}). */
    public static List<AttachmentType> getAllowedTypes(GunData ubData) {
        if (ubData == null || ubData.getAllowAttachments() == null) return List.of();
        return ubData.getAllowAttachments();
    }

    /** Whether the underbarrel accepts an attachment of {@code type}. */
    public static boolean isAllowed(GunData ubData, AttachmentType type) {
        return getAllowedTypes(ubData).contains(type);
    }

    /** The attachment installed in the underbarrel's {@code type} slot, or {@link ItemStack#EMPTY}. */
    public static ItemStack getInstalled(ItemStack gunItem, AttachmentType type) {
        CompoundTag gripItemTag = gripItemTag(gunItem);
        if (gripItemTag == null) return ItemStack.EMPTY;
        String key = GunItemDataAccessor.GUN_ATTACHMENT_BASE + type.name();
        return gripItemTag.contains(key, Tag.TAG_COMPOUND)
                ? ItemStack.of(gripItemTag.getCompound(key)) : ItemStack.EMPTY;
    }

    /** The id of the attachment installed in {@code type}, or {@link DefaultAssets#EMPTY_ATTACHMENT_ID}. */
    public static ResourceLocation getInstalledId(ItemStack gunItem, AttachmentType type) {
        ItemStack installed = getInstalled(gunItem, type);
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(installed);
        return iAttachment == null ? DefaultAssets.EMPTY_ATTACHMENT_ID : iAttachment.getAttachmentId(installed);
    }

    /** Install (or clear, when {@code attachment} is empty/null) an attachment in the underbarrel's {@code type} slot. */
    public static void setInstalled(ItemStack gunItem, AttachmentType type, ItemStack attachment) {
        CompoundTag gunTag = gunItem.getOrCreateTag();
        if (!gunTag.contains(GRIP_KEY, Tag.TAG_COMPOUND)) return; // no underbarrel (grip) installed
        CompoundTag gripStack = gunTag.getCompound(GRIP_KEY);
        CompoundTag gripItemTag = gripStack.getCompound(ITEMSTACK_TAG); // the grip's own NBT (created if absent)
        String key = GunItemDataAccessor.GUN_ATTACHMENT_BASE + type.name();
        if (attachment == null || attachment.isEmpty()) {
            gripItemTag.remove(key);
        } else {
            ItemStack one = attachment.copy();
            one.setCount(1);
            gripItemTag.put(key, one.save(new CompoundTag()));
        }
        gripStack.put(ITEMSTACK_TAG, gripItemTag);
        gunTag.put(GRIP_KEY, gripStack);
    }

    /** The grip attachment's own NBT ({@code gun.AttachmentGRIP.tag}), or {@code null} if no grip is installed. */
    private static CompoundTag gripItemTag(ItemStack gunItem) {
        CompoundTag gunTag = gunItem.getTag();
        if (gunTag == null || !gunTag.contains(GRIP_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag gripStack = gunTag.getCompound(GRIP_KEY);
        return gripStack.contains(ITEMSTACK_TAG, Tag.TAG_COMPOUND) ? gripStack.getCompound(ITEMSTACK_TAG) : null;
    }
}
