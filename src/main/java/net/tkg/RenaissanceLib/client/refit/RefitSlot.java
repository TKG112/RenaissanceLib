package net.tkg.RenaissanceLib.client.refit;

import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.MountPath;

/**
 * One card in the interactive refit screen: a TaC:Z slot, or one of ours — a rail mount (any depth), one of the
 * installed underbarrel's own slots, or the conversion-kit slot. The identity used for selection and for keeping a
 * card's placement between frames (records compare by value; {@link MountPath} has value equality).
 */
@OnlyIn(Dist.CLIENT)
public sealed interface RefitSlot {

    /** The TaC:Z refit camera view that frames this slot (the native slot it lives on). */
    AttachmentType cameraType();

    /** Whether TaC:Z's own attachment list serves this slot (else the picker draws its own options). */
    default boolean isNative() {
        return false;
    }

    /** A TaC:Z attachment slot on the gun. */
    record Native(AttachmentType type) implements RefitSlot {
        @Override
        public AttachmentType cameraType() {
            return type;
        }

        @Override
        public boolean isNative() {
            return true;
        }
    }

    /** A rail mount on a rail-hosting attachment, addressed by its full mount path (host slot + rail indices). */
    record Rail(MountPath path) implements RefitSlot {
        @Override
        public AttachmentType cameraType() {
            return path.hostType();
        }
    }

    /** One of the installed underbarrel's own attachment slots. */
    record UnderbarrelSlot(AttachmentType type) implements RefitSlot {
        @Override
        public AttachmentType cameraType() {
            return AttachmentType.GRIP;
        }
    }

    /**
     * The conversion-kit slot (conceptually under the magazine). Keeps the overview camera: a gun that doesn't take
     * extended mags may have no extended-mag refit view to frame.
     */
    record Conversion() implements RefitSlot {
        @Override
        public AttachmentType cameraType() {
            return AttachmentType.NONE;
        }
    }
}
