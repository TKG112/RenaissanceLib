package net.tkg.RenaissanceLib.network;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.attachment.UnderbarrelFireMode;

import java.util.function.Supplier;

/**
 * Client → server: cycle the active underbarrel's fire mode. The server advances and persists the selection
 * ({@link UnderbarrelFireMode}) so it syncs back and survives relog.
 */
public class ClientMessageCycleUnderbarrelFireMode {

    public ClientMessageCycleUnderbarrelFireMode() {}

    public static void encode(ClientMessageCycleUnderbarrelFireMode message, FriendlyByteBuf buf) {
    }

    public static ClientMessageCycleUnderbarrelFireMode decode(FriendlyByteBuf buf) {
        return new ClientMessageCycleUnderbarrelFireMode();
    }

    public static void handle(ClientMessageCycleUnderbarrelFireMode message, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                try {
                    ItemStack gunItem = player.getMainHandItem();
                    if (IGun.getIGunOrNull(gunItem) == null) return;
                    if (!ActiveWeapon.isUnderbarrelActive(gunItem)) return;
                    GunData ubData = Underbarrel.getUnderbarrelData(Underbarrel.getInstalledUnderbarrel(gunItem));
                    if (ubData == null) return;
                    UnderbarrelFireMode.cycle(gunItem, ubData);
                } catch (Throwable t) {
                    RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Underbarrel fire-mode cycle failed", t);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
