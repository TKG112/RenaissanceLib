package net.tkg.RenaissanceLib.client.underbarrel;

import com.tacz.guns.client.sound.SoundPlayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.network.ServerMessageUnderbarrelSound;

/**
 * Client-side handler for {@link ServerMessageUnderbarrelSound}: plays another shooter's underbarrel fire
 * sound (3rd-person) positioned at them. Kept in its own client-only class so the packet's {@code handle}
 * never classloads client rendering/sound types on a dedicated server.
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelSoundClient {
    private UnderbarrelSoundClient() {}

    public static void play(ServerMessageUnderbarrelSound message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(message.entityId());
        if (!(entity instanceof LivingEntity shooter)) return;

        ItemStack underbarrel = Underbarrel.getInstalledUnderbarrel(shooter.getMainHandItem());
        ResourceLocation sound = UnderbarrelClient.getFireSound(underbarrel, message.silenced(), true);
        if (sound == null) return;
        SoundPlayManager.playClientSound(shooter, sound, message.volume(), message.pitch(), message.distance());
    }
}
