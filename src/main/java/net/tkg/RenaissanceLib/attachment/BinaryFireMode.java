package net.tkg.RenaissanceLib.attachment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Binary trigger — a pseudo fire-mode layered on TaC:Z's SEMI.
 *
 * <p>The {@link com.tacz.guns.api.item.gun.FireMode} enum is fixed, so "binary" can't be one of its
 * values. Instead, when the binary pseudo-mode is selected the gun's real fire mode is set to SEMI
 * (so TaC:Z's semi shoot logic runs) and this flag is stored alongside it in the gun's NBT — synced
 * the same way the fire mode is. While the flag is set, the trigger also fires one shot on release.
 */
public final class BinaryFireMode {
    /** Sentinel entry standing in for the binary pseudo-mode inside the fire-select cycle. */
    public static final Object MARKER = new Object();

    private static final String TAG = "RenaissanceLibBinary";

    private BinaryFireMode() {}

    public static boolean isActive(ItemStack gunItem) {
        CompoundTag tag = gunItem.getTag();
        return tag != null && tag.getBoolean(TAG);
    }

    public static void setActive(ItemStack gunItem, boolean active) {
        if (active) {
            gunItem.getOrCreateTag().putBoolean(TAG, true);
        } else {
            CompoundTag tag = gunItem.getTag();
            if (tag != null) tag.remove(TAG);
        }
    }
}
