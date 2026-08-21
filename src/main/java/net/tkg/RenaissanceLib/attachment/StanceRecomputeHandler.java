package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recomputes a gun's attachment property cache when the player's stance changes, so a
 * state's {@code require} condition (e.g. prone) turns its effect on and off live.
 *
 * <p>Only stance changes need this. Gun swaps and attachment changes already trigger TaC:Z's
 * own recompute, and the property-event mixin supplies the shooter there — so the condition
 * is evaluated correctly in those cases without any help from here.
 *
 * <p>Runs on both sides via {@code PlayerTickEvent}: the client cache drives recoil, the
 * server cache drives spread. The per-tick cost is one cheap stance read that early-outs
 * unless the stance actually changed.
 */
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID)
public class StanceRecomputeHandler {
    private static final Map<UUID, Integer> CLIENT_LAST = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SERVER_LAST = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;

        boolean client = player.level().isClientSide;
        Map<UUID, Integer> lastMap = client ? CLIENT_LAST : SERVER_LAST;

        int sig = stanceSignature(player);
        Integer last = lastMap.put(player.getUUID(), sig);

        // Unchanged, or first observation: nothing to recompute.
        if (last == null || last == sig) return;

        try {
            ItemStack gunItem = player.getMainHandItem();
            if (IGun.getIGunOrNull(gunItem) == null) return;
            if (!hasConditionalState(gunItem)) return;
            AttachmentPropertyManager.postChangeEvent(player, gunItem);
        } catch (Throwable t) {
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Stance recompute failed", t);
        }
    }

    /** Cheap bitmask of the stance dimensions any condition can test. */
    private static int stanceSignature(Player player) {
        int sig = 0;
        if (player.getPose() == net.minecraft.world.entity.Pose.SWIMMING && !player.isInWater()) sig |= 1;
        if (player.isCrouching()) sig |= 2;
        return sig;
    }

    /** Does the held gun have any attachment whose current state declares a require? */
    private static boolean hasConditionalState(ItemStack gunItem) {
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            AttachmentStatesModifier.States states = AttachmentStates.getStates(gunItem, type);
            if (states == null) continue;
            if (!states.getRequire(AttachmentStates.getState(gunItem, type)).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
