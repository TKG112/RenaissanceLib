package net.tkg.RenaissanceLib.client.refit;

import com.mojang.math.Axis;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.client.RailAim;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Turntable camera for the interactive refit screen: the player drags to spin the gun (yaw free, pitch clamped to
 * ±90°) and scrolls to zoom. Applied on top of TaC:Z's own refit camera — its {@code refit_view} positioning — by
 * {@code RefitOrbitMixin}, as a rotation about the gun's centre in camera space, weighted by TaC:Z's refit opening
 * progress so it eases in and out with the screen. Global client state (there's one refit screen).
 */
@OnlyIn(Dist.CLIENT)
public final class RefitOrbit {
    /** Optional pack-author override for the rotation centre; otherwise the centre of the gun's bounding box. */
    private static final String PIVOT_NODE = "refit_pivot";
    private static final float MAX_PITCH = 90f;
    private static final float MIN_ZOOM = -1.5f;   // blocks away from the camera
    private static final float MAX_ZOOM = 0.6f;    // blocks toward the camera (further clamped by MIN_PIVOT_DEPTH)
    /** Closest the rotation centre may come to the camera, in blocks (keeps the gun out of the near plane). */
    private static final float MIN_PIVOT_DEPTH = 0.25f;
    /** Smoothing time constant (seconds) — the displayed view eases toward the dragged/scrolled target. */
    private static final float EASE_TAU = 0.06f;

    private static float targetYaw, targetPitch, targetZoom;
    private static float yaw, pitch, zoom;
    private static long lastUpdateNanos = 0L;

    private RefitOrbit() {}

    /** Back to TaC:Z's default refit view. {@code instant} also snaps the displayed view (used when opening). */
    public static void reset(boolean instant) {
        targetYaw = targetPitch = targetZoom = 0f;
        if (instant) {
            yaw = pitch = zoom = 0f;
            lastUpdateNanos = 0L;
        }
    }

    /** Drag by a screen delta in GUI pixels: right spins the near side right, down tips it down. */
    public static void drag(double dx, double dy) {
        targetYaw += (float) dx * 0.6f;
        targetPitch = Mth.clamp(targetPitch + (float) dy * 0.6f, -MAX_PITCH, MAX_PITCH);
    }

    /** Scroll wheel: positive brings the gun closer. */
    public static void scroll(double delta) {
        targetZoom = Mth.clamp(targetZoom + (float) delta * 0.08f, MIN_ZOOM, MAX_ZOOM);
    }

    /** Advance the displayed view toward the target by the real time since the last frame. */
    private static void update() {
        long now = System.nanoTime();
        float dt = lastUpdateNanos == 0L ? 1f : Math.min((now - lastUpdateNanos) / 1_000_000_000f, 0.1f);
        lastUpdateNanos = now;
        float alpha = 1f - (float) Math.exp(-dt / EASE_TAU);
        yaw += (targetYaw - yaw) * alpha;
        pitch += (targetPitch - pitch) * alpha;
        zoom += (targetZoom - zoom) * alpha;
    }

    /**
     * Applies the orbit to TaC:Z's final positioning matrix {@code m} (the one it multiplies in between
     * {@code translate(0, 1.5, 0)} and {@code translate(0, -1.5, 0)}), weighted by {@code weight} (the refit
     * opening progress). Returns {@code m} unchanged when there's nothing to apply.
     */
    public static Matrix4f apply(Matrix4f m, BedrockGunModel model, float weight) {
        update();
        if (weight <= 0f || model == null) return m;
        float w = Mth.clamp(weight, 0f, 1f);
        float y = yaw * w, p = pitch * w, z = zoom * w;
        if (Math.abs(y) < 1e-3f && Math.abs(p) < 1e-3f && Math.abs(z) < 1e-4f) return m;

        // Rotation centre in camera space: c = T(0,1.5,0) · m · pivot, pivot in m's input space.
        Vector4f c4 = new Vector4f(pivot(model), 1f);
        new Matrix4f().translate(0f, 1.5f, 0f).mul(m).transform(c4);
        Vector3f c = new Vector3f(c4.x, c4.y, c4.z);
        // Zoom along the view axis (camera looks down -Z), never pulling the centre into the near plane.
        z = Math.min(z, -MIN_PIVOT_DEPTH - c.z);

        // Camera-space orbit about c, then undo the surrounding 1.5 translate so it slots in for m:
        // m' = T(0,-1.5,0) · T(0,0,z) · T(c) · Rx(pitch) · Ry(yaw) · T(-c) · T(0,1.5,0) · m
        Matrix4f out = new Matrix4f()
                .translate(0f, -1.5f, 0f)
                .translate(0f, 0f, z)
                .translate(c)
                .rotate(Axis.XP.rotationDegrees(p))
                .rotate(Axis.YP.rotationDegrees(y))
                .translate(-c.x, -c.y, -c.z)
                .translate(0f, 1.5f, 0f);
        return out.mul(m);
    }

    /** Geometric centre per gun model (rest pose) — models are rebuilt on resource reload, so weak keys expire them. */
    private static final Map<BedrockGunModel, Vector3f> CENTRE_CACHE = new WeakHashMap<>();

    /**
     * The gun's rotation centre in the positioning matrix's input space: the {@code refit_pivot} bone if the pack
     * defines one; otherwise the centre of the model's bounding box (its visible cubes), so every gun turns about
     * its own middle with nothing to author; the mount-bone centroid only if the model has no geometry.
     */
    private static Vector3f pivot(BedrockGunModel model) {
        BedrockPart override = model.getNode(PIVOT_NODE);
        if (override != null) return toPivotSpace(override, new Vector3f());
        return new Vector3f(CENTRE_CACHE.computeIfAbsent(model, RefitOrbit::computeCentre));
    }

    private static Vector3f computeCentre(BedrockGunModel model) {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        List<BedrockPart> roots = ((BedrockModel) model).getShouldRender();
        if (roots != null) {
            for (BedrockPart root : roots) accumulateBounds(root, min, max);
        }
        if (min.x <= max.x) return min.add(max).mul(0.5f);
        return mountBoneCentroid(model);
    }

    /**
     * Grows the box by every corner of every cube in {@code part}'s visible subtree. Cube bounds are local to their
     * bone, in pixels; the corners are mapped into pivot space through the bone's rest transform.
     */
    private static void accumulateBounds(BedrockPart part, Vector3f min, Vector3f max) {
        if (!part.visible) return;
        if (part.cubes != null && !part.cubes.isEmpty()) {
            Matrix4f toPivot = pivotSpaceTransform(part);
            Vector3f corner = new Vector3f();
            for (BedrockCube cube : part.cubes) {
                float[] b = cubeBounds(cube);
                if (b == null) continue;
                for (int i = 0; i < 8; i++) {
                    corner.set((i & 1) == 0 ? b[0] : b[3], (i & 2) == 0 ? b[1] : b[4], (i & 4) == 0 ? b[2] : b[5])
                            .div(16f);
                    Vector3f p = toPivot.transformPosition(new Vector3f(corner));
                    min.min(p);
                    max.max(p);
                }
            }
        }
        if (part.children != null) {
            for (BedrockPart child : part.children) accumulateBounds(child, min, max);
        }
    }

    /** {min x, min y, min z, max x, max y, max z} in pixels, for the cube shapes TaC:Z builds. */
    private static float[] cubeBounds(BedrockCube cube) {
        if (cube instanceof BedrockCubeBox box) {
            return new float[]{box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ};
        }
        if (cube instanceof BedrockCubePerFace face) {
            return new float[]{face.minX, face.minY, face.minZ, face.maxX, face.maxY, face.maxZ};
        }
        return null;
    }

    /** Centroid of the gun's attachment mount bones ({@code <type>_pos}) — fallback for a model with no cubes. */
    private static Vector3f mountBoneCentroid(BedrockGunModel model) {
        Vector3f sum = new Vector3f();
        int count = 0;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            BedrockPart node = model.getNode(type.name().toLowerCase() + "_pos");
            if (node == null) continue;
            sum.add(toPivotSpace(node, new Vector3f()));
            count++;
        }
        return count > 0 ? sum.div(count) : new Vector3f(0f, -1.5f, 0f);
    }

    /**
     * Maps a point in {@code bone}'s local frame (blocks) into pivot space. For a bone X, TaC:Z's positioning inverse
     * A satisfies {@code T(1.5)·A·T(-1.5)·G = I} (G = the render chain to X's frame) — that's what aiming at X means —
     * so a local point L lands at {@code A⁻¹ · (L - (0, 1.5, 0))}.
     */
    private static Vector3f toPivotSpace(BedrockPart bone, Vector3f local) {
        return pivotSpaceTransform(bone).transformPosition(local);
    }

    private static Matrix4f pivotSpaceTransform(BedrockPart bone) {
        List<BedrockPart> path = new ArrayList<>();
        RailAim.appendNodePath(bone, path);
        return RailAim.positioningNodeInverse(path).invert().translate(0f, -1.5f, 0f);
    }
}
