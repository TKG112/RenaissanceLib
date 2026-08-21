package net.tkg.RenaissanceLib.mixin.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.FireSelectKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the fire-select key entirely to RenaissanceLib's {@code FireSelectInput}, which splits it into a tap
 * (cycle, as normal) and a hold (open the fire-mode radial). TaC:Z cycles immediately on press, so both its
 * key and mouse press handlers are cancelled here whenever a gun is held — otherwise a tap would cycle twice
 * (once here, once in our handler) and a hold would still cycle before the radial opened.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = FireSelectKey.class, remap = false)
public class FireSelectKeyMixin {

    @Inject(method = "onFireSelectKeyPress", at = @At("HEAD"), cancellable = true, remap = false)
    private static void renaissance$takeOverKeyPress(InputEvent.Key event, CallbackInfo ci) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (!FireSelectKey.FIRE_SELECT_KEY.matches(event.getKey(), event.getScanCode())) return;
        if (renaissance$holdingGun()) ci.cancel();
    }

    @Inject(method = "onFireSelectMousePress", at = @At("HEAD"), cancellable = true, remap = false)
    private static void renaissance$takeOverMousePress(InputEvent.MouseButton.Post event, CallbackInfo ci) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (!FireSelectKey.FIRE_SELECT_KEY.matchesMouse(event.getButton())) return;
        if (renaissance$holdingGun()) ci.cancel();
    }

    private static boolean renaissance$holdingGun() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && IGun.getIGunOrNull(player.getMainHandItem()) != null;
    }
}
