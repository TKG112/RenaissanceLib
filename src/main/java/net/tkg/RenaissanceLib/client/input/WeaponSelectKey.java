package net.tkg.RenaissanceLib.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.network.ClientMessageSetActiveWeapon;
import net.tkg.RenaissanceLib.network.NetworkHandler;
import org.lwjgl.glfw.GLFW;

/**
 * Weapon-select input: a single key that <em>cycles</em> the active weapon on the held gun between the host
 * gun and its installed underbarrel. Since a gun carries at most one underbarrel, there's nothing to pick
 * from a menu — each press just toggles — so this replaces the old weapon radial wheel. Writes
 * {@link ActiveWeapon} (client prediction) and notifies the server, which re-validates authoritatively.
 */
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class WeaponSelectKey {

    public static final KeyMapping SELECT_WEAPON = new KeyMapping(
            "key.renaissance_lib.weapon_wheel.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.category.renaissance_lib");

    private static boolean wasDown = false;

    private WeaponSelectKey() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        boolean down = !SELECT_WEAPON.isUnbound() && SELECT_WEAPON.isDown();
        if (down && !wasDown) {
            toggleActiveWeapon();
        }
        wasDown = down;
    }

    private static void toggleActiveWeapon() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;

        ItemStack gun = player.getMainHandItem();
        if (IGun.getIGunOrNull(gun) == null) return;
        if (!Underbarrel.hasUnderbarrel(gun)) return; // nothing to switch to

        int next = ActiveWeapon.isUnderbarrelActive(gun) ? ActiveWeapon.MAIN : ActiveWeapon.UNDERBARREL;
        ActiveWeapon.set(gun, next); // client prediction; server re-validates and applies authoritatively
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetActiveWeapon(next));
    }
}
