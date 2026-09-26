package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The underbarrel's currently selected fire mode, as an index into its own cycle, stored on the gun NBT. Per
 * the design, the weapon-select radial switches which weapon is active while the fire-mode key cycles the
 * <em>active</em> weapon's own modes — so the underbarrel keeps its own selection separate from the host gun's.
 *
 * <p>The cycle ({@link #cycle}) is the underbarrel's declared {@link FireMode}s plus the {@link SemiVariant}
 * pseudo-modes (binary, manual) it opts into, woven in at their authored positions. A variant isn't a
 * {@link FireMode} enum value, so when one is selected {@link #get} reports SEMI (so the semi fire path runs) and
 * {@link #variant} names it (binary also fires on release).
 */
public final class UnderbarrelFireMode {
    private static final String KEY = "RenaissanceUnderbarrelFireMode";

    private UnderbarrelFireMode() {}

    /** The underbarrel's declared fire modes (variants excluded — see {@link #variants}). */
    public static List<FireMode> getModes(GunData ubData) {
        List<FireMode> list = ubData.getFireModeSet();
        return (list == null || list.isEmpty()) ? List.of(FireMode.SEMI) : list;
    }

    /**
     * The semi variants the underbarrel offers — <em>opt-in only</em>, exactly like the host gun: the author adds
     * the token ({@code "binary"}, {@code "manual"}) to its {@code fire_mode} array (recorded as
     * {@code script_param.<token>_fire_mode}, see {@link SemiVariantJson}). Never auto-added.
     */
    public static List<SemiVariant> variants(GunData ubData) {
        List<SemiVariant> variants = new ArrayList<>();
        for (SemiVariant variant : SemiVariant.values()) {
            if (variant.authoredIn(ubData)) variants.add(variant);
        }
        return variants;
    }

    /**
     * The cycle in order: {@link FireMode}s and {@link SemiVariant}s. Every consumer (the stored index, the
     * fire-mode wheel, the HUD icon, the fire logic) derives from this.
     */
    public static List<Object> cycle(GunData ubData) {
        return SemiVariant.cycle(getModes(ubData), variants(ubData), ubData);
    }

    /** The selected cycle index, clamped into the cycle. */
    public static int getIndex(ItemStack gunItem, GunData ubData) {
        int size = cycle(ubData).size();
        if (gunItem == null || !gunItem.hasTag() || !gunItem.getTag().contains(KEY)) return 0;
        int stored = gunItem.getTag().getInt(KEY);
        return Math.floorMod(stored, size);
    }

    /** The selected semi variant, or {@code null} when a real fire mode is selected. */
    @Nullable
    public static SemiVariant variant(ItemStack gunItem, GunData ubData) {
        return cycle(ubData).get(getIndex(gunItem, ubData)) instanceof SemiVariant v ? v : null;
    }

    /** The real {@link FireMode} to fire with — SEMI while a variant is selected, else the declared mode. */
    public static FireMode get(ItemStack gunItem, GunData ubData) {
        Object entry = cycle(ubData).get(getIndex(gunItem, ubData));
        return entry instanceof FireMode mode ? mode : FireMode.SEMI;
    }

    public static void setIndex(ItemStack gunItem, GunData ubData, int index) {
        if (gunItem == null) return;
        gunItem.getOrCreateTag().putInt(KEY, Math.floorMod(index, cycle(ubData).size()));
    }

    /** Advance to the next entry in the cycle; returns the new real fire mode. */
    public static FireMode cycle(ItemStack gunItem, GunData ubData) {
        int next = getIndex(gunItem, ubData) + 1;
        setIndex(gunItem, ubData, next);
        return get(gunItem, ubData);
    }
}
