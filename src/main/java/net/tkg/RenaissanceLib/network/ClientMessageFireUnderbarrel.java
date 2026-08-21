package net.tkg.RenaissanceLib.network;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExplosionData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.tkg.RenaissanceLib.attachment.UnderbarrelCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.IUnderbarrelBullet;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAmmo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Client → server: fire the active underbarrel once. The server is authoritative — it re-checks that an
 * underbarrel is installed and active, enforces the rate of fire (so a spoofed client can't fire faster),
 * then spawns the sub-gun's projectile(s) straight from the parsed {@code underbarrel_data}: a
 * {@link EntityKineticBullet} built with the underbarrel's own {@link GunData}/{@link BulletData}, launched
 * from the shooter's eye. The bullet carries the underbarrel's explosion, so the blast happens on impact
 * via TaC:Z's own projectile logic — we only spawn it.
 *
 * <p>P2 scope: projectile + explosion + rate-of-fire gate + a shoot sound. Ammo/reload are P3; the
 * underbarrel's own muzzle flash, shell, recoil and dedicated sounds are P4 (this uses the host gun's
 * shoot sound for now).
 */
public class ClientMessageFireUnderbarrel {

    /** Server-side last-fire timestamps per player, for the rate-of-fire gate. */
    private static final Map<UUID, Long> LAST_FIRE_MS = new ConcurrentHashMap<>();
    /** Small grace on the server gate so client/server clock jitter doesn't eat legitimate shots. */
    private static final long RATE_GRACE_MS = 30L;
    /** How far the shoot sound carries, in blocks. */
    private static final int SOUND_DISTANCE = 24;

    public ClientMessageFireUnderbarrel() {}

    public static void encode(ClientMessageFireUnderbarrel message, FriendlyByteBuf buf) {
        // No payload: the server reads the player's own gun and look direction.
    }

    public static ClientMessageFireUnderbarrel decode(FriendlyByteBuf buf) {
        return new ClientMessageFireUnderbarrel();
    }

    public static void handle(ClientMessageFireUnderbarrel message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isServer()) {
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                try {
                    fire(player);
                } catch (Throwable t) {
                    RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Underbarrel fire failed", t);
                }
            });
        }
        context.setPacketHandled(true);
    }

    private static void fire(ServerPlayer player) {
        ItemStack gunItem = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return;
        if (!ActiveWeapon.isUnderbarrelActive(gunItem)) return;

        ItemStack underbarrel = Underbarrel.getInstalledUnderbarrel(gunItem);
        GunData ubData = Underbarrel.getUnderbarrelData(underbarrel);
        if (ubData == null) return;
        BulletData bulletData = ubData.getBulletData();
        if (bulletData == null) return;

        Level level = player.level();

        // Apply the underbarrel's own attachments' stat modifiers to its sub-gun data (rpm, inaccuracy, …).
        UnderbarrelCache cache = UnderbarrelCache.compute(gunItem, underbarrel, ubData);

        // Can't fire mid-reload, or with an empty chamber/magazine.
        if (UnderbarrelAmmo.isReloading(gunItem, level)) return;
        int ammo = UnderbarrelAmmo.get(gunItem, ubData);
        if (ammo <= 0) return;

        Integer moddedRpm = cache.get(GunProperties.ROUNDS_PER_MINUTE);
        int rpm = Math.max(1, moddedRpm != null ? moddedRpm : ubData.getRoundsPerMinute());
        long intervalMs = Math.max(50L, 60000L / rpm);
        long now = System.currentTimeMillis();
        Long last = LAST_FIRE_MS.get(player.getUUID());
        if (last != null && now - last < intervalMs - RATE_GRACE_MS) return; // rate-of-fire gate
        LAST_FIRE_MS.put(player.getUUID(), now);

        // Consume one round (authoritative).
        UnderbarrelAmmo.set(gunItem, ammo - 1);

        ResourceLocation gunId = iGun.getGunId(gunItem);
        ResourceLocation gunDisplayId = iGun.getGunDisplayId(gunItem);
        ResourceLocation ammoId = ubData.getAmmoId();
        // Velocity = (speed * globalModifier) / 20, exactly as TaC:Z computes it in ModernKineticGunScriptAPI
        // (the raw "speed" is blocks/second-ish; /20 converts to per-tick). Passing the raw value made the
        // grenade ~20x too fast, so it flew off and detonated out of sight.
        Float moddedSpeed = cache.get(GunProperties.AMMO_SPEED);
        float baseSpeed = moddedSpeed != null ? moddedSpeed : bulletData.getSpeed();
        float velocity = (float) (baseSpeed * AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER.get()) / 20.0f;
        InaccuracyType inaccuracyType = InaccuracyType.getInaccuracyType(player);
        java.util.Map<InaccuracyType, Float> moddedInaccuracy = cache.get(GunProperties.INACCURACY);
        float inaccuracy = (moddedInaccuracy != null && moddedInaccuracy.containsKey(inaccuracyType))
                ? moddedInaccuracy.get(inaccuracyType)
                : ubData.getInaccuracy(inaccuracyType);
        int amount = Math.max(1, bulletData.getBulletAmount());
        boolean tracer = bulletData.hasTracerAmmo();

        ExplosionData explosion = bulletData.getExplosionData();
        for (int i = 0; i < amount; i++) {
            EntityKineticBullet bullet = new EntityKineticBullet(
                    level, player, gunItem, ammoId, gunId, gunDisplayId, tracer, ubData, bulletData);
            bullet.applyShotgunDamageSpread(amount);
            bullet.setShotDamageMultiplier(1.0f);
            // The bullet derives its explosion from the host gun's property cache (no explosion) — override
            // it with the underbarrel's own ExplosionData so grenades actually detonate.
            if (explosion != null && bullet instanceof IUnderbarrelBullet ub) {
                ub.renaissance$setExplosion(explosion.isExplode());
                ub.renaissance$setExplosionDamage(explosion.getDamage());
                ub.renaissance$setExplosionRadius(explosion.getRadius());
                ub.renaissance$setExplosionKnockback(explosion.isKnockback());
                ub.renaissance$setExplosionDestroyBlock(AmmoConfig.EXPLOSIVE_AMMO_DESTROYS_BLOCK.get());
            }
            // Vanilla shootFromRotation(entity, pitch, yaw, roll, velocity, inaccuracy) — the same call
            // TaC:Z's AbstractGunItem.doBulletSpread makes. The bullet ctor already positioned it at the eye.
            bullet.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, velocity, inaccuracy);
            level.addFreshEntity(bullet);
        }

        // 3rd-person sound for nearby players: the underbarrel's own authored shoot/silence sound. The server
        // can't resolve it (client-only display), so it sends the shooter + silenced flag and each client
        // resolves and plays it. The shooter is excluded — it plays its own 1st-person sound locally.
        boolean silenced = cache.isSilenced();
        float pitch = 0.9f + player.getRandom().nextFloat() * 0.125f;
        NetworkHandler.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        player, player.getX(), player.getY(), player.getZ(), SOUND_DISTANCE, level.dimension())),
                new ServerMessageUnderbarrelSound(player.getId(), silenced, 0.8f, pitch, SOUND_DISTANCE));
    }
}
