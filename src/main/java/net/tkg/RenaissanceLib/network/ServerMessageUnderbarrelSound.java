package net.tkg.RenaissanceLib.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelSoundClient;

import java.util.function.Supplier;

/**
 * Server → nearby clients: play a shooter's underbarrel fire sound for the 3rd-person listeners.
 *
 * <p>The underbarrel's authored sounds live in its <em>client</em> display, which the server can't resolve
 * (it has no registered gun-display id — unlike a normal gun). So the server sends only the shooter's entity
 * id and whether the shot was silenced; each receiving client reads the shooter's (synced) held gun, resolves
 * the underbarrel's own {@code shoot_3p}/{@code silence_3p} sound and plays it positioned at the shooter. The
 * shooter itself is excluded from the send — it plays its 1st-person sound locally.
 */
public class ServerMessageUnderbarrelSound {
    private final int entityId;
    private final boolean silenced;
    private final float volume;
    private final float pitch;
    private final int distance;

    public ServerMessageUnderbarrelSound(int entityId, boolean silenced, float volume, float pitch, int distance) {
        this.entityId = entityId;
        this.silenced = silenced;
        this.volume = volume;
        this.pitch = pitch;
        this.distance = distance;
    }

    public int entityId() {
        return entityId;
    }

    public boolean silenced() {
        return silenced;
    }

    public float volume() {
        return volume;
    }

    public float pitch() {
        return pitch;
    }

    public int distance() {
        return distance;
    }

    public static void encode(ServerMessageUnderbarrelSound message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.entityId);
        buf.writeBoolean(message.silenced);
        buf.writeFloat(message.volume);
        buf.writeFloat(message.pitch);
        buf.writeVarInt(message.distance);
    }

    public static ServerMessageUnderbarrelSound decode(FriendlyByteBuf buf) {
        return new ServerMessageUnderbarrelSound(
                buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    public static void handle(ServerMessageUnderbarrelSound message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> UnderbarrelSoundClient.play(message)));
        context.setPacketHandled(true);
    }
}
