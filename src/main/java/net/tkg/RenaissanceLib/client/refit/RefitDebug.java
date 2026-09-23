package net.tkg.RenaissanceLib.client.refit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Debug overlay for the refit screen (toggle with P): the pivot the gun turns about (red cross), the bounding box it
 * comes from (cyan wireframe), and — stage 2 of the interactive refit screen — every slot the gun allows, as a dot
 * on its mount bone with a leader line out to a label placed away from the gun's centre (dimmed when the part is on
 * the far side). Slots the gun allows but has no mount bone for are listed in a dock. All positions come from
 * {@link RefitProjection}, so they turn with the gun.
 */
@OnlyIn(Dist.CLIENT)
public final class RefitDebug {
    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},  // along X
            {0, 2}, {1, 3}, {4, 6}, {5, 7},  // along Y
            {0, 4}, {1, 5}, {2, 6}, {3, 7}}; // along Z
    /** How far a slot label sits out from its mount point, in GUI pixels. */
    private static final float LABEL_DISTANCE = 55f;

    private static boolean enabled = false;

    private RefitDebug() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    private record Anchor(AttachmentType type, RefitProjection.Point point, boolean farSide) {}

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

        // Slots the held gun allows: on-gun anchors, or the dock when the model has no mount bone for them.
        List<Anchor> anchors = new ArrayList<>();
        List<AttachmentType> docked = new ArrayList<>();
        ItemStack gun = Minecraft.getInstance().player == null ? ItemStack.EMPTY
                : Minecraft.getInstance().player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun != null) {
            for (AttachmentType type : AttachmentType.values()) {
                if (type == AttachmentType.NONE || !iGun.allowAttachmentType(gun, type)) continue;
                RefitProjection.Point p = RefitProjection.project(RefitOrbit.slotAnchor(model, type));
                if (p == null) {
                    docked.add(type);
                } else {
                    anchors.add(new Anchor(type, p, pivot != null && p.depth() > pivot.depth() + 0.01f));
                }
            }
        }

        // Lines: box, pivot cross, slot leader lines.
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
        float[][] labelAt = new float[anchors.size()][];
        for (int i = 0; i < anchors.size(); i++) {
            Anchor a = anchors.get(i);
            labelAt[i] = labelPosition(a.point(), pivot);
            int alpha = a.farSide() ? 110 : 255;
            line(buf, pose, a.point().x(), a.point().y(), labelAt[i][0], labelAt[i][1], 0xFF, 0xD0, 0x40, alpha);
        }
        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();

        // Dots and labels.
        for (int i = 0; i < anchors.size(); i++) {
            Anchor a = anchors.get(i);
            int alpha = a.farSide() ? 0x70 : 0xFF;
            int px = Math.round(a.point().x()), py = Math.round(a.point().y());
            graphics.fill(px - 2, py - 2, px + 2, py + 2, (alpha << 24) | 0xFFD040);
            String label = slotName(a.type());
            int w = font.width(label);
            int lx = Math.round(labelAt[i][0]) - (labelAt[i][0] < a.point().x() ? w + 3 : -3);
            int ly = Math.round(labelAt[i][1]) - 4;
            graphics.fill(lx - 2, ly - 2, lx + w + 2, ly + 10, (Math.min(alpha, 0xB0) << 24));
            graphics.drawString(font, label, lx, ly, (alpha << 24) | 0xFFFFFF, false);
        }
        if (!docked.isEmpty()) {
            int y = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 14 - docked.size() * 11;
            graphics.drawString(font, "No mount bone (dock):", 6, y, 0xFFAAAAAA, true);
            for (AttachmentType type : docked) {
                y += 11;
                graphics.drawString(font, "- " + slotName(type), 10, y, 0xFFFFD040, true);
            }
        }
        if (pivot != null) {
            graphics.fill(Math.round(pivot.x()) - 1, Math.round(pivot.y()) - 1,
                    Math.round(pivot.x()) + 2, Math.round(pivot.y()) + 2, 0xFFFF4040);
        }

        String what = RefitOrbit.hasPivotOverride(model) ? "pivot: refit_pivot bone" : "pivot: bounding-box centre";
        String size = box.min() == null ? " | no box (mount-bone fallback)"
                : String.format(" | box %.1f x %.1f x %.1f px", (box.max().x - box.min().x) * 16f,
                (box.max().y - box.min().y) * 16f, (box.max().z - box.min().z) * 16f);
        graphics.drawString(font, "[P] refit debug  " + what + size + " | slots " + anchors.size() + " on gun, "
                + docked.size() + " docked", 4, 4, 0xFFFFFF, true);
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

    /** A label position pushed out from the mount point, away from the gun's projected centre. */
    private static float[] labelPosition(RefitProjection.Point anchor, RefitProjection.Point centre) {
        float dx = centre == null ? 1f : anchor.x() - centre.x();
        float dy = centre == null ? 0f : anchor.y() - centre.y();
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) {
            dx = 0f;
            dy = -1f;
        } else {
            dx /= len;
            dy /= len;
        }
        return new float[]{anchor.x() + dx * LABEL_DISTANCE, anchor.y() + dy * LABEL_DISTANCE};
    }

    private static String slotName(AttachmentType type) {
        String n = type.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static void line(BufferBuilder buf, Matrix4f pose, float x0, float y0, float x1, float y1,
                             int r, int g, int b, int a) {
        buf.vertex(pose, x0, y0, 0f).color(r, g, b, a).endVertex();
        buf.vertex(pose, x1, y1, 0f).color(r, g, b, a).endVertex();
    }
}
