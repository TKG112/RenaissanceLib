package net.tkg.RenaissanceLib.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Small facade over the radial wheels so the shared mouse hook ({@code MouseHandlerMixin}) can route
 * look/click input to whichever one is open without knowing about each. The wheels are mutually exclusive in
 * practice (each is opened by its own key). The attachment wheel selects on left-click (press mode); the
 * fire-mode wheel confirms on key release (driven by {@code FireSelectInput}), so it only takes look and
 * cancel here, not the press-mode click.
 */
@OnlyIn(Dist.CLIENT)
public final class WheelInput {

    private WheelInput() {}

    /** Whether any radial wheel is currently accepting input. */
    public static boolean anyOpen() {
        return AttachmentWheel.isOpen() || FireModeWheel.isOpen();
    }

    /** Divert accumulated look delta into the open wheel's pointer. */
    public static void feedLook(double dx, double dy) {
        if (AttachmentWheel.isOpen()) {
            AttachmentWheel.feedLook(dx, dy);
        } else if (FireModeWheel.isOpen()) {
            FireModeWheel.feedLook(dx, dy);
        }
    }

    /** Left-click in press mode: confirm the open wheel's highlighted choice (attachment wheel only). */
    public static void selectPressMode() {
        if (AttachmentWheel.isOpen()) {
            AttachmentWheel.confirm();
        }
    }

    /** Right-click (either mode): cancel the open wheel without selecting. */
    public static void cancel() {
        if (AttachmentWheel.isOpen()) {
            AttachmentWheel.close();
        } else if (FireModeWheel.isOpen()) {
            FireModeWheel.close();
        }
    }
}
