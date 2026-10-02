package net.tkg.RenaissanceLib.client.underbarrel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

/**
 * The underbarrel's camera recoil — the same smooth, data-driven kick TaC:Z uses for guns
 * ({@code CameraSetupEvent}), applied to the underbarrel. A shot builds pitch/yaw spline functions from the
 * underbarrel's recoil data (scaled by its own attachments' recoil modifier), and this evaluates them each
 * frame onto the player's view, applying the per-frame delta so the kick eases in and settles.
 *
 * <p>Parallel to TaC:Z's own recoil rather than reusing its private state: TaC:Z's fires only on the host
 * gun's {@code GunFireEvent}, which never happens while the underbarrel is the active weapon, so the two never
 * overlap. Global client state (shooter-only), like the other underbarrel client effects.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class UnderbarrelRecoil {
    private static PolynomialSplineFunction pitchSpline;
    private static PolynomialSplineFunction yawSpline;
    private static long shootTimeStamp = -1L;
    private static float speed = 1f;
    private static double xRotO;
    private static double yRotO;

    private UnderbarrelRecoil() {}

    /**
     * Begin a recoil kick from the given pitch/yaw spline functions (as {@code GunRecoil} generates them), played at
     * {@code speed} (the attachments' {@code recoil_speed}; 1 = as authored).
     */
    public static void trigger(PolynomialSplineFunction pitch, PolynomialSplineFunction yaw, float speed) {
        pitchSpline = pitch;
        yawSpline = yaw;
        UnderbarrelRecoil.speed = speed;
        shootTimeStamp = System.currentTimeMillis();
        xRotO = 0;
        yRotO = 0;
    }

    @SubscribeEvent
    public static void applyCameraRecoil(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        long timeTotal = (long) ((System.currentTimeMillis() - shootTimeStamp) * (double) speed);
        if (pitchSpline != null && pitchSpline.isValidPoint(timeTotal)) {
            double value = pitchSpline.value(timeTotal);
            player.setXRot(player.getXRot() - (float) (value - xRotO));
            xRotO = value;
        }
        if (yawSpline != null && yawSpline.isValidPoint(timeTotal)) {
            double value = yawSpline.value(timeTotal);
            player.setYRot(player.getYRot() - (float) (value - yRotO));
            yRotO = value;
        }
    }
}
