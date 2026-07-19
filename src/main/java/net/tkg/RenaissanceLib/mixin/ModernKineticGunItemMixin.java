package net.tkg.RenaissanceLib.mixin;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.AttachmentOverrides;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ModernKineticGunItem.class, remap = false)
public abstract class ModernKineticGunItemMixin {

    @Inject(method = "fireSelect", at = @At("HEAD"), cancellable = true, remap = false)
    private void renaissance$attachmentAwareFireSelect(ShooterDataHolder dataHolder,
                                                       ItemStack gunItem,
                                                       CallbackInfo ci) {
        try {
            IGun iGun = IGun.getIGunOrNull(gunItem);
            if (iGun == null) return;

            ResourceLocation gunId = iGun.getGunId(gunItem);
            var indexOpt = TimelessAPI.getCommonGunIndex(gunId);
            if (indexOpt.isEmpty()) return;

            List<FireMode> available =
                    AttachmentOverrides.effectiveFireModes(gunItem, indexOpt.get().getGunData());

            if (available.isEmpty()) return;
            if (available.equals(indexOpt.get().getGunData().getFireModeSet())) return;

            FireMode current = iGun.getFireMode(gunItem);
            int nextIndex = (available.indexOf(current) + 1) % available.size();
            iGun.setFireMode(gunItem, available.get(nextIndex));
            ci.cancel();
        } catch (Throwable t) {

            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] attachment-aware fireSelect failed", t);
        }
    }
}
