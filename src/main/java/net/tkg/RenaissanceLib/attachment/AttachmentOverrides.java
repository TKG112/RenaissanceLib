package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.modifier.CacheValue;
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
            if (!modifiers.containsKey(ScopeShaderModifier.ID)) {
                modifiers.put(ScopeShaderModifier.ID, new ScopeShaderModifier());
            }
            if (!modifiers.containsKey(RailsModifier.ID)) {
                modifiers.put(RailsModifier.ID, new RailsModifier());
            }
            if (!modifiers.containsKey(UnderbarrelDataModifier.ID)) {
                modifiers.put(UnderbarrelDataModifier.ID, new UnderbarrelDataModifier());
            }
            if (!modifiers.containsKey(ConversionModifier.ID)) {
                modifiers.put(ConversionModifier.ID, new ConversionModifier());
            }
            if (!modifiers.containsKey(FireAnimationModifier.ID)) {
                modifiers.put(FireAnimationModifier.ID, new FireAnimationModifier());
            }
            if (!modifiers.containsKey(AimAnimationModifier.ID)) {
                modifiers.put(AimAnimationModifier.ID, new AimAnimationModifier());
            }
            if (!modifiers.containsKey(RecoilSpeedModifier.ID)) {
                modifiers.put(RecoilSpeedModifier.ID, new RecoilSpeedModifier());
            }
            MinecraftForge.EVENT_BUS.register(AttachmentOverrides.class);
            registered = true;
            RenaissanceLibMod.LOGGER.info(
                    "[RenaissanceLib] Registered attachment modifiers '{}', '{}', '{}', '{}', '{}', '{}', '{}', '{}', '{}'.",
                    FireModeModifier.ID, AttachmentStatesModifier.ID, ScopeShaderModifier.ID, RailsModifier.ID,
                    UnderbarrelDataModifier.ID, ConversionModifier.ID, FireAnimationModifier.ID, AimAnimationModifier.ID,
                    RecoilSpeedModifier.ID);
        } catch (Throwable t) {
            RenaissanceLibMod.LOGGER.error(
                    "[RenaissanceLib] Failed to register attachment modifiers; "
                            + "fire-mode attachments will be inactive.", t);
        }
    }

    public static List<FireMode> effectiveFireModes(ItemStack gunItem, GunData gunData) {
        List<FireMode> base = gunData.getFireModeSet();
        List<FireModeModifier.Spec> specs = collectFireModeSpecs(gunItem, gunData);
        if (specs.isEmpty()) return base;

        CacheValue<List<FireMode>> cache = new CacheValue<>(new ArrayList<>(base));
        new FireModeModifier().eval(specs, cache);
        return cache.getValue();
    }

    private static List<FireModeModifier.Spec> collectFireModeSpecs(ItemStack gunItem, GunData gunData) {
        List<FireModeModifier.Spec> specs = new ArrayList<>();
        AttachmentDataUtils.getAllAttachmentData(gunItem, gunData, data -> {
            JsonProperty<?> property = data.getModifier().get(FireModeModifier.ID);
            if (property != null && property.getValue() instanceof FireModeModifier.Spec spec) {
                specs.add(spec);
            }
        });
        return specs;
    }

    public static List<FireMode> effectiveFireModes(ItemStack gunItem) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return List.of();
        ResourceLocation gunId = iGun.getGunId(gunItem);
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> effectiveFireModes(gunItem, index.getGunData()))
                .orElse(List.of());
    }

    /**
     * True if the {@link SemiVariant} pseudo-mode is available on this gun. A variant is a token in the same
     * {@code set}/{@code add}/{@code remove} algebra as the real modes, evaluated in the same order:
     * the gun's own {@code fire_mode} array (recorded natively as {@code script_param.<token>_fire_mode})
     * is the base, then attachment {@code set}s override, {@code add}s enable, {@code remove}s disable.
     */
    public static boolean isVariantCapable(ItemStack gunItem, GunData gunData, SemiVariant variant) {
        return isVariantCapable(variant, gunData, collectFireModeSpecs(gunItem, gunData));
    }

    /** Every {@link SemiVariant} available on this gun, in declaration order. */
    public static List<SemiVariant> availableVariants(ItemStack gunItem, GunData gunData) {
        List<FireModeModifier.Spec> specs = collectFireModeSpecs(gunItem, gunData);
        List<SemiVariant> variants = new ArrayList<>();
        for (SemiVariant variant : SemiVariant.values()) {
            if (isVariantCapable(variant, gunData, specs)) variants.add(variant);
        }
        return variants;
    }

    private static boolean isVariantCapable(SemiVariant variant, GunData gunData, List<FireModeModifier.Spec> specs) {
        boolean capable = variant.authoredIn(gunData);
        for (FireModeModifier.Spec spec : specs) {
            if (spec.setPresent()) capable = spec.setHas(variant);
        }
        for (FireModeModifier.Spec spec : specs) {
            if (spec.addHas(variant)) capable = true;
        }
        for (FireModeModifier.Spec spec : specs) {
            if (spec.removeHas(variant)) capable = false;
        }
        return capable;
    }

    @SubscribeEvent
    public static void onAttachmentProperty(AttachmentPropertyEvent event) {
        try {
            ItemStack gunItem = event.getGunItem();
            IGun iGun = IGun.getIGunOrNull(gunItem);
            if (iGun == null) return;

            AttachmentStates.applyStateOverrides(gunItem, event.getCacheProperty());

            GunData gunData = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunItem))
                    .map(index -> index.getGunData()).orElse(null);
            if (gunData == null) return;

            List<FireMode> available = effectiveFireModes(gunItem, gunData);
            if (available.isEmpty()) return;

            SemiVariant variant = SemiVariant.active(gunItem);
            if (variant != null) {
                // A semi variant keeps SEMI as its underlying mode on purpose; don't clamp it away while the
                // gun still offers it. If it was just lost (e.g. the granting attachment was removed), drop
                // it and fall through to a normal clamp.
                if (isVariantCapable(gunItem, gunData, variant)) return;
                SemiVariant.setActive(gunItem, null);
            }

            FireMode current = iGun.getFireMode(gunItem);
            if (!available.contains(current)) {
                iGun.setFireMode(gunItem, available.get(0));
            }
        } catch (Throwable t) {
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Fire-mode clamp failed", t);
        }
    }
}
