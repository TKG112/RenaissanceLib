package net.tkg.RenaissanceLib.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelPlayerAnimation;

import java.util.function.Supplier;

/**
 * Server → players tracking the shooter: the shooter started an underbarrel reload, so play the 3rd-person reload
 * body animation on them ({@link UnderbarrelPlayerAnimation}). The shooter plays its own locally when it presses
 * reload, so it isn't a recipient.
 */
public class ServerMessageUnderbarrelReload {
    private final int entityId;

    public ServerMessageUnderbarrelReload(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(ServerMessageUnderbarrelReload message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.entityId);
    }

    public static ServerMessageUnderbarrelReload decode(FriendlyByteBuf buf) {
        return new ServerMessageUnderbarrelReload(buf.readVarInt());
    }

    public static void handle(ServerMessageUnderbarrelReload message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> UnderbarrelPlayerAnimation.onRemoteReload(message.entityId)));
        context.setPacketHandled(true);
    }
}
