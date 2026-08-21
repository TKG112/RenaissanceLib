package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.item.attachment.AttachmentType;

import java.util.Objects;

/**
 * Names any toggleable attachment on a gun uniformly, as a top-level {@link AttachmentType} slot plus a
 * {@link MountPath} into that slot's rail tree.
 *
 * <ul>
 *   <li>A flat slot attachment (muzzle, grip, …) or the scope itself → {@code (slot, ROOT)}.</li>
 *   <li>A rail-mounted optic under the scope → {@code (SCOPE, [i])}, {@code (SCOPE, [i, j])}, …</li>
 * </ul>
 *
 * <p>Stage 1 only produces {@link MountPath#ROOT} targets; the path is modelled from the start so the
 * radial UI and toggle plumbing don't need reworking when rail-mounted targets are added (Stage 2).
 */
public final class ToggleTarget {
    private final AttachmentType slot;
    private final MountPath path;

    private ToggleTarget(AttachmentType slot, MountPath path) {
        this.slot = slot;
        this.path = path;
    }

    public static ToggleTarget of(AttachmentType slot, MountPath path) {
        return new ToggleTarget(slot, path == null ? MountPath.ROOT : path);
    }

    /** A top-level slot target (path {@link MountPath#ROOT}). */
    public static ToggleTarget slot(AttachmentType slot) {
        return new ToggleTarget(slot, MountPath.ROOT);
    }

    public AttachmentType slot() {
        return slot;
    }

    public MountPath path() {
        return path;
    }

    public boolean isRoot() {
        return path.isRoot();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ToggleTarget t && slot == t.slot && path.equals(t.path));
    }

    @Override
    public int hashCode() {
        return Objects.hash(slot, path);
    }

    @Override
    public String toString() {
        return "ToggleTarget[" + slot + "," + path + "]";
    }
}
