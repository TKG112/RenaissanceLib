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

    /** {@code BedrockGunModel.render} — beta's BufferSource overload (11 args) carries the stencil masking. */
    public static final String RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFF"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V";

    /** {@code BedrockAttachmentModel.renderOcularAndDivision} — beta's BufferSource overload (7 args). */
    public static final String OCULAR =
            "renderOcularAndDivision(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIZ"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V";
}
