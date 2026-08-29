package net.tkg.RenaissanceLib.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * The fire-mode wheel's presentation: the shared {@link RadialRing} plus a fire-mode icon per segment (the
 * SEMI/AUTO/BURST/binary HUD textures) and the highlighted mode's name below. Driven by
 * {@link FireModeWheelOverlay}. Mirrors {@link AttachmentWheelRenderer}, but the icons are textures (blitted
 * at full UV so the source size doesn't matter) rather than item stacks.
 */
@OnlyIn(Dist.CLIENT)
public final class FireModeWheelRenderer {

    private static final int ICON = 16;
    // Distinct warm/amber tint (vs the default blue) for the underbarrel's segments in the shared wheel.
    private static final int UB_SEG_BASE = 0xB02E2412;
    private static final int UB_SEG_HL = 0xF0E0902E;
    private static final Component UNDERBARREL_HEADER =
            Component.translatable("hud.renaissance_lib.underbarrel_fire_mode");
    private static final Component MAIN_HEADER =
            Component.translatable("gui.renaissance_lib.weapon_wheel.main");

    private FireModeWheelRenderer() {}

    public static void draw(GuiGraphics gg, List<FireModeWheel.Choice> choices, int highlighted,
                            int cx, int cy, float alpha, double pointerAngleDeg) {
        if (choices.isEmpty() || alpha <= 0.02f) return;
        float ease = RadialRing.ease(alpha);
        float scale = RadialRing.scaleFactor(ease);

        int n = choices.size();
        // Per-segment: the underbarrel's modes (ubIndex >= 0) tint amber, the host gun's stay blue. Only build
        // the flag array when there's actually an underbarrel segment, so a lone host wheel draws plainly.
        boolean[] ubSegment = null;
        boolean anyUb = false;
        for (int i = 0; i < n; i++) {
            if (choices.get(i).ubIndex() >= 0) { anyUb = true; break; }
        }
        if (anyUb) {
            ubSegment = new boolean[n];
            for (int i = 0; i < n; i++) ubSegment[i] = choices.get(i).ubIndex() >= 0;
        }

        gg.pose().pushPose();
        gg.pose().translate(cx, cy, 0);
        gg.pose().scale(scale, scale, 1f);
        gg.pose().translate(-cx, -cy, 0);

        if (ubSegment != null) {
            RadialRing.drawRing(gg, n, highlighted, cx, cy, ease, pointerAngleDeg,
                    ubSegment, UB_SEG_BASE, UB_SEG_HL);
        } else {
            RadialRing.drawRing(gg, n, highlighted, cx, cy, ease, pointerAngleDeg);
        }

        if (ease > 0.35f) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, ease);
            for (int i = 0; i < n; i++) {
                int x = RadialRing.segmentX(cx, i, n);
                int y = RadialRing.segmentY(cy, i, n);
                gg.blit(choices.get(i).icon(), x - ICON / 2, y - ICON / 2, 0, 0, ICON, ICON, ICON, ICON);
            }
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        int textA = (int) (ease * 255) << 24;
        if ((textA & 0xFF000000) != 0) {
            Font font = Minecraft.getInstance().font;
            if (highlighted >= 0 && highlighted < n) {
                gg.drawCenteredString(font, choices.get(highlighted).label(), cx, cy + RadialRing.OUTER_R + 6,
                        0x00FFFFFF | textA);
                // A header naming which weapon the highlighted segment belongs to — amber for the underbarrel
                // (matching its tinted segments), plain for the host gun — only meaningful on a mixed wheel.
                if (ubSegment != null) {
                    boolean ubHi = choices.get(highlighted).ubIndex() >= 0;
                    gg.drawCenteredString(font, ubHi ? UNDERBARREL_HEADER : MAIN_HEADER,
                            cx, cy - RadialRing.OUTER_R - 14,
                            (ubHi ? 0x00FFA500 : 0x00FFFFFF) | textA);
                }
            }
        }

        gg.pose().popPose();
    }
}
