package net.tkg.RenaissanceLib.mixin.client;

import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Takes the gun out of its crouch "slide" (canted hold) while the underbarrel reloads or inspects, like the gun's
 * own reload/inspect does. TaC:Z's state machine slides while {@code context:shouldSlide()} (crouching and the gun's
 * {@code can_slide}) holds and the host's main animation track is idle; a host reload/inspect plays on that main
 * track, so the gun straightens ({@code slide_back}) and slides again afterwards. The underbarrel's clips play on
 * its own model, not the host's main track, so we report "don't slide" while one is running — the script then
 * straightens and re-slides exactly as for a host reload. Same method on the TaC:Z release and beta.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = GunAnimationStateContext.class, remap = false)
public class GunAnimationStateContextMixin {

    @Inject(method = "shouldSlide", at = @At("RETURN"), cancellable = true, remap = false)
    private void renaissance$noSlideDuringUnderbarrelAction(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack gun = player.getMainHandItem();
        if (!ActiveWeapon.isUnderbarrelActive(gun)) return;
        if (UnderbarrelAnimator.isReloadOrInspectPlaying(Underbarrel.getInstalledUnderbarrel(gun))) {
            cir.setReturnValue(false);
        }
    }
}
