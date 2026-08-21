package net.tkg.RenaissanceLib.client.underbarrel;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Eases the first-person weapon between host and underbarrel anchoring so switching the active weapon
 * <em>glides</em> instead of snapping. The underbarrel's {@code camera} node offsets the whole weapon to
 * the launcher's view position; without this that offset appears/disappears in a single frame on switch.
 *
 * <p>A real-time factor {@code 0..1} (0 = host, 1 = underbarrel) that {@link #update} advances toward the
 * current target each frame and {@code FirstPersonRenderGunEventMixin} multiplies into the camera delta
 * ({@link UnderbarrelCameraAnchor#animationDelta(float)}), so the delta fades in and out. Global client
 * state (shooter-only), like the other underbarrel client effects. If the weapon isn't rendered for a
 * while (unequipped, screen open) the elapsed gap simply snaps the factor to its target on return — a
 * transition only plays for a switch made while the weapon is on screen.
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelTransition {
    /** How long a full host↔underbarrel switch takes, in milliseconds. */
    private static final float DURATION_MS = 250f;

    private static float progress = 0f; // linear 0 (host) .. 1 (underbarrel)
    private static long lastUpdateMs = 0L;

    private UnderbarrelTransition() {}

    /**
     * Advance toward the current target (1 when the underbarrel is active, else 0) by the real time elapsed
     * since the last call, and return the eased factor. Call once per first-person weapon render.
     */
    public static float update(boolean underbarrelActive) {
        long now = System.currentTimeMillis();
        float dt = lastUpdateMs == 0L ? 0f : (now - lastUpdateMs);
        lastUpdateMs = now;

        float target = underbarrelActive ? 1f : 0f;
        float step = DURATION_MS <= 0f ? 1f : dt / DURATION_MS;
        if (progress < target) {
            progress = Math.min(target, progress + step);
        } else if (progress > target) {
            progress = Math.max(target, progress - step);
        }
        return eased();
    }

    /** The current eased factor without advancing it — for readers other than the once-per-frame driver. */
    public static float factor() {
        return eased();
    }

    private static float eased() {
        float x = progress; // smoothstep for ease-in-out
        return x * x * (3f - 2f * x);
    }
}
