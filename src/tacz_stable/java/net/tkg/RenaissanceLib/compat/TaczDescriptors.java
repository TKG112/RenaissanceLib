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

    /**
     * Ordinal of the <em>idle</em>-anchor {@code getPositioningNodeInverse(List)} call in
     * {@code applyFirstPersonPositioningTransform}. On the release the aim anchor also uses that 1-arg method
     * (so idle is the 2nd call, ordinal 1); the beta moved the aim anchor to a new 3-arg overload, so idle
     * becomes the 1st 1-arg call (ordinal 0).
     */
    public static final int IDLE_ANCHOR_ORDINAL = 1;

    /** {@code BedrockGunModel.render} — the first-person gun render (stencil masking lives here). */
    public static final String RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V";

    /** {@code BedrockAttachmentModel.render(attachment, gun, ...)} — the attachment draw (attachment stack first). */
    public static final String ATT_RENDER =
            "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;"
                    + "Lnet/minecraft/client/renderer/RenderType;II)V";

    /** {@code LeftHandRender.render} — the support-hand functional renderer (5-arg on stable). */
    public static final String HAND_RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;II)V";

    /** {@code BedrockAttachmentModel.renderOcularAndDivision} — the scope lens ocular/reticle draw. */
    public static final String OCULAR =
            "renderOcularAndDivision(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIZ)V";
}
