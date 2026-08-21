package net.tkg.RenaissanceLib.mixin.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.ReloadKey;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAmmo;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelAnimator;
import net.tkg.RenaissanceLib.network.ClientMessageReloadUnderbarrel;
import net.tkg.RenaissanceLib.network.NetworkHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes the reload key to the underbarrel when it's the active weapon: instead of reloading the host gun,
 * we ask the server to reload the underbarrel ({@link ClientMessageReloadUnderbarrel}) and cancel TaC:Z's
 * own reload. To reload the host gun again, switch back to it via the weapon wheel.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = ReloadKey.class, remap = false)
public class ReloadKeyMixin {

    /** Borrowed TaC:Z M320 reload sound for the underbarrel (single-shot, so the empty-reload variant). */
    private static final ResourceLocation GRENADE_RELOAD_SOUND =
            new ResourceLocation("tacz", "m320/m320_reload_empty");

    private static long renaissance$lastReloadMs = 0L;

    @Inject(method = "onReloadPress", at = @At("HEAD"), cancellable = true, remap = false)
    private static void renaissance$routeUnderbarrelReload(InputEvent.Key event, CallbackInfo ci) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (!ReloadKey.RELOAD_KEY.matches(event.getKey(), event.getScanCode())) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gun = player.getMainHandItem();
        if (IGun.getIGunOrNull(gun) == null) return;
        if (!ActiveWeapon.isUnderbarrelActive(gun)) return;

        // Route the key to the underbarrel (cancel TaC:Z's reload) even when we don't actually start one, so a
        // press never falls through to reloading the host gun.
        ci.cancel();

        // Don't restart the reload (and its animation) while one is already running or the magazine is full —
        // otherwise holding/spamming the key re-triggers it every press. Mirrors the server's own guards.
        GunData ubData = Underbarrel.getUnderbarrelData(Underbarrel.getInstalledUnderbarrel(gun));
        if (ubData == null) return;
        if (UnderbarrelAmmo.get(gun, ubData) >= UnderbarrelAmmo.maxAmmo(gun, ubData)) return;
        if (UnderbarrelAmmo.isReloading(gun, mc.level)) return;

        // Immediate client-side gate: the server-synced isReloading above only kicks in once the reload state
        // syncs back, so cover the gap until then by refusing to re-trigger within the reload's own duration.
        long now = System.currentTimeMillis();
        int current = UnderbarrelAmmo.get(gun, ubData);
        long reloadMs = Math.max(0L, UnderbarrelAmmo.reloadDurationTicks(gun, ubData, current)) * 50L;
        if (now - renaissance$lastReloadMs < reloadMs) return;
        renaissance$lastReloadMs = now;

        NetworkHandler.CHANNEL.sendToServer(new ClientMessageReloadUnderbarrel());
        UnderbarrelAnimator.triggerReload(gun, Underbarrel.getInstalledUnderbarrel(gun), ubData);

        // Shooter's own reload sound (borrows TaC:Z's M320 reload, like the fire path borrows its shot sound).
        float pitch = 0.95f + player.getRandom().nextFloat() * 0.1f;
        SoundPlayManager.playClientSound(player, GRENADE_RELOAD_SOUND, 0.9f, pitch, 16);
    }
}
