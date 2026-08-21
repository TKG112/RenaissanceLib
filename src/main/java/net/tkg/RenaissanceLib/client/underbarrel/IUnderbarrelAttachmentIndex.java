package net.tkg.RenaissanceLib.client.underbarrel;

import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;

import javax.annotation.Nullable;

/**
 * Exposes the private {@code display} that TaC:Z's {@code ClientAttachmentIndex} already holds, so the
 * underbarrel display extras ({@link IUnderbarrelDisplay}) can be read back from an attachment id.
 * TaC:Z offers {@code getData()} but no {@code getDisplay()}; this fills that gap.
 */
public interface IUnderbarrelAttachmentIndex {
    @Nullable
    AttachmentDisplay renaissance$getDisplay();
}
