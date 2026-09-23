package net.tkg.RenaissanceLib.network;

import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAmmo;

import java.util.function.Supplier;

/**
 * Client → server: reload the active underbarrel. Server-authoritative — it validates the underbarrel is
 * installed and active, isn't already reloading and isn't full, then pulls matching ammo ({@code IAmmo}
 * with the underbarrel's {@code ammo} id) from the player's inventory, tops the magazine up, and starts a
 * reload window ({@link UnderbarrelAmmo}) during which the underbarrel can't fire.
 *
 * <p>P3 scope: consumes plain ammo item stacks from the main inventory. Ammo boxes are a later addition.
 */
public class ClientMessageReloadUnderbarrel {

    public ClientMessageReloadUnderbarrel() {}

    public static void encode(ClientMessageReloadUnderbarrel message, FriendlyByteBuf buf) {
    }

    public static ClientMessageReloadUnderbarrel decode(FriendlyByteBuf buf) {
        return new ClientMessageReloadUnderbarrel();
    }

    public static void handle(ClientMessageReloadUnderbarrel message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                try {
                    reload(player);
                } catch (Throwable t) {
                    RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Underbarrel reload failed", t);
                }
            });
        }
        context.setPacketHandled(true);
    }

    private static void reload(ServerPlayer player) {
        ItemStack gunItem = player.getMainHandItem();
        if (IGun.getIGunOrNull(gunItem) == null) return;
        if (!ActiveWeapon.isUnderbarrelActive(gunItem)) return;

        GunData ubData = Underbarrel.getUnderbarrelData(Underbarrel.getInstalledUnderbarrel(gunItem));
        if (ubData == null) return;

        Level level = player.level();
        if (UnderbarrelAmmo.isReloading(gunItem, level)) return;

        int current = UnderbarrelAmmo.get(gunItem, ubData);
        int max = UnderbarrelAmmo.maxAmmo(gunItem, ubData);
        if (current >= max) return; // already full

        ResourceLocation ammoId = ubData.getAmmoId();
        if (ammoId == null) return;

        int consumed = consumeFromInventory(player, ammoId, max - current);
        if (consumed <= 0) return; // no matching ammo

        // Two-phase: rounds load at the feed time (shell-by-shell scales with the deficit); firing unlocks at the
        // reload's total lockout. The loaded count is set now (authoritative) but stays hidden by getDisplay
        // and unusable by isReloading until those elapse.
        int feedTicks = UnderbarrelAmmo.feedTicks(gunItem, ubData, current);
        int lockTicks = UnderbarrelAmmo.lockTicks(gunItem, ubData, current);
        UnderbarrelAmmo.set(gunItem, current + consumed);
        UnderbarrelAmmo.startReload(gunItem, level, feedTicks, lockTicks, current);
    }

    /**
     * Removes up to {@code count} rounds of {@code ammoId} from the player; returns how many were obtained.
     * Sources, in priority order: creative mode (unlimited), a creative ammo box (unlimited), loose ammo item
     * stacks, then normal ammo boxes.
     */
    private static int consumeFromInventory(ServerPlayer player, ResourceLocation ammoId, int count) {
        // Creative players reload freely.
        if (player.getAbilities().instabuild) return count;

        Inventory inventory = player.getInventory();
        int size = inventory.getContainerSize();

        // A creative ammo box of the right type (or an all-type creative box) is an unlimited source.
        for (int i = 0; i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof IAmmoBox box
                    && (box.isAllTypeCreative(stack)
                    || (box.isCreative(stack) && ammoId.equals(box.getAmmoId(stack))))) {
                return count;
            }
        }

        // Loose ammo item stacks first.
        int remaining = count;
        for (int i = 0; i < size && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            IAmmo iAmmo = IAmmo.getIAmmoOrNull(stack);
            if (iAmmo != null && ammoId.equals(iAmmo.getAmmoId(stack))) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }

        // Then draw from (non-creative) ammo boxes.
        for (int i = 0; i < size && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof IAmmoBox box)) continue;
            if (!ammoId.equals(box.getAmmoId(stack))) continue;
            int available = box.getAmmoCount(stack);
            if (available <= 0) continue;
            int take = Math.min(remaining, available);
            box.setAmmoCount(stack, available - take);
            remaining -= take;
        }
        return count - remaining;
    }
}
