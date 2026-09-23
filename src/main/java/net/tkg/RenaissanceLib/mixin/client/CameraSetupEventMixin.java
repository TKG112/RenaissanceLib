package net.tkg.RenaissanceLib.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.event.CameraSetupEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelAim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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
        float hostZoom = iGun.getAimingZoom(stack);
        float weight = UnderbarrelAim.ironWeight(stack);
        if (weight > 0f) {
            float ubZoom = UnderbarrelAim.ironZoom(stack);
            if (ubZoom > 0f) return Mth.lerp(weight, hostZoom, ubZoom);
        }
        return hostZoom;
    }

    /**
     * The aimed gun-model FOV. TaC:Z eases the model FOV with {@code Mth.lerp(aimProgress, baseFov, aimedFov)},
     * where {@code aimedFov} comes from the host gun's {@code zoom_model_fov} (or its scope's {@code views_fov});
     * while the underbarrel's iron sight is active we substitute the underbarrel display's {@code zoom_model_fov},
     * so its sight is drawn the same size on every host gun. Same call shape on the TaC:Z release and beta.
     */
    @ModifyArg(
            method = "applyGunModelFovModifying",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F", remap = true),
            index = 2)
    private static float renaissance$underbarrelModelFov(float aimedFov,
                                                        @Local(argsOnly = true) ViewportEvent.ComputeFov event) {
        if (!(event.getCamera().getEntity() instanceof LivingEntity living)) return aimedFov;
        ItemStack stack = living.getMainHandItem();
        float weight = UnderbarrelAim.ironWeight(stack);
        if (weight <= 0f) return aimedFov;
        float ubFov = UnderbarrelAim.modelFov(stack);
        return ubFov > 0f ? Mth.lerp(weight, aimedFov, ubFov) : aimedFov;
    }
}
