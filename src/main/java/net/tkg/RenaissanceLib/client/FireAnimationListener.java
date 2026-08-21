package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.event.common.GunFireEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;

/**
 * Triggers fire-reaction attachment animations when the host gun fires. Subscribes to TaC:Z's
 * {@link GunFireEvent} — posted once per <em>round</em> on the client — so full-auto cycles the clip each
 * shot. Gated to the local player in first person (v1); the shared attachment controllers are driven by
 * {@link AttachmentAnimationManager#onGunFire(net.minecraft.world.item.ItemStack)}.
 */
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class FireAnimationListener {
    private FireAnimationListener() {}

    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getShooter() != mc.player) return;              // local player only (shared model)
        if (!mc.options.getCameraType().isFirstPerson()) return;  // first-person only (v1)
        try {
            AttachmentAnimationManager.onGunFire(event.getGunItemStack());
        } catch (Throwable t) {
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Fire-reaction animation trigger failed", t);
        }
    }
}
