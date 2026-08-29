package net.tkg.RenaissanceLib.client.underbarrel;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.network.ClientMessageSetActiveWeapon;
import net.tkg.RenaissanceLib.network.NetworkHandler;

/**
 * Borrows the host gun to inspect it while the underbarrel is the active weapon, then hands control back.
 *
 * <p>When the underbarrel is up, its rig owns the left arm, so the host gun's inspect animation would play
 * with a frozen support hand. So the inspect key (see {@code InspectKeyMixin}) temporarily switches the active
 * weapon back to the host — letting TaC:Z's own inspect play in full — and this handler watches the host's
 * animation state and re-arms the underbarrel the moment the inspect finishes.
 *
 * <p>Robust to the inspect never starting (state-locked) via a start timeout, and to the player swapping guns
 * or manually re-arming the underbarrel mid-inspect (it simply stops watching).
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class UnderbarrelInspectHandler {

    private static final int PHASE_IDLE = 0;
    private static final int PHASE_WAIT_START = 1; // switched to host, waiting for the inspect anim to begin
    private static final int PHASE_PLAYING = 2;    // inspect anim is playing, waiting for it to end

    private static final int START_TIMEOUT_TICKS = 20; // if inspect never starts, give the underbarrel back
    private static final int MAX_PLAY_TICKS = 400;      // hard safety cap on a stuck "playing" state

    private static int phase = PHASE_IDLE;
    private static int ticks = 0;

    private UnderbarrelInspectHandler() {}

    /**
     * Called after the inspect key switched the active weapon to the host: begin watching so the underbarrel is
     * re-armed when the host inspect finishes. No-op if already watching.
     */
    public static void beginHostInspect() {
        if (phase != PHASE_IDLE) return;
        phase = PHASE_WAIT_START;
        ticks = 0;
    }

    private static void reset() {
        phase = PHASE_IDLE;
        ticks = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase == PHASE_IDLE) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        try {
            if (player == null) { reset(); return; }

            ItemStack gun = player.getMainHandItem();
            IGun iGun = IGun.getIGunOrNull(gun);
            // Gun swapped away, or the underbarrel is gone: nothing to hand back to.
            if (iGun == null || !Underbarrel.hasUnderbarrel(gun)) { reset(); return; }
            // Player already re-armed the underbarrel themselves — stop watching, don't fight them.
            if (ActiveWeapon.isUnderbarrelActive(gun)) { reset(); return; }

            boolean inspecting = isHostInspecting(gun);
            ticks++;

            if (phase == PHASE_WAIT_START) {
                if (inspecting) {
                    phase = PHASE_PLAYING;
                    ticks = 0;
                } else if (ticks >= START_TIMEOUT_TICKS) {
                    // Inspect never started (state-locked, cancelled, etc.) — restore the underbarrel.
                    restoreUnderbarrel(gun);
                }
            } else { // PHASE_PLAYING
                if (!inspecting || ticks >= MAX_PLAY_TICKS) {
                    restoreUnderbarrel(gun);
                }
            }
        } catch (Throwable t) {
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] underbarrel inspect hand-back failed", t);
            reset();
        }
    }

    /**
     * True while the host gun's first-person inspect clip is running. TaC:Z's newer Lua state machine no longer
     * exposes an {@code isPlayingInspectAnimation()}, so we read the display's animation controller directly and
     * look for a running clip named "inspect" on any updating track.
     */
    private static boolean isHostInspecting(ItemStack gun) {
        GunDisplayInstance display = TimelessAPI.getGunDisplay(gun).orElse(null);
        if (display == null) return false;
        LuaAnimationStateMachine<?> sm = display.getAnimationStateMachine();
        if (sm == null) return false;
        AnimationController controller = sm.getAnimationController();
        if (controller == null || controller.getUpdatingTrackArray() == null) return false;
        for (Integer track : controller.getUpdatingTrackArray()) {
            if (track == null) continue;
            ObjectAnimationRunner runner = controller.getAnimation(track);
            if (runner == null || !runner.isRunning()) continue;
            ObjectAnimation anim = runner.getAnimation();
            if (anim != null && anim.name != null && anim.name.toLowerCase().contains("inspect")) {
                return true;
            }
        }
        return false;
    }

    private static void restoreUnderbarrel(ItemStack gun) {
        ActiveWeapon.set(gun, ActiveWeapon.UNDERBARREL); // client prediction; server re-validates
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetActiveWeapon(ActiveWeapon.UNDERBARREL));
        reset();
    }
}
