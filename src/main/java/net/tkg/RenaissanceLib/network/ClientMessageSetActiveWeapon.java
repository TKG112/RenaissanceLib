package net.tkg.RenaissanceLib.network;

import com.tacz.guns.api.item.IGun;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;

import java.util.function.Supplier;

/**
 * Client → server: select which weapon on the held gun fires (main gun or underbarrel), chosen through the
 * weapon-select radial. The server re-validates ({@link ActiveWeapon#set} only keeps {@code UNDERBARREL}
 * when one is actually installed) and applies it authoritatively.
 */
public class ClientMessageSetActiveWeapon {
    private final int weapon;

    public ClientMessageSetActiveWeapon(int weapon) {
        this.weapon = weapon;
    }

    public static void encode(ClientMessageSetActiveWeapon message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.weapon);
    }

    public static ClientMessageSetActiveWeapon decode(FriendlyByteBuf buf) {
        return new ClientMessageSetActiveWeapon(buf.readVarInt());
    }

    public static void handle(ClientMessageSetActiveWeapon message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                try {
                    ItemStack gunItem = player.getMainHandItem();
                    if (IGun.getIGunOrNull(gunItem) == null) return;
                    ActiveWeapon.set(gunItem, message.weapon);
                } catch (Throwable t) {
                    RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Active-weapon set failed", t);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
