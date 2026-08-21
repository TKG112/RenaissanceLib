package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunReloadData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Ammo state for the installed underbarrel, stored on the gun's NBT (so it persists and syncs to the
 * client for the fire gate / HUD). Independent of the main gun's ammo — TaC:Z's {@code IGun} ammo methods
 * are the host gun's.
 *
 * <ul>
 *   <li>{@code RenaissanceUnderbarrelAmmo} — rounds currently loaded. Absent = "comes loaded": treated as a
 *       full magazine ({@code ammo_amount}) until the underbarrel is first fired/reloaded.</li>
 *   <li>{@code RenaissanceUnderbarrelReloadEnd} — world game-time tick the current reload finishes; the
 *       underbarrel can't fire until then.</li>
 * </ul>
 */
public final class UnderbarrelAmmo {
    private static final String KEY_AMMO = "RenaissanceUnderbarrelAmmo";
    private static final String KEY_RELOAD_END = "RenaissanceUnderbarrelReloadEnd";

    private UnderbarrelAmmo() {}

    /** Base magazine capacity of the underbarrel (its {@code ammo_amount}, at least 1) — no attachments. */
    public static int maxAmmo(GunData ubData) {
        return Math.max(1, ubData.getAmmoAmount());
    }

    /**
     * Magazine capacity of the underbarrel including its own extended-mag attachment, if any. Mirrors TaC:Z's
     * {@code getAmmoCountWithAttachment}: an installed extended-mag raises the count to the underbarrel's
     * {@code extended_mag_ammo_amount} entry for that mag's level.
     */
    public static int maxAmmo(ItemStack gunItem, GunData ubData) {
        int base = maxAmmo(ubData);
        int[] extended = ubData.getExtendedMagAmmoAmount();
        if (extended == null || extended.length == 0) return base;
        int level = magExtendLevel(gunItem, ubData);
        if (level <= 0) return base;
        return Math.max(1, extended[Math.min(level, extended.length) - 1]);
    }

    /** The extended-mag level of the underbarrel's own installed mag attachment (0 = none), clamped to 3. */
    private static int magExtendLevel(ItemStack gunItem, GunData ubData) {
        ResourceLocation id = UnderbarrelAttachments.getInstalledId(gunItem, AttachmentType.EXTENDED_MAG);
        if (DefaultAssets.isEmptyAttachmentId(id)) return 0;
        return TimelessAPI.getCommonAttachmentIndex(id)
                .map(index -> Math.min(Math.max(0, index.getData().getExtendedMagLevel()), 3))
                .orElse(0);
    }

    /** Rounds currently loaded. A fresh underbarrel (no tag yet) is considered a full magazine. */
    public static int get(ItemStack gunItem, GunData ubData) {
        if (gunItem == null || !gunItem.hasTag() || !gunItem.getTag().contains(KEY_AMMO)) {
            return maxAmmo(gunItem, ubData);
        }
        return Math.max(0, gunItem.getTag().getInt(KEY_AMMO));
    }

    public static void set(ItemStack gunItem, int rounds) {
        if (gunItem == null) return;
        gunItem.getOrCreateTag().putInt(KEY_AMMO, Math.max(0, rounds));
    }

    /** Total reload duration in ticks: feed + cooldown "empty" times (seconds → ticks). */
    public static int reloadDurationTicks(GunData ubData) {
        GunReloadData reload = ubData.getReloadData();
        float seconds = 0f;
        if (reload != null) {
            if (reload.getFeed() != null) seconds += reload.getFeed().getEmptyTime();
            if (reload.getCooldown() != null) seconds += reload.getCooldown().getEmptyTime();
        }
        if (seconds <= 0f) seconds = 1.0f;
        return Math.max(1, Math.round(seconds * 20f));
    }

    /**
     * Reload duration accounting for the rounds actually being loaded. For a manual-feed (shell-by-shell)
     * underbarrel the {@code feed} time is <em>per shell</em>, so the total scales with how many rounds are
     * missing; other feed types use the flat {@link #reloadDurationTicks(GunData)}.
     */
    public static int reloadDurationTicks(GunData ubData, int currentAmmo) {
        return reloadDurationTicks(null, ubData, currentAmmo);
    }

    /** As {@link #reloadDurationTicks(GunData, int)} but counts shells against the mag-attachment capacity. */
    public static int reloadDurationTicks(ItemStack gunItem, GunData ubData, int currentAmmo) {
        GunReloadData reload = ubData.getReloadData();
        if (reload == null || reload.getType() != FeedType.MANUAL) {
            return reloadDurationTicks(ubData);
        }
        int capacity = gunItem != null ? maxAmmo(gunItem, ubData) : maxAmmo(ubData);
        int shells = Math.max(1, capacity - Math.max(0, currentAmmo));
        float perShell = reload.getFeed() != null ? reload.getFeed().getEmptyTime() : 0.5f;
        float cooldown = reload.getCooldown() != null ? reload.getCooldown().getEmptyTime() : 0f;
        float seconds = Math.max(0.1f, perShell * shells + cooldown);
        return Math.max(1, Math.round(seconds * 20f));
    }

    /** Whether the underbarrel is mid-reload (can't fire yet). */
    public static boolean isReloading(ItemStack gunItem, Level level) {
        if (gunItem == null || level == null || !gunItem.hasTag()
                || !gunItem.getTag().contains(KEY_RELOAD_END)) {
            return false;
        }
        return level.getGameTime() < gunItem.getTag().getLong(KEY_RELOAD_END);
    }

    /** Begin a reload window ending {@code durationTicks} from now. */
    public static void startReload(ItemStack gunItem, Level level, int durationTicks) {
        if (gunItem == null || level == null) return;
        gunItem.getOrCreateTag().putLong(KEY_RELOAD_END, level.getGameTime() + durationTicks);
    }
}
