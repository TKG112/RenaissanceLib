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
import net.tkg.RenaissanceLib.attachment.MountPath;
import net.tkg.RenaissanceLib.attachment.ToggleTarget;

import java.util.function.Supplier;

/**
 * Client → server: toggle the attachment at a {@link ToggleTarget} (slot + rail {@link MountPath}) to its
 * next state. The server re-resolves and cycles the state authoritatively. An empty path is a top-level
 * slot (backward-compatible with the pre-Stage-2 slot-only message).
 */
public class ClientMessageToggleAttachment {
    private final AttachmentType type;
    private final int[] path;
    /** Combined-cycle zoom number to apply, or {@code -1} to leave it to the server's own zoom handling. */
    private final int zoomNumber;

    public ClientMessageToggleAttachment(AttachmentType type) {
        this(type, new int[0], -1);
    }

    public ClientMessageToggleAttachment(AttachmentType type, int[] path, int zoomNumber) {
        this.type = type;
        this.path = path == null ? new int[0] : path;
        this.zoomNumber = zoomNumber;
    }

    public static void encode(ClientMessageToggleAttachment message, FriendlyByteBuf buf) {
        buf.writeEnum(message.type);
        buf.writeVarInt(message.path.length);
        for (int index : message.path) {
            buf.writeVarInt(index);
        }
        buf.writeVarInt(message.zoomNumber + 1); // +1 so -1 (none) stays a small non-negative varint
    }

    public static ClientMessageToggleAttachment decode(FriendlyByteBuf buf) {
        AttachmentType type = buf.readEnum(AttachmentType.class);
        int length = buf.readVarInt();
        int[] path = new int[length];
        for (int i = 0; i < length; i++) {
            path[i] = buf.readVarInt();
        }
        int zoomNumber = buf.readVarInt() - 1;
        return new ClientMessageToggleAttachment(type, path, zoomNumber);
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

                    ToggleTarget target = ToggleTarget.of(type, MountPath.of(message.path));

                    String newState = AttachmentStates.cycleState(gunItem, target);
                    if (newState == null) return;

                    // Apply the same view/zoom the client resolved (so the item sync doesn't revert it). For
                    // rail optics the client sends the combined-cycle position; the top-level scope falls back
                    // to its own zoom_index handling.
                    if (message.zoomNumber >= 0) {
                        AttachmentStates.setScopeZoomNumber(gunItem, message.zoomNumber);
                    } else if (target.isRoot()) {
                        AttachmentStates.applyZoomForState(gunItem, type, newState);
                    }

                    AttachmentPropertyManager.postChangeEvent(player, gunItem);
                } catch (Throwable t) {
                    RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Attachment toggle failed", t);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
