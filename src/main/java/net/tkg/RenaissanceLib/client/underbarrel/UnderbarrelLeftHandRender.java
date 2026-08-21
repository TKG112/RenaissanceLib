package net.tkg.RenaissanceLib.client.underbarrel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.util.RenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the player's left (support) arm at the underbarrel's own {@code lefthand_pos} bone while the
 * underbarrel is the active weapon, so the support hand follows the underbarrel's animation. Mirrors TaC:Z's
 * {@link com.tacz.guns.client.model.functional.LeftHandRender}: the modeled arm geometry on that bone is a
 * placeholder for positioning — being a functional renderer, this replaces it with the real first-person arm
 * at the bone's transform. Runs only while the underbarrel is active — the host's own left-hand render is
 * cancelled in that case (see {@code LeftHandRenderMixin}); the right hand stays on the host grip.
 *
 * <p>Registered on the {@code lefthand_pos} bone by {@link UnderbarrelRenderRegistrar}.
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelLeftHandRender implements IFunctionalRenderer {
    private final BedrockAttachmentModel model;

    public UnderbarrelLeftHandRender(BedrockAttachmentModel model) {
        this.model = model;
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexConsumer, ItemDisplayContext ctx,
                       int light, int overlay) {
        if (!ctx.firstPerson()) return;
        // Hide the support arm while the refit screen is open, matching how TaC:Z hides the host's hands.
        if (RefitTransform.getOpeningProgress() != 0) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack gun = player.getMainHandItem();
        if (!Underbarrel.hasUnderbarrel(gun)) return;

        // While fully in main-gun mode the host renderer draws its own hand; we only take over once a switch
        // starts (factor > 0), gliding the arm from the host grip to the underbarrel grip and back.
        float t = UnderbarrelTransition.factor();
        if (t <= 0f) return;

        // Both grips are captured as raw bone poses in the same view space (host by LeftHandRenderMixin,
        // underbarrel here), then we apply the shared ZP-180 hand-flip convention.
        Matrix4f underbarrelGrip = new Matrix4f(poseStack.last().pose());
        Matrix4f hostGrip = UnderbarrelHandAnchor.hostHand();
        Matrix4f base = interpolateGrip(hostGrip, underbarrelGrip, t);

        Matrix4f drawPose = new Matrix4f(base).rotateZ((float) Math.PI);
        Matrix3f normal = new Matrix3f(drawPose);
        // Arms share the gun's vertex buffer, so defer the draw to the end of the model render (as TaC:Z does).
        model.delegateRender((poseStack1, vertexBuffer1, transformType1, light1, overlay1) -> {
            PoseStack armPose = new PoseStack();
            armPose.last().normal().mul(normal);
            armPose.last().pose().mul(drawPose);
            RenderHelper.renderFirstPersonArm(player, HumanoidArm.LEFT, armPose, light1);
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        });
    }

    /**
     * Blends the support hand between the host grip and the underbarrel grip so the hand travels a
     * <em>straight line</em> between them. Uses a component-wise matrix lerp rather than decompose +
     * translation-lerp/rotation-slerp: a point is transformed <em>linearly</em> by a matrix, so lerping the
     * two grip matrices element-by-element moves the hand (a fixed point in the arm's local space, well off
     * the {@code lefthand_pos} pivot) along a straight line — regardless of the pivot offset. Slerping the
     * pivot rotation instead swings the arm through an arc (hand lurches up); holding one grip's rotation
     * flings the hand to a wrong spot (the offset rotates with it). The mid-blend rotation isn't perfectly
     * rigid (a slight, brief shear) but that's imperceptible over a quick switch. Exact at both endpoints
     * ({@code t=0}→host, {@code t=1}→underbarrel), so the handoff to the plain renders is seamless.
     */
    private static Matrix4f interpolateGrip(Matrix4f hostGrip, Matrix4f underbarrelGrip, float t) {
        if (hostGrip == null || t >= 1f) return underbarrelGrip;
        return new Matrix4f(hostGrip).lerp(underbarrelGrip, t);
    }
}
