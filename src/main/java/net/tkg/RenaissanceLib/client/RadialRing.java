package net.tkg.RenaissanceLib.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Shared radial-wheel visuals: the ring of wedge segments (highlighted one accented), the centre hub, and
 * the directional arrow — one batched triangle draw. Extracted so every radial menu (the attachment wheel
 * and the weapon-select wheel) renders as one consistent widget; each caller layers its own icons/labels on
 * top using {@link #segmentX}/{@link #segmentY}.
 *
 * <p>Geometry is screen-space, clockwise from straight up (screen Y grows downward). The caller sets up the
 * scaled pose (via {@link #ease}/{@link #scaleFactor}) before calling {@link #drawRing}.
 */
@OnlyIn(Dist.CLIENT)
public final class RadialRing {

    public static final int OUTER_R = 64;
    public static final int INNER_R = 34;
    public static final int ICON_R = 49;
    private static final float GAP_DEG = 3.0f;

    // Palette (ARGB); alpha is scaled by the fade factor at draw time.
    private static final int BACKDROP = 0xCC121317;
    private static final int SEG_BASE = 0xB021242C;
    private static final int SEG_HL = 0xF04E8CD6;
    private static final int HUB = 0xE60E0F13;
    private static final int ARROW = 0xF0FFFFFF;

    private RadialRing() {}

    /** Smoothstep of the 0..1 fade alpha, used for both opacity and the scale-in. */
    public static float ease(float alpha) {
        float a = Mth.clamp(alpha, 0f, 1f);
        return a * a * (3f - 2f * a);
    }

    /** Scale-in factor for the given eased alpha. */
    public static float scaleFactor(float ease) {
        return 0.82f + 0.18f * ease;
    }

    public static int segmentX(int cx, int i, int n) {
        return cx + (int) Math.round(Math.sin(Math.toRadians((360.0 / n) * i)) * ICON_R);
    }

    public static int segmentY(int cy, int i, int n) {
        return cy - (int) Math.round(Math.cos(Math.toRadians((360.0 / n) * i)) * ICON_R);
    }

    /** The segment index a screen point selects (press mode / cursor), or -1 in the centre dead zone. */
    public static int pick(double px, double py, int cx, int cy, int n) {
        if (n <= 0) return -1;
        if (n == 1) return 0;
        double dx = px - cx;
        double dy = py - cy;
        if (Math.sqrt(dx * dx + dy * dy) < INNER_R) return -1;
        double angle = Math.toDegrees(Math.atan2(dx, -dy));
        if (angle < 0) angle += 360.0;
        double seg = 360.0 / n;
        int index = (int) Math.floor(((angle + seg / 2.0) % 360.0) / seg);
        return Math.max(0, Math.min(n - 1, index));
    }

    /**
     * Draw the ring, hub and arrow (one batched triangle draw). The caller must already have pushed the
     * scaled pose.
     *
     * @param ease            eased 0..1 fade factor (see {@link #ease})
     * @param pointerAngleDeg selector direction in degrees (0 = up, clockwise), or {@code NaN} for no active
     *                        pointer (arrow shown dim, pointing up)
     */
    public static void drawRing(GuiGraphics gg, int n, int highlighted, int cx, int cy,
                                float ease, double pointerAngleDeg) {
        drawRing(gg, n, highlighted, cx, cy, ease, pointerAngleDeg, SEG_BASE, SEG_HL);
    }

    /**
     * As {@link #drawRing(GuiGraphics, int, int, int, int, float, double)} but with caller-chosen segment
     * colours — used to tint a whole wheel a distinct hue.
     *
     * @param segBase resting segment colour (ARGB); @param segHl highlighted segment colour (ARGB)
     */
    public static void drawRing(GuiGraphics gg, int n, int highlighted, int cx, int cy,
                                float ease, double pointerAngleDeg, int segBase, int segHl) {
        drawRing(gg, n, highlighted, cx, cy, ease, pointerAngleDeg, segBase, segHl, null, 0, 0);
    }

    /**
     * Per-segment tint over the <em>default</em> ring colours: segments flagged in {@code special} draw with
     * {@code specialBase}/{@code specialHl}, the rest with the standard blue.
     */
    public static void drawRing(GuiGraphics gg, int n, int highlighted, int cx, int cy,
                                float ease, double pointerAngleDeg,
                                boolean[] special, int specialBase, int specialHl) {
        drawRing(gg, n, highlighted, cx, cy, ease, pointerAngleDeg, SEG_BASE, SEG_HL,
                special, specialBase, specialHl);
    }

    /**
     * As the colour overload, but tints only the segments flagged in {@code special} with
     * {@code specialBase}/{@code specialHl} — used for a mixed wheel (e.g. host gun modes vs. underbarrel
     * modes in the same ring). {@code special} may be shorter than {@code n} (missing entries treated false).
     */
    public static void drawRing(GuiGraphics gg, int n, int highlighted, int cx, int cy,
                                float ease, double pointerAngleDeg, int segBase, int segHl,
                                boolean[] special, int specialBase, int specialHl) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // No back-face culling: our triangles (annulus vs. centre fan vs. arrow) don't share a winding order,
        // so with culling on some of them (the hub disc and the arrow) get dropped.
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f mat = gg.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        // faint full-ring backdrop
        annulus(bb, mat, cx, cy, INNER_R - 2, OUTER_R + 3, 0f, 360f, scaleA(BACKDROP, ease * 0.85f));

        if (n == 1) {
            boolean sp0 = special != null && special.length > 0 && special[0];
            int hlC = sp0 ? specialHl : segHl;
            annulus(bb, mat, cx, cy, INNER_R, OUTER_R, 0f, 360f, scaleA(hlC, ease), scaleA(hlC, ease * 0.6f));
        } else {
            float seg = 360f / n;
            for (int i = 0; i < n; i++) {
                float center = seg * i;
                float a0 = center - seg / 2f + GAP_DEG;
                float a1 = center + seg / 2f - GAP_DEG;
                boolean hi = i == highlighted;
                boolean sp = special != null && i < special.length && special[i];
                int baseC = sp ? specialBase : segBase;
                int hlC = sp ? specialHl : segHl;
                int outerC = hi ? scaleA(hlC, ease) : scaleA(baseC, ease);
                int innerC = hi ? scaleA(hlC, ease * 0.55f) : scaleA(baseC, ease * 0.85f);
                annulus(bb, mat, cx, cy, INNER_R, OUTER_R, a0, a1, outerC, innerC);
            }
        }

        // centre hub
        disc(bb, mat, cx, cy, INNER_R - 1, scaleA(HUB, ease));

        // directional arrow — always shown; points up while centred, brightens once aiming at a segment.
        boolean aiming = !Double.isNaN(pointerAngleDeg);
        float arrowDeg = aiming ? (float) pointerAngleDeg : 0f;
        arrow(bb, mat, cx, cy, arrowDeg, scaleA(ARROW, ease * (aiming ? 1f : 0.5f)));

        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ---- geometry helpers (screen-space, clockwise from up) --------------------------------------------

    private static void annulus(BufferBuilder bb, Matrix4f mat, float cx, float cy, float rIn, float rOut,
                                float a0Deg, float a1Deg, int color) {
        annulus(bb, mat, cx, cy, rIn, rOut, a0Deg, a1Deg, color, color);
    }

    /** Filled annular wedge from {@code a0}..{@code a1} degrees, colour lerped outer→inner. */
    private static void annulus(BufferBuilder bb, Matrix4f mat, float cx, float cy, float rIn, float rOut,
                                float a0Deg, float a1Deg, int outerColor, int innerColor) {
        int steps = Math.max(2, (int) Math.ceil(Math.abs(a1Deg - a0Deg) / 5.0));
        for (int i = 0; i < steps; i++) {
            float aA = (float) Math.toRadians(Mth.lerp((float) i / steps, a0Deg, a1Deg));
            float aB = (float) Math.toRadians(Mth.lerp((float) (i + 1) / steps, a0Deg, a1Deg));
            float sinA = Mth.sin(aA), cosA = Mth.cos(aA);
            float sinB = Mth.sin(aB), cosB = Mth.cos(aB);
            float oAx = cx + sinA * rOut, oAy = cy - cosA * rOut;
            float iAx = cx + sinA * rIn, iAy = cy - cosA * rIn;
            float oBx = cx + sinB * rOut, oBy = cy - cosB * rOut;
            float iBx = cx + sinB * rIn, iBy = cy - cosB * rIn;
            vtx(bb, mat, oAx, oAy, outerColor);
            vtx(bb, mat, iAx, iAy, innerColor);
            vtx(bb, mat, oBx, oBy, outerColor);
            vtx(bb, mat, oBx, oBy, outerColor);
            vtx(bb, mat, iAx, iAy, innerColor);
            vtx(bb, mat, iBx, iBy, innerColor);
        }
    }

    private static void disc(BufferBuilder bb, Matrix4f mat, float cx, float cy, float r, int color) {
        int steps = 48;
        for (int i = 0; i < steps; i++) {
            float aA = (float) (Math.PI * 2 * i / steps);
            float aB = (float) (Math.PI * 2 * (i + 1) / steps);
            vtx(bb, mat, cx, cy, color);
            vtx(bb, mat, cx + Mth.sin(aA) * r, cy - Mth.cos(aA) * r, color);
            vtx(bb, mat, cx + Mth.sin(aB) * r, cy - Mth.cos(aB) * r, color);
        }
    }

    private static void arrow(BufferBuilder bb, Matrix4f mat, float cx, float cy, float angleDeg, int color) {
        float a = (float) Math.toRadians(angleDeg);
        float fx = Mth.sin(a), fy = -Mth.cos(a);       // forward (outward)
        float px = Mth.cos(a), py = Mth.sin(a);        // perpendicular
        // A wide, shallow arrowhead sitting at the ring's inner edge, pointing outward at the selection.
        float tipR = INNER_R, baseR = INNER_R - 8f, half = 13f;
        float tX = cx + fx * tipR, tY = cy + fy * tipR;
        float lX = cx + fx * baseR + px * half, lY = cy + fy * baseR + py * half;
        float rX = cx + fx * baseR - px * half, rY = cy + fy * baseR - py * half;
        vtx(bb, mat, tX, tY, color);
        vtx(bb, mat, lX, lY, color);
        vtx(bb, mat, rX, rY, color);
    }

    private static void vtx(BufferBuilder bb, Matrix4f mat, float x, float y, int argb) {
        bb.vertex(mat, x, y, 0f)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .endVertex();
    }

    /** Scale an ARGB colour's alpha by {@code factor} (0..1). */
    public static int scaleA(int argb, float factor) {
        int a = (int) (((argb >>> 24) & 0xFF) * Mth.clamp(factor, 0f, 1f));
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
