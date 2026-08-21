package net.tkg.RenaissanceLib.mixin;

import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.world.item.ItemStack;
import net.tkg.RenaissanceLib.attachment.ConversionKit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a conversion kit out of the native attachment slots. A kit rides a dedicated RenaissanceLib
 * virtual slot (the conversion slot under the mag); if its native attachment type happened to line up
 * with a slot the base gun allows, TaC:Z's ordinary refit would otherwise let a player drop the kit into
 * that slot, where it would do nothing. Rejecting it in {@code allowAttachment} blocks that install
 * (which is gated on this check) and keeps it out of the native picker.
 *
 * <p>Safe for the conversion flow itself: {@link ConversionKit#isKitCompatible} matches the kit's tags
 * against the base gun <em>directly</em> ({@code AllowAttachmentTagMatcher}), not through this method, so
 * installing via the conversion slot is unaffected.
 */
@Mixin(value = AbstractGunItem.class, remap = false)
public abstract class AbstractGunItemMixin {
    @Inject(method = "allowAttachment", at = @At("HEAD"), cancellable = true, remap = false)
    private void renaissance$rejectConversionKitInNativeSlot(ItemStack gun, ItemStack attachmentItem,
                                                             CallbackInfoReturnable<Boolean> cir) {
        if (ConversionKit.isConversionKit(attachmentItem)) {
            cir.setReturnValue(false);
        }
    }
}
