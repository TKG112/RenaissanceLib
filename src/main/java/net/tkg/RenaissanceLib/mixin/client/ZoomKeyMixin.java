package net.tkg.RenaissanceLib.mixin.client;

import com.tacz.guns.client.input.ZoomKey;
import net.tkg.RenaissanceLib.client.VariableZoom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Defers TaC:Z's discrete zoom-step change while the player is aiming a continuous scope.
 *
 * <p>TaC:Z cycles the zoom step the instant the zoom key is pressed ({@code doZoomLogic} on
 * {@code GLFW_PRESS}). That means holding the key to enter continuous scroll mode always stepped
 * once first. We cancel that press-time step for continuous scopes; {@link VariableZoom} instead
 * re-issues the step on release only if the player never scrolled (i.e. it was a tap).
 */
@Mixin(value = ZoomKey.class, remap = false)
public class ZoomKeyMixin {

    @Inject(method = "doZoomLogic", at = @At("HEAD"), cancellable = true)
    private static void renaissance$deferContinuousZoom(CallbackInfo ci) {
        if (VariableZoom.onZoomKeyPress()) {
            ci.cancel();
        }
    }
}
