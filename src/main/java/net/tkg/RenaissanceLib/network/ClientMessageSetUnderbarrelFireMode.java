package net.tkg.RenaissanceLib.network;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.attachment.UnderbarrelFireMode;

import java.util.function.Supplier;

/**
 * Sets the installed underbarrel's fire mode to a specific cycle index, for the fire-mode radial wheel (the
 * complement of {@link ClientMessageCycleUnderbarrelFireMode}, which only advances by one). Server-authoritative:
 * ignored unless the gun actually has an underbarrel; {@link UnderbarrelFireMode#setIndex} clamps the index
 * into the underbarrel's own cycle.
 */
public class ClientMessageSetUnderbarrelFireMode {
    private final int index;

    public ClientMessageSetUnderbarrelFireMode(int index) {
        this.index = index;
    }

    public static void encode(ClientMessageSetUnderbarrelFireMode message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.index);
    }

    public static ClientMessageSetUnderbarrelFireMode decode(FriendlyByteBuf buf) {
        return new ClientMessageSetUnderbarrelFireMode(buf.readVarInt());
    }

    public static void handle(ClientMessageSetUnderbarrelFireMode message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                try {
                    ItemStack gun = player.getMainHandItem();
                    if (IGun.getIGunOrNull(gun) == null) return;
                    GunData ubData = Underbarrel.getUnderbarrelData(Underbarrel.getInstalledUnderbarrel(gun));
                    if (ubData == null) return;
                    UnderbarrelFireMode.setIndex(gun, ubData, message.index);
                } catch (Throwable t) {
                    RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Set-underbarrel-fire-mode failed", t);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
