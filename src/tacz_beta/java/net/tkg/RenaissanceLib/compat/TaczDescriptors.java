package net.tkg.RenaissanceLib.compat;

/**
 * Mixin {@code method=} descriptors for TaC:Z methods whose signature differs between the stable release and
 * the beta, and which are <b>overloaded</b> in the beta (so a name-only selector would be ambiguous). These are
 * compile-time constants, so {@code @Inject(method = TaczDescriptors.RENDER)} inlines the right descriptor for
 * the variant being built. <b>BETA variant</b>; the stable copy is in {@code src/tacz_stable/java}.
 *
 * <p>The beta threaded a {@link net.minecraft.client.renderer.MultiBufferSource.BufferSource} through the FP
 * render pipeline and kept the old overloads as (empty/delegating) stubs, moving the real stencil work into the
 * new BufferSource overloads — so we must target those exact overloads, not the same-named originals.
 */
public final class TaczDescriptors {

    private TaczDescriptors() {}

    /**
     * Ordinal of the <em>idle</em>-anchor {@code getPositioningNodeInverse(List)} call in
     * {@code applyFirstPersonPositioningTransform}. The beta moved the aim anchor to a new 3-arg overload, so
     * the idle anchor is the 1st 1-arg call (ordinal 0). (On the release it's ordinal 1, since the aim anchor
     * also uses the 1-arg method there.)
     */
    public static final int IDLE_ANCHOR_ORDINAL = 0;

    /** {@code BedrockGunModel.render} — beta's BufferSource overload (11 args) carries the stencil masking. */
    public static final String RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFF"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V";

    /**
     * {@code BedrockAttachmentModel.render(attachment, gun, ...)} — the overload TaC:Z actually calls to draw an
     * attachment. The beta routes through the BufferSource overload ({@code ...II, float, BufferSource}); the
     * plain 7-arg one is a convenience stub that isn't on the render path, so our hook must target this.
     */
    public static final String ATT_RENDER =
            "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;"
                    + "Lnet/minecraft/client/renderer/RenderType;IIFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V";

    /**
     * {@code LeftHandRender.render} — the support-hand functional renderer. The render loop calls the
     * {@code IFunctionalRenderer} 6-arg BufferSource overload, which LeftHandRender overrides, so we must hook
     * that exact overload on the beta.
     */
    public static final String HAND_RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;IILnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V";

    /** {@code BedrockAttachmentModel.renderOcularAndDivision} — beta's BufferSource overload (7 args). */
    public static final String OCULAR =
            "renderOcularAndDivision(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIZ"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V";
}
