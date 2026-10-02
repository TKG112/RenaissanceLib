package net.tkg.RenaissanceLib.mixin.client;

import com.tacz.guns.client.event.CameraSetupEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.client.RecoilSpeed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Attachment {@code recoil_speed}. TaC:Z plays the camera recoil by sampling the pitch/yaw curves (built from the gun
 * data's keyframes at the shot) at "milliseconds since the shot", and stops once that's past the last keyframe.
 * Scaling that elapsed time plays the same curve faster or slower — and its end moves with it. Same code on the
 * release and the beta.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = CameraSetupEvent.class, remap = false)
public abstract class CameraRecoilSpeedMixin {

    @ModifyVariable(method = "applyCameraRecoil", at = @At(value = "STORE", ordinal = 0), ordinal = 0, remap = false)
    private static long renaissance$scaleRecoilTime(long elapsedMs) {
        float speed = RecoilSpeed.current();
        return speed == 1f ? elapsedMs : (long) (elapsedMs * (double) speed);
    }
}
