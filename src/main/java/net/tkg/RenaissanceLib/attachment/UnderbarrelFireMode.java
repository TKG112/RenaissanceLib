package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * The underbarrel's currently selected fire mode, as an index into its own cycle, stored on the gun NBT. Per
 * the design, the weapon-select radial switches which weapon is active while the fire-mode key cycles the
 * <em>active</em> weapon's own modes — so the underbarrel keeps its own selection separate from the host gun's.
 *
 * <p>The cycle is the underbarrel's declared {@link FireMode}s plus a <em>binary</em> pseudo-mode appended when
 * the underbarrel supports SEMI (binary layers on semi, exactly like the host gun's binary trigger). Binary
 * isn't a {@link FireMode} enum value, so it lives as the last cycle entry: when selected, {@link #get} reports
 * SEMI (so the semi fire path runs) and {@link #isBinary} is true (so the trigger also fires on release).
 */
public final class UnderbarrelFireMode {
    private static final String KEY = "RenaissanceUnderbarrelFireMode";

    private UnderbarrelFireMode() {}

    /** The underbarrel's declared fire modes (binary excluded — it's a pseudo-mode, see {@link #binaryPosition}). */
    public static List<FireMode> getModes(GunData ubData) {
        List<FireMode> list = ubData.getFireModeSet();
        return (list == null || list.isEmpty()) ? List.of(FireMode.SEMI) : list;
    }

    /**
     * Whether a binary pseudo-mode is offered — <em>opt-in only</em>, exactly like the host gun: the author
     * adds {@code "binary"} to the underbarrel's {@code fire_mode} array (recorded as
     * {@code script_param.binary_fire_mode}, see {@link BinaryFireModeJson}). It is never auto-added (an
     * underbarrel that merely fires SEMI does <em>not</em> get binary). Every consumer (the cycle size,
     * {@link #isBinary}, the fire-mode wheel, the HUD icon, the fire logic) derives from this.
     */
    public static boolean binaryAvailable(GunData ubData) {
        Map<String, Object> params = ubData.getScriptParam();
        return params != null && isTruthy(params.get("binary_fire_mode"));
    }

    /**
     * The 0-based position binary occupies in the cycle, or {@code -1} if not available. Honours the authored
     * position: {@code script_param.binary_fire_mode} is 1-based (its index in the original {@code fire_mode}
     * array), clamped into {@code [0, modes]}. A truthy-but-non-numeric value falls back to last.
     */
    public static int binaryPosition(GunData ubData) {
        if (!binaryAvailable(ubData)) return -1;
        int max = getModes(ubData).size();
        Object value = ubData.getScriptParam().get("binary_fire_mode");
        if (value instanceof Number n) {
            return Math.max(0, Math.min((int) n.doubleValue() - 1, max));
        }
        return max;
    }

    private static boolean isTruthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value instanceof String s) return Boolean.parseBoolean(s) || "1".equals(s);
        return false;
    }

    /** Number of entries in the cycle: the declared modes, plus binary if available. */
    private static int cycleSize(GunData ubData) {
        return getModes(ubData).size() + (binaryAvailable(ubData) ? 1 : 0);
    }

    /** The selected cycle index, clamped into the cycle. */
    public static int getIndex(ItemStack gunItem, GunData ubData) {
        int size = cycleSize(ubData);
        if (gunItem == null || !gunItem.hasTag() || !gunItem.getTag().contains(KEY)) return 0;
        int stored = gunItem.getTag().getInt(KEY);
        return Math.floorMod(stored, size);
    }

    /** Whether the binary pseudo-mode is currently selected (the cycle slot at {@link #binaryPosition}). */
    public static boolean isBinary(ItemStack gunItem, GunData ubData) {
        int pos = binaryPosition(ubData);
        return pos >= 0 && getIndex(gunItem, ubData) == pos;
    }

    /** The real {@link FireMode} to fire with — SEMI while binary is selected, else the declared mode. */
    public static FireMode get(ItemStack gunItem, GunData ubData) {
        if (isBinary(gunItem, ubData)) return FireMode.SEMI;
        List<FireMode> modes = getModes(ubData);
        int index = getIndex(gunItem, ubData);
        int pos = binaryPosition(ubData);
        // Map the cycle index to a declared-mode index, skipping the binary slot.
        int modeIndex = (pos >= 0 && index > pos) ? index - 1 : index;
        return modes.get(Math.floorMod(modeIndex, modes.size()));
    }

    public static void setIndex(ItemStack gunItem, GunData ubData, int index) {
        if (gunItem == null) return;
        gunItem.getOrCreateTag().putInt(KEY, Math.floorMod(index, cycleSize(ubData)));
    }

    /** Advance to the next entry in the cycle; returns the new real fire mode. */
    public static FireMode cycle(ItemStack gunItem, GunData ubData) {
        int next = getIndex(gunItem, ubData) + 1;
        setIndex(gunItem, ubData, next);
        return get(gunItem, ubData);
    }
}
