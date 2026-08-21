package net.tkg.RenaissanceLib.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.config.client.ZoomConfig;
import com.tacz.guns.util.math.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.tkg.RenaissanceLib.client.VariableZoom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes aim sensitivity track the continuous scope magnification.
 *
 * <p>TaC:Z already scales turn speed by the discrete zoom step ({@link
 * com.tacz.guns.mixin.client.MouseHandlerMixin}). This wraps the same {@code turn} call at a
 * higher mixin priority, so it runs on the outside. When a variable-zoom view is active it
 * runs TaC:Z's own formula with the continuous magnification and applies the turn directly,
 * bypassing the discrete-step version; otherwise it defers to TaC:Z untouched.
 */
@Mixin(value = MouseHandler.class, priority = 1500)
public class MouseHandlerSensitivityMixin {

    @WrapOperation(
            method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void renaissance$continuousSensitivity(LocalPlayer player, double yaw, double pitch,
                                                   Operation<Void> original) {
        ItemStack gunItem = player.getMainHandItem();
        float magnification = VariableZoom.activeMagnification(gunItem);
        if (magnification < 0f) {
            // Not a continuous view — let TaC:Z's discrete scaling run.
            original.call(player, yaw, pitch);
            return;
        }

        float progress = IGunOperator.fromLivingEntity(player).getSynAimingProgress();
        double sensitivityMultiplier = ZoomConfig.ZOOM_SENSITIVITY_BASE_MULTIPLIER.get();
        sensitivityMultiplier = 1 + (sensitivityMultiplier - 1) * progress;

        double originalFov = Minecraft.getInstance().options.fov().get();
        double currentFov = MathUtil.magnificationToFov(1 + (magnification - 1) * progress, originalFov);
        double coefficient = ZoomConfig.SCREEN_DISTANCE_COEFFICIENT.get();
        double denominator = MathUtil.zoomSensitivityRatio(currentFov, originalFov, coefficient) * sensitivityMultiplier;

        double finalYaw = yaw * denominator;
        double finalPitch = crawlLimitedPitch(player, pitch * denominator);
        player.turn(finalYaw, finalPitch);
    }

    /** Mirrors TaC:Z's prone pitch clamp so continuous zoom keeps the same feel while crawling. */
    private static double crawlLimitedPitch(LocalPlayer player, double finalPitch) {
        if (!player.isSwimming() && player.getPose() == Pose.SWIMMING) {
            float playerPitch = -player.getXRot();
            if (playerPitch > 45) finalPitch = Math.max(finalPitch, 0);
            if (playerPitch < -30) finalPitch = Math.min(finalPitch, 0);
        }
        return finalPitch;
    }
}
