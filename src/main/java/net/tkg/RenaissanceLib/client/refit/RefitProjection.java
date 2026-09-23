package net.tkg.RenaissanceLib.client.refit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;

/**
 * Maps points on the refit gun to the screen, so GUI elements (the slot callouts, the debug overlay) can sit on its
 * parts as it turns. Captured once per frame when TaC:Z has positioned the first-person gun ({@code RefitOrbitMixin},
 * at the end of {@code applyFirstPersonPositioningTransform}): the pose there is {@code pre · T(0,1.5,0) · M ·
 * T(0,-1.5,0)}, so a pivot-space point p ({@link RefitOrbit}) is at {@code pose · T(0,1.5,0) · p} in view space, and
 * the first-person projection (current during the hand pass) takes it to clip space.
 */
@OnlyIn(Dist.CLIENT)
public final class RefitProjection {
    private static final Matrix4f toView = new Matrix4f();
    private static final Matrix4f toClip = new Matrix4f();
    @Nullable
    private static BedrockGunModel model;

    private RefitProjection() {}

    /** A projected point: GUI-scaled screen position, and distance in front of the camera (blocks). */
    public record Point(float x, float y, float depth) {}

    public static void capture(PoseStack poseStack, BedrockGunModel gunModel) {
        toView.set(poseStack.last().pose()).translate(0f, 1.5f, 0f);
        toClip.set(RenderSystem.getProjectionMatrix()).mul(toView);
        model = gunModel;
    }

    /** The gun model last captured (the one in the refit screen), or {@code null} before the first frame. */
    @Nullable
    public static BedrockGunModel model() {
        return model;
    }

    /** Project a pivot-space point; {@code null} if it's behind the camera or nothing's been captured yet. */
    @Nullable
    public static Point project(Vector3f pivotSpace) {
        if (model == null || pivotSpace == null) return null;
        Vector4f clip = toClip.transform(new Vector4f(pivotSpace, 1f));
        if (clip.w <= 1e-4f) return null;
        var window = Minecraft.getInstance().getWindow();
        float x = (clip.x / clip.w * 0.5f + 0.5f) * window.getGuiScaledWidth();
        float y = (0.5f - clip.y / clip.w * 0.5f) * window.getGuiScaledHeight();
        float depth = -toView.transformPosition(new Vector3f(pivotSpace)).z;
        return new Point(x, y, depth);
    }
}
