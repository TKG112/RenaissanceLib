package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerZoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.MountPath;

import javax.annotation.Nullable;

/**
 * Continuous (scroll-adjustable) scope magnification, generalised to the <em>active optic</em>.
 *
 * <p>The active optic is whatever the combined zoom cycle currently points at (see {@link ActiveOptic}):
 * the installed scope, or a sight mounted on a rail. Two things flow from that optic's display config:
 * <ul>
 *   <li><b>Variable zoom</b> — when a view maps to more than one zoom value (e.g. {@code zoom: [3, 9]}),
 *       the player holds the zoom key and scrolls to any magnification in the range. Works for the scope
 *       and for canted rail sights alike.</li>
 *   <li><b>Rail-sight FOV</b> — a mounted sight always applies its own zoom/fov while it is the active
 *       optic (even for a single-value view), so looking through a canted sight zooms to <em>its</em>
 *       magnification rather than the host scope's.</li>
 * </ul>
 *
 * <p>Scope behaviour is unchanged when no rail sight is active: the FOV override still only kicks in
 * once the player scrolls (engages), leaving TaC:Z's discrete tap-cycle in charge otherwise.
 *
 * <p>Zoom is a first-person, client-only visual, so the current magnification lives here as client
 * state rather than in synced NBT.
 */
@OnlyIn(Dist.CLIENT)
public final class VariableZoom {
    /** How much one scroll notch moves through the range, as a fraction of its span. */
    private static final float SCROLL_FRACTION = 0.1f;

    /** Smoothing time constant (seconds) for easing the shown magnification toward the target. */
    private static final float SMOOTH_TAU = 0.07f;

    /** How close (in magnification) the smooth exit must get to the step before it disengages. */
    private static final float EXIT_EPSILON = 0.02f;

    /** What a released zoom-key press turned out to mean. */
    public enum TapResult {
        /** Nothing for the caller to do (a hold+scroll, or a non-intercepted release). */
        NONE,
        /** A tap while not engaged — caller should run TaC:Z's discrete zoom-step cycle. */
        CYCLE_STEP,
        /** A tap while engaged — caller should start a smooth exit back to the current step. */
        EXIT_TO_STEP
    }

    /** Identity of the optic+view the current value belongs to; resets the value when it changes. */
    @Nullable
    private static ResourceLocation activeOpticId = null;
    @Nullable
    private static MountPath activePath = null;
    private static int activeView = -1;

    /** Where the scroll wheel wants the magnification; {@link #magnification} eases toward it. */
    private static float targetMagnification = 0f;
    /** The smoothed magnification actually used for FOV/sensitivity this frame. */
    private static float magnification = 0f;

    /**
     * Whether continuous mode is currently driving the zoom. Stays {@code false} — leaving TaC:Z's
     * discrete tap-to-cycle in control — until the player scrolls while holding the zoom key. Note a
     * mounted rail sight applies its FOV regardless of this flag (see {@link #fovOverrideActive}).
     */
    private static boolean engaged = false;

    /** Tracks a held zoom key whose discrete step we deferred, to decide tap vs. hold on release. */
    private static boolean zoomKeyIntercepted = false;
    private static boolean scrolledWhileHeld = false;

    /** While {@code true}, we're easing the magnification down to the step before disengaging. */
    private static boolean exiting = false;

    /**
     * Smooths the aim-in/out ramp so dynamic zoom eases like TaC:Z instead of snapping to target as
     * soon as raw aiming progress hits 1. Tuned with TaC:Z's world-FOV dynamics values
     * ({@code CameraSetupEvent.WORLD_FOV_DYNAMICS} = {@code 0.5, 1.2, 0.5}). Kept warm every frame so
     * engaging mid-aim doesn't pop.
     */
    private static final SecondOrderDynamics progressDynamics = new SecondOrderDynamics(0.5f, 1.2f, 0.5f, 0f);
    private static float smProgress = 0f;

    private VariableZoom() {}

    /** True once the player has scrolled into continuous mode for the current optic view. */
    public static boolean isEngaged() {
        return engaged;
    }

    /** Drops back to TaC:Z's discrete behavior (called when the optic changes / becomes non-continuous). */
    public static void disengage() {
        engaged = false;
        exiting = false;
    }

    /**
     * Whether we must fully drive the aim FOV this frame. Two cases:
     * <ul>
     *   <li>the player scrolled into continuous mode (variable zoom, scope or sight); or</li>
     *   <li>the gun has any mounted rail sight — then we own the FOV across the <em>whole</em> combined
     *       cycle, so scope→sight→scope transitions ease through one smoother instead of popping as
     *       control passes between us and TaC:Z.</li>
     * </ul>
     * A gun with no rail sights and no scroll returns {@code false}: TaC:Z keeps control, exactly as
     * before.
     */
    public static boolean fovOverrideActive(ItemStack gunItem) {
        return engaged || ActiveOptic.hasMountedRailSight(gunItem);
    }

    /**
     * Called when the zoom key is pressed. If the player is aiming an optic whose current view is
     * continuous, we take over the key: TaC:Z's immediate discrete step is canceled (by the mixin that
     * calls this) and instead deferred until release, so holding-to-scroll no longer cycles a step
     * first. Returns {@code true} if we intercepted the press.
     */
    public static boolean onZoomKeyPress() {
        LocalPlayer player = localPlayer();
        if (player == null) return false;
        if (!IClientPlayerGunOperator.fromLocalPlayer(player).isAim()) return false;
        Range range = currentRange(player.getMainHandItem());
        if (range == null || !range.isContinuous()) return false;
        zoomKeyIntercepted = true;
        scrolledWhileHeld = false;
        return true;
    }

    /**
     * Called when the zoom key is released. Decides what the deferred press meant:
     * <ul>
     *   <li>scrolled while held → continuous adjustment; stay engaged, no discrete step.</li>
     *   <li>tap while <b>engaged</b> → exit dynamic zoom back to step mode (disengage).</li>
     *   <li>tap while <b>not engaged</b> → returns {@code CYCLE_STEP} so the caller performs TaC:Z's
     *       normal discrete zoom-step cycle.</li>
     * </ul>
     */
    public static TapResult onZoomKeyRelease() {
        if (!zoomKeyIntercepted) return TapResult.NONE;
        zoomKeyIntercepted = false;
        boolean scrolled = scrolledWhileHeld;
        scrolledWhileHeld = false;
        if (scrolled) return TapResult.NONE;
        return engaged ? TapResult.EXIT_TO_STEP : TapResult.CYCLE_STEP;
    }

    /**
     * Begins a smooth exit from continuous mode: aims the magnification at the current discrete step
     * and keeps the override running so the FOV eases there instead of snapping. When it arrives,
     * {@link #updateSmoothing} disengages — by then our value equals the step, so no pop.
     */
    public static void beginExit(@Nullable Range range) {
        if (range == null) {
            disengage();
            return;
        }
        targetMagnification = clamp(range.stepZoom, range.minZoom, range.maxZoom);
        exiting = true;
    }

    /** Performs TaC:Z's own discrete zoom-step cycle (used to honor a deferred tap). */
    public static void triggerDiscreteZoom() {
        LocalPlayer player = localPlayer();
        if (player == null || player.isSpectator()) return;
        if (IClientPlayerGunOperator.fromLocalPlayer(player).isAim()) {
            NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerZoom());
        }
    }

    /**
     * The zoom range of the held gun's <em>active</em> optic view, or {@code null} if there's no optic.
     * The range is returned even when it is a single fixed value ({@code minZoom == maxZoom}); callers
     * use {@link Range#isContinuous()} to tell whether scrolling applies.
     */
    @Nullable
    public static Range currentRange(ItemStack gunItem) {
        ActiveOptic optic = ActiveOptic.resolve(gunItem);
        if (optic == null) return null;

        ClientAttachmentIndex index = optic.index();
        if (index == null) return null;

        float[] zoom = index.getZoom();
        int[] views = index.getViews();
        float[] viewsFov = index.getViewsFov();
        if (zoom == null || zoom.length == 0 || views == null || viewsFov == null) return null;

        int viewNode = views[Math.floorMod(optic.localIndex, views.length)];

        // Find the min/max zoom step among all steps that share this view node.
        int minStep = -1, maxStep = -1;
        for (int i = 0; i < zoom.length; i++) {
            int vn = views[Math.floorMod(i, views.length)];
            if (vn != viewNode) continue;
            if (minStep == -1 || zoom[i] < zoom[minStep]) minStep = i;
            if (maxStep == -1 || zoom[i] > zoom[maxStep]) maxStep = i;
        }
        if (minStep == -1) return null;

        float step = zoom[Math.floorMod(optic.localIndex, zoom.length)];
        return new Range(optic.opticId, optic.path, viewNode,
                zoom[minStep], zoom[maxStep],
                fovAt(viewsFov, minStep), fovAt(viewsFov, maxStep),
                step, !optic.isScope());
    }

    private static float fovAt(float[] viewsFov, int step) {
        return viewsFov[Math.floorMod(step, viewsFov.length)];
    }

    /**
     * Current (smoothed) magnification for the range. While not in scrolled continuous mode, the target
     * follows the active discrete step every frame — so tapping the zoom key to cycle an optic's zoom
     * levels (e.g. {@code zoom: [4, 8]}, even on a single view) eases to the new step. Scrolling engages
     * continuous mode and pins the target to the scrolled value instead. The shown value only snaps on
     * first initialisation; optic/view/step changes all ease through the one smoother.
     */
    public static float magnification(Range range) {
        boolean opticChanged = !range.opticId.equals(activeOpticId) || !range.path.equals(activePath);
        if (opticChanged) {
            activeOpticId = range.opticId;
            activePath = range.path;
            engaged = false;
            exiting = false;
        }
        activeView = range.viewNode;
        if (!engaged) {
            // Follow the current discrete step (tap-to-cycle) when not scroll-adjusting.
            targetMagnification = clamp(range.stepZoom, range.minZoom, range.maxZoom);
            if (magnification <= 0f) {
                magnification = targetMagnification;
            }
        }
        return magnification;
    }

    /**
     * Moves the target magnification by {@code notches} scroll steps, clamped to the range, and
     * engages continuous mode so our FOV/sensitivity override takes over from TaC:Z.
     */
    public static void scroll(Range range, double notches) {
        magnification(range);
        float span = range.maxZoom - range.minZoom;
        float value = targetMagnification + (float) notches * span * SCROLL_FRACTION;
        targetMagnification = clamp(value, range.minZoom, range.maxZoom);
        engaged = true;
        exiting = false;
        scrolledWhileHeld = true;
    }

    /** Smooths the raw aiming progress into the gentle ADS ramp; called once per rendered frame. */
    public static void updateProgressSmoothing(float dtSeconds, float rawProgress) {
        float v = progressDynamics.update(dtSeconds < 0f ? 0f : dtSeconds, rawProgress);
        smProgress = v < 0f ? 0f : v;
    }

    /** The smoothed aim ramp (0 = hip, 1 = fully aimed) used to drive the dynamic-zoom FOV. */
    public static float smoothedProgress() {
        return smProgress;
    }

    /**
     * Eases the shown magnification toward the target; called once per rendered frame while the FOV
     * override is active (engaged, or a rail sight is active). If a smooth exit is in progress,
     * disengages once the value reaches the step so the handoff back to TaC:Z's discrete FOV is
     * seamless.
     */
    public static void updateSmoothing(float dtSeconds, boolean overrideActive) {
        if (!overrideActive) return;
        float dt = dtSeconds < 0f ? 0f : Math.min(dtSeconds, 0.1f);
        float alpha = 1f - (float) Math.exp(-dt / SMOOTH_TAU);
        magnification += (targetMagnification - magnification) * alpha;

        if (exiting && Math.abs(magnification - targetMagnification) <= EXIT_EPSILON) {
            magnification = targetMagnification;
            engaged = false;
            exiting = false;
        }
    }

    /**
     * The current magnification to scale mouse sensitivity by, or a negative value if TaC:Z's own
     * discrete sensitivity should apply. Active whenever we're driving the FOV (engaged, or a rail
     * sight is active), so aim sensitivity tracks the optic actually being looked through.
     */
    public static float activeMagnification(ItemStack gunItem) {
        Range range = currentRange(gunItem);
        if (range == null) return -1f;
        if (!engaged && !range.railSight) return -1f;
        return magnification(range);
    }

    @Nullable
    public static LocalPlayer localPlayer() {
        return Minecraft.getInstance().player;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** The FOV the given magnification maps to, interpolated between the view's min/max fovs. */
    public static float fovForMagnification(Range range, float mag) {
        if (range.maxZoom <= range.minZoom) return range.fovAtMin;
        float t = (mag - range.minZoom) / (range.maxZoom - range.minZoom);
        return range.fovAtMin + t * (range.fovAtMax - range.fovAtMin);
    }

    public static final class Range {
        public final ResourceLocation opticId;
        /** Mount path of the active optic; {@link MountPath#ROOT} for the scope. */
        public final MountPath path;
        public final int viewNode;
        public final float minZoom, maxZoom;
        public final float fovAtMin, fovAtMax;
        public final float stepZoom;
        /** Whether the active optic is a mounted rail sight (vs. the host scope). */
        public final boolean railSight;

        Range(ResourceLocation opticId, MountPath path, int viewNode, float minZoom, float maxZoom,
              float fovAtMin, float fovAtMax, float stepZoom, boolean railSight) {
            this.opticId = opticId;
            this.path = path;
            this.viewNode = viewNode;
            this.minZoom = minZoom;
            this.maxZoom = maxZoom;
            this.fovAtMin = fovAtMin;
            this.fovAtMax = fovAtMax;
            this.stepZoom = stepZoom;
            this.railSight = railSight;
        }

        /** Whether this view has a scroll-adjustable span (more than one zoom value). */
        public boolean isContinuous() {
            return maxZoom > minZoom;
        }
    }
}
