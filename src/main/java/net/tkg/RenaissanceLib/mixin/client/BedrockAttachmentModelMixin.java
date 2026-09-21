package net.tkg.RenaissanceLib.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.compat.ar.ARCompat;
import com.llamalad7.mixinextras.sugar.Local;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelAnimator;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelClient;
import net.tkg.RenaissanceLib.client.IRailGunItemAccessor;
import net.tkg.RenaissanceLib.client.RailPassengerClip;
import net.tkg.RenaissanceLib.client.ShaderManager;
import net.tkg.RenaissanceLib.client.ScopeShaderRenderer;
import net.tkg.RenaissanceLib.compat.TaczCompat;
import net.tkg.RenaissanceLib.compat.TaczDescriptors;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
@Mixin(value = BedrockAttachmentModel.class, remap = false)
public abstract class BedrockAttachmentModelMixin implements IRailGunItemAccessor {

    @Shadow
    private ItemStack currentGunItem;

    @Shadow
    private ItemStack attachmentItem;

    /**
     * Bones that belong to a full gun model but must not render when that model is drawn as an on-gun
     * underbarrel attachment: the off-hand support-arm chain and the reload shell casing. The underbarrel
     * file is authored as a complete sub-gun, so without this its support arm and stray shell would render
     * stuck to the host gun.
     *
     * <p>When the underbarrel is the <em>active</em> weapon, we keep the support-arm chain visible so its
     * {@code lefthand_pos} bone is traversed and {@code UnderbarrelLeftHandRender} can draw the player arm
     * there; only the shell casing stays hidden. When the host gun is active, the whole set is hidden. See
     * {@link #RENAISSANCE_UB_SHELL_BONES}.
     */
    private static final Set<String> RENAISSANCE_UB_HIDDEN_BONES =
            Set.of("lefthand", "lefthand_pos", "lh_shell", "shell_bullet");

    /** The subset always hidden in the attachment pass (a stray reload shell casing), even when active. */
    private static final Set<String> RENAISSANCE_UB_SHELL_BONES = Set.of("shell_bullet");

    /** Guards the underbarrel-render-prep error log so a per-frame failure logs once, not every frame. */
    private static boolean renaissance$ubRenderErrorLogged = false;

    @Shadow
    protected List<BedrockPart> scopeBodyPath;

    @Shadow
    protected List<BedrockPart> ocularRingPath;

    @Shadow
    private void renderTempPart(PoseStack poseStack, ItemDisplayContext transformType, RenderType renderType,
                               int light, int overlay, List<BedrockPart> path) {
        throw new AssertionError("shadow");
    }

    @Override
    public ItemStack renaissance$getCurrentGunItem() {
        return currentGunItem;
    }

    @Override
    public ItemStack renaissance$getAttachmentItem() {
        return attachmentItem;
    }

    /** The render(...) descriptor — attachment stack first, gun stack second (verified). */
    private static final String RENAISSANCE_ATT_RENDER =
            "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;II)V";

    /**
     * At the start of an underbarrel's attachment render: (1) hide its gun-only bones (support arm, reload
     * shell — see {@link #RENAISSANCE_UB_HIDDEN_BONES}); (2) if the display authored a {@code mount_offset},
     * nudge the whole model by it (a fallback for placing the launcher on a gun with no dedicated underbarrel
     * mount node). The matching pose pop is in {@link #renaissance$underbarrelRenderReturn}.
     *
     * <p>Note the parameter order: TaC:Z's {@code render} takes the <em>attachment</em> stack first, then the
     * gun stack (verified: param1 → {@code attachmentItem}, param2 → {@code currentGunItem}).
     */
    @Inject(method = RENAISSANCE_ATT_RENDER, at = @At("HEAD"), remap = false)
    private void renaissance$underbarrelRenderHead(
            ItemStack attachment, ItemStack gunItem, PoseStack poseStack, ItemDisplayContext transformType,
            RenderType renderType, int light, int overlay, CallbackInfo ci) {
        if (attachment == null || attachment.isEmpty()) return;
        if (!Underbarrel.isUnderbarrel(attachment)) return;

        // Animation + bone-hiding are wrapped so that if anything throws (e.g. a TaC:Z-beta model/animation API
        // change), it doesn't abort the whole attachment render — which would drop the underbarrel to its flat
        // 2D item fallback. The first failure is logged once with the real cause.
        try {
            // Pose the underbarrel for its current animation frame (idle/shoot/reload) before it draws.
            UnderbarrelAnimator.apply((BedrockAttachmentModel) (Object) this, attachment);

            // Hide gun-only bones. Walk the parts the model actually renders (shouldRender), not the animated-model
            // `root` field (null for a plain grip attachment). When the underbarrel is the active weapon we keep
            // its support-arm chain visible (so the left hand follows it) and hide only the shell casing; otherwise
            // the whole gun-only set is hidden. Also keep the arm chain visible while a host<->underbarrel switch is
            // easing (transition factor > 0), so the interpolated support hand doesn't vanish mid-transition.
            boolean active = ActiveWeapon.isUnderbarrelActive(gunItem)
                    || net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelTransition.factor() > 0f;
            List<BedrockPart> parts = ((BedrockModel) (Object) this).getShouldRender();
            if (parts != null) {
                for (BedrockPart part : parts) {
                    renaissance$applyUnderbarrelBoneVisibility(part, active);
                }
            }
        } catch (Throwable t) {
            if (!renaissance$ubRenderErrorLogged) {
                renaissance$ubRenderErrorLogged = true;
                RenaissanceLibMod.LOGGER.error("[RenaissanceLib] underbarrel render prep failed "
                        + "(model falls back to 2D); animation/bone-hiding skipped", t);
            }
        }

        // Manual mount nudge (fallback placement). Pushed here, popped at RETURN under the same condition.
        float[] off = UnderbarrelClient.getMountOffset(attachment);
        if (off != null) {
            poseStack.pushPose();
            poseStack.translate(off[0] / 16.0f, off[1] / 16.0f, off[2] / 16.0f);
            poseStack.mulPose(Axis.XP.rotationDegrees(off[3]));
            poseStack.mulPose(Axis.YP.rotationDegrees(off[4]));
            poseStack.mulPose(Axis.ZP.rotationDegrees(off[5]));
        }
    }

    /** Pops the {@code mount_offset} transform pushed in {@link #renaissance$underbarrelRenderHead}. */
    @Inject(method = RENAISSANCE_ATT_RENDER, at = @At("RETURN"), remap = false)
    private void renaissance$underbarrelRenderReturn(
            ItemStack attachment, ItemStack gunItem, PoseStack poseStack, ItemDisplayContext transformType,
            RenderType renderType, int light, int overlay, CallbackInfo ci) {
        if (attachment == null || attachment.isEmpty()) return;
        if (!Underbarrel.isUnderbarrel(attachment)) return;
        if (UnderbarrelClient.getMountOffset(attachment) != null) {
            poseStack.popPose();
        }
    }

    /**
     * Sets visibility on the underbarrel's gun-only bones each render (both directions, so switching weapons
     * re-shows them). When {@code active}, only the shell casing is hidden — the support-arm chain stays
     * visible so {@code UnderbarrelLeftHandRender} can draw the player arm at {@code lefthand_pos}. When not
     * active, the whole set is hidden so no stray arm/shell sticks to the host gun.
     */
    private void renaissance$applyUnderbarrelBoneVisibility(BedrockPart part, boolean active) {
        if (part == null) return;
        if (part.name != null && RENAISSANCE_UB_HIDDEN_BONES.contains(part.name)) {
            boolean hide = active ? RENAISSANCE_UB_SHELL_BONES.contains(part.name) : true;
            part.visible = !hide;
        }
        if (part.children != null) {
            for (BedrockPart child : part.children) {
                renaissance$applyUnderbarrelBoneVisibility(child, active);
            }
        }
    }

    /**
     * When this attachment model is being drawn as a clipped rail passenger (a mounted sight seen while
     * the player aims through a masking main scope), skip its whole stencil pipeline and instead draw only
     * its physical body ({@code scope_body} + {@code ocular_ring}) clipped against the scope's ocular
     * mask — which is still in the stencil buffer at this point (TaC:Z clears it only after the gun body).
     * This makes the passenger vanish inside the lens and stay visible around it, exactly like the barrel.
     *
     * @return {@code true} if handled as a passenger (caller should cancel the normal render).
     */
    private boolean renaissance$renderClippedPassenger(PoseStack matrixStack, ItemDisplayContext transformType,
                                                       RenderType renderType, int light, int overlay) {
        // On a buffered TaC:Z pipeline (beta) our immediate-mode stencil clip corrupts the ocular; let the
        // passenger render normally instead (no in-lens clip). See TaczCompat.CLIP_IN_LENS.
        if (!TaczCompat.CLIP_IN_LENS) return false;
        RailPassengerClip.Mask mask = RailPassengerClip.current();
        if (mask == null) return false;

        RenderHelper.enableItemEntityStencilTest();
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        RenderSystem.stencilFunc(mask.func(), mask.ref(), 0xFF);
        if (scopeBodyPath != null) {
            renderTempPart(matrixStack, transformType, renderType, light, overlay, scopeBodyPath);
        }
        if (ocularRingPath != null) {
            renderTempPart(matrixStack, transformType, renderType, light, overlay, ocularRingPath);
        }
        // Restore the state renderScope/renderSight would have left before their own super.render.
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        RenderHelper.disableItemEntityStencilTest();
        return true;
    }

    @Inject(
            method = "renderScope",
            at = @At("HEAD"), cancellable = true, remap = false
    )
    private void renaissance$clipPassenger_Scope(
            CallbackInfo ci,
            @Local(argsOnly = true, ordinal = 0) PoseStack matrixStack,
            @Local(argsOnly = true, ordinal = 0) ItemDisplayContext transformType,
            @Local(argsOnly = true, ordinal = 0) RenderType renderType,
            @Local(argsOnly = true, ordinal = 0) int light,
            @Local(argsOnly = true, ordinal = 1) int overlay) {
        if (renaissance$renderClippedPassenger(matrixStack, transformType, renderType, light, overlay)) ci.cancel();
    }

    @Inject(
            method = "renderSight",
            at = @At("HEAD"), cancellable = true, remap = false
    )
    private void renaissance$clipPassenger_Sight(
            CallbackInfo ci,
            @Local(argsOnly = true, ordinal = 0) PoseStack matrixStack,
            @Local(argsOnly = true, ordinal = 0) ItemDisplayContext transformType,
            @Local(argsOnly = true, ordinal = 0) RenderType renderType,
            @Local(argsOnly = true, ordinal = 0) int light,
            @Local(argsOnly = true, ordinal = 1) int overlay) {
        if (renaissance$renderClippedPassenger(matrixStack, transformType, renderType, light, overlay)) ci.cancel();
    }

    @Inject(
            method = "renderBoth",
            at = @At("HEAD"), cancellable = true, remap = false
    )
    private void renaissance$clipPassenger_Both(
            CallbackInfo ci,
            @Local(argsOnly = true, ordinal = 0) PoseStack matrixStack,
            @Local(argsOnly = true, ordinal = 0) ItemDisplayContext transformType,
            @Local(argsOnly = true, ordinal = 0) RenderType renderType,
            @Local(argsOnly = true, ordinal = 0) int light,
            @Local(argsOnly = true, ordinal = 1) int overlay) {
        if (renaissance$renderClippedPassenger(matrixStack, transformType, renderType, light, overlay)) ci.cancel();
    }

    // ---- Accelerated Rendering (the `acceleratedrendering` mod) passenger clip --------------------
    // TaC:Z routes scope rendering through separate *Accelerated methods when that mod is active; those defer
    // draws and express stencil masking through ARCompat's layer + before/after runnables instead of immediate
    // GL. So the immediate-path hooks above never fire, and passengers wouldn't clip. These mirror the clip via
    // the same ARCompat API TaC:Z uses (layers -943/-942/-941 for the active optic; the passenger sits just
    // after, at RENAISSANCE_AR_PASSENGER_LAYER). BLIND — the `acceleratedrendering` mod isn't in the dev
    // workspace (shouldAccelerate() is false here), so the layer number is provisional and needs in-game tuning.

    /**
     * AR draw layer for a clipped passenger — after the active optic's layers (-943/-942/-941) and after the
     * gun body's own clip layer (TaC:Z uses -940 for it), on its own layer so their before/after runnables
     * don't clash. Provisional — needs in-game tuning with the mod installed.
     */
    private static final int RENAISSANCE_AR_PASSENGER_LAYER = -939;

    /** Accelerated counterpart of {@link #renaissance$renderClippedPassenger}: queues the body clipped via ARCompat. */
    private boolean renaissance$renderClippedPassengerAccelerated(PoseStack matrixStack, ItemDisplayContext transformType,
                                                                 RenderType renderType, int light, int overlay) {
        RailPassengerClip.Mask mask = RailPassengerClip.current();
        if (mask == null) return false;

        ARCompat.setRenderLayer(RENAISSANCE_AR_PASSENGER_LAYER);
        ARCompat.setRenderBeforeFunction(() -> {
            RenderHelper.enableItemEntityStencilTest();
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.stencilFunc(mask.func(), mask.ref(), 0xFF);
        });
        ARCompat.setRenderAfterFunction(() -> {
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            RenderHelper.disableItemEntityStencilTest();
        });
        if (scopeBodyPath != null) {
            renderTempPart(matrixStack, transformType, renderType, light, overlay, scopeBodyPath);
        }
        if (ocularRingPath != null) {
            renderTempPart(matrixStack, transformType, renderType, light, overlay, ocularRingPath);
        }
        ARCompat.resetRenderLayer();
        ARCompat.resetRenderBeforeFunction();
        ARCompat.resetRenderAfterFunction();
        return true;
    }

    @Inject(
            method = "renderScopeAccelerated(Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0
    )
    private void renaissance$clipPassengerAccelerated_Scope(
            PoseStack matrixStack, ItemDisplayContext transformType,
            RenderType renderType, int light, int overlay, CallbackInfo ci) {
        if (renaissance$renderClippedPassengerAccelerated(matrixStack, transformType, renderType, light, overlay)) ci.cancel();
    }

    @Inject(
            method = "renderSightAccelerated(Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0
    )
    private void renaissance$clipPassengerAccelerated_Sight(
            PoseStack matrixStack, ItemDisplayContext transformType,
            RenderType renderType, int light, int overlay, CallbackInfo ci) {
        if (renaissance$renderClippedPassengerAccelerated(matrixStack, transformType, renderType, light, overlay)) ci.cancel();
    }

    @Inject(
            method = "renderBothAccelerated(Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0
    )
    private void renaissance$clipPassengerAccelerated_Both(
            PoseStack matrixStack, ItemDisplayContext transformType,
            RenderType renderType, int light, int overlay, CallbackInfo ci) {
        if (renaissance$renderClippedPassengerAccelerated(matrixStack, transformType, renderType, light, overlay)) ci.cancel();
    }

    /**
     * Composites the shaded scene into the lens <em>after</em> TaC:Z's aperture carve but before it draws the
     * ocular mask + reticle. Injected right after the second {@code stencilOp} in {@code renderOcularAndDivision}
     * (the {@code KEEP,KEEP,KEEP} that ends the carve loop), so the carved-aperture stencil (bit 0x80) is set and
     * the composite paints only the aperture — leaving the ocular rim/mask region untouched. Compositing over the
     * whole ocular before the carve (the old approach) overwrote the scope's own inner ring, so TaC:Z's black mask
     * showed on top of it as a dark ring. The reticle division draws over this composite afterwards, on top.
     */
    @Inject(
            method = TaczDescriptors.OCULAR,
            at = @At(value = "INVOKE", ordinal = 1, shift = At.Shift.AFTER,
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;stencilOp(III)V"),
            remap = false
    )
    private void renaissance$compositeAfterCarve(CallbackInfo ci) {
        if (ShaderManager.isShaderActive()) {
            ScopeShaderRenderer.compositeIntoLens();
        }
    }

    @Inject(
            method = TaczDescriptors.OCULAR,
            at = @At("TAIL"),
            remap = false
    )
    private void renaissance$afterOcularAndDivision(CallbackInfo ci) {
        // For the end-of-frame composite (Iris shaderpack OR Accelerated Rendering), capture the lens mask now
        // — this runs in both the immediate and the AR-deferred ocular draw, so AR gets its mask too.
        if (ScopeShaderRenderer.useEndOfFramePath() && ShaderManager.isShaderActive()) {
            ScopeShaderRenderer.captureLensMaskIris();
        }
    }

}
