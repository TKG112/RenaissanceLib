package net.tkg.RenaissanceLib.client.refit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Debug overlay for the refit turntable (toggle with P in the interactive refit screen): the pivot the gun turns
 * about, and the bounding box it's derived from, drawn as a wireframe that turns with the gun.
 *
 * <p>How: when TaC:Z has positioned the first-person gun ({@code RefitOrbitMixin}, at the end of
 * {@code applyFirstPersonPositioningTransform}), the pose there plus the first-person projection map pivot-space
 * points to the screen; we project the pivot and the box's 8 corners, and the screen draws them after the GUI.
 * The same projection is what the floating slot cards will use (stage 2).
 */
@OnlyIn(Dist.CLIENT)
public final class RefitDebug {
    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},  // along X
            {0, 2}, {1, 3}, {4, 6}, {5, 7},  // along Y
            {0, 4}, {1, 5}, {2, 6}, {3, 7}}; // along Z

    private static boolean enabled = false;

    // Last captured frame (GUI-scaled screen coordinates; NaN = behind the camera).
    private static final float[] cornerX = new float[8], cornerY = new float[8];
    private static float pivotX = Float.NaN, pivotY = Float.NaN;
    private static boolean hasBox = false, override = false;
    private static final Vector3f boxSizePx = new Vector3f();

    private RefitDebug() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    /**
     * Capture the projected pivot + box. {@code poseStack} is TaC:Z's pose at the end of the positioning method,
     * i.e. {@code pre · T(0,1.5,0) · M · T(0,-1.5,0)}; a pivot-space point p renders at {@code pose · T(0,1.5,0) · p}.
     */
    public static void capture(PoseStack poseStack, BedrockGunModel model) {
        if (!enabled || model == null) return;
        Matrix4f toClip = new Matrix4f(RenderSystem.getProjectionMatrix())
                .mul(poseStack.last().pose())
                .translate(0f, 1.5f, 0f);

        Vector3f pivot = RefitOrbit.pivot(model);
        float[] p = project(toClip, pivot);
        pivotX = p[0];
        pivotY = p[1];
        override = RefitOrbit.hasPivotOverride(model);

        RefitOrbit.Bounds box = RefitOrbit.bounds(model);
        hasBox = box.min() != null;
        if (hasBox) {
            Vector3f min = box.min(), max = box.max();
            boxSizePx.set(max).sub(min).mul(16f);
            for (int i = 0; i < 8; i++) {
                Vector3f c = new Vector3f((i & 1) == 0 ? min.x : max.x, (i & 2) == 0 ? min.y : max.y,
                        (i & 4) == 0 ? min.z : max.z);
                float[] s = project(toClip, c);
                cornerX[i] = s[0];
                cornerY[i] = s[1];
            }
        }
    }

    /** Pivot-space point → GUI-scaled screen coordinates ({NaN, NaN} if behind the camera). */
    private static float[] project(Matrix4f toClip, Vector3f point) {
        Vector4f clip = toClip.transform(new Vector4f(point, 1f));
        if (clip.w <= 1e-4f) return new float[]{Float.NaN, Float.NaN};
        var window = Minecraft.getInstance().getWindow();
        float x = (clip.x / clip.w * 0.5f + 0.5f) * window.getGuiScaledWidth();
        float y = (0.5f - clip.y / clip.w * 0.5f) * window.getGuiScaledHeight();
        return new float[]{x, y};
    }

    /** Draw the last captured pivot + box over the screen. */
    public static void draw(GuiGraphics graphics) {
        if (!enabled) return;
        Matrix4f pose = graphics.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        if (hasBox) {
            for (int[] e : EDGES) {
                line(buf, pose, cornerX[e[0]], cornerY[e[0]], cornerX[e[1]], cornerY[e[1]], 0x40, 0xC0, 0xFF);
            }
        }
        if (!Float.isNaN(pivotX)) {
            float r = 6f;
            line(buf, pose, pivotX - r, pivotY, pivotX + r, pivotY, 0xFF, 0x40, 0x40);
            line(buf, pose, pivotX, pivotY - r, pivotX, pivotY + r, 0xFF, 0x40, 0x40);
        }
        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();

        var font = Minecraft.getInstance().font;
        String what = override ? "pivot: refit_pivot bone" : "pivot: bounding-box centre";
        String size = hasBox ? String.format(" | box %.1f x %.1f x %.1f px", boxSizePx.x, boxSizePx.y, boxSizePx.z)
                : " | no box (mount-bone fallback)";
        graphics.drawString(font, "[P] refit debug  " + what + size, 4, 4, 0xFFFFFF, true);
        if (!Float.isNaN(pivotX)) graphics.fill((int) pivotX - 1, (int) pivotY - 1, (int) pivotX + 2, (int) pivotY + 2, 0xFFFF4040);
    }

    private static void line(BufferBuilder buf, Matrix4f pose, float x0, float y0, float x1, float y1,
                             int r, int g, int b) {
        if (Float.isNaN(x0) || Float.isNaN(x1)) return;
        buf.vertex(pose, x0, y0, 0f).color(r, g, b, 255).endVertex();
        buf.vertex(pose, x1, y1, 0f).color(r, g, b, 255).endVertex();
    }
}
