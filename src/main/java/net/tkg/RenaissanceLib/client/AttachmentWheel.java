package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.AttachmentToggleTargets;
import net.tkg.RenaissanceLib.attachment.ToggleTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side state of the attachment radial wheel: whether it's open, the targets it lists, the
 * accumulated pointing direction, and which segment is highlighted. Look input is fed in by
 * {@code MouseHandlerMixin} while open (so the camera doesn't turn), and the overlay reads this to render.
 */
@OnlyIn(Dist.CLIENT)
public final class AttachmentWheel {

    /** Movement (in accumulated look units) from centre before a segment is picked. */
    private static final double DEAD_ZONE = 6.0;
    /** Fade in/out duration, milliseconds. */
    private static final long FADE_MS = 140L;

    private static boolean open = false;
    private static final List<ToggleTarget> targets = new ArrayList<>();
    private static int openSlot = -1;          // hotbar slot the wheel was opened for
    private static double pointerX = 0.0;
    private static double pointerY = 0.0;
    private static int highlighted = -1;
    private static long openAtMs = 0L;
    private static long closeAtMs = -1L;

    private AttachmentWheel() {}

    /** Logically open — accepts input and diverts look. False during the fade-out tail. */
    public static boolean isOpen() {
        return open;
    }

    /** Whether the wheel should still be drawn (open, or within the fade-out tail). */
    public static boolean isRendering() {
        return open || (closeAtMs >= 0 && System.currentTimeMillis() - closeAtMs < FADE_MS);
    }

    /** Fade factor 0..1 for the current open/closing transition. */
    public static float renderAlpha() {
        long t = System.currentTimeMillis();
        if (open) return clamp01((t - openAtMs) / (float) FADE_MS);
        if (closeAtMs < 0) return 0f;
        return clamp01(1f - (t - closeAtMs) / (float) FADE_MS);
    }

    /** Selector direction in degrees (0 = up, clockwise), or {@code NaN} within the centre dead zone. */
    public static double pointerAngleDeg() {
        double dist = Math.sqrt(pointerX * pointerX + pointerY * pointerY);
        if (dist < DEAD_ZONE) return Double.NaN;
        double angle = Math.toDegrees(Math.atan2(pointerX, -pointerY));
        return angle < 0 ? angle + 360.0 : angle;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static List<ToggleTarget> targets() {
        return targets;
    }

    public static int highlighted() {
        return highlighted;
    }

    public static double pointerX() {
        return pointerX;
    }

    public static double pointerY() {
        return pointerY;
    }

    /** The hotbar slot the wheel was opened for, so we can close it if the player switches items. */
    public static int openSlot() {
        return openSlot;
    }

    /** Open for the currently held gun. Returns {@code false} (and stays closed) if nothing is toggleable. */
    public static boolean tryOpen() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return false;

        ItemStack gun = player.getMainHandItem();
        if (IGun.getIGunOrNull(gun) == null) return false;

        List<ToggleTarget> found = AttachmentToggleTargets.list(gun);
        if (found.isEmpty()) return false;

        targets.clear();
        targets.addAll(found);
        openSlot = player.getInventory().selected;
        pointerX = 0.0;
        pointerY = 0.0;
        // A single toggleable attachment fills the whole ring and is always the highlighted choice.
        highlighted = targets.size() == 1 ? 0 : -1;
        open = true;
        openAtMs = System.currentTimeMillis();
        closeAtMs = -1L;
        return true;
    }

    /**
     * Stop accepting input and begin the fade-out. Targets/pointer are kept so the fade-out still renders;
     * they're reset on the next {@link #tryOpen()}.
     */
    public static void close() {
        if (open) {
            closeAtMs = System.currentTimeMillis();
        }
        open = false;
        openSlot = -1;
    }

    /** Accumulate mouse-look delta (from {@code MouseHandlerMixin}) and recompute the highlighted segment. */
    public static void feedLook(double dx, double dy) {
        if (!open) return;
        pointerX += dx;
        pointerY += dy;
        recompute();
    }

    private static void recompute() {
        int n = targets.size();
        if (n <= 1) {
            highlighted = n == 1 ? 0 : -1;
            return;
        }
        double dist = Math.sqrt(pointerX * pointerX + pointerY * pointerY);
        if (dist < DEAD_ZONE) {
            highlighted = -1;
            return;
        }
        // Angle measured clockwise from straight up; screen Y grows downward.
        double angle = Math.toDegrees(Math.atan2(pointerX, -pointerY));
        if (angle < 0) angle += 360.0;
        double seg = 360.0 / n;
        int index = (int) Math.floor(((angle + seg / 2.0) % 360.0) / seg);
        highlighted = Math.max(0, Math.min(n - 1, index));
    }

    /** Toggle the highlighted target (if any) and close. */
    public static void confirm() {
        if (open && highlighted >= 0 && highlighted < targets.size()) {
            AttachmentToggle.toggle(targets.get(highlighted));
        }
        close();
    }
}
