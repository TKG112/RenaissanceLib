package net.tkg.RenaissanceLib.network;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.AttachmentOverrides;
import net.tkg.RenaissanceLib.attachment.BinaryFireMode;

import java.util.function.Supplier;

/**
 * Sets the <em>host gun's</em> fire mode to a specific value (with the binary flag), for the fire-mode radial
 * wheel — TaC:Z natively only cycles, so picking a mode directly needs this. Server-authoritative: the mode
 * must be one the gun actually offers (its attachment-effective modes), and binary only if the gun is
 * binary-capable; otherwise the packet is ignored.
 */
public class ClientMessageSetFireMode {
    private final FireMode mode;
    private final boolean binary;

    public ClientMessageSetFireMode(FireMode mode, boolean binary) {
        this.mode = mode;
        this.binary = binary;
    }

    public static void encode(ClientMessageSetFireMode message, FriendlyByteBuf buf) {
        buf.writeEnum(message.mode);
        buf.writeBoolean(message.binary);
    }

    public static ClientMessageSetFireMode decode(FriendlyByteBuf buf) {
        return new ClientMessageSetFireMode(buf.readEnum(FireMode.class), buf.readBoolean());
    }

    public static void handle(ClientMessageSetFireMode message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                try {
                    ItemStack gun = player.getMainHandItem();
                    IGun iGun = IGun.getIGunOrNull(gun);
                    if (iGun == null) return;

                    GunData gunData = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                            .map(index -> index.getGunData()).orElse(null);
                    if (gunData == null) return;

                    if (!AttachmentOverrides.effectiveFireModes(gun, gunData).contains(message.mode)) return;
                    if (message.binary && !AttachmentOverrides.isBinaryCapable(gun, gunData)) return;

                    iGun.setFireMode(gun, message.mode);
                    BinaryFireMode.setActive(gun, message.binary);
                } catch (Throwable t) {
                    RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Set-fire-mode failed", t);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
