package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageRefreshRefitScreen;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.tkg.RenaissanceLib.RenaissanceLibMod;

/**
 * Server-side install/uninstall of an underbarrel that the player unlocked by owning its {@code item_link} gun
 * (see {@link ItemLinkRegistry}). Round-trip: installing consumes one linked gun and stores a copy of it on the
 * underbarrel attachment ({@link #LINKED_GUN_TAG}); uninstalling gives that gun back instead of the attachment
 * item (see {@code ClientMessageUnloadAttachmentItemLinkMixin}).
 */
public final class ItemLinkInstall {

    /** NBT key (on the underbarrel attachment stack) holding a saved copy of the consumed linked gun. */
    public static final String LINKED_GUN_TAG = "RenaissanceLinkedGun";

    private ItemLinkInstall() {}

    /**
     * If the item at {@code attachmentSlotIndex} is a gun that unlocks an underbarrel installable in the held
     * gun's grip slot, install it (consuming one gun) and return {@code true}. Otherwise return {@code false} so
     * TaC:Z's normal refit handling proceeds.
     */
    public static boolean tryInstall(ServerPlayer player, int attachmentSlotIndex, AttachmentType type) {
        if (type != AttachmentType.GRIP) return false; // item_link only unlocks the underbarrel (a grip)
        Inventory inv = player.getInventory();
        if (attachmentSlotIndex < 0 || attachmentSlotIndex >= inv.getContainerSize()) return false;
        ItemStack linkedStack = inv.getItem(attachmentSlotIndex);
        ResourceLocation attachmentId = ItemLinkRegistry.findAttachmentForItem(linkedStack);
        if (attachmentId == null) return false; // not a linked gun

        ItemStack gunStack = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null || iGun.hasAttachmentLock(gunStack)) return false;

        // Build the underbarrel attachment and stamp it with a round-trip copy of the gun we're consuming.
        ItemStack ub = AttachmentItemBuilder.create().setId(attachmentId).setCount(1).build();
        ItemStack consumed = linkedStack.copy();
        consumed.setCount(1);
        ub.getOrCreateTag().put(LINKED_GUN_TAG, consumed.save(new CompoundTag()));

        if (!iGun.allowAttachment(gunStack, ub)) return false; // e.g. host gun doesn't accept this grip

        try {
            ItemStack old = iGun.getAttachment(gunStack, type);
            iGun.installAttachment(gunStack, ub);
            AttachmentPropertyManager.postChangeEvent(player, gunStack);
            linkedStack.shrink(1);
            if (!old.isEmpty() && !inv.add(old)) {
                player.drop(old, false);
            }
            iGun.dropAllAmmo(player, gunStack);
            player.inventoryMenu.broadcastChanges();
            NetworkHandler.sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player);
        } catch (Throwable t) {
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] item_link install failed", t);
        }
        return true;
    }

    /** The saved linked gun stored on an underbarrel attachment, or {@link ItemStack#EMPTY} if it wasn't linked. */
    public static ItemStack extractLinkedGun(ItemStack attachmentStack) {
        if (attachmentStack == null || !attachmentStack.hasTag()) return ItemStack.EMPTY;
        CompoundTag tag = attachmentStack.getTag();
        if (tag == null || !tag.contains(LINKED_GUN_TAG)) return ItemStack.EMPTY;
        try {
            return ItemStack.of(tag.getCompound(LINKED_GUN_TAG));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
