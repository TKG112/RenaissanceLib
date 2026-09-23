package net.tkg.RenaissanceLib.mixin.client;

import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets an underbarrel reload override a gun's crouch pose, like the gun's own reload does. Gun packs that tilt
 * the weapon while crouching drive it from the Lua state machine's {@code context:isCrouching()} (the only way
 * TaC:Z exposes crouching to them); the host gun's state machine doesn't know the underbarrel is reloading, so
 * the weapon stayed tilted through the underbarrel reload. While the active underbarrel's reload animation plays
 * we report "not crouching", so the script straightens the gun exactly as if the player stood up, and it tilts
 * back when the reload ends. Same method on the TaC:Z release and beta.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = GunAnimationStateContext.class, remap = false)
public class GunAnimationStateContextMixin {

    @Inject(method = "isCrouching", at = @At("RETURN"), cancellable = true, remap = false)
    private void renaissance$uprightDuringUnderbarrelReload(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || !UnderbarrelAnimator.isReloadPlaying()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && ActiveWeapon.isUnderbarrelActive(player.getMainHandItem())) {
            cir.setReturnValue(false);
        }
    }
}
