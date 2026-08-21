package net.tkg.RenaissanceLib.network;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.ConversionKit;
import net.tkg.RenaissanceLib.attachment.ConversionStorage;

import java.util.function.Supplier;

/**
 * Installs or clears the conversion kit in a gun's virtual conversion slot, server-authoritatively.
 *
 * <p>{@code inventorySlot >= 0}: take one conversion kit from that inventory slot and install it (any kit
 * already fitted is returned to the player). {@code inventorySlot == -1}: remove the installed kit,
 * returning it to the player's inventory. Once a kit is installed the gun's identity is redirected to the
 * converted gun (see {@link net.tkg.RenaissanceLib.mixin.ModernKineticGunItemMixin}); the kit itself is
 * stored on raw gun NBT ({@link ConversionStorage}), so it stays reachable for removal even while the
 * weapon reads as the converted gun.
 */
public class ClientMessageSetConversionKit {
    public static final int CLEAR = -1;

    private final int inventorySlot;

    public ClientMessageSetConversionKit(int inventorySlot) {
        this.inventorySlot = inventorySlot;
    }

    public static void encode(ClientMessageSetConversionKit message, FriendlyByteBuf buf) {
        buf.writeInt(message.inventorySlot);
    }

    public static ClientMessageSetConversionKit decode(FriendlyByteBuf buf) {
        return new ClientMessageSetConversionKit(buf.readInt());
    }

    public static void handle(ClientMessageSetConversionKit message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                try {
                    ItemStack gunItem = player.getMainHandItem();
                    IGun iGun = IGun.getIGunOrNull(gunItem);
                    if (iGun == null) return;

                    if (message.inventorySlot == CLEAR) {
                        ItemStack existing = ConversionStorage.getKit(gunItem);
                        if (!existing.isEmpty() && !player.getInventory().add(existing)) {
                            player.drop(existing, false);
                        }
                        ConversionStorage.setKit(gunItem, ItemStack.EMPTY);
                        iGun.setCurrentAmmoCount(gunItem, 0); // the base weapon returns; start it clean
                        return;
                    }

                    ItemStack kit = player.getInventory().getItem(message.inventorySlot);
                    // Compatibility: the base weapon must accept this kit via its allow_attachments tags.
                    if (!ConversionKit.isKitCompatible(gunItem, kit)) return;

                    ItemStack previous = ConversionStorage.getKit(gunItem);
                    if (!previous.isEmpty() && !player.getInventory().add(previous)) {
                        player.drop(previous, false);
                    }
                    ConversionStorage.setKit(gunItem, kit); // stores a single copy
                    kit.shrink(1);

                    // From here the weapon resolves as the converted gun. Force-strip anything the converted
                    // gun no longer allows (returned to the player) and clear ammo, so it starts clean.
                    stripDisallowedAttachments(iGun, gunItem, player);
                    iGun.setCurrentAmmoCount(gunItem, 0);
                } catch (Throwable t) {
                    RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Conversion-kit set failed", t);
                }
            });
        }
        context.setPacketHandled(true);
    }

    /**
     * Returns to the player any native-slot attachment the (now converted) gun no longer allows — by type
     * or by the converted gun's {@code allow_attachments} tags. Reads/removes the raw {@code Attachment<TYPE>}
     * NBT directly, because {@code getAttachment} yields empty for a type the converted gun disallows.
     */
    private static void stripDisallowedAttachments(IGun iGun, ItemStack gunItem, ServerPlayer player) {
        CompoundTag gunTag = gunItem.getTag();
        if (gunTag == null) return;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            String key = GunItemDataAccessor.GUN_ATTACHMENT_BASE + type.name();
            if (!gunTag.contains(key, Tag.TAG_COMPOUND)) continue;
            ItemStack installed = ItemStack.of(gunTag.getCompound(key));
            if (installed.isEmpty()) continue;
            if (iGun.allowAttachmentType(gunItem, type) && iGun.allowAttachment(gunItem, installed)) continue;
            gunTag.remove(key);
            if (!player.getInventory().add(installed)) {
                player.drop(installed, false);
            }
        }
    }
}
