package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.AttachmentStates;
import net.tkg.RenaissanceLib.attachment.AttachmentStatesModifier;
import net.tkg.RenaissanceLib.attachment.ToggleTarget;
import net.tkg.RenaissanceLib.network.ClientMessageToggleAttachment;
import net.tkg.RenaissanceLib.network.NetworkHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * The single client-side toggle action, shared by the attachment wheel. Advances a target's state,
 * applies its zoom, plays its animation, starts its cooldown, and notifies the server (which re-validates
 * and applies authoritatively). Extracted from the old per-slot key handler so all toggle entry points
 * share one path.
 *
 * <p>Stage 1 operates on top-level ({@link ToggleTarget#isRoot() root}) targets, keyed by
 * {@link AttachmentType}. Stage 2 will make the state lookups path-aware.
 */
@OnlyIn(Dist.CLIENT)
public final class AttachmentToggle {

    private static final Map<ToggleTarget, Long> COOLDOWN_UNTIL = new HashMap<>();

    private AttachmentToggle() {}

    /** Whether this target is within its post-toggle cooldown window right now. */
    public static boolean isOnCooldown(ToggleTarget target) {
        Long until = COOLDOWN_UNTIL.get(target);
        return until != null && clientTime() < until;
    }

    /**
     * Toggle the target's attachment to its next state (client-side prediction + server notify).
     * No-op if the target has no ≥2-state block, or is on cooldown. Works for both top-level slots and
     * rail-mounted (path) targets.
     */
    public static void toggle(ToggleTarget target) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        ItemStack gunItem = player.getMainHandItem();
        if (IGun.getIGunOrNull(gunItem) == null) return;

        AttachmentStatesModifier.States states = AttachmentStates.getStates(gunItem, target);
        if (states == null || states.getCycle().size() < 2) return;

        long now = mc.level.getGameTime();
        Long until = COOLDOWN_UNTIL.get(target);
        if (until != null && now < until) return;

        String next = states.next(AttachmentStates.getState(gunItem, target));
        AttachmentStates.setState(gunItem, target, next);

        // Switch the view/zoom to match the new state. For the top-level scope, its zoom_index maps directly
        // onto the scope's zoom counter. For a rail-mounted optic, the state's zoom_index is a LOCAL view of
        // that optic — translate it into the shared combined-cycle position (ActiveOptic) so toggling the
        // sight also switches the player to look through it at that view, in sync with the animation. The
        // resolved position is sent to the server too, so the item sync doesn't revert the client's change.
        int zoomNumberForPacket = -1;
        if (target.isRoot()) {
            AttachmentStates.applyZoomForState(gunItem, target.slot(), next);
        } else {
            Integer zoomIndex = states.getZoomIndex(next);
            if (zoomIndex != null) {
                int globalPos = ActiveOptic.globalPosFor(gunItem, target.path(), zoomIndex);
                if (globalPos >= 0) {
                    AttachmentStates.setScopeZoomNumber(gunItem, globalPos);
                    zoomNumberForPacket = globalPos;
                }
            }
        }

        String animation = states.getAnimation(next);
        if (animation != null && !animation.isEmpty()) {
            triggerAnimation(gunItem, animation);
        }

        int cooldown = states.getCooldownTicks(next);
        if (cooldown > 0) {
            COOLDOWN_UNTIL.put(target, now + cooldown);
        } else {
            COOLDOWN_UNTIL.remove(target);
        }

        NetworkHandler.CHANNEL.sendToServer(
                new ClientMessageToggleAttachment(target.slot(), target.path().toArray(), zoomNumberForPacket));
    }

    private static long clientTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0L : mc.level.getGameTime();
    }

    private static void triggerAnimation(ItemStack gunItem, String input) {
        try {
            TimelessAPI.getGunDisplay(gunItem).ifPresent(display -> {
                var stateMachine = display.getAnimationStateMachine();
                if (stateMachine != null) {
                    stateMachine.trigger(input);
                }
            });
        } catch (Throwable t) {
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Failed to trigger animation '{}'", input, t);
        }
    }
}
