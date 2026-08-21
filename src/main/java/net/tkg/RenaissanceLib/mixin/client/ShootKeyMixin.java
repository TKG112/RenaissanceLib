package net.tkg.RenaissanceLib.mixin.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.ShootKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.client.UnderbarrelFire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes fire to the underbarrel. TaC:Z fires the held gun from {@link ShootKey#autoShoot} each client
 * tick; when the underbarrel is the active weapon ({@link ActiveWeapon}) we suppress that and drive the
 * underbarrel instead ({@link UnderbarrelFire}), so the same shoot key fires whichever weapon is selected.
 *
 * <p>Acts on the END phase only (matching {@code autoShoot}'s own gate): the host gun does nothing on
 * START anyway, so cancelling END is enough to stop it firing while the underbarrel is active.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = ShootKey.class, remap = false)
public class ShootKeyMixin {

    @Inject(method = "autoShoot", at = @At("HEAD"), cancellable = true, remap = false)
    private static void renaissance$routeUnderbarrel(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gun = player.getMainHandItem();
        if (IGun.getIGunOrNull(gun) == null) return;
        if (!ActiveWeapon.isUnderbarrelActive(gun)) return;

        UnderbarrelFire.clientTick();
        ci.cancel();
    }
}
