package net.tkg.RenaissanceLib.mixin.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.event.CameraSetupEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelAim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Gives the active underbarrel its own aim zoom when it has iron sights (see {@link UnderbarrelAim}). TaC:Z's
 * aim magnification reads {@code IGun.getAimingZoom} off the held (host) gun; we substitute the underbarrel
 * display's {@code iron_zoom} while its iron aim is active. When the underbarrel has no iron sights (or no
 * {@code iron_zoom}), the host gun's zoom is used unchanged.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = CameraSetupEvent.class, remap = false)
public abstract class CameraSetupEventMixin {

    @Redirect(
            method = "applyScopeMagnification",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/api/item/IGun;getAimingZoom(Lnet/minecraft/world/item/ItemStack;)F"))
    private static float renaissance$underbarrelAimZoom(IGun iGun, ItemStack stack) {
        if (UnderbarrelAim.isIronAimActive(stack)) {
            float ubZoom = UnderbarrelAim.ironZoom(stack);
            if (ubZoom > 0f) return ubZoom;
        }
        return iGun.getAimingZoom(stack);
    }
}
