package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.RecoilSpeedModifier;

/**
 * The {@code recoil_speed} of the local player's gun, taken at each shot — TaC:Z also builds the recoil curve at
 * the shot, so a mid-recoil weapon swap doesn't change the speed of the kick already playing.
 * {@code CameraRecoilSpeedMixin} reads it.
 */
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class RecoilSpeed {
    private static float shotSpeed = 1f;

    private RecoilSpeed() {}

    /** The multiplier for the recoil currently playing (1 = as authored). */
    public static float current() {
        return shotSpeed;
    }

    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.getLogicalSide().isClient() || mc.player == null || event.getShooter() != mc.player) return;
        try {
            var cache = IGunOperator.fromLivingEntity(mc.player).getCacheProperty();
            Object speed = cache == null ? null : cache.getCache(RecoilSpeedModifier.ID);
            shotSpeed = speed instanceof Float f && Float.isFinite(f) && f > 0f ? f : 1f;
        } catch (Throwable t) {
            shotSpeed = 1f;
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Reading recoil_speed failed", t);
        }
    }
}
