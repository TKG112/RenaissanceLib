package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.item.attachment.AttachmentType;

/**
 * Accessor for {@code ClientMessageRefitGun}'s private fields, so the item-link install intercept can read them.
 * Lives outside the mixin package (Mixin forbids directly-referenced classes there); the mixin implements it.
 */
public interface IClientMessageRefitGun {
    int renaissance$attachmentSlotIndex();

    AttachmentType renaissance$attachmentType();
}
