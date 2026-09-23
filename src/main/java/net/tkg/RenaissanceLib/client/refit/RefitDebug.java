package net.tkg.RenaissanceLib.client.refit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Properties;

/**
 * Debug overlay for the refit screen (toggle with P): the pivot the gun turns about (red cross) and the bounding box
 * it comes from (cyan wireframe), both turning with the gun ({@link RefitProjection}), plus the jar's build stamp so
 * a tester can tell which build is running. The slots themselves are shown by the cards ({@link RefitCallouts}).
 */
@OnlyIn(Dist.CLIENT)
public final class RefitDebug {
    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},  // along X
            {0, 2}, {1, 3}, {4, 6}, {5, 7},  // along Y
            {0, 4}, {1, 5}, {2, 6}, {3, 7}}; // along Z

    private static boolean enabled = false;

    private RefitDebug() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    public static void draw(GuiGraphics graphics) {
        if (!enabled) return;
        BedrockGunModel model = RefitProjection.model();
        if (model == null) return;
        Font font = Minecraft.getInstance().font;

        RefitProjection.Point pivot = RefitProjection.project(RefitOrbit.pivot(model));
        RefitOrbit.Bounds box = RefitOrbit.bounds(model);
        RefitProjection.Point[] corners = new RefitProjection.Point[8];
        if (box.min() != null) {
            Vector3f min = box.min(), max = box.max();
            for (int i = 0; i < 8; i++) {
                corners[i] = RefitProjection.project(new Vector3f((i & 1) == 0 ? min.x : max.x,
                        (i & 2) == 0 ? min.y : max.y, (i & 4) == 0 ? min.z : max.z));
            }
        }

        Matrix4f pose = graphics.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int[] e : EDGES) {
            RefitProjection.Point a = corners[e[0]], b = corners[e[1]];
            if (a != null && b != null) line(buf, pose, a.x(), a.y(), b.x(), b.y(), 0x40, 0xC0, 0xFF, 160);
        }
        if (pivot != null) {
            line(buf, pose, pivot.x() - 6, pivot.y(), pivot.x() + 6, pivot.y(), 0xFF, 0x40, 0x40, 255);
            line(buf, pose, pivot.x(), pivot.y() - 6, pivot.x(), pivot.y() + 6, 0xFF, 0x40, 0x40, 255);
        }
        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();
        if (pivot != null) {
            graphics.fill(Math.round(pivot.x()) - 1, Math.round(pivot.y()) - 1,
                    Math.round(pivot.x()) + 2, Math.round(pivot.y()) + 2, 0xFFFF4040);
        }

        String what = RefitOrbit.hasPivotOverride(model) ? "pivot: refit_pivot bone" : "pivot: bounding-box centre";
        String size = box.min() == null ? " | no box (mount-bone fallback)"
                : String.format(" | box %.1f x %.1f x %.1f px", (box.max().x - box.min().x) * 16f,
                (box.max().y - box.min().y) * 16f, (box.max().z - box.min().z) * 16f);
        graphics.drawString(font, "[P] refit debug  " + what + size, 4, 4, 0xFFFFFF, true);
        graphics.drawString(font, BUILD_STAMP, 4, 15, 0xFFAAAAAA, true);
    }

    /** "build <commit> (<variant>, <time>)" from the jar's build stamp — which build is actually running. */
    private static final String BUILD_STAMP = readBuildStamp();

    private static String readBuildStamp() {
        try (var in = RefitDebug.class.getResourceAsStream("/renaissance_lib_build.properties")) {
            if (in == null) return "build: no stamp (IDE run or pre-stamp jar)";
            Properties p = new Properties();
            p.load(in);
            return "build " + p.getProperty("commit", "?") + " (" + p.getProperty("variant", "?") + ", "
                    + p.getProperty("built", "?") + ")";
        } catch (Exception e) {
            return "build: stamp unreadable";
        }
    }

    private static void line(BufferBuilder buf, Matrix4f pose, float x0, float y0, float x1, float y1,
                             int r, int g, int b, int a) {
        buf.vertex(pose, x0, y0, 0f).color(r, g, b, a).endVertex();
        buf.vertex(pose, x1, y1, 0f).color(r, g, b, a).endVertex();
    }
}
