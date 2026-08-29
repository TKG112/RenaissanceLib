package net.tkg.RenaissanceLib.mixin.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.InspectKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAmmo;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelInspectHandler;
import net.tkg.RenaissanceLib.network.ClientMessageSetActiveWeapon;
import net.tkg.RenaissanceLib.network.NetworkHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the inspect key inspect the <em>host gun</em> even while the underbarrel is the active weapon.
 *
 * <p>The underbarrel's rig owns the left arm while it's up, so a host inspect played in that state would have a
 * frozen support hand. So instead of cancelling, we switch the active weapon back to the host (giving the arms
 * back) and let TaC:Z's own inspect run in full; {@link UnderbarrelInspectHandler} then re-arms the underbarrel
 * the moment the inspect finishes. When the host gun is already the active weapon we do nothing and let TaC:Z
 * inspect normally.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = InspectKey.class, remap = false)
public class InspectKeyMixin {

    @Inject(method = "onInspectPress", at = @At("HEAD"), cancellable = true, remap = false)
    private static void renaissance$routeUnderbarrelInspect(InputEvent.Key event, CallbackInfo ci) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (!InspectKey.INSPECT_KEY.matches(event.getKey(), event.getScanCode())) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gun = player.getMainHandItem();
        if (IGun.getIGunOrNull(gun) == null) return;
        if (!ActiveWeapon.isUnderbarrelActive(gun)) return; // host active: let TaC:Z inspect it normally

        if (UnderbarrelAmmo.isReloading(gun, mc.level)) {
            ci.cancel(); // don't cut off an underbarrel reload
            return;
        }

        // Hand the arms back to the host for the duration of its inspect, then re-arm the underbarrel.
        ActiveWeapon.set(gun, ActiveWeapon.MAIN); // client prediction; server re-validates
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetActiveWeapon(ActiveWeapon.MAIN));
        UnderbarrelInspectHandler.beginHostInspect();
        // Don't cancel: TaC:Z proceeds to inspect the (now host) gun.
    }
}
