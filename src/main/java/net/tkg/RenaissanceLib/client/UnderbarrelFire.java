package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.modifier.ParameterizedCachePair;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunRecoil;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAmmo;
import net.tkg.RenaissanceLib.attachment.UnderbarrelCache;
import net.tkg.RenaissanceLib.attachment.UnderbarrelFireMode;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelRecoil;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelAnimator;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelClient;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelEffects;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelShellRender;
import net.tkg.RenaissanceLib.network.ClientMessageFireUnderbarrel;
import net.tkg.RenaissanceLib.network.NetworkHandler;

/**
 * Client-side trigger for the underbarrel: while it's the active weapon, TaC:Z's normal shoot handling is
 * suppressed ({@code ShootKeyMixin}) and this drives the underbarrel instead — reading the shoot key,
 * honouring the underbarrel's own fire mode and rate of fire, and asking the server to fire (which is
 * authoritative). The client gate is prediction/spam-limiting only.
 *
 * <p>The underbarrel's fire mode is its own (per the design: the radial switches weapons, the fire-mode key
 * cycles the active weapon's modes). For P2 the underbarrel uses the first mode in its {@code fire_mode}
 * list; SEMI/BURST fire once per press, AUTO fires while held. Mode cycling for the underbarrel comes later.
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelFire {

    private static long lastFireMs = 0L;
    private static boolean wasDown = false;

    private UnderbarrelFire() {}

    /** Called once per client tick while the underbarrel is the active weapon. */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            wasDown = false;
            return;
        }
        ItemStack gun = player.getMainHandItem();
        if (IGun.getIGunOrNull(gun) == null) {
            wasDown = false;
            return;
        }
        GunData ubData = Underbarrel.getUnderbarrelData(Underbarrel.getInstalledUnderbarrel(gun));
        if (ubData == null) {
            wasDown = false;
            return;
        }

        boolean down = mc.screen == null && !ShootKey.SHOOT_KEY.isUnbound() && ShootKey.SHOOT_KEY.isDown();

        // Client prediction gate (server is authoritative): don't fire while reloading or empty. Reads the
        // gun NBT, which the server syncs.
        if (UnderbarrelAmmo.isReloading(gun, mc.level) || UnderbarrelAmmo.get(gun, ubData) <= 0) {
            wasDown = down;
            return;
        }

        int rpm = Math.max(1, ubData.getRoundsPerMinute());
        long intervalMs = Math.max(50L, 60000L / rpm);
        long now = System.currentTimeMillis();
        boolean rateReady = now - lastFireMs >= intervalMs;

        boolean auto = UnderbarrelFireMode.get(gun, ubData) == FireMode.AUTO;
        boolean binary = UnderbarrelFireMode.isBinary(gun, ubData);
        boolean pressEdge = down && !wasDown;
        boolean releaseEdge = !down && wasDown;
        // AUTO: fire while held. Otherwise fire on the press edge, and — in binary — also on the release edge.
        boolean trigger = rateReady && (auto ? down : (pressEdge || (binary && releaseEdge)));

        if (trigger) {
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageFireUnderbarrel());
            // The shooter's own (1st-person) shot sound + flash, from the underbarrel's own authored sounds
            // (the server's 3p broadcast excludes the shooter, so play locally). A silencer — one of the
            // underbarrel's own attachments — switches to the silenced sound and hides the muzzle flash.
            ItemStack ub = Underbarrel.getInstalledUnderbarrel(gun);
            boolean silenced = UnderbarrelCache.compute(gun, ub, ubData).isSilenced();
            ResourceLocation sound = UnderbarrelClient.getFireSound(ub, silenced, false);
            if (sound != null) {
                float pitch = 0.9f + player.getRandom().nextFloat() * 0.125f;
                SoundPlayManager.playClientSound(player, sound, 0.8f, pitch, 16);
            }
            applyRecoil(gun, ubData);
            if (!silenced) UnderbarrelEffects.onFire();
            UnderbarrelAnimator.trigger("shoot");
            ejectShell(gun);
            lastFireMs = now;
        }
        wasDown = down;
    }

    /** Eject a spent casing if the underbarrel's display defines shell physics (shotgun-style). */
    private static void ejectShell(ItemStack gun) {
        GunDisplay ubDisplay = UnderbarrelClient.getUnderbarrelDisplay(Underbarrel.getInstalledUnderbarrel(gun));
        if (ubDisplay != null && ubDisplay.getShellEjection() != null) {
            UnderbarrelShellRender.eject(ubDisplay.getShellEjection());
        }
    }

    /**
     * Data-driven recoil, the same as TaC:Z's guns ({@code CameraSetupEvent.initialCameraRecoil}): build
     * pitch/yaw spline functions from the underbarrel's own {@code recoil} data, scaled by its attachments'
     * recoil modifier (from {@link UnderbarrelCache}) evaluated at the recoil factor, and hand them to
     * {@link UnderbarrelRecoil} which eases the camera kick each frame. No {@code recoil} data → no kick.
     *
     * <p>The factor mirrors TaC:Z: the underbarrel never aims down sights, so the hip-fire factor is 1.0
     * (matching a hip-fired gun); crawling — the SWIMMING pose out of water — scales it by the sub-gun's
     * {@code crawl_recoil_multiplier}, exactly as TaC:Z reduces recoil while prone.
     */
    private static void applyRecoil(ItemStack gun, GunData ubData) {
        GunRecoil recoil = ubData.getRecoil();
        if (recoil == null) return;
        double factor = 1.0;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && !player.isSwimming() && player.getPose() == Pose.SWIMMING) {
            factor = ubData.getCrawlRecoilMultiplier();
        }
        ParameterizedCachePair<Float, Float> recoilModifier =
                UnderbarrelCache.compute(gun, Underbarrel.getInstalledUnderbarrel(gun), ubData)
                        .get(GunProperties.RECOIL);
        float pitchModifier = recoilModifier != null ? (float) recoilModifier.left().eval(factor) : (float) factor;
        float yawModifier = recoilModifier != null ? (float) recoilModifier.right().eval(factor) : (float) factor;
        UnderbarrelRecoil.trigger(recoil.genPitchSplineFunction(pitchModifier), recoil.genYawSplineFunction(yawModifier));
    }
}
