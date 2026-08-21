package net.tkg.RenaissanceLib.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * Thread-of-render flag that marks a mounted rail sight as a <em>clipped passenger</em>: it is being
 * drawn on the gun while the player aims through a masking main scope, so its body must be clipped out
 * of that scope's ocular the same way the gun barrel is.
 *
 * <p>{@link RailSightRenderer} sets the mask (the active scope's stencil compare func + ref) right
 * around the sight's {@code renderAttachment} call; the {@code BedrockAttachmentModel} mixin reads it at
 * the head of {@code renderScope}/{@code renderSight}/{@code renderBoth} and, when present, replaces the
 * sight's full stencil pipeline with a body-only draw clipped against the scope's mask (which is still
 * sitting in the stencil buffer at that point — TaC:Z doesn't clear it until the gun body is done).
 *
 * <p>Client render thread only; a plain static is sufficient (no cross-thread use).
 */
@OnlyIn(Dist.CLIENT)
public final class RailPassengerClip {
    private RailPassengerClip() {}

    /** The stencil compare function + reference value that clips a passenger out of the active scope's ocular. */
    public record Mask(int func, int ref) {}

    @Nullable
    private static Mask current;

    public static void begin(Mask mask) {
        current = mask;
    }

    public static void end() {
        current = null;
    }

    @Nullable
    public static Mask current() {
        return current;
    }
}
