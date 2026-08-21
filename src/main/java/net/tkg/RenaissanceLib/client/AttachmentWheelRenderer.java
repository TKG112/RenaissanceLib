package net.tkg.RenaissanceLib.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.AttachmentStates;
import net.tkg.RenaissanceLib.attachment.AttachmentToggleTargets;
import net.tkg.RenaissanceLib.attachment.ToggleTarget;

import java.util.List;

/**
 * The attachment wheel's presentation: the shared {@link RadialRing} plus attachment-specific item icons,
 * post-toggle cooldown tint, and the current state named below. Driven by {@link AttachmentWheelOverlay},
 * which supplies an animation {@code alpha} (fade + scale) and the pointer angle for the arrow.
 */
@OnlyIn(Dist.CLIENT)
public final class AttachmentWheelRenderer {

    public static final int OUTER_R = RadialRing.OUTER_R;
    public static final int INNER_R = RadialRing.INNER_R;

    private static final int SEG_COOLDOWN = 0xC01A1A1E;

    private AttachmentWheelRenderer() {}

    /**
     * Draw the wheel.
     *
     * @param alpha            0..1 fade factor (also drives a subtle scale-in)
     * @param pointerAngleDeg  hold-mode selector direction in degrees (0 = up, clockwise), or {@code NaN} for
     *                         no arrow (press mode, or hold mode before the pointer leaves the dead zone)
     */
    public static void draw(GuiGraphics gg, ItemStack gun, List<ToggleTarget> targets, int highlighted,
                            int cx, int cy, float alpha, double pointerAngleDeg) {
        if (targets.isEmpty() || alpha <= 0.02f) return;
        float ease = RadialRing.ease(alpha);
        float scale = RadialRing.scaleFactor(ease);

        gg.pose().pushPose();
        gg.pose().translate(cx, cy, 0);
        gg.pose().scale(scale, scale, 1f);
        gg.pose().translate(-cx, -cy, 0);

        int n = targets.size();
        RadialRing.drawRing(gg, n, highlighted, cx, cy, ease, pointerAngleDeg);

        // --- icons + labels (drawn after the ring; own render state) ---
        if (ease > 0.35f) {
            for (int i = 0; i < n; i++) {
                int x = RadialRing.segmentX(cx, i, n);
                int y = RadialRing.segmentY(cy, i, n);
                ItemStack icon = AttachmentToggleTargets.getItem(gun, targets.get(i));
                if (!icon.isEmpty()) {
                    gg.renderItem(icon, x - 8, y - 8);
                }
                if (AttachmentToggle.isOnCooldown(targets.get(i))) {
                    gg.fill(x - 9, y - 9, x + 9, y + 9, RadialRing.scaleA(SEG_COOLDOWN, ease));
                }
            }
        }

        int textA = (int) (ease * 255) << 24;
        if ((textA & 0xFF000000) != 0 && highlighted >= 0 && highlighted < n) {
            Font font = Minecraft.getInstance().font;
            ToggleTarget target = targets.get(highlighted);
            String state = AttachmentStates.getState(gun, target);
            String label = (state == null || state.isEmpty())
                    ? target.slot().name().toLowerCase() : state;
            // Below the ring, so it never collides with the centre arrow.
            gg.drawCenteredString(font, Component.literal(label), cx, cy + OUTER_R + 6, 0x00FFFFFF | textA);
        }

        gg.pose().popPose();
    }

    /** The segment index a screen point selects (press mode / cursor), or -1 in the centre dead zone. */
    public static int pick(double px, double py, int cx, int cy, int n) {
        return RadialRing.pick(px, py, cx, cy, n);
    }
}
