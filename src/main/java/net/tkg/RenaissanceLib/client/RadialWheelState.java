package net.tkg.RenaissanceLib.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable cursorless radial-wheel state machine: open/closed with a fade tail, an accumulated pointing
 * direction (fed by {@code MouseHandlerMixin} so the camera doesn't turn), and the highlighted segment.
 * A wheel is a thin wrapper that supplies its item list and a confirm action ({@link Consumer}). The
 * attachment wheel predates this and keeps its own copy; the weapon-select wheel builds on this.
 *
 * @param <T> the choice type this wheel lists
 */
@OnlyIn(Dist.CLIENT)
public final class RadialWheelState<T> {

    /** Movement (in accumulated look units) from centre before a segment is picked. */
    private static final double DEAD_ZONE = 6.0;
    /** Fade in/out duration, milliseconds. */
    private static final long FADE_MS = 140L;

    private final Consumer<T> onConfirm;
    private final List<T> items = new ArrayList<>();
    private boolean open = false;
    private int openSlot = -1;
    private double pointerX = 0.0;
    private double pointerY = 0.0;
    private int highlighted = -1;
    private long openAtMs = 0L;
    private long closeAtMs = -1L;

    public RadialWheelState(Consumer<T> onConfirm) {
        this.onConfirm = onConfirm;
    }

    /** Logically open — accepts input and diverts look. False during the fade-out tail. */
    public boolean isOpen() {
        return open;
    }

    /** Whether the wheel should still be drawn (open, or within the fade-out tail). */
    public boolean isRendering() {
        return open || (closeAtMs >= 0 && System.currentTimeMillis() - closeAtMs < FADE_MS);
    }

    /** Fade factor 0..1 for the current open/closing transition. */
    public float renderAlpha() {
        long t = System.currentTimeMillis();
        if (open) return clamp01((t - openAtMs) / (float) FADE_MS);
        if (closeAtMs < 0) return 0f;
        return clamp01(1f - (t - closeAtMs) / (float) FADE_MS);
    }

    /** Selector direction in degrees (0 = up, clockwise), or {@code NaN} within the centre dead zone. */
    public double pointerAngleDeg() {
        double dist = Math.sqrt(pointerX * pointerX + pointerY * pointerY);
        if (dist < DEAD_ZONE) return Double.NaN;
        double angle = Math.toDegrees(Math.atan2(pointerX, -pointerY));
        return angle < 0 ? angle + 360.0 : angle;
    }

    public List<T> items() {
        return items;
    }

    public int highlighted() {
        return highlighted;
    }

    /** The hotbar slot the wheel was opened for, so it can be closed if the player switches items. */
    public int openSlot() {
        return openSlot;
    }

    /**
     * Open with the given choices for the given hotbar slot. Returns {@code false} (and stays closed) if
     * there's nothing to choose.
     */
    public boolean open(List<T> newItems, int slot) {
        if (newItems == null || newItems.isEmpty()) return false;
        items.clear();
        items.addAll(newItems);
        openSlot = slot;
        pointerX = 0.0;
        pointerY = 0.0;
        // A single choice fills the whole ring and is always the highlighted one.
        highlighted = items.size() == 1 ? 0 : -1;
        open = true;
        openAtMs = System.currentTimeMillis();
        closeAtMs = -1L;
        return true;
    }

    /**
     * Stop accepting input and begin the fade-out. Items/pointer are kept so the fade-out still renders;
     * they're reset on the next {@link #open}.
     */
    public void close() {
        if (open) {
            closeAtMs = System.currentTimeMillis();
        }
        open = false;
        openSlot = -1;
    }

    /** Accumulate mouse-look delta and recompute the highlighted segment. */
    public void feedLook(double dx, double dy) {
        if (!open) return;
        pointerX += dx;
        pointerY += dy;
        recompute();
    }

    private void recompute() {
        int n = items.size();
        if (n <= 1) {
            highlighted = n == 1 ? 0 : -1;
            return;
        }
        double dist = Math.sqrt(pointerX * pointerX + pointerY * pointerY);
        if (dist < DEAD_ZONE) {
            highlighted = -1;
            return;
        }
        double angle = Math.toDegrees(Math.atan2(pointerX, -pointerY));
        if (angle < 0) angle += 360.0;
        double seg = 360.0 / n;
        int index = (int) Math.floor(((angle + seg / 2.0) % 360.0) / seg);
        highlighted = Math.max(0, Math.min(n - 1, index));
    }

    /** Confirm the highlighted choice (if any) and close. */
    public void confirm() {
        if (open && highlighted >= 0 && highlighted < items.size()) {
            onConfirm.accept(items.get(highlighted));
        }
        close();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
