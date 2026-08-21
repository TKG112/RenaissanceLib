package net.tkg.RenaissanceLib.client;

import net.minecraft.world.item.ItemStack;

/**
 * Exposes a {@code BedrockAttachmentModel}'s live {@code currentGunItem} (set each render) so a
 * rail-node functional renderer can resolve which gun — and therefore which mounted rail sight —
 * it is drawing for. Implemented on the model via {@code BedrockAttachmentModelMixin}.
 *
 * <p>Lives outside the {@code mixin} package on purpose: classes in a declared mixin package are
 * owned by the mixin processor and cannot be referenced directly by ordinary code (such as
 * {@link RailSightRenderer}).
 */
public interface IRailGunItemAccessor {
    ItemStack renaissance$getCurrentGunItem();

    /**
     * The live {@code attachmentItem} (the item whose model is being drawn this pass). This is the
     * per-level <em>host</em> in the recursive rail tree: the scope-slot attachment at the top, or a
     * mounted optic when a nested model is rendering. May be {@code null} — notably TaC:Z passes
     * {@code null} when rendering an attachment as a standalone item (handled via
     * {@link RailStandaloneContext}).
     */
    ItemStack renaissance$getAttachmentItem();
}
