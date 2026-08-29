package net.tkg.RenaissanceLib.mixin;

import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.tkg.RenaissanceLib.attachment.ItemLinkInstall;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Round-trip uninstall for an item-link underbarrel: when the grip attachment being unloaded carries a saved
 * linked gun ({@link ItemLinkInstall#LINKED_GUN_TAG}), give that <em>gun</em> back to the player instead of the
 * underbarrel attachment item — mirroring the consume-on-install. Normal (non-linked) attachments are unchanged.
 */
@Mixin(value = ClientMessageUnloadAttachment.class, remap = false)
public abstract class ClientMessageUnloadAttachmentItemLinkMixin {

    @Redirect(
            method = "lambda$handle$0",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z",
                    remap = true),
            remap = false)
    private static boolean renaissance$giveBackLinkedGun(Inventory inventory, ItemStack attachment) {
        ItemStack linkedGun = ItemLinkInstall.extractLinkedGun(attachment);
        return inventory.add(linkedGun.isEmpty() ? attachment : linkedGun);
    }
}
