package net.tkg.RenaissanceLib.client.refit;

import com.mojang.math.Axis;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /** Pan speed: blocks of camera-plane movement per GUI pixel dragged. */
    private static final float PAN_PER_PIXEL = 0.0025f;
    /** Pan limit, in blocks from TaC:Z's refit framing. */
    private static final float MAX_PAN = 1.0f;

    private static float targetYaw, targetPitch, targetZoom, targetPanX, targetPanY;
    private static float yaw, pitch, zoom, panX, panY;
    private static long lastUpdateNanos = 0L;

    private RefitOrbit() {}

    /** Back to TaC:Z's default refit view. {@code instant} also snaps the displayed view (used when opening). */
    public static void reset(boolean instant) {
        targetYaw = targetPitch = targetZoom = targetPanX = targetPanY = 0f;
        if (instant) {
            yaw = pitch = zoom = panX = panY = 0f;
            lastUpdateNanos = 0L;
        }
    }

    /** Right-drag: slide the gun across the screen (camera plane) without turning it. */
    public static void pan(double dx, double dy) {
        targetPanX = Mth.clamp(targetPanX + (float) dx * PAN_PER_PIXEL, -MAX_PAN, MAX_PAN);
        targetPanY = Mth.clamp(targetPanY - (float) dy * PAN_PER_PIXEL, -MAX_PAN, MAX_PAN);
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
        panX += (targetPanX - panX) * alpha;
        panY += (targetPanY - panY) * alpha;
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
        float y = yaw * w, p = pitch * w, z = zoom * w, px = panX * w, py = panY * w;
        if (Math.abs(y) < 1e-3f && Math.abs(p) < 1e-3f && Math.abs(z) < 1e-4f
                && Math.abs(px) < 1e-4f && Math.abs(py) < 1e-4f) {
            return m;
        }

        // Rotation centre in the frame TaC:Z's positioning sits in: c = T(0,1.5,0) · m · pivot (pivot in m's input
        // space). That frame is the camera's after the gun renderer's translate(0,1.5,0) + 180° roll, so its X/Y are
        // the camera's flipped (drag directions below are as tuned in-game) and its Z is the camera's.
        Vector4f c4 = new Vector4f(pivot(model), 1f);
        new Matrix4f().translate(0f, 1.5f, 0f).mul(m).transform(c4);
        Vector3f c = new Vector3f(c4.x, c4.y, c4.z);
        // Zoom along the view axis (camera looks down -Z), never pulling the centre into the near plane.
        z = Math.min(z, -MIN_PIVOT_DEPTH - c.z);

        // Orbit about c, then undo the surrounding 1.5 translate so it slots in for m:
        // m' = T(0,-1.5,0) · T(pan, z) · T(c) · Rx(pitch) · Ry(yaw) · T(-c) · T(0,1.5,0) · m
        // This frame's X/Y are the camera's flipped (the renderer's 180° roll), so screen-right / screen-up pan is
        // -X / -Y here.
        Matrix4f out = new Matrix4f()
                .translate(0f, -1.5f, 0f)
                .translate(-px, -py, z)
                .translate(c)
                .rotate(Axis.XP.rotationDegrees(p))
                .rotate(Axis.YP.rotationDegrees(y))
                .translate(-c.x, -c.y, -c.z)
                .translate(0f, 1.5f, 0f);
        return out.mul(m);
    }

    /** Bounding box per gun model (rest pose) — models are rebuilt on resource reload, so weak keys expire them. */
    private static final Map<BedrockGunModel, Bounds> BOUNDS_CACHE = new WeakHashMap<>();

    /** TaC:Z's hand anchors: they carry placeholder arm cubes that are never drawn as gun geometry. */
    private static final Set<String> HAND_NODES = Set.of("lefthand_pos", "righthand_pos");

    /**
     * The gun's rotation centre in the positioning matrix's input space: the {@code refit_pivot} bone if the pack
     * defines one; otherwise the centre of the model's bounding box (its visible cubes), so every gun turns about
     * its own middle with nothing to author; the mount-bone centroid only if the model has no geometry.
     */
    public static Vector3f pivot(BedrockGunModel model) {
        Matrix4f override = findNode(model, PIVOT_NODE);
        if (override != null) return override.transformPosition(new Vector3f());
        Bounds box = bounds(model);
        if (box.min() != null) return new Vector3f(box.min()).add(box.max()).mul(0.5f);
        return mountBoneCentroid(model);
    }

    /** Whether the pivot comes from a pack-authored {@code refit_pivot} bone rather than the bounding box. */
    public static boolean hasPivotOverride(BedrockGunModel model) {
        return model.getNode(PIVOT_NODE) != null;
    }

    /** The gun's rest-pose bounding box in pivot space (cached per model); empty if it has no measurable cubes. */
    public static Bounds bounds(BedrockGunModel model) {
        return BOUNDS_CACHE.computeIfAbsent(model, RefitOrbit::computeBounds);
    }

    /** A bounding box in pivot space (blocks); {@code min}/{@code max} are {@code null} when there was no geometry. */
    public record Bounds(Vector3f min, Vector3f max) {}

    /** Slot mount-bone positions per gun model (rest pose); an empty Optional = the model has no bone for it. */
    private static final Map<BedrockGunModel, Map<AttachmentType, Optional<Vector3f>>> ANCHOR_CACHE =
            new WeakHashMap<>();

    /**
     * Where a slot mounts on the gun, in pivot space: the origin of its {@code <type>_pos} bone (the same bone TaC:Z
     * renders that slot's attachment at), or {@code null} if the model has none. Rest pose — the gun barely animates
     * in the refit screen.
     */
    public static Vector3f slotAnchor(BedrockGunModel model, AttachmentType type) {
        Optional<Vector3f> anchor = ANCHOR_CACHE
                .computeIfAbsent(model, m -> new EnumMap<>(AttachmentType.class))
                .computeIfAbsent(type, t -> {
                    Matrix4f node = findNode(model, t.name().toLowerCase() + "_pos");
                    return Optional.ofNullable(node == null ? null : node.transformPosition(new Vector3f()));
                });
        return anchor.map(Vector3f::new).orElse(null);
    }

    // ---- walking the model -------------------------------------------------------------------------------------
    //
    // Pivot space is the input space of TaC:Z's positioning matrix M (applied as T(0,1.5,0)·M·T(0,-1.5,0)); the model
    // render adds no transform of its own, and TaC:Z's positioning inverse for a bone is G⁻¹·T(0,1.5,0) (G = the rest
    // bone chain), so a point L in a bone's frame sits at T(0,-1.5,0)·G·L in pivot space.
    //
    // The walk goes DOWN the children lists with the traversal parent's transform — exactly how TaC:Z renders —
    // rather than up via getParent(): the loader attaches the extra bone it creates for a per-cube rotation with
    // addChild, which never sets `parent`, so walking up treated every rotated cube as a root (it lost all its
    // parents' offsets and gained the root's 1.5-block shift — the "box far too tall / past the stock" bug).

    /** Local transform of a bone at rest: {@code T(pos/16) · Rz · Ry · Rx}, as {@code translateAndRotateAndScale}. */
    private static Matrix4f restLocal(BedrockPart part) {
        return new Matrix4f()
                .translate(part.x / 16f, part.y / 16f, part.z / 16f)
                .rotate(Axis.ZP.rotation(part.zRot))
                .rotate(Axis.YP.rotation(part.yRot))
                .rotate(Axis.XP.rotation(part.xRot));
    }

    private static Matrix4f rootFrame() {
        return new Matrix4f().translate(0f, -1.5f, 0f);
    }

    private static Bounds computeBounds(BedrockGunModel model) {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        List<BedrockPart> roots = ((BedrockModel) model).getShouldRender();
        if (roots != null) {
            for (BedrockPart root : roots) accumulateBounds(root, rootFrame(), min, max);
        }
        return min.x <= max.x ? new Bounds(min, max) : new Bounds(null, null);
    }

    /**
     * Grows the box by every corner of every cube in {@code part}'s visible subtree ({@code parentFrame} = the
     * parent's pivot-space transform). Cube bounds are local to their bone, in pixels.
     *
     * <p>Left out, like the renderer leaves them out: the hand anchors' placeholder arm cubes (by name — TaC:Z hides
     * the hands in the refit screen, so their hook doesn't report itself there); a hooked node whose hook returns a
     * renderer ({@code FunctionalBedrockPart.render} then draws that <em>instead of</em> the node's cubes and
     * children — attachment slots, muzzle flash); and hidden parts. A hook that returns nothing (handguards, mags,
     * sights) only toggles {@code visible}, so the node is measured normally — calling it is what TaC:Z does every
     * frame.
     */
    private static void accumulateBounds(BedrockPart part, Matrix4f parentFrame, Vector3f min, Vector3f max) {
        if (part.name != null && HAND_NODES.contains(part.name)) return;
        if (part instanceof FunctionalBedrockPart functional && functional.functionalRenderer != null
                && functional.functionalRenderer.apply(part) != null) {
            return;
        }
        if (!part.visible) return;
        Matrix4f frame = new Matrix4f(parentFrame).mul(restLocal(part));
        if (part.cubes != null) {
            Vector3f corner = new Vector3f();
            for (BedrockCube cube : part.cubes) {
                float[] b = cubeBounds(cube);
                if (b == null) continue;
                for (int i = 0; i < 8; i++) {
                    corner.set((i & 1) == 0 ? b[0] : b[3], (i & 2) == 0 ? b[1] : b[4], (i & 4) == 0 ? b[2] : b[5])
                            .div(16f);
                    Vector3f p = frame.transformPosition(new Vector3f(corner));
                    min.min(p);
                    max.max(p);
                }
            }
        }
        if (part.children != null) {
            for (BedrockPart child : part.children) accumulateBounds(child, frame, min, max);
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

    /** The pivot-space transform of the first bone named {@code name} (found walking down), or {@code null}. */
    private static Matrix4f findNode(BedrockGunModel model, String name) {
        List<BedrockPart> roots = ((BedrockModel) model).getShouldRender();
        if (roots == null) return null;
        for (BedrockPart root : roots) {
            Matrix4f found = findNode(root, rootFrame(), name);
            if (found != null) return found;
        }
        return null;
    }

    private static Matrix4f findNode(BedrockPart part, Matrix4f parentFrame, String name) {
        Matrix4f frame = new Matrix4f(parentFrame).mul(restLocal(part));
        if (name.equals(part.name)) return frame;
        if (part.children != null) {
            for (BedrockPart child : part.children) {
                Matrix4f found = findNode(child, frame, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Centroid of the gun's attachment mount bones ({@code <type>_pos}) — fallback for a model with no cubes. */
    private static Vector3f mountBoneCentroid(BedrockGunModel model) {
        Vector3f sum = new Vector3f();
        int count = 0;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            Matrix4f node = findNode(model, type.name().toLowerCase() + "_pos");
            if (node == null) continue;
            sum.add(node.transformPosition(new Vector3f()));
            count++;
        }
        return count > 0 ? sum.div(count) : new Vector3f();
    }
}

