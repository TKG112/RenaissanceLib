package net.tkg.RenaissanceLib.network;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.AttachmentStates;

import java.util.function.Supplier;

public class ClientMessageToggleAttachment {
    private final AttachmentType type;

    public ClientMessageToggleAttachment(AttachmentType type) {
        this.type = type;
    }

    public static void encode(ClientMessageToggleAttachment message, FriendlyByteBuf buf) {
        buf.writeEnum(message.type);
    }

    public static ClientMessageToggleAttachment decode(FriendlyByteBuf buf) {
        return new ClientMessageToggleAttachment(buf.readEnum(AttachmentType.class));
    }

    public static void handle(ClientMessageToggleAttachment message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                try {
                    AttachmentType type = message.type;
                    if (type == null || type == AttachmentType.NONE) return;

                    ItemStack gunItem = player.getMainHandItem();
                    if (IGun.getIGunOrNull(gunItem) == null) return;

                    String newState = AttachmentStates.cycleState(gunItem, type);
                    if (newState == null) return;

                    AttachmentStates.applyZoomForState(gunItem, type, newState);

                    AttachmentPropertyManager.postChangeEvent(player, gunItem);
                } catch (Throwable t) {
                    RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Attachment toggle failed", t);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
