package net.tkg.RenaissanceLib.mixin.client;

import net.minecraft.client.MouseHandler;
import net.tkg.RenaissanceLib.RenaissanceConfig;
import net.tkg.RenaissanceLib.client.WheelInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reroutes mouse input while any radial wheel (attachment or weapon-select) is open, via {@link WheelInput}.
 * Both wheels are cursorless overlays, so the mouse stays grabbed and is repurposed into a directional
 * selector.
 *
 * <ul>
 *   <li><b>Look</b> is diverted into the open wheel's pointer instead of turning the camera.</li>
 *   <li><b>Left/right mouse</b> (fire / ADS) are swallowed at the source, because TaC:Z reads mouse input
 *       directly and does not honour the cancellable Forge input events. Other buttons (incl. a mouse-bound
 *       wheel key) pass through, and keyboard movement is unaffected.</li>
 *   <li><b>Scroll</b> is swallowed so the hotbar slot can't change while a wheel is up.</li>
 * </ul>
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void renaissance$divertToWheel(CallbackInfo ci) {
        if (WheelInput.anyOpen()) {
            WheelInput.feedLook(this.accumulatedDX, this.accumulatedDY);
            this.accumulatedDX = 0.0;
            this.accumulatedDY = 0.0;
            ci.cancel();
        }
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void renaissance$wheelMouseButtons(long window, int button, int action, int mods, CallbackInfo ci) {
        if (!WheelInput.anyOpen()) return;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        // Consume both buttons entirely so the gun never fires/ADSes while a wheel is up. On the press:
        // right-click cancels (both modes); left-click selects in press mode (hold mode selects on key release).
        if (action == GLFW.GLFW_PRESS) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                WheelInput.cancel();
            } else if (!RenaissanceConfig.CLIENT.holdToOpenWheel.get()) {
                WheelInput.selectPressMode();
            }
        }
        ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void renaissance$blockScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (WheelInput.anyOpen()) {
            ci.cancel();
        }
    }
}
