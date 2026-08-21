package net.tkg.RenaissanceLib.client.underbarrel;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Holds the host gun's support-hand (left) grip transform in view space, captured each frame from TaC:Z's
 * {@code LeftHandRender} bone (see {@code LeftHandRenderMixin}). {@link UnderbarrelLeftHandRender} reads it
 * to interpolate the drawn arm from the host grip to the underbarrel grip across a weapon switch, so the
 * support hand glides between the two positions instead of teleporting. Global client state (shooter-only),
 * like the other underbarrel client effects.
 *
 * <p>The host and underbarrel hand renderers both run every frame (functional renderers run even when their
 * bone is hidden) and receive a posestack already at their {@code lefthand_pos} bone, so both grip matrices
 * are available in the same view space within a frame; the host body renders before the attachments, so this
 * is populated before the underbarrel renderer reads it.
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelHandAnchor {
    private static final Matrix4f HOST_HAND = new Matrix4f();
    private static boolean hasHostHand;

    private UnderbarrelHandAnchor() {}

    /** Records the host support-hand grip transform (the raw bone pose, before the hand's own ZP-180 flip). */
    public static void setHostHand(Matrix4f pose) {
        HOST_HAND.set(pose);
        hasHostHand = true;
    }

    /** A copy of the host support-hand grip transform, or {@code null} if none has been captured yet. */
    public static Matrix4f hostHand() {
        return hasHostHand ? new Matrix4f(HOST_HAND) : null;
    }
}
