package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.input.ZoomKey;
import com.tacz.guns.util.math.MathUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;

@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public class VariableZoomHandler {

    /**
     * Hold the zoom key and scroll to adjust magnification. The scroll is consumed so it
     * doesn't also move the hotbar. Only fires while aiming a gun whose current scope view
     * is continuous.
     */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        LocalPlayer player = VariableZoom.localPlayer();
        if (player == null) return;
        if (!ZoomKey.ZOOM_KEY.isDown()) return;
        if (!isAiming(player)) return;

        ItemStack gunItem = player.getMainHandItem();
        VariableZoom.Range range = VariableZoom.currentRange(gunItem);
        if (range == null || !range.isContinuous()) return;

        VariableZoom.scroll(range, event.getScrollDelta());
        event.setCanceled(true);
    }

    /** Base (pre-TaC:Z) FOV of the two render passes, captured while continuous mode is engaged. */
    private static double baseWorldFov = -1;
    private static double baseHandFov = -1;

    /**
     * Captures the vanilla base FOV before TaC:Z transforms it. TaC:Z computes its zoom FOV from
     * this base (world: {@code magnificationToFov(zoom, base)}; hand: {@code lerp(progress, base,
     * viewsFov)}), so to recompute with a continuous magnification we need the same base — which
     * is only available before TaC:Z (default priority) has overwritten {@code event.getFOV()}.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void captureBaseFov(ViewportEvent.ComputeFov event) {
        Entity entity = event.getCamera().getEntity();
        if (!(entity instanceof LocalPlayer player)) return;
        if (!VariableZoom.fovOverrideActive(player.getMainHandItem())) return;
        if (event.usedConfiguredFov()) baseWorldFov = event.getFOV();
        else baseHandFov = event.getFOV();
    }

    /**
     * Fully re-applies the aim FOV with the continuous magnification, replicating TaC:Z's own two
     * formulas so both the world (camera) and the gun-model (hand) passes zoom together:
     * <ul>
     *   <li>world — {@code magnificationToFov(1 + (mag-1)*progress, base)}</li>
     *   <li>hand  — {@code lerp(progress, base, interpolatedViewsFov)}</li>
     * </ul>
     * Runs at LOWEST so it overrides TaC:Z's discrete value. Dormant until the player scrolls into
     * continuous mode, leaving TaC:Z's discrete tap-cycle (and its FOV smoothing) fully in charge.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void applyContinuousFov(ViewportEvent.ComputeFov event) {
        Entity entity = event.getCamera().getEntity();
        if (!(entity instanceof LocalPlayer player)) return;
        if (!VariableZoom.fovOverrideActive(player.getMainHandItem())) return;

        VariableZoom.Range range = VariableZoom.currentRange(player.getMainHandItem());
        if (range == null) return;

        // Use the smoothed aim ramp (updated in onRenderTick) so ADS in/out eases like TaC:Z. We
        // control the FOV every frame including ramp == 0, where the formulas yield exactly the base
        // FOV — so aim-out hands off to ourselves rather than exposing TaC:Z's lagging FOV dynamics.
        float progress = VariableZoom.smoothedProgress();
        float mag = VariableZoom.magnification(range);

        if (event.usedConfiguredFov()) {
            double base = baseWorldFov > 0 ? baseWorldFov : event.getFOV();
            event.setFOV(MathUtil.magnificationToFov(1 + (mag - 1) * progress, base));
        } else {
            double base = baseHandFov > 0 ? baseHandFov : event.getFOV();
            float modelFov = VariableZoom.fovForMagnification(range, mag);
            event.setFOV(Mth.lerp(Mth.clamp(progress, 0f, 1f), (float) base, modelFov));
        }
    }

    private static boolean zoomKeyWasDown = false;
    private static long lastFrameNanos = 0L;

    /**
     * Per-frame work: advances the aim-ramp smoother (gentle ADS) and the magnification smoother
     * (responsive scroll), and detects the zoom-key release edge to honour a deferred tap (a press
     * with no scroll performs TaC:Z's discrete step now, on release, instead of at press time).
     */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;

        LocalPlayer player = VariableZoom.localPlayer();
        VariableZoom.updateProgressSmoothing(dt, player == null ? 0f : aimingProgress(player, event.renderTickTime));
        boolean overrideActive = player != null && VariableZoom.fovOverrideActive(player.getMainHandItem());
        if (overrideActive) {
            // Seed the target magnification for the active optic view (detects optic/view changes) so
            // the smoother eases toward it — done before updateSmoothing so transitions have no lag.
            VariableZoom.Range range = VariableZoom.currentRange(player.getMainHandItem());
            if (range != null) VariableZoom.magnification(range);
        }
        VariableZoom.updateSmoothing(dt, overrideActive);

        boolean down = ZoomKey.ZOOM_KEY.isDown();
        if (zoomKeyWasDown && !down) {
            switch (VariableZoom.onZoomKeyRelease()) {
                case CYCLE_STEP -> VariableZoom.triggerDiscreteZoom();
                case EXIT_TO_STEP -> VariableZoom.beginExit(player == null ? null
                        : VariableZoom.currentRange(player.getMainHandItem()));
                default -> {}
            }
        }
        zoomKeyWasDown = down;
    }

    /**
     * Drops continuous mode back to TaC:Z's discrete behaviour only when the continuous scope is
     * no longer in play — the gun/scope changed, or a tap cycled to a view that isn't continuous.
     * Merely lowering the gun does <em>not</em> disengage: the scrolled magnification is kept so
     * re-aiming returns to it (aiming progress already gates the zoom to zero while not aiming).
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!VariableZoom.isEngaged()) return;

        LocalPlayer player = VariableZoom.localPlayer();
        VariableZoom.Range range = player == null ? null : VariableZoom.currentRange(player.getMainHandItem());
        if (range == null || !range.isContinuous()) {
            VariableZoom.disengage();
        }
    }

    private static boolean isAiming(LivingEntity player) {
        try {
            return player instanceof LocalPlayer local
                    && IClientPlayerGunOperator.fromLocalPlayer(local).getClientAimingProgress(0f) > 0f;
        } catch (Throwable t) {
            return false;
        }
    }

    private static float aimingProgress(LocalPlayer player, float partialTick) {
        try {
            return IClientPlayerGunOperator.fromLocalPlayer(player).getClientAimingProgress(partialTick);
        } catch (Throwable t) {
            return 0f;
        }
    }
}
