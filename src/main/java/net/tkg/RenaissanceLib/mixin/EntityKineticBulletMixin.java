package net.tkg.RenaissanceLib.mixin;

import com.tacz.guns.entity.EntityKineticBullet;
import net.tkg.RenaissanceLib.attachment.IUnderbarrelBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link EntityKineticBullet}'s explosion fields so the underbarrel fire path can override them with
 * the underbarrel's own {@code ExplosionData} after spawning. See {@link IUnderbarrelBullet} for why: the
 * bullet otherwise derives its explosion from the firing gun's property cache, which for an underbarrel is
 * the host gun's (no explosion).
 */
@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class EntityKineticBulletMixin implements IUnderbarrelBullet {

    @Accessor("explosion")
    @Override
    public abstract void renaissance$setExplosion(boolean explode);

    @Accessor("explosionDamage")
    @Override
    public abstract void renaissance$setExplosionDamage(float damage);

    @Accessor("explosionRadius")
    @Override
    public abstract void renaissance$setExplosionRadius(float radius);

    @Accessor("explosionKnockback")
    @Override
    public abstract void renaissance$setExplosionKnockback(boolean knockback);

    @Accessor("explosionDestroyBlock")
    @Override
    public abstract void renaissance$setExplosionDestroyBlock(boolean destroy);
}
