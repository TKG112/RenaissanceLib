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
import net.tkg.RenaissanceLib.attachment.RailsModifier;
import net.tkg.RenaissanceLib.attachment.ScopeRails;

import java.util.List;
import java.util.function.Supplier;

/**
 * Installs or clears an optic at a rail {@link MountPath}, server-authoritatively.
 *
 * <p>{@code inventorySlot >= 0}: take one optic (a scope-type attachment) from that inventory slot and
 * mount it at {@code path}. {@code inventorySlot == -1}: clear the mount at {@code path}, returning the
 * mounted optic (and anything mounted on it) to the player's inventory.
 *
 * <p>The server validates that every parent hop along the path is actually mounted and every slot index
 * is in range for its host's declared rails, then writes through {@link RailStorage#setMounted}.
 */
public class ClientMessageSetRailSight {
    public static final int CLEAR = -1;

    private final AttachmentType hostType;
    private final int[] path;
    private final int inventorySlot;

    public ClientMessageSetRailSight(MountPath path, int inventorySlot) {
        this.hostType = path.hostType();
        this.path = path.toArray();
        this.inventorySlot = inventorySlot;
    }

    private ClientMessageSetRailSight(AttachmentType hostType, int[] path, int inventorySlot) {
        this.hostType = hostType;
        this.path = path;
        this.inventorySlot = inventorySlot;
    }

    public static void encode(ClientMessageSetRailSight message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.hostType.ordinal());
        buf.writeVarInt(message.path.length);
        for (int i : message.path) buf.writeVarInt(i);
        buf.writeInt(message.inventorySlot);
    }

    public static ClientMessageSetRailSight decode(FriendlyByteBuf buf) {
        AttachmentType hostType = readHostType(buf);
        int len = buf.readVarInt();
        int[] path = new int[Math.max(0, Math.min(len, 16))]; // cap depth defensively
        for (int i = 0; i < len; i++) {
            int v = buf.readVarInt();
            if (i < path.length) path[i] = v;
        }
        return new ClientMessageSetRailSight(hostType, path, buf.readInt());
    }

    private static AttachmentType readHostType(FriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        AttachmentType[] values = AttachmentType.values();
        return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : AttachmentType.SCOPE;
    }

    public static void handle(ClientMessageSetRailSight message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                try {
                    ItemStack gunItem = player.getMainHandItem();
                    if (IGun.getIGunOrNull(gunItem) == null) return;

                    MountPath path = MountPath.of(message.hostType, message.path);
                    if (path.isRoot() || !isValidPath(gunItem, path)) return;

                    if (message.inventorySlot == CLEAR) {
                        ItemStack mounted = RailStorage.getMountedOnGun(gunItem, path);
                        if (!mounted.isEmpty() && !player.getInventory().add(mounted)) {
                            player.drop(mounted, false);
                        }
                        RailStorage.setMounted(gunItem, path, ItemStack.EMPTY);
                        return;
                    }

                    ItemStack optic = player.getInventory().getItem(message.inventorySlot);
                    IAttachment attachment = IAttachment.getIAttachmentOrNull(optic);
                    if (attachment == null) return;
                    // Coarse type authorization against the target slot's allow categories (the scope/sight
                    // refinement is client display data, enforced in the picker UI), plus its allow_attachments.
                    RailsModifier.RailSlot target = targetSlot(gunItem, path);
                    if (target == null
                            || !ScopeRails.typeAllowed(target.getAllow(), attachment.getType(optic), path.hostType())
                            || !target.acceptsAttachment(attachment.getAttachmentId(optic))) return;

                    ItemStack previous = RailStorage.getMountedOnGun(gunItem, path);
                    if (!previous.isEmpty() && !player.getInventory().add(previous)) {
                        player.drop(previous, false);
                    }
                    if (RailStorage.setMounted(gunItem, path, optic)) {
                        optic.shrink(1);
                    }
                } catch (Throwable t) {
                    RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Rail-sight set failed", t);
                }
            });
        }
        context.setPacketHandled(true);
    }

    /**
     * Whether {@code path} names a real slot: every parent hop is mounted and every slot index is in
     * range for its host's declared rails. Walks the tree via the common data (server-safe).
     */
    private static boolean isValidPath(ItemStack gunItem, MountPath path) {
        ItemStack host = RailStorage.getHostItem(gunItem, path.hostType());
        if (host.isEmpty()) return false;
        for (int d = 0; d < path.depth(); d++) {
            int slot = path.get(d);
            List<RailsModifier.RailSlot> slots = slotsOf(gunItem, path.hostType(), host, d == 0);
            if (slot < 0 || slot >= slots.size()) return false;
            if (d < path.depth() - 1) {
                host = RailStorage.getRailSightFromAttachment(host, slot);
                if (host.isEmpty()) return false; // parent hop not mounted
            }
        }
        return true;
    }

    /** Rail slots of a host: the gun's host-slot spec at the root, else the mounted optic's own spec. */
    private static List<RailsModifier.RailSlot> slotsOf(ItemStack gunItem, AttachmentType hostType,
                                                        ItemStack host, boolean root) {
        RailsModifier.Spec spec = root ? ScopeRails.getRailsSpecForType(gunItem, hostType)
                : ScopeRails.getRailsSpecForAttachment(host);
        return spec == null ? List.of() : spec.getSlots();
    }

    /** The rail slot {@code path} targets, or {@code null} if unresolvable. */
    private static RailsModifier.RailSlot targetSlot(ItemStack gunItem, MountPath path) {
        ItemStack host = RailStorage.getHostItem(gunItem, path.hostType());
        if (host.isEmpty()) return null;
        for (int d = 0; d < path.depth() - 1; d++) {
            host = RailStorage.getRailSightFromAttachment(host, path.get(d));
            if (host.isEmpty()) return null;
        }
        List<RailsModifier.RailSlot> slots = slotsOf(gunItem, path.hostType(), host, path.depth() == 1);
        int slot = path.last();
        if (slot < 0 || slot >= slots.size()) return null;
        return slots.get(slot);
    }
}
