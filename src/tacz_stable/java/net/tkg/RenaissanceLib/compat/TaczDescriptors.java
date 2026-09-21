package net.tkg.RenaissanceLib.compat;

/**
 * Mixin {@code method=} descriptors for TaC:Z methods whose signature differs between the stable release and
 * the beta, and which are <b>overloaded</b> in the beta (so a name-only selector would be ambiguous). These are
 * compile-time constants, so {@code @Inject(method = TaczDescriptors.RENDER)} inlines the right descriptor for
 * the variant being built. <b>STABLE variant</b>; the beta copy is in {@code src/tacz_beta/java}.
 *
 * <p>Only overloaded/moved methods need this — signature changes on <em>uniquely named</em> methods are handled
 * with plain name-only selectors in the shared mixins.
 */
public final class TaczDescriptors {

    private TaczDescriptors() {}

    /** {@code BedrockGunModel.render} — the first-person gun render (stencil masking lives here). */
    public static final String RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V";

    /** {@code BedrockAttachmentModel.renderOcularAndDivision} — the scope lens ocular/reticle draw. */
    public static final String OCULAR =
            "renderOcularAndDivision(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIZ)V";
}
