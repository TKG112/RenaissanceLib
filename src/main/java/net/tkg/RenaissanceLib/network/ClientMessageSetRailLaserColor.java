package net.tkg.RenaissanceLib.network;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.MountPath;
import net.tkg.RenaissanceLib.attachment.RailStorage;

import java.util.function.Supplier;

/**
 * Persists the laser color of a rail-mounted laser, server-authoritatively.
 *
 * <p>The refit UI dirty-writes the color onto the client's gun for a live beam preview while the player
 * drags the sliders; on release this message tells the server to set the same color on the laser stored
 * at {@code path} in rail storage, so it survives and syncs back.
 */
public class ClientMessageSetRailLaserColor {
    private final AttachmentType hostType;
    private final int[] path;
    private final int color;

    public ClientMessageSetRailLaserColor(MountPath path, int color) {
        this.hostType = path.hostType();
        this.path = path.toArray();
        this.color = color;
    }

    private ClientMessageSetRailLaserColor(AttachmentType hostType, int[] path, int color) {
        this.hostType = hostType;
        this.path = path;
        this.color = color;
    }

    public static void encode(ClientMessageSetRailLaserColor message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.hostType.ordinal());
        buf.writeVarInt(message.path.length);
        for (int i : message.path) buf.writeVarInt(i);
        buf.writeInt(message.color);
    }

    public static ClientMessageSetRailLaserColor decode(FriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        AttachmentType[] values = AttachmentType.values();
        AttachmentType hostType = (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : AttachmentType.SCOPE;
        int len = buf.readVarInt();
        int[] path = new int[Math.max(0, Math.min(len, 16))];
        for (int i = 0; i < len; i++) {
            int v = buf.readVarInt();
            if (i < path.length) path[i] = v;
        }
        return new ClientMessageSetRailLaserColor(hostType, path, buf.readInt());
    }

    public static void handle(ClientMessageSetRailLaserColor message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                try {
                    ItemStack gunItem = player.getMainHandItem();
                    if (IGun.getIGunOrNull(gunItem) == null) return;

                    MountPath path = MountPath.of(message.hostType, message.path);
                    if (path.isRoot()) return;

                    ItemStack laser = RailStorage.getMountedOnGun(gunItem, path);
                    if (!(laser.getItem() instanceof IAttachment iAttachment)) return;
                    iAttachment.setLaserColor(laser, message.color);
                    RailStorage.setMounted(gunItem, path, laser);
                } catch (Throwable t) {
                    RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Rail laser color set failed", t);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
