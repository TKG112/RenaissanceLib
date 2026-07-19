package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.tkg.RenaissanceLib.RenaissanceLibMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AttachmentOverrides {
    private static boolean registered = false;

    private AttachmentOverrides() {}

    public static void register() {
        if (registered) return;
        try {
            Map<String, IAttachmentModifier<?, ?>> modifiers = AttachmentPropertyManager.getModifiers();
            if (modifiers == null) {
                RenaissanceLibMod.LOGGER.error(
                        "[RenaissanceLib] TaC:Z modifier registry is null; fire-mode attachments disabled.");
                return;
            }
            if (modifiers.isEmpty()) {
                RenaissanceLibMod.LOGGER.warn(
                        "[RenaissanceLib] TaC:Z modifier registry is empty at common setup - "
                                + "registration order may have changed. Registering anyway.");
            }
            if (modifiers.containsKey(FireModeModifier.ID)) {
                RenaissanceLibMod.LOGGER.warn(
                        "[RenaissanceLib] A modifier with id '{}' is already registered; not overwriting it.",
                        FireModeModifier.ID);
                return;
            }
            modifiers.put(FireModeModifier.ID, new FireModeModifier());
            if (!modifiers.containsKey(AttachmentStatesModifier.ID)) {
                modifiers.put(AttachmentStatesModifier.ID, new AttachmentStatesModifier());
            }
            MinecraftForge.EVENT_BUS.register(AttachmentOverrides.class);
            registered = true;
            RenaissanceLibMod.LOGGER.info("[RenaissanceLib] Registered attachment modifiers '{}', '{}'.",
                    FireModeModifier.ID, AttachmentStatesModifier.ID);
        } catch (Throwable t) {
            RenaissanceLibMod.LOGGER.error(
                    "[RenaissanceLib] Failed to register attachment modifiers; "
                            + "fire-mode attachments will be inactive.", t);
        }
    }

    public static List<FireMode> effectiveFireModes(ItemStack gunItem, GunData gunData) {
        List<FireMode> base = gunData.getFireModeSet();
        List<Object> specs = new ArrayList<>();

        AttachmentDataUtils.getAllAttachmentData(gunItem, gunData, data -> {
            JsonProperty<?> property = data.getModifier().get(FireModeModifier.ID);
            if (property != null && property.getValue() != null) {
                specs.add(property.getValue());
            }
        });

        if (specs.isEmpty()) return base;

        List<FireModeModifier.Spec> typed = new ArrayList<>(specs.size());
        for (Object o : specs) {
            if (o instanceof FireModeModifier.Spec spec) typed.add(spec);
        }
        if (typed.isEmpty()) return base;

        com.tacz.guns.api.modifier.CacheValue<List<FireMode>> cache =
                new com.tacz.guns.api.modifier.CacheValue<>(new ArrayList<>(base));
        new FireModeModifier().eval(typed, cache);
        return cache.getValue();
    }

    public static List<FireMode> effectiveFireModes(ItemStack gunItem) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return List.of();
        ResourceLocation gunId = iGun.getGunId(gunItem);
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> effectiveFireModes(gunItem, index.getGunData()))
                .orElse(List.of());
    }

    @SubscribeEvent
    public static void onAttachmentProperty(AttachmentPropertyEvent event) {
        try {
            ItemStack gunItem = event.getGunItem();
            IGun iGun = IGun.getIGunOrNull(gunItem);
            if (iGun == null) return;

            AttachmentStates.applyStateOverrides(gunItem, event.getCacheProperty());

            List<FireMode> available = effectiveFireModes(gunItem);
            if (available.isEmpty()) return;

            FireMode current = iGun.getFireMode(gunItem);
            if (!available.contains(current)) {
                iGun.setFireMode(gunItem, available.get(0));
            }
        } catch (Throwable t) {
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Fire-mode clamp failed", t);
        }
    }
}
