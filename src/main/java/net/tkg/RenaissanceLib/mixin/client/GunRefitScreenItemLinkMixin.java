package net.tkg.RenaissanceLib.mixin.client;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.ItemLinkRegistry;
import net.tkg.RenaissanceLib.client.gui.LinkedAttachmentStub;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes an owned {@code item_link} gun appear as an installable grip candidate in TaC:Z's refit picker, by
 * bending the two checks in {@code addInventoryAttachmentButtons}:
 * <ul>
 *   <li>{@link IAttachment#getIAttachmentOrNull} returns a {@link LinkedAttachmentStub} (type = GRIP) for a
 *       linked gun, so TaC:Z creates its inventory button via its own layout/paging;</li>
 *   <li>{@link IGun#allowAttachment} is answered against the underbarrel the gun would install (not the gun
 *       itself, which is never a valid attachment) so the button only shows when it actually fits.</li>
 * </ul>
 * Clicking it sends TaC:Z's normal refit packet, which {@code ClientMessageRefitGunItemLinkMixin} turns into the
 * round-trip install. Both redirects fall through to vanilla behaviour for real attachment items.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = GunRefitScreen.class, remap = false)
public abstract class GunRefitScreenItemLinkMixin {

    @Redirect(
            method = "addInventoryAttachmentButtons",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/api/item/IAttachment;getIAttachmentOrNull(Lnet/minecraft/world/item/ItemStack;)Lcom/tacz/guns/api/item/IAttachment;"))
    private IAttachment renaissance$linkedCandidate(ItemStack stack) {
        IAttachment real = IAttachment.getIAttachmentOrNull(stack);
        if (real != null) return real;
        return ItemLinkRegistry.findAttachmentForItem(stack) != null ? LinkedAttachmentStub.INSTANCE : null;
    }

    @Redirect(
            method = "addInventoryAttachmentButtons",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/api/item/IGun;allowAttachment(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean renaissance$allowLinked(IGun iGun, ItemStack gunStack, ItemStack candidate) {
        if (IAttachment.getIAttachmentOrNull(candidate) == null) {
            ResourceLocation ubId = ItemLinkRegistry.findAttachmentForItem(candidate);
            if (ubId != null) {
                ItemStack ub = AttachmentItemBuilder.create().setId(ubId).setCount(1).build();
                return iGun.allowAttachment(gunStack, ub);
            }
        }
        return iGun.allowAttachment(gunStack, candidate);
    }
}
