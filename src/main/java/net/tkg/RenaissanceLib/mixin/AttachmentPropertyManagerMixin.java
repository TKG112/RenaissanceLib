package net.tkg.RenaissanceLib.mixin;

import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.tkg.RenaissanceLib.attachment.PropertyEventShooter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Exposes the shooter to our {@code AttachmentPropertyEvent} handler for the duration of a
 * property recompute, so a state's {@code require} condition can be checked against the
 * player. The event itself only carries the gun item and the cache.
 */
@Mixin(value = AttachmentPropertyManager.class, remap = false)
public class AttachmentPropertyManagerMixin {

    @Inject(method = "postChangeEvent", at = @At("HEAD"), remap = false)
    private static void renaissance$captureShooter(LivingEntity shooter, ItemStack gunItem, CallbackInfo ci) {
        PropertyEventShooter.set(shooter);
    }

    @Inject(method = "postChangeEvent", at = @At("RETURN"), remap = false)
    private static void renaissance$clearShooter(LivingEntity shooter, ItemStack gunItem, CallbackInfo ci) {
        PropertyEventShooter.clear();
    }
}
