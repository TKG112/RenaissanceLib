package net.tkg.RenaissanceLib.client.underbarrel;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.AnimationName;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import com.tacz.guns.compat.playeranimator.animation.AnimationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The 3rd-person (PlayerAnimator) body animation for an underbarrel reload. TaC:Z plays its {@code reload_upper}
 * player animation on the one-shot upper-body layer when a gun reloads ({@code AnimationManager.onReload}), which
 * also overrides the looping hold/crouch upper-body pose — so the gun straightens out of the crouch cant for the
 * reload. The underbarrel reload doesn't fire TaC:Z's reload event, so we play the same animation ourselves,
 * mirroring {@code onReload}: {@code lie_reload} when prone, and skipped for the local player in first person
 * (no body model there). PlayerAnimator is optional, hence the {@link PlayerAnimatorCompat#isInstalled()} gate.
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelPlayerAnimation {

    private UnderbarrelPlayerAnimation() {}

    /** Play the reload body animation on {@code player} (the local player or another one), if applicable. */
    public static void playReload(AbstractClientPlayer player) {
        if (player == null || !PlayerAnimatorCompat.isInstalled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        ItemStack gun = player.getMainHandItem();
        if (IGun.getIGunOrNull(gun) == null) return;
        TimelessAPI.getGunDisplay(gun).ifPresent(display -> Impl.playReload(player, display));
    }

    /** Server-sent notice that entity {@code entityId} started an underbarrel reload (for nearby players). */
    public static void onRemoteReload(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(entityId);
        if (entity instanceof AbstractClientPlayer player) {
            playReload(player);
        }
    }

    /**
     * Touches {@link AnimationManager}, which links against PlayerAnimator classes — only reached after the
     * {@link PlayerAnimatorCompat#isInstalled()} check, so it never loads without the mod.
     */
    private static final class Impl {
        static void playReload(AbstractClientPlayer player, GunDisplayInstance display) {
            // Prone = the SWIMMING pose out of water (same test as AnimationManager.isPlayerLie).
            boolean lie = !player.isSwimming() && player.getPose() == Pose.SWIMMING;
            AnimationManager.playOnceAnimation(player, display, PlayerAnimatorCompat.ONCE_UPPER_ANIMATION,
                    lie ? AnimationName.LIE_RELOAD : AnimationName.RELOAD_UPPER);
        }
    }
}
