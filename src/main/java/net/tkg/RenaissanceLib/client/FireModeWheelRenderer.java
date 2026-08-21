package net.tkg.RenaissanceLib.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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

    private FireModeWheelRenderer() {}

    public static void draw(GuiGraphics gg, List<FireModeWheel.Choice> choices, int highlighted,
                            int cx, int cy, float alpha, double pointerAngleDeg) {
        if (choices.isEmpty() || alpha <= 0.02f) return;
        float ease = RadialRing.ease(alpha);
        float scale = RadialRing.scaleFactor(ease);

        gg.pose().pushPose();
        gg.pose().translate(cx, cy, 0);
        gg.pose().scale(scale, scale, 1f);
        gg.pose().translate(-cx, -cy, 0);

        int n = choices.size();
        RadialRing.drawRing(gg, n, highlighted, cx, cy, ease, pointerAngleDeg);

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
        if ((textA & 0xFF000000) != 0 && highlighted >= 0 && highlighted < n) {
            Font font = Minecraft.getInstance().font;
            gg.drawCenteredString(font, choices.get(highlighted).label(), cx, cy + RadialRing.OUTER_R + 6,
                    0x00FFFFFF | textA);
        }

        gg.pose().popPose();
    }
}
