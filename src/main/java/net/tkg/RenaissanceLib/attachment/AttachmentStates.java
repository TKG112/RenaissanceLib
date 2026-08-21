package net.tkg.RenaissanceLib.attachment;

import com.google.gson.JsonObject;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.GunProperty;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.tkg.RenaissanceLib.RenaissanceLibMod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AttachmentStates {

    private static final String TAG = "RenaissanceLibAttachmentStates";

    private AttachmentStates() {}

    public static String getState(ItemStack gunItem, AttachmentType type) {
        AttachmentStatesModifier.States states = getStates(gunItem, type);
        String fallback = states == null ? "" : states.getDefaultState();

        CompoundTag tag = gunItem.getTag();
        if (tag == null || !tag.contains(TAG)) return fallback;
        CompoundTag stored = tag.getCompound(TAG);
        String name = stored.getString(type.name());
        if (name.isEmpty()) return fallback;

        if (states != null && !states.getCycle().contains(name)) return fallback;
        return name;
    }

    public static void setState(ItemStack gunItem, AttachmentType type, String state) {
        CompoundTag tag = gunItem.getOrCreateTag();
        CompoundTag stored = tag.contains(TAG) ? tag.getCompound(TAG) : new CompoundTag();
        stored.putString(type.name(), state);
        tag.put(TAG, stored);
    }

    @Nullable
    public static String cycleState(ItemStack gunItem, AttachmentType type) {
        AttachmentStatesModifier.States states = getStates(gunItem, type);
        if (states == null || states.getCycle().size() < 2) return null;
        String next = states.next(getState(gunItem, type));
        setState(gunItem, type, next);
        return next;
    }

    @Nullable
    public static AttachmentStatesModifier.States getStates(ItemStack gunItem, AttachmentType type) {
        AttachmentData data = getAttachmentData(gunItem, type);
        if (data == null) return null;
        JsonProperty<?> property = data.getModifier().get(AttachmentStatesModifier.ID);
        if (property == null) return null;
        Object value = property.getValue();
        return value instanceof AttachmentStatesModifier.States states ? states : null;
    }

    // ---- Path-addressed access (ToggleTarget: a slot + a MountPath into the scope's rail tree) ----------

    /** NBT sub-key for a target. Root targets keep the legacy per-slot key so existing guns are unchanged. */
    private static String keyFor(ToggleTarget target) {
        if (target.isRoot()) return target.slot().name();
        StringBuilder sb = new StringBuilder(target.slot().name()).append('#');
        int[] indices = target.path().toArray();
        for (int i = 0; i < indices.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(indices[i]);
        }
        return sb.toString();
    }

    /** The {@code states} block declared by an attachment <em>item</em> (a rail-mounted optic), or null. */
    @Nullable
    public static AttachmentStatesModifier.States getStatesForItem(ItemStack attachmentItem) {
        if (attachmentItem == null || attachmentItem.isEmpty()) return null;
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment == null) return null;
        return TimelessAPI.getCommonAttachmentIndex(iAttachment.getAttachmentId(attachmentItem))
                .map(index -> index.getData().getModifier().get(AttachmentStatesModifier.ID))
                .filter(p -> p != null && p.getValue() instanceof AttachmentStatesModifier.States)
                .map(p -> (AttachmentStatesModifier.States) p.getValue())
                .orElse(null);
    }

    @Nullable
    public static AttachmentStatesModifier.States getStates(ItemStack gunItem, ToggleTarget target) {
        if (target.isRoot()) return getStates(gunItem, target.slot());
        return getStatesForItem(RailStorage.getMountedOnGun(gunItem, target.path()));
    }

    public static String getState(ItemStack gunItem, ToggleTarget target) {
        AttachmentStatesModifier.States states = getStates(gunItem, target);
        String fallback = states == null ? "" : states.getDefaultState();

        CompoundTag tag = gunItem.getTag();
        if (tag == null || !tag.contains(TAG)) return fallback;
        String name = tag.getCompound(TAG).getString(keyFor(target));
        if (name.isEmpty()) return fallback;
        if (states != null && !states.getCycle().contains(name)) return fallback;
        return name;
    }

    public static void setState(ItemStack gunItem, ToggleTarget target, String state) {
        CompoundTag tag = gunItem.getOrCreateTag();
        CompoundTag stored = tag.contains(TAG) ? tag.getCompound(TAG) : new CompoundTag();
        stored.putString(keyFor(target), state);
        tag.put(TAG, stored);
    }

    @Nullable
    public static String cycleState(ItemStack gunItem, ToggleTarget target) {
        AttachmentStatesModifier.States states = getStates(gunItem, target);
        if (states == null || states.getCycle().size() < 2) return null;
        String next = states.next(getState(gunItem, target));
        setState(gunItem, target, next);
        return next;
    }

    @Nullable
    private static AttachmentData getAttachmentData(ItemStack gunItem, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return null;
        ResourceLocation attachmentId = iGun.getAttachmentId(gunItem, type);
        if (DefaultAssets.isEmptyAttachmentId(attachmentId)) return null;
        return TimelessAPI.getCommonAttachmentIndex(attachmentId)
                .map(index -> index.getData())
                .orElse(null);
    }

    public static List<AttachmentType> getToggleableSlots(ItemStack gunItem) {
        List<AttachmentType> result = new ArrayList<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            AttachmentStatesModifier.States states = getStates(gunItem, type);
            if (states != null && states.getCycle().size() > 1) result.add(type);
        }
        return result;
    }

    @Nullable
    public static String getAnimationFor(ItemStack gunItem, AttachmentType type, String state) {
        AttachmentStatesModifier.States states = getStates(gunItem, type);
        return states == null ? null : states.getAnimation(state);
    }

    /**
     * Write the scope's shared combined-cycle zoom counter (used by the rail aim system to pick the active
     * optic + view). Used to switch the view to a rail-mounted optic when its state changes.
     */
    public static void setScopeZoomNumber(ItemStack gunItem, int zoomNumber) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return;
        CompoundTag scopeTag = iGun.getAttachmentTag(gunItem, AttachmentType.SCOPE);
        if (scopeTag == null) return;
        AttachmentItemDataAccessor.setZoomNumberToTag(scopeTag, zoomNumber);
    }

    public static void applyZoomForState(ItemStack gunItem, AttachmentType type, String state) {
        AttachmentStatesModifier.States states = getStates(gunItem, type);
        if (states == null) return;

        Integer zoomIndex = states.getZoomIndex(state);
        if (zoomIndex == null) return;

        if (type != AttachmentType.SCOPE) {
            RenaissanceLibMod.LOGGER.warn(
                    "[RenaissanceLib] '{}' declared on slot {} (state '{}') but only the scope "
                            + "slot has zoom levels; ignoring.",
                    AttachmentStatesModifier.ZOOM_INDEX_KEY, type.name(), state);
            return;
        }
        if (zoomIndex < 0) {
            RenaissanceLibMod.LOGGER.warn("[RenaissanceLib] Negative {} ({}) in state '{}'; ignoring.",
                    AttachmentStatesModifier.ZOOM_INDEX_KEY, zoomIndex, state);
            return;
        }

        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return;

        CompoundTag scopeTag = iGun.getAttachmentTag(gunItem, AttachmentType.SCOPE);
        if (scopeTag == null) return;
        AttachmentItemDataAccessor.setZoomNumberToTag(scopeTag, zoomIndex);
    }

    public static void applyStateOverrides(ItemStack gunItem, AttachmentCacheProperty cacheProperty) {
        Map<String, IAttachmentModifier<?, ?>> modifiers = AttachmentPropertyManager.getModifiers();
        Map<String, GunProperty<?>> properties = GunProperties.all();

        // Top-level slots (the scope itself included via its ROOT state).
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            AttachmentStatesModifier.States states = getStates(gunItem, type);
            if (states == null) continue;
            applyStateBody(gunItem, ToggleTarget.slot(type), states, cacheProperty, modifiers, properties);
        }

        // Rail-mounted sub-attachments (canted sights, piggyback optics) under the scope.
        for (ToggleTarget target : AttachmentToggleTargets.railTargets(gunItem)) {
            AttachmentStatesModifier.States states = getStates(gunItem, target);
            if (states == null) continue;
            applyStateBody(gunItem, target, states, cacheProperty, modifiers, properties);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyStateBody(ItemStack gunItem, ToggleTarget target,
                                       AttachmentStatesModifier.States states,
                                       AttachmentCacheProperty cacheProperty,
                                       Map<String, IAttachmentModifier<?, ?>> modifiers,
                                       Map<String, GunProperty<?>> properties) {
        String state = getState(gunItem, target);
        JsonObject body = states.getStateBody(state);
        if (body == null) return;

        // A state's overrides only fold into the cache while its condition is met (e.g. a bipod's recoil
        // bonus only when prone). The animation/visual state is unaffected - only the stat effect is gated.
        // The shooter comes from the property-event mixin; if absent we fail closed (no free stats).
        List<String> require = states.getRequire(state);
        if (!require.isEmpty() && !StanceConditions.anyMet(PropertyEventShooter.get(), require)) {
            return;
        }

        String bodyJson = body.toString();

        for (Map.Entry<String, IAttachmentModifier<?, ?>> entry : modifiers.entrySet()) {
            String id = entry.getKey();
            IAttachmentModifier modifier = entry.getValue();

            if (AttachmentStatesModifier.ID.equals(id) || FireModeModifier.ID.equals(id)) continue;

            // Only apply overrides the state actually declares. Without this, modifiers whose readJson
            // doesn't null-guard a missing key (e.g. RecoilModifier) throw on every state that omits them.
            if (!body.has(id)) continue;

            GunProperty property = properties.get(id);
            if (property == null) continue;

            try {
                JsonProperty parsed = modifier.readJson(bodyJson);
                if (parsed == null || parsed.getValue() == null) continue;

                Object current = cacheProperty.getCache(id);
                CacheValue cache = new CacheValue(current);
                List values = new ArrayList();
                values.add(parsed.getValue());
                modifier.eval(values, cache);

                cacheProperty.setCache(property, cache.getValue());
            } catch (Throwable t) {
                RenaissanceLibMod.LOGGER.error(
                        "[RenaissanceLib] Failed applying state override '{}' ({}, state {})",
                        id, target, state, t);
            }
        }
    }

}
