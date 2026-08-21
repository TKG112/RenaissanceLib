package net.tkg.RenaissanceLib.client.underbarrel;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Layers the underbarrel's animation movement onto the whole first-person weapon while it's the active weapon,
 * <em>added</em> to the host's normal first-person transform rather than replacing it — so the host keeps its
 * full idle / sway / crouch and the underbarrel's shoot/reload motion moves the whole gun on top.
 *
 * <p>The movement is the underbarrel {@code root} node's <em>own</em> animation (the offset and rotation our
 * {@link UnderbarrelAnimator} samples from that bone), relative to its rest pose. Driving the whole weapon from
 * {@code root} (rather than applying it to the underbarrel model) is what keeps the underbarrel on the host rail
 * during shoot/reload — otherwise root would swing the underbarrel model off its mount. At idle root isn't
 * animated, so the delta is identity and this is a pure no-op (the host is untouched); during shoot/reload it
 * moves, and {@link #animationDelta()} returns it as a matrix that {@code FirstPersonRenderGunEventMixin}
 * multiplies onto the host's idle anchor.
 *
 * <p>Because it's the root bone's own local animation (not a captured render pose), it carries only the
 * underbarrel's motion — never the host's sway — so it can't cancel the host movement. Global client state
 * (shooter-only), like the other underbarrel client effects. Fed one animator pass before it's read, an
 * imperceptible one-frame lag.
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelCameraAnchor {
    /** Below this, the animation is treated as rest (identity delta), keeping idle a true no-op. */
    private static final float EPSILON = 1.0e-5f;
    /** How strongly the underbarrel root animation moves the whole weapon (1 = exactly as authored). */
    private static final float STRENGTH = 1.0f;

    private static float offsetX, offsetY, offsetZ;
    private static final Quaternionf rotation = new Quaternionf();
    private static boolean active;

    private UnderbarrelCameraAnchor() {}

    /**
     * Records the underbarrel camera node's animation this frame: {@code offset*} in blocks and {@code rot} the
     * bone's animation quaternion (its {@code additionalQuaternion}), both relative to the node's rest pose.
     * Called by {@link UnderbarrelAnimator} after it poses the model.
     */
    public static void setCameraAnimation(float offsetX, float offsetY, float offsetZ, Quaternionf rot) {
        UnderbarrelCameraAnchor.offsetX = offsetX;
        UnderbarrelCameraAnchor.offsetY = offsetY;
        UnderbarrelCameraAnchor.offsetZ = offsetZ;
        rotation.set(rot);
        float offsetMag = Math.abs(offsetX) + Math.abs(offsetY) + Math.abs(offsetZ);
        float rotMag = Math.abs(rot.x()) + Math.abs(rot.y()) + Math.abs(rot.z());
        active = offsetMag > EPSILON || rotMag > EPSILON;
    }

    /** Marks the camera animation as rest (identity) — e.g. when the underbarrel isn't the active weapon. */
    public static void clear() {
        active = false;
    }

    /**
     * The camera node's current animation as a transform to add onto the host anchor, or {@code null} when the
     * node is at rest (idle) so the caller leaves the host transform untouched.
     */
    public static Matrix4f animationDelta() {
        return animationDelta(1f);
    }

    /**
     * The camera-node delta scaled by {@code factor} (0..1), for easing the anchor across a host↔underbarrel
     * switch: {@code 0} yields {@code null} (host anchor untouched) and {@code 1} the full delta, so
     * {@link UnderbarrelTransition} can fade the weapon's offset in and out instead of snapping. Scales both
     * the translation and the rotation angle, so it interpolates smoothly from identity to the full move.
     */
    public static Matrix4f animationDelta(float factor) {
        if (!active || factor <= 0f) return null;
        // Scale the authored camera motion up by STRENGTH (visible) and by the transition factor (eased).
        org.joml.AxisAngle4f aa = new org.joml.AxisAngle4f().set(rotation);
        aa.angle *= STRENGTH * factor;
        Matrix4f m = new Matrix4f();
        m.translate(offsetX * STRENGTH * factor, offsetY * STRENGTH * factor, offsetZ * STRENGTH * factor);
        m.rotate(new Quaternionf().set(aa));
        return m;
    }
}
