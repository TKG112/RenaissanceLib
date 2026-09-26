package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.ShootKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.SemiVariant;

/**
 * Binary trigger: fires one extra shot when the trigger is <em>released</em>.
 *
 * <p>TaC:Z only fires on trigger pull. While the gun's binary pseudo-mode is active (underlying
 * SEMI, see {@link SemiVariant#BINARY}), this detects the shoot-key release edge and fires once via the
 * normal client operator — so it still respects cooldown, ammo and every other shoot check.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public class BinaryTriggerHandler {
    private static boolean prevShootDown = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean down = ShootKey.SHOOT_KEY.isDown();
        try {
            if (player == null || player.isSpectator() || mc.screen != null) return;
            if (prevShootDown && !down) {
                ItemStack gunItem = player.getMainHandItem();
                if (IGun.getIGunOrNull(gunItem) != null && firesOnRelease(gunItem)) {
                    IClientPlayerGunOperator.fromLocalPlayer(player).shoot();
                }
            }
        } catch (Throwable t) {
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] binary release-fire failed", t);
        } finally {
            prevShootDown = down;
        }
    }

    private static boolean firesOnRelease(ItemStack gunItem) {
        SemiVariant variant = SemiVariant.active(gunItem);
        return variant != null && variant.firesOnRelease();
    }
}
