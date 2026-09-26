package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.tkg.RenaissanceLib.RenaissanceLibMod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pseudo fire modes layered on TaC:Z's SEMI.
 *
 * <p>The {@link com.tacz.guns.api.item.gun.FireMode} enum is fixed, so these can't be values of it. Instead, when a
 * variant is selected the gun's real fire mode is set to SEMI (so TaC:Z's semi shoot logic runs) and the variant is
 * stored alongside it in the gun's NBT — synced the same way the fire mode is. Each variant is written as a token in
 * a {@code fire_mode} array or a fire-mode attachment's {@code set}/{@code add}/{@code remove}, like a real mode:
 * <ul>
 *   <li>{@link #BINARY} — the trigger also fires one shot on release.</li>
 *   <li>{@link #MANUAL} — plain semi with its own icon and name (for manually-cycled actions).</li>
 * </ul>
 * A gun-data {@code fire_mode} token is recorded (1-based position) as {@code script_param.<token>_fire_mode} — see
 * {@link SemiVariantJson}.
 */
public enum SemiVariant {
    BINARY("binary", true),
    MANUAL("manual", false);

    /** {@code script_param} flag: the {@code fire_mode} listed only variants, so SEMI is implicit and hidden. */
    public static final String IMPLICIT_SEMI = "implicit_semi_fire_mode";

    private static final String TAG = "RenaissanceLibSemiVariant";
    /** Pre-manual saves stored binary as a boolean; still read so existing guns keep their mode. */
    private static final String LEGACY_BINARY_TAG = "RenaissanceLibBinary";

    private final String token;
    private final boolean firesOnRelease;

    SemiVariant(String token, boolean firesOnRelease) {
        this.token = token;
        this.firesOnRelease = firesOnRelease;
    }

    /** The token authors write, e.g. {@code "binary"}. */
    public String token() {
        return token;
    }

    /** Whether releasing the trigger fires one more shot. */
    public boolean firesOnRelease() {
        return firesOnRelease;
    }

    /** The gun-data {@code script_param} key a {@code fire_mode} token is recorded under. */
    public String scriptParam() {
        return token + "_fire_mode";
    }

    /** The HUD / wheel icon a pack may provide (the client falls back to the SEMI icon when it's missing). */
    public ResourceLocation icon() {
        return ResourceLocation.fromNamespaceAndPath(RenaissanceLibMod.MOD_ID, "textures/hud/fire_mode_" + token + ".png");
    }

    public String labelKey() {
        return "tooltip.renaissance_lib.fire_mode." + token;
    }

    /** Whether {@code token} names this variant. */
    public boolean matches(@Nullable String token) {
        return token != null && this.token.equalsIgnoreCase(token.trim());
    }

    @Nullable
    public static SemiVariant byToken(@Nullable String token) {
        if (token == null) return null;
        String t = token.trim().toLowerCase(Locale.ENGLISH);
        for (SemiVariant v : values()) {
            if (v.token.equals(t)) return v;
        }
        return null;
    }

    /** Whether the gun data authored this variant in its {@code fire_mode} array. */
    public boolean authoredIn(GunData gunData) {
        Map<String, Object> params = gunData.getScriptParam();
        return params != null && isTruthy(params.get(scriptParam()));
    }

    /**
     * The 0-based position this variant was authored at in the gun data's original {@code fire_mode} array, or
     * {@code -1} if not authored or authored without a position.
     */
    public int authoredIndex(GunData gunData) {
        Map<String, Object> params = gunData.getScriptParam();
        if (params != null && params.get(scriptParam()) instanceof Number number && number.doubleValue() >= 1) {
            return (int) number.doubleValue() - 1;
        }
        return -1;
    }

    /** The host gun's selected variant, or {@code null} when a real fire mode is selected. */
    @Nullable
    public static SemiVariant active(ItemStack gunItem) {
        CompoundTag tag = gunItem.getTag();
        if (tag == null) return null;
        if (tag.contains(TAG)) return byToken(tag.getString(TAG));
        return tag.getBoolean(LEGACY_BINARY_TAG) ? BINARY : null;
    }

    /** Select {@code variant} on the host gun ({@code null} = a real fire mode). */
    public static void setActive(ItemStack gunItem, @Nullable SemiVariant variant) {
        if (variant != null) {
            CompoundTag tag = gunItem.getOrCreateTag();
            tag.putString(TAG, variant.token);
            tag.remove(LEGACY_BINARY_TAG);
        } else {
            CompoundTag tag = gunItem.getTag();
            if (tag != null) {
                tag.remove(TAG);
                tag.remove(LEGACY_BINARY_TAG);
            }
        }
    }

    /**
     * The fire-select cycle: the real {@code modes} with each of {@code variants} woven in — at its authored
     * {@code fire_mode} position when the gun data has one (restoring the authored order), otherwise right after SEMI
     * (or at the end). Entries are {@link com.tacz.guns.api.item.gun.FireMode}s and {@link SemiVariant}s.
     */
    public static List<Object> cycle(List<FireMode> modes, List<SemiVariant> variants, GunData gunData) {
        List<Object> entries = new ArrayList<>(modes);
        if (hidesSemi(gunData, variants)) entries.remove(FireMode.SEMI);
        List<SemiVariant> authored = new ArrayList<>(), rest = new ArrayList<>();
        for (SemiVariant v : variants) (v.authoredIndex(gunData) >= 0 ? authored : rest).add(v);
        // Ascending original positions: inserting each at its original index rebuilds the authored order.
        authored.sort(Comparator.comparingInt(v -> v.authoredIndex(gunData)));
        for (SemiVariant v : authored) entries.add(Math.min(v.authoredIndex(gunData), entries.size()), v);
        for (SemiVariant v : rest) {
            int after = entries.lastIndexOf(FireMode.SEMI);
            // Behind SEMI and any variant already placed after it, so several stay in declaration order.
            int at = after < 0 ? entries.size() : after + 1;
            while (at < entries.size() && entries.get(at) instanceof SemiVariant) at++;
            entries.add(at, v);
        }
        return entries;
    }

    /**
     * Whether plain SEMI is left out of the cycle: the gun data listed <em>only</em> variants in its
     * {@code fire_mode} (e.g. {@code ["manual"]}), so {@link SemiVariantJson} kept an implicit {@code "semi"} for
     * TaC:Z to run on ({@link #IMPLICIT_SEMI}) — shown only as the variants. If attachments take every variant
     * away, SEMI shows again rather than leaving the gun with nothing.
     */
    public static boolean hidesSemi(GunData gunData, List<SemiVariant> variants) {
        Map<String, Object> params = gunData.getScriptParam();
        return !variants.isEmpty() && params != null && isTruthy(params.get(IMPLICIT_SEMI));
    }

    /**
     * The variant the gun is actually in: the stored one, or — on a gun whose SEMI is hidden ({@link #hidesSemi})
     * sitting in plain SEMI, e.g. fresh from the creative tab — the first variant of its cycle.
     */
    @Nullable
    public static SemiVariant resolve(ItemStack gunItem) {
        SemiVariant stored = active(gunItem);
        if (stored != null) return stored;
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null || iGun.getFireMode(gunItem) != FireMode.SEMI) return null;
        GunData gunData = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunItem))
                .map(index -> index.getGunData()).orElse(null);
        if (gunData == null) return null;
        List<SemiVariant> variants = AttachmentOverrides.availableVariants(gunItem, gunData);
        if (!hidesSemi(gunData, variants)) return null;
        for (Object entry : cycle(List.of(), variants, gunData)) {
            if (entry instanceof SemiVariant variant) return variant;
        }
        return null;
    }

    static boolean isTruthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value instanceof String s) return Boolean.parseBoolean(s) || "1".equals(s);
        return false;
    }
}
