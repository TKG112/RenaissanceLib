package net.tkg.RenaissanceLib.client.input;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.FireSelectKey;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.attachment.UnderbarrelFireMode;
import net.tkg.RenaissanceLib.client.FireModeWheel;
import net.tkg.RenaissanceLib.network.ClientMessageCycleUnderbarrelFireMode;
import net.tkg.RenaissanceLib.network.NetworkHandler;

/**
 * Drives the fire-select key with a tap-vs-hold split (TaC:Z's own press handling is cancelled in
 * {@code FireSelectKeyMixin}, so this is the sole handler):
 * <ul>
 *   <li><b>Tap</b> (released before {@link #HOLD_MS}): cycle the active weapon's fire mode — exactly the
 *       normal behaviour (host via TaC:Z's own operator, so it stays binary-aware; underbarrel via its own
 *       cycle).</li>
 *   <li><b>Hold</b> ({@link #HOLD_MS}+): open the {@link FireModeWheel} to pick a mode; releasing confirms
 *       the pointed one.</li>
 * </ul>
 * Uses TaC:Z's own {@link FireSelectKey#FIRE_SELECT_KEY} mapping, so it follows the player's fire-select bind.
 */
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class FireSelectInput {

    /** Hold at least this long to open the radial; a shorter press just cycles. */
    private static final long HOLD_MS = 250L;

    private static boolean wasDown = false;
    private static long downTimeMs = 0L;
    private static boolean holdConsumed = false;

    private FireSelectInput() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        // Close the wheel if it can no longer be shown or the player switched items.
        if (FireModeWheel.isOpen()) {
            if (mc.player == null || mc.screen != null
                    || mc.player.getInventory().selected != FireModeWheel.openSlot()) {
                FireModeWheel.close();
            }
        }

        boolean down = !FireSelectKey.FIRE_SELECT_KEY.isUnbound() && FireSelectKey.FIRE_SELECT_KEY.isDown();
        long now = System.currentTimeMillis();

        if (down && !wasDown) {
            downTimeMs = now;
            holdConsumed = false;
        } else if (down && wasDown) {
            if (!holdConsumed && (now - downTimeMs) >= HOLD_MS) {
                holdConsumed = true; // the press became a hold — release won't cycle
                FireModeWheel.tryOpen(); // opens only if the active weapon has 2+ modes
            }
        } else if (!down && wasDown) {
            if (FireModeWheel.isOpen()) {
                FireModeWheel.confirm();
            } else if (!holdConsumed) {
                cycle(mc);
            }
        }
        wasDown = down;
    }

    /** Normal single-step cycle of the active weapon's fire mode. */
    private static void cycle(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;
        ItemStack gun = player.getMainHandItem();
        if (IGun.getIGunOrNull(gun) == null) return;

        if (ActiveWeapon.isUnderbarrelActive(gun)) {
            GunData ubData = Underbarrel.getUnderbarrelData(Underbarrel.getInstalledUnderbarrel(gun));
            if (ubData != null) {
                UnderbarrelFireMode.cycle(gun, ubData); // client prediction
                NetworkHandler.CHANNEL.sendToServer(new ClientMessageCycleUnderbarrelFireMode());
                // Host fireSelect() plays the change click; the underbarrel path is ours, so play it here.
                TimelessAPI.getGunDisplay(gun).ifPresent(display -> SoundPlayManager.playFireSelectSound(player, display));
            }
        } else if (IGun.mainHandHoldGun(player)) {
            // Host gun: TaC:Z's own fire-select (server-side, made binary-aware by ModernKineticGunItemMixin).
            IClientPlayerGunOperator.fromLocalPlayer(player).fireSelect();
        }
    }
}
