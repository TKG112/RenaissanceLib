package net.tkg.RenaissanceLib.attachment;

import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * Carries the shooter through TaC:Z's {@code AttachmentPropertyEvent}, which exposes the
 * gun item and the cache but not the entity holding it.
 *
 * <p>A mixin on {@code AttachmentPropertyManager.postChangeEvent} sets this at HEAD and
 * clears it at RETURN. Because the event is posted synchronously on the same thread, our
 * event handler reads the correct shooter for the recompute in progress. Needed so a
 * state's {@code require} condition (e.g. prone) can be checked against the actual player.
 */
public final class PropertyEventShooter {
    private static final ThreadLocal<LivingEntity> CURRENT = new ThreadLocal<>();

    private PropertyEventShooter() {}

    public static void set(LivingEntity shooter) {
        CURRENT.set(shooter);
    }

    public static void clear() {
        CURRENT.remove();
    }

    @Nullable
    public static LivingEntity get() {
        return CURRENT.get();
    }
}
