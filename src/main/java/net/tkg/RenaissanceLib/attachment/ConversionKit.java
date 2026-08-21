package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Server-safe helper for the conversion-kit feature: identifies conversion kits (any attachment carrying
 * a {@code conversion} block, see {@link ConversionModifier}) and resolves the gun a host weapon is
 * converted into while a kit is installed.
 *
 * <p>Mirrors {@link Underbarrel}: detection reads the common attachment index's modifier map, so it works
 * identically on both sides. Unlike the underbarrel there is no native slot to gate on — a kit rides a
 * virtual slot ({@link ConversionStorage}) — so an attachment is a kit purely by carrying a parseable
 * {@code conversion} block. The actual weapon swap (approach B) is done by redirecting the gun's identity
 * to {@link #getConversionTarget(ItemStack)}; once redirected, the converted gun's own
 * {@code allow_attachments} handles locking the other slots.
 */
public final class ConversionKit {
    private ConversionKit() {}

    /** Whether an attachment item is a conversion kit (carries a parseable {@code conversion} block). */
    public static boolean isConversionKit(ItemStack attachmentItem) {
        return getKitConvertedGunId(attachmentItem) != null;
    }

    /** The gun id a conversion-kit item declares it converts into, or {@code null} if it isn't a kit. */
    @Nullable
    public static ResourceLocation getKitConvertedGunId(ItemStack attachmentItem) {
        if (attachmentItem == null || attachmentItem.isEmpty()) return null;
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment == null) return null;
        return TimelessAPI.getCommonAttachmentIndex(iAttachment.getAttachmentId(attachmentItem))
                .map(index -> index.getData().getModifier().get(ConversionModifier.ID))
                .filter(property -> property != null && property.getValue() instanceof ConversionModifier.Spec)
                .map(property -> ((ConversionModifier.Spec) property.getValue()).getConvertedGun())
                .orElse(null);
    }

    /** The conversion kit installed on the gun's virtual slot, or {@link ItemStack#EMPTY} if none. */
    public static ItemStack getInstalledKit(ItemStack gunItem) {
        return ConversionStorage.getKit(gunItem);
    }

    /**
     * The gun id this weapon should currently resolve as, or {@code null} if it isn't converted. This is
     * the installed kit's declared target, validated to exist as a real common gun index — an unknown
     * target yields {@code null} so the weapon safely falls back to its own definition.
     */
    @Nullable
    public static ResourceLocation getConversionTarget(ItemStack gunItem) {
        if (gunItem == null || gunItem.isEmpty()) return null;
        if (!ConversionStorage.hasKit(gunItem)) return null;
        ResourceLocation target = getKitConvertedGunId(getInstalledKit(gunItem));
        if (target == null) return null;
        return TimelessAPI.getCommonGunIndex(target).isPresent() ? target : null;
    }

    /** Whether the gun currently has a valid conversion kit installed (its identity should be redirected). */
    public static boolean isConverted(ItemStack gunItem) {
        return getConversionTarget(gunItem) != null;
    }

    /**
     * The gun's <em>own</em> id read straight from raw NBT, bypassing the conversion redirect. Needed for
     * compatibility checks: a kit's compatibility is always judged against the base weapon, even while a
     * kit is already installed (so {@code getGunId} would otherwise report the converted gun).
     */
    public static ResourceLocation getBaseGunId(ItemStack gunItem) {
        CompoundTag tag = gunItem == null ? null : gunItem.getTag();
        if (tag != null && tag.contains(GunItemDataAccessor.GUN_ID_TAG, Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(GunItemDataAccessor.GUN_ID_TAG));
            if (id != null) return id;
        }
        return DefaultAssets.EMPTY_GUN_ID;
    }

    /**
     * Whether {@code kitItem} is a conversion kit the {@code gunItem}'s base weapon accepts. Reuses TaC:Z's
     * {@code allow_attachments} tag system (the same gate normal attachments pass), matched against the
     * <em>base</em> gun id — so pack authors opt a gun into a kit by tagging it in that gun's
     * {@code allow_attachments}, and only guns that do so show/accept the kit.
     */
    public static boolean isKitCompatible(ItemStack gunItem, ItemStack kitItem) {
        if (!isConversionKit(kitItem)) return false;
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(kitItem);
        if (iAttachment == null) return false;
        return AllowAttachmentTagMatcher.match(getBaseGunId(gunItem), iAttachment.getAttachmentId(kitItem));
    }
}
