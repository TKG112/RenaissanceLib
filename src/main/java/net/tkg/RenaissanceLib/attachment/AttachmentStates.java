package net.tkg.RenaissanceLib.attachment;

import com.google.gson.JsonObject;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.GunProperty;
import com.tacz.guns.api.TimelessAPI;
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void applyStateOverrides(ItemStack gunItem, AttachmentCacheProperty cacheProperty) {
        Map<String, IAttachmentModifier<?, ?>> modifiers = AttachmentPropertyManager.getModifiers();
        Map<String, GunProperty<?>> properties = GunProperties.all();

        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;

            AttachmentStatesModifier.States states = getStates(gunItem, type);
            if (states == null) continue;

            String state = getState(gunItem, type);
            JsonObject body = states.getStateBody(state);
            if (body == null) continue;

            String bodyJson = body.toString();

            for (Map.Entry<String, IAttachmentModifier<?, ?>> entry : modifiers.entrySet()) {
                String id = entry.getKey();
                IAttachmentModifier modifier = entry.getValue();

                if (AttachmentStatesModifier.ID.equals(id) || FireModeModifier.ID.equals(id)) continue;

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
                            "[RenaissanceLib] Failed applying state override '{}' (slot {}, state {})",
                            id, type.name(), state, t);
                }
            }
        }
    }

}
