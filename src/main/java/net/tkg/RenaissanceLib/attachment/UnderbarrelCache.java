package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.GunProperty;
import com.tacz.guns.api.TimelessAPI;
import it.unimi.dsi.fastutil.Pair;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies the underbarrel's own installed attachments' stat modifiers to its sub-gun data — the underbarrel
 * counterpart of TaC:Z's {@link com.tacz.guns.resource.modifier.AttachmentCacheProperty}, which only runs for
 * real gun items (it reads installed attachments through {@code IGun}). This mirrors its eval, but reads the
 * underbarrel's attachments from {@link UnderbarrelAttachments} instead, so every registered TaC:Z stat
 * modifier (inaccuracy, rpm, recoil, damage, silence, …) works on the underbarrel too.
 *
 * <p>Read a result with {@link #get(GunProperty)} (e.g. {@code get(GunProperties.INACCURACY)}), which returns
 * the sub-gun's base value with the underbarrel's attachments applied. Each modifier is init/eval'd defensively
 * — one that assumes a real gun item is skipped rather than breaking the rest. Computed on demand; cheap enough
 * for the fire path (a couple of reads per shot).
 */
public final class UnderbarrelCache {
    @SuppressWarnings("rawtypes")
    private final Map<String, CacheValue> cacheValues = new HashMap<>();

    private UnderbarrelCache() {}

    /** Compute the modified sub-gun properties for the underbarrel installed on {@code gunItem}. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static UnderbarrelCache compute(ItemStack gunItem, ItemStack ubGrip, GunData ubData) {
        UnderbarrelCache cache = new UnderbarrelCache();
        Map<String, IAttachmentModifier<?, ?>> modifiers = AttachmentPropertyManager.getModifiers();
        if (modifiers == null || ubData == null) return cache;

        Map<String, List> collected = new HashMap<>();
        modifiers.forEach((id, modifier) -> {
            try {
                cache.cacheValues.put(id, modifier.initCache(ubGrip, ubData));
                collected.put(id, new ArrayList<>());
            } catch (Throwable ignored) {
                // A modifier that assumes a real gun item is simply skipped for the underbarrel.
            }
        });

        // Read the modifiers declared by each attachment installed on the underbarrel (its own slots).
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ResourceLocation id = UnderbarrelAttachments.getInstalledId(gunItem, type);
            if (DefaultAssets.isEmptyAttachmentId(id)) continue;
            TimelessAPI.getCommonAttachmentIndex(id).ifPresent(index ->
                    index.getData().getModifier().forEach((mid, value) -> {
                        List list = collected.get(mid);
                        if (list != null) list.add(value.getValue());
                    }));
        }

        // Evaluate each modifier that received attachment values, onto its base cache.
        cache.cacheValues.forEach((id, value) -> {
            List mods = collected.get(id);
            if (mods == null || mods.isEmpty()) return;
            try {
                modifiers.get(id).eval(mods, value);
            } catch (Throwable ignored) {
            }
        });
        return cache;
    }

    /** The modified value for {@code key}, or {@code null} if that modifier isn't present/computed. */
    public <T> T get(GunProperty<T> key) {
        CacheValue<?> value = cacheValues.get(key.name());
        return value == null ? null : key.type().cast(value.getValue());
    }

    /**
     * Whether a silencer is installed on the underbarrel — i.e. one of its own attachments sets the
     * {@link GunProperties#SILENCE} flag. Drives the silenced fire sound and flash suppression.
     */
    public boolean isSilenced() {
        Pair<Integer, Boolean> silence = get(GunProperties.SILENCE);
        return silence != null && Boolean.TRUE.equals(silence.right());
    }
}
