package net.tkg.RenaissanceLib.network;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAttachments;

import java.util.function.Supplier;

/**
 * Installs or clears an attachment in one of the underbarrel's own slots, server-authoritatively. The
 * underbarrel is a sub-gun that declares its own {@code allow_attachment_types}; its attachments are stored on
 * the grip attachment's NBT ({@link UnderbarrelAttachments}) and their stat effects applied via
 * {@link net.tkg.RenaissanceLib.attachment.UnderbarrelCache}.
 *
 * <p>{@code inventorySlot >= 0}: take an attachment of {@code type} from that slot and install it;
 * {@code -1}: clear the underbarrel's {@code type} slot, returning the attachment to the player. Validated
 * against the underbarrel's allowed types and the item's own type.
 */
public class ClientMessageSetUnderbarrelAttachment {
    public static final int CLEAR = -1;

    private final AttachmentType type;
    private final int inventorySlot;

    public ClientMessageSetUnderbarrelAttachment(AttachmentType type, int inventorySlot) {
        this.type = type;
        this.inventorySlot = inventorySlot;
    }

    public static void encode(ClientMessageSetUnderbarrelAttachment message, FriendlyByteBuf buf) {
        buf.writeEnum(message.type);
        buf.writeInt(message.inventorySlot);
    }

    public static ClientMessageSetUnderbarrelAttachment decode(FriendlyByteBuf buf) {
        return new ClientMessageSetUnderbarrelAttachment(buf.readEnum(AttachmentType.class), buf.readInt());
    }

    public static void handle(ClientMessageSetUnderbarrelAttachment message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                try {
                    ItemStack gunItem = player.getMainHandItem();
                    if (IGun.getIGunOrNull(gunItem) == null) return;
                    GunData ubData = Underbarrel.getUnderbarrelData(Underbarrel.getInstalledUnderbarrel(gunItem));
                    if (ubData == null) return;
                    if (!UnderbarrelAttachments.isAllowed(ubData, message.type)) return;

                    if (message.inventorySlot == CLEAR) {
                        ItemStack installed = UnderbarrelAttachments.getInstalled(gunItem, message.type);
                        if (!installed.isEmpty() && !player.getInventory().add(installed)) {
                            player.drop(installed, false);
                        }
                        UnderbarrelAttachments.setInstalled(gunItem, message.type, ItemStack.EMPTY);
                        return;
                    }

                    ItemStack attachment = player.getInventory().getItem(message.inventorySlot);
                    IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachment);
                    if (iAttachment == null || iAttachment.getType(attachment) != message.type) return;

                    ItemStack previous = UnderbarrelAttachments.getInstalled(gunItem, message.type);
                    if (!previous.isEmpty() && !player.getInventory().add(previous)) {
                        player.drop(previous, false);
                    }
                    UnderbarrelAttachments.setInstalled(gunItem, message.type, attachment);
                    attachment.shrink(1);
                } catch (Throwable t) {
                    RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Underbarrel attachment set failed", t);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
