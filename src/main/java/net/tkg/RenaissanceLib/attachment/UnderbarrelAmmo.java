package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunReloadData;
import com.tacz.guns.resource.pojo.data.gun.GunReloadTime;
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
    private static final String KEY_FEED_END = "RenaissanceUnderbarrelFeedEnd";
    private static final String KEY_PRE_RELOAD = "RenaissanceUnderbarrelPreReloadAmmo";

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

    /**
     * The <b>feed</b> phase in ticks: how long until the rounds are loaded (the {@code feed} "empty" time). For
     * a manual (shell-by-shell) feed this is per-shell and scales with the rounds being loaded; otherwise it's
     * the flat feed time. The post-feed {@code cooldown} is separate — see {@link #cooldownTicks}.
     */
    public static int feedTicks(ItemStack gunItem, GunData ubData, int currentAmmo) {
        GunReloadData reload = ubData.getReloadData();
        float perShell = reload != null ? stateTime(reload.getFeed(), currentAmmo) : 0f;
        if (perShell <= 0f) perShell = 1.0f;
        if (isManual(ubData)) {
            int capacity = gunItem != null ? maxAmmo(gunItem, ubData) : maxAmmo(ubData);
            int shells = Math.max(1, capacity - Math.max(0, currentAmmo));
            return Math.max(1, Math.round(Math.max(0.1f, perShell * shells) * 20f));
        }
        return Math.max(1, Math.round(perShell * 20f));
    }

    /**
     * The authored reload {@code cooldown} in ticks for the reload state (empty / tactical). Zero if none. Its
     * meaning depends on the feed type — see {@link #lockTicks}.
     */
    public static int cooldownTicks(GunData ubData, int currentAmmo) {
        GunReloadData reload = ubData.getReloadData();
        float seconds = reload != null ? stateTime(reload.getCooldown(), currentAmmo) : 0f;
        return Math.max(0, Math.round(seconds * 20f));
    }

    /**
     * The full reload lockout in ticks — how long from reload start until the underbarrel can fire again. For a
     * magazine feed this is TaC:Z's own semantics: {@code cooldown} is the <em>total</em> reload time measured
     * from the start (so the lockout is the later of feed and cooldown, not their sum). For a shell-by-shell
     * (manual) feed the feed scales with the shells loaded, so the cooldown follows it.
     */
    public static int lockTicks(ItemStack gunItem, GunData ubData, int currentAmmo) {
        int feed = feedTicks(gunItem, ubData, currentAmmo);
        int cooldown = cooldownTicks(ubData, currentAmmo);
        return isManual(ubData) ? feed + cooldown : Math.max(feed, cooldown);
    }

    private static boolean isManual(GunData ubData) {
        GunReloadData reload = ubData.getReloadData();
        return reload != null && reload.getType() == FeedType.MANUAL;
    }

    /** TaC:Z's reload-state pick: the tactical time when rounds remain (if authored), else the empty time. */
    private static float stateTime(GunReloadTime time, int currentAmmo) {
        if (time == null) return 0f;
        if (currentAmmo > 0 && time.getTacticalTime() > 0f) return time.getTacticalTime();
        return time.getEmptyTime();
    }

    /** Whether the underbarrel is mid-reload (can't fire yet) — locked through feed + cooldown. */
    public static boolean isReloading(ItemStack gunItem, Level level) {
        if (gunItem == null || level == null || !gunItem.hasTag()
                || !gunItem.getTag().contains(KEY_RELOAD_END)) {
            return false;
        }
        return level.getGameTime() < gunItem.getTag().getLong(KEY_RELOAD_END);
    }

    /** True while the rounds haven't been fed in yet (before the feed phase ends) during an active reload. */
    private static boolean beforeFeed(ItemStack gunItem, Level level) {
        if (gunItem == null || level == null || !gunItem.hasTag()
                || !gunItem.getTag().contains(KEY_FEED_END)) {
            return false;
        }
        return level.getGameTime() < gunItem.getTag().getLong(KEY_FEED_END);
    }

    /**
     * Rounds to <em>display</em>: the pre-reload count until the feed phase completes, then the loaded count.
     * So the magazine visibly fills when the rounds actually go in (at the feed time), not at reload start —
     * while firing stays locked through the following cooldown ({@link #isReloading}).
     */
    public static int getDisplay(ItemStack gunItem, GunData ubData, Level level) {
        if (beforeFeed(gunItem, level) && gunItem.getTag().contains(KEY_PRE_RELOAD)) {
            return Math.max(0, gunItem.getTag().getInt(KEY_PRE_RELOAD));
        }
        return get(gunItem, ubData);
    }

    /**
     * Begin a two-phase reload: the rounds load (display) {@code feedTicks} after start, and firing is allowed
     * {@code lockTicks} after start ({@link #lockTicks}). {@code preReloadAmmo} is the count shown until the feed
     * completes. The loaded count itself ({@link #set}) is written by the caller at reload start (authoritative),
     * but stays hidden by {@link #getDisplay} and unusable by {@link #isReloading} until the phases elapse.
     */
    public static void startReload(ItemStack gunItem, Level level, int feedTicks, int lockTicks,
                                   int preReloadAmmo) {
        if (gunItem == null || level == null) return;
        long now = level.getGameTime();
        gunItem.getOrCreateTag().putLong(KEY_FEED_END, now + Math.max(0, feedTicks));
        gunItem.getOrCreateTag().putLong(KEY_RELOAD_END, now + Math.max(Math.max(0, feedTicks), lockTicks));
        gunItem.getOrCreateTag().putInt(KEY_PRE_RELOAD, Math.max(0, preReloadAmmo));
    }
}
