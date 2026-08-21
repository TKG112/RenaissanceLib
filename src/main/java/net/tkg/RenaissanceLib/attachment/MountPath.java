package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.item.attachment.AttachmentType;

import java.util.Arrays;

/**
 * Identifies a mount position in the (recursive) rail tree as a <em>host slot</em> plus a sequence of slot
 * indices from that host's rail-bearing attachment down through nested mounts.
 *
 * <p>The {@link #hostType() host type} is the native gun slot whose installed attachment declares the rails
 * — historically always {@link AttachmentType#SCOPE}, now any slot (a grip/handguard hosting a laser rail,
 * etc.). {@link #ROOT} is the SCOPE host itself with no mount; {@link #root(AttachmentType)} is another
 * host's attachment with no mount. {@code [0]} is rail slot 0 on that host; {@code [0, 1]} is the mount in
 * slot 0, then <em>its</em> rail slot 1; and so on. Storage and rendering walk the index chain one hop at a
 * time under the host's {@code Attachment<TYPE>} NBT (see {@link RailStorage}).
 *
 * <p>The default host is {@code SCOPE}, so the scope-optic aim/zoom code that predates non-scope hosts keeps
 * constructing paths with the index-only factories and behaves exactly as before.
 */
public final class MountPath {
    /** The scope-slot attachment itself (no mount) — the default host. */
    public static final MountPath ROOT = new MountPath(AttachmentType.SCOPE, new int[0]);

    private final AttachmentType hostType;
    private final int[] indices;

    private MountPath(AttachmentType hostType, int[] indices) {
        this.hostType = hostType;
        this.indices = indices;
    }

    /** A path on the {@link AttachmentType#SCOPE} host (back-compat: the original scope-rooted behaviour). */
    public static MountPath of(int... indices) {
        return of(AttachmentType.SCOPE, indices);
    }

    /** A path on the given host slot. */
    public static MountPath of(AttachmentType hostType, int... indices) {
        return new MountPath(hostType, indices.clone());
    }

    /** The given host's attachment itself, with no mount (its rail-tree root). */
    public static MountPath root(AttachmentType hostType) {
        return new MountPath(hostType, new int[0]);
    }

    /** The native gun slot whose installed attachment carries the rails this path descends into. */
    public AttachmentType hostType() {
        return hostType;
    }

    /** Number of hops from the root; {@code 0} for a host root. */
    public int depth() {
        return indices.length;
    }

    public boolean isRoot() {
        return indices.length == 0;
    }

    /** The slot index at hop {@code i} (0-based from the root). */
    public int get(int i) {
        return indices[i];
    }

    /** The final slot index; throws if {@link #isRoot()}. */
    public int last() {
        return indices[indices.length - 1];
    }

    /** This path with one more hop appended. */
    public MountPath child(int index) {
        int[] next = Arrays.copyOf(indices, indices.length + 1);
        next[indices.length] = index;
        return new MountPath(hostType, next);
    }

    /** This path with its last hop removed; a host root has no parent (throws). */
    public MountPath parent() {
        return new MountPath(hostType, Arrays.copyOf(indices, indices.length - 1));
    }

    /** A defensive copy of the raw indices, for serialization. */
    public int[] toArray() {
        return indices.clone();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof MountPath other
                && hostType == other.hostType && Arrays.equals(indices, other.indices));
    }

    @Override
    public int hashCode() {
        return 31 * hostType.hashCode() + Arrays.hashCode(indices);
    }

    @Override
    public String toString() {
        return "MountPath(" + hostType + ")" + Arrays.toString(indices);
    }
}
