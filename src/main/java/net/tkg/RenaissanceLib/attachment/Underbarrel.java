package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Server-safe helper for the underbarrel feature: identifies underbarrel attachments (a {@code grip}
 * that carries an {@code underbarrel_data} block, see {@link UnderbarrelDataModifier}) and exposes the
 * embedded sub-gun {@link GunData}.
 *
 * <p>Mirrors {@link ScopeRails}: detection reads the common attachment index's modifier map, so it works
 * identically on both sides. The underbarrel rides the {@link AttachmentType#GRIP} slot (per the devs'
 * format), so we gate on that slot to avoid mistaking any other data-carrying attachment for one.
 * Client-only display extras (the sub-gun {@code GunDisplay}, {@code hide_tactical_handguard}) live in
 * {@link net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelClient}.
 */
public final class Underbarrel {
    private Underbarrel() {}

    /** Whether an attachment item is an underbarrel sub-gun (a grip carrying {@code underbarrel_data}). */
    public static boolean isUnderbarrel(ItemStack attachmentItem) {
        return getUnderbarrelData(attachmentItem) != null;
    }

    /** The embedded sub-gun {@link GunData} declared by an underbarrel attachment item, or {@code null}. */
    @Nullable
    public static GunData getUnderbarrelData(ItemStack attachmentItem) {
        if (attachmentItem == null || attachmentItem.isEmpty()) return null;
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment == null) return null;
        if (iAttachment.getType(attachmentItem) != AttachmentType.GRIP) return null;
        return TimelessAPI.getCommonAttachmentIndex(iAttachment.getAttachmentId(attachmentItem))
                .map(index -> index.getData().getModifier().get(UnderbarrelDataModifier.ID))
                .filter(property -> property != null && property.getValue() instanceof GunData)
                .map(property -> (GunData) property.getValue())
                .orElse(null);
    }

    /**
     * The underbarrel grip installed on a gun, or {@link ItemStack#EMPTY} if none. The underbarrel rides
     * the grip slot, so this reads the grip and confirms it's an underbarrel.
     */
    public static ItemStack getInstalledUnderbarrel(ItemStack gunItem) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return ItemStack.EMPTY;
        ItemStack grip = iGun.getAttachment(gunItem, AttachmentType.GRIP);
        return isUnderbarrel(grip) ? grip : ItemStack.EMPTY;
    }

    /** Whether the gun currently has an underbarrel sub-gun installed. */
    public static boolean hasUnderbarrel(ItemStack gunItem) {
        return !getInstalledUnderbarrel(gunItem).isEmpty();
    }
}
