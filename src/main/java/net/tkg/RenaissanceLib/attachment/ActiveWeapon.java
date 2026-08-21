package net.tkg.RenaissanceLib.attachment;

import net.minecraft.world.item.ItemStack;

/**
 * Which weapon on the gun is currently selected to fire: the main gun ({@link #MAIN}) or the installed
 * underbarrel ({@link #UNDERBARREL}). Stored on the gun's NBT so it persists and syncs, and validated on
 * read — if the underbarrel is gone, we fall back to the main gun. The weapon-select radial writes this
 * (client prediction + server packet); the fire path reads it to route the shot.
 *
 * <p>Kept a plain int (not a boolean) so additional under-mounts could be indexed later; today only 0/1
 * are meaningful because the underbarrel rides the single grip slot.
 */
public final class ActiveWeapon {
    /** The host gun fires normally. */
    public static final int MAIN = 0;
    /** The installed underbarrel fires. */
    public static final int UNDERBARREL = 1;

    private static final String KEY = "RenaissanceActiveWeapon";

    private ActiveWeapon() {}

    /** The selected weapon index, validated against what's actually installed (falls back to {@link #MAIN}). */
    public static int get(ItemStack gunItem) {
        if (gunItem == null || !gunItem.hasTag()) return MAIN;
        int value = gunItem.getTag().getInt(KEY);
        if (value == UNDERBARREL && !Underbarrel.hasUnderbarrel(gunItem)) return MAIN;
        return value == UNDERBARREL ? UNDERBARREL : MAIN;
    }

    /** Whether the underbarrel is the active weapon (and actually installed). */
    public static boolean isUnderbarrelActive(ItemStack gunItem) {
        return get(gunItem) == UNDERBARREL;
    }

    /**
     * Set the active weapon. {@link #UNDERBARREL} only sticks if one is installed; anything else (or a
     * missing underbarrel) resets to {@link #MAIN} and clears the tag.
     */
    public static void set(ItemStack gunItem, int weapon) {
        if (gunItem == null) return;
        if (weapon == UNDERBARREL && Underbarrel.hasUnderbarrel(gunItem)) {
            gunItem.getOrCreateTag().putInt(KEY, UNDERBARREL);
        } else if (gunItem.hasTag()) {
            gunItem.getTag().remove(KEY);
        }
    }
}
