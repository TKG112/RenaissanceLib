package net.tkg.RenaissanceLib.attachment;

/**
 * Lets us set an {@link com.tacz.guns.entity.EntityKineticBullet}'s explosion parameters directly after
 * spawning it. TaC:Z's bullet reads its explosion from the firing gun's attachment-property cache
 * ({@code ExplosionModifier}), not from the {@code BulletData} passed to the constructor — so a bullet we
 * spawn on a host rifle inherits the rifle's (no) explosion. We override those fields with the underbarrel's
 * own {@code ExplosionData} instead. Woven by {@code EntityKineticBulletMixin}.
 */
public interface IUnderbarrelBullet {
    void renaissance$setExplosion(boolean explode);

    void renaissance$setExplosionDamage(float damage);

    void renaissance$setExplosionRadius(float radius);

    void renaissance$setExplosionKnockback(boolean knockback);

    void renaissance$setExplosionDestroyBlock(boolean destroy);
}
