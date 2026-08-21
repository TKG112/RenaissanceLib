package net.tkg.RenaissanceLib.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.client.ActiveOptic;
import net.tkg.RenaissanceLib.client.RailAim;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelCameraAnchor;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelTransition;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Routes first-person aim through a mounted rail sight when the combined zoom cycle (see
 * {@link ActiveOptic}) has landed on one, and eases the camera between cycle positions.
 *
 * <p>Two hooks:
 * <ul>
 *   <li><b>View path</b> — redirects {@code getScopeViewPath} so the aim path becomes
 *       {@code canted_N + the sight's own scope_view} when a rail sight is active (and the correct
 *       scope view otherwise), making the camera look down the canted optic.</li>
 *   <li><b>Easing</b> — redirects the aiming {@code getPositioningNodeInverse} to ease the resulting
 *       matrix ourselves (see {@link RailAim#easedAimMatrix}). TaC:Z's own view-transition smoother is
 *       keyed on {@code viewIndex}, which is constant for a single-view mount, so it snaps; taking over
 *       the matrix lets scope↔sight (and sight↔sight) transitions glide. For guns without a rail sight
 *       we return TaC:Z's exact value and leave its smoother in charge.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = FirstPersonRenderGunEvent.class, remap = false)
public abstract class FirstPersonRenderGunEventMixin {

    @Shadow
    private static Matrix4f getPositioningNodeInverse(java.util.List<BedrockPart> nodePath) {
        throw new AssertionError("shadow");
    }

    /**
     * Adds the underbarrel's animation movement onto the host's first-person anchor while it's the active
     * weapon, without replacing it — so the host keeps its full idle/sway/crouch and the underbarrel's
     * shoot/reload motion is layered on top (moving the whole weapon). This is the idle anchor
     * ({@code getPositioningNodeInverse(idleNodePath)}, ordinal 1). The added delta is the underbarrel
     * {@code camera} node's own animation (zero at idle, so a pure no-op then); see
     * {@link UnderbarrelCameraAnchor#animationDelta()}. Ordinary guns / main-weapon mode return TaC:Z's value
     * unchanged, and the refit screen is left entirely alone.
     */
    @Redirect(
            method = "applyFirstPersonPositioningTransform",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/client/event/FirstPersonRenderGunEvent;"
                            + "getPositioningNodeInverse(Ljava/util/List;)Lorg/joml/Matrix4f;",
                    ordinal = 1),
            remap = false
    )
    private static Matrix4f renaissance$anchorToUnderbarrelCamera(
            List<BedrockPart> idleNodePath,
            PoseStack poseStack, BedrockGunModel model, ItemStack stack,
            float aimingProgress, float refitScreenOpeningProgress) {
        Matrix4f mIdle = getPositioningNodeInverse(idleNodePath);
        // Ease the host↔underbarrel anchor every frame so switching glides rather than snapping: the factor
        // ramps 0..1 and scales the camera delta in/out (the animator publishes it in both modes). Gate the
        // delta on this gun actually having an underbarrel, so the ease-out plays on the same gun but a
        // stale delta is never applied to a different (e.g. plain) gun the player just swapped to.
        boolean hasUnderbarrel = Underbarrel.hasUnderbarrel(stack);
        float transition = UnderbarrelTransition.update(hasUnderbarrel && ActiveWeapon.isUnderbarrelActive(stack));
        if (RefitTransform.getOpeningProgress() != 0) {
            return mIdle;
        }
        if (hasUnderbarrel && transition > 0f) {
            Matrix4f delta = UnderbarrelCameraAnchor.animationDelta(transition);
            if (delta != null) {
                return new Matrix4f(mIdle).mul(delta);
            }
        }
        return mIdle;
    }

    @Redirect(
            method = "applyFirstPersonPositioningTransform",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/client/model/BedrockAttachmentModel;getScopeViewPath(I)Ljava/util/List;"),
            remap = false
    )
    private static List<BedrockPart> renaissance$railViewPath(
            BedrockAttachmentModel attachmentModel, int viewSwitchCount,
            PoseStack poseStack, BedrockGunModel model, ItemStack stack,
            float aimingProgress, float refitScreenOpeningProgress) {
        // Untouched for ordinary guns — leave TaC:Z's own view selection alone.
        if (!ActiveOptic.hasMountedRailSight(stack)) {
            return attachmentModel.getScopeViewPath(viewSwitchCount);
        }
        ActiveOptic optic = ActiveOptic.resolve(stack);
        if (optic == null) {
            return attachmentModel.getScopeViewPath(viewSwitchCount);
        }
        if (!optic.isScope()) {
            List<BedrockPart> suffix = RailAim.buildViewSuffix(stack, optic);
            if (suffix != null) {
                return suffix;
            }
            return attachmentModel.getScopeViewPath(viewSwitchCount);
        }
        // Scope stage of the combined cycle: pick the scope view from the stage-local index.
        ClientAttachmentIndex scopeIndex = optic.index();
        if (scopeIndex != null && scopeIndex.getViews() != null && scopeIndex.getViews().length > 0) {
            int[] views = scopeIndex.getViews();
            int viewIndex = views[Math.floorMod(optic.localIndex, views.length)] - 1;
            return attachmentModel.getScopeViewPath(viewIndex);
        }
        return attachmentModel.getScopeViewPath(viewSwitchCount);
    }

    @Redirect(
            method = "applyFirstPersonPositioningTransform",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/client/event/FirstPersonRenderGunEvent;"
                            + "getPositioningNodeInverse(Ljava/util/List;)Lorg/joml/Matrix4f;",
                    ordinal = 0),
            remap = false
    )
    private static Matrix4f renaissance$easeAimMatrix(
            List<BedrockPart> nodePath,
            PoseStack poseStack, BedrockGunModel model, ItemStack stack,
            float aimingProgress, float refitScreenOpeningProgress) {
        return RailAim.easedAimMatrix(nodePath, stack);
    }
}
