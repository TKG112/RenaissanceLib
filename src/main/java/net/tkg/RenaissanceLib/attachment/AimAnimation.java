package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Server-safe helper for the ADS-animation feature: reads the {@code aim_animation} block (see
 * {@link AimAnimationModifier}) off an attachment. Mirrors {@link FireAnimation}; the animation itself only plays
 * client-side.
 */
public final class AimAnimation {
    private AimAnimation() {}

    /** The {@code aim_animation} spec of the attachment in {@code type}, or {@code null} if none/absent. */
    @Nullable
    public static AimAnimationModifier.Spec get(ItemStack gunItem, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return null;
        return forId(iGun.getAttachmentId(gunItem, type));
    }

    /** The {@code aim_animation} spec of an attachment item (e.g. one mounted on a rail), or {@code null}. */
    @Nullable
    public static AimAnimationModifier.Spec getForItem(ItemStack attachmentItem) {
        if (attachmentItem == null || attachmentItem.isEmpty()) return null;
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        return iAttachment == null ? null : forId(iAttachment.getAttachmentId(attachmentItem));
    }

    @Nullable
    private static AimAnimationModifier.Spec forId(@Nullable ResourceLocation attachmentId) {
        if (attachmentId == null || DefaultAssets.isEmptyAttachmentId(attachmentId)) return null;
        return TimelessAPI.getCommonAttachmentIndex(attachmentId)
                .map(index -> index.getData().getModifier().get(AimAnimationModifier.ID))
                .filter(property -> property != null && property.getValue() instanceof AimAnimationModifier.Spec)
                .map(property -> (AimAnimationModifier.Spec) property.getValue())
                .orElse(null);
    }
}
