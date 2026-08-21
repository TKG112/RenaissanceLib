package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Server-safe helper for the fire-reaction feature: reads the {@code fire_animation} block (see
 * {@link FireAnimationModifier}) off the attachment installed in a given slot. Mirrors
 * {@link AttachmentStates#getStates} — detection goes through the common attachment index's modifier map,
 * so it works identically on both sides (though the animation itself only plays client-side).
 */
public final class FireAnimation {
    private FireAnimation() {}

    /** The {@code fire_animation} spec of the attachment in {@code type}, or {@code null} if none/absent. */
    @Nullable
    public static FireAnimationModifier.Spec get(ItemStack gunItem, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return null;
        ResourceLocation attachmentId = iGun.getAttachmentId(gunItem, type);
        if (attachmentId == null || DefaultAssets.isEmptyAttachmentId(attachmentId)) return null;
        return TimelessAPI.getCommonAttachmentIndex(attachmentId)
                .map(index -> index.getData().getModifier().get(FireAnimationModifier.ID))
                .filter(property -> property != null && property.getValue() instanceof FireAnimationModifier.Spec)
                .map(property -> (FireAnimationModifier.Spec) property.getValue())
                .orElse(null);
    }
}
