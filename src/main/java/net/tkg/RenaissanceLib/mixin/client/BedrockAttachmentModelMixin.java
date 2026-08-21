package net.tkg.RenaissanceLib.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelAnimator;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelClient;
import net.tkg.RenaissanceLib.client.IRailGunItemAccessor;
import net.tkg.RenaissanceLib.client.IrisCompat;
import net.tkg.RenaissanceLib.client.RailPassengerClip;
import net.tkg.RenaissanceLib.client.ShaderManager;
import net.tkg.RenaissanceLib.client.ScopeShaderRenderer;
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
            method = "renderScope(Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At("HEAD"), cancellable = true, remap = false
    )
    private void renaissance$clipPassenger_Scope(
            PoseStack matrixStack, ItemDisplayContext transformType,
            RenderType renderType, int light, int overlay, CallbackInfo ci) {
        if (renaissance$renderClippedPassenger(matrixStack, transformType, renderType, light, overlay)) ci.cancel();
    }

    @Inject(
            method = "renderSight(Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At("HEAD"), cancellable = true, remap = false
    )
    private void renaissance$clipPassenger_Sight(
            PoseStack matrixStack, ItemDisplayContext transformType,
            RenderType renderType, int light, int overlay, CallbackInfo ci) {
        if (renaissance$renderClippedPassenger(matrixStack, transformType, renderType, light, overlay)) ci.cancel();
    }

    @Inject(
            method = "renderBoth(Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At("HEAD"), cancellable = true, remap = false
    )
    private void renaissance$clipPassenger_Both(
            PoseStack matrixStack, ItemDisplayContext transformType,
            RenderType renderType, int light, int overlay, CallbackInfo ci) {
        if (renaissance$renderClippedPassenger(matrixStack, transformType, renderType, light, overlay)) ci.cancel();
    }

    private static final String RENDER_OCULAR_AND_DIVISION =
            "Lcom/tacz/guns/client/model/BedrockAttachmentModel;" +
                    "renderOcularAndDivision(" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;IIZ)V";

    @Inject(
            method = "renderScope(Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At(value = "INVOKE", target = RENDER_OCULAR_AND_DIVISION, shift = At.Shift.BEFORE),
            remap = false
    )
    private void renaissance$beforeOcularAndDivision_Scope(
            PoseStack matrixStack, ItemDisplayContext transformType,
            RenderType renderType, int light, int overlay, CallbackInfo ci) {
        // Post-shader composites here (before the carve, whole ocular). Vision composites AFTER the carve
        // into the aperture instead (see the TAIL hook) — that's what lets the double render use the real
        // main target without breaking TaCZ's ocular carve.
        if (ShaderManager.isShaderActive()) {
            ScopeShaderRenderer.compositeIntoLens(0);
        }
    }

    @Inject(
            method = "renderBoth(Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At(value = "INVOKE", target = RENDER_OCULAR_AND_DIVISION, shift = At.Shift.BEFORE),
            remap = false
    )
    private void renaissance$beforeOcularAndDivision_Both(
            PoseStack matrixStack, ItemDisplayContext transformType,
            RenderType renderType, int light, int overlay, CallbackInfo ci) {
        if (ShaderManager.isShaderActive()) {
            ScopeShaderRenderer.compositeIntoLens(2);
        }
    }

    @Inject(
            method = "renderOcularAndDivision(Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/world/item/ItemDisplayContext;" +
                    "Lnet/minecraft/client/renderer/RenderType;IIZ)V",
            at = @At("TAIL"),
            remap = false
    )
    private void renaissance$afterOcularAndDivision(
            PoseStack matrixStack, ItemDisplayContext transformType,
            RenderType renderType, int light, int overlay, boolean selective, CallbackInfo ci) {
        // Under a shaderpack the post-shader composites the lens mask at end-of-frame; capture it here.
        if (IrisCompat.isShaderPackInUse() && ShaderManager.isShaderActive()) {
            ScopeShaderRenderer.captureLensMaskIris();
        }
    }

}
