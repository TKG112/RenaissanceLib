package net.tkg.RenaissanceLib.attachment;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FireModeModifier implements IAttachmentModifier<FireModeModifier.Spec, List<FireMode>> {
    public static final String ID = "fire_mode";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<Spec> readJson(String json) {

        Data data = CommonAssetsManager.GSON.fromJson(json, Data.class);
        return new FireModeJsonProperty(data == null ? null : data.getFireMode());
    }

    @Override
    public CacheValue<List<FireMode>> initCache(ItemStack gunItem, GunData gunData) {

        return new CacheValue<>(new ArrayList<>(gunData.getFireModeSet()));
    }

    @Override
    public void eval(List<Spec> modifiers, CacheValue<List<FireMode>> cache) {
        List<FireMode> result = new ArrayList<>(cache.getValue());

        for (Spec spec : modifiers) {
            if (spec != null && spec.setPresent()) {
                result = new ArrayList<>(spec.getSetModes());
            }
        }
        for (Spec spec : modifiers) {
            if (spec == null) continue;
            for (FireMode mode : spec.getAddModes()) {
                if (mode != FireMode.UNKNOWN && !result.contains(mode)) {
                    result.add(mode);
                }
            }
        }
        for (Spec spec : modifiers) {
            if (spec != null) {
                result.removeAll(spec.getRemoveModes());
            }
        }

        result.remove(FireMode.UNKNOWN);

        if (result.isEmpty()) {
            result = new ArrayList<>(cache.getValue());
        }
        cache.setValue(result);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public List<DiagramsData> getPropertyDiagramsData(ItemStack gunItem, GunData gunData,
                                                      AttachmentCacheProperty cacheProperty) {
        List<FireMode> base     = gunData.getFireModeSet();
        List<FireMode> modified = cacheProperty.getCache(ID);
        if (modified == null) modified = base;

        int delta = modified.size() - base.size();

        double basePercent  = Math.min(base.size() / 3.0, 1.0);
        double deltaPercent = Math.min(delta / 3.0, 1.0);

        String names = describe(modified);
        String titleKey = "gui.renaissance_lib.gun_refit.property_diagrams.fire_mode";

        return Collections.singletonList(new DiagramsData(
                basePercent, deltaPercent, delta, titleKey,
                String.format("%s §a(+%d)", names, delta),
                String.format("%s §c(%d)", names, delta),
                names,
                true));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public int getDiagramsDataSize() {
        return 1;
    }

    @OnlyIn(Dist.CLIENT)
    private static String describe(List<FireMode> modes) {
        StringBuilder sb = new StringBuilder();
        for (FireMode mode : modes) {
            if (sb.length() > 0) sb.append('/');
            sb.append(Component.translatable("gui.renaissance_lib.fire_mode."
                    + mode.name().toLowerCase(java.util.Locale.ENGLISH)).getString());
        }
        return sb.toString();
    }

    public static class FireModeJsonProperty extends JsonProperty<Spec> {
        public FireModeJsonProperty(@Nullable Spec value) {
            super(value);
        }

        @Override
        public void initComponents() {
            Spec value = getValue();
            if (value == null) return;
            if (value.getSet() != null && !value.getSet().isEmpty()) {
                components.add(Component.translatable("tooltip.renaissance_lib.attachment.fire_mode.set")
                        .withStyle(ChatFormatting.AQUA));
            }
            if (value.getAdd() != null && !value.getAdd().isEmpty()) {
                components.add(Component.translatable("tooltip.renaissance_lib.attachment.fire_mode.add")
                        .withStyle(ChatFormatting.GREEN));
            }
            if (value.getRemove() != null && !value.getRemove().isEmpty()) {
                components.add(Component.translatable("tooltip.renaissance_lib.attachment.fire_mode.remove")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    public static class Data {
        @SerializedName("fire_mode")
        @Nullable
        private Spec fireMode = null;

        @Nullable
        public Spec getFireMode() {
            return fireMode;
        }
    }

    public static class Spec {
        // Parsed as raw strings, not List<FireMode>, so "binary"/"manual" can sit in the list next to the
        // real modes: Gson would turn an unknown enum token into null, corrupting the list. We split
        // the tokens ourselves into real FireModes (getXModes) and the semi variants (xHas).
        @SerializedName("set")
        @Nullable
        private List<String> set = null;

        @SerializedName("add")
        @Nullable
        private List<String> add = null;

        @SerializedName("remove")
        @Nullable
        private List<String> remove = null;

        @Nullable
        public List<String> getSet() {
            return set;
        }

        @Nullable
        public List<String> getAdd() {
            return add;
        }

        @Nullable
        public List<String> getRemove() {
            return remove;
        }

        public boolean setPresent() {
            return set != null && !set.isEmpty();
        }

        public List<FireMode> getSetModes() {
            return toModes(set);
        }

        public List<FireMode> getAddModes() {
            return toModes(add);
        }

        public List<FireMode> getRemoveModes() {
            return toModes(remove);
        }

        public boolean setHas(SemiVariant variant) {
            return has(set, variant);
        }

        public boolean addHas(SemiVariant variant) {
            return has(add, variant);
        }

        public boolean removeHas(SemiVariant variant) {
            return has(remove, variant);
        }

        private static List<FireMode> toModes(@Nullable List<String> tokens) {
            if (tokens == null) return Collections.emptyList();
            List<FireMode> modes = new ArrayList<>(tokens.size());
            for (String token : tokens) {
                FireMode mode = parseMode(token);
                if (mode != null) modes.add(mode);
            }
            return modes;
        }

        private static boolean has(@Nullable List<String> tokens, SemiVariant variant) {
            if (tokens == null) return false;
            for (String token : tokens) {
                if (variant.matches(token)) return true;
            }
            return false;
        }

        @Nullable
        private static FireMode parseMode(@Nullable String token) {
            if (token == null) return null;
            switch (token.trim().toLowerCase(java.util.Locale.ENGLISH)) {
                case "auto":  return FireMode.AUTO;
                case "semi":  return FireMode.SEMI;
                case "burst": return FireMode.BURST;
                default:      return null; // a semi variant (handled separately) or anything unrecognized
            }
        }
    }
}
