package net.tkg.RenaissanceLib.attachment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Parses a conversion-kit attachment's {@code conversion} block: the id of the gun the kit
 * <em>converts the host weapon into</em> while installed.
 *
 * <p>A conversion kit is a caliber/platform swap — install it and the whole weapon resolves as a
 * different, fully-defined TaC:Z gun (its data, model, display, ammo, fire control and
 * {@code allow_attachments} all take over). We only need to carry the target gun id here; the actual
 * swap is done by redirecting the gun's identity (see {@link ConversionKit}), and the attachment
 * locking falls out of the <em>converted</em> gun's own {@code allow_attachments}.
 *
 * <pre>
 * "conversion": {
 *   "converted_gun": "mypack:ak12_556"
 * }
 * </pre>
 *
 * <p>The kit rides a RenaissanceLib virtual slot (mirroring the rail system), not a native TaC:Z
 * attachment slot, so it isn't gated to any {@link com.tacz.guns.api.item.attachment.AttachmentType};
 * an attachment is a conversion kit purely by carrying a parseable {@code conversion} block.
 * Registration mirrors the other RenaissanceLib modifiers (see {@link AttachmentOverrides#register()});
 * the parsed value is read back off a kit item through {@link ConversionKit}.
 */
public class ConversionModifier implements IAttachmentModifier<ConversionModifier.Spec, Boolean> {
    public static final String ID = "conversion";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<Spec> readJson(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return new ConversionJsonProperty(null);
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has(ID) || !obj.get(ID).isJsonObject()) return new ConversionJsonProperty(null);

            JsonObject conversionObj = obj.getAsJsonObject(ID);
            if (!conversionObj.has("converted_gun") || !conversionObj.get("converted_gun").isJsonPrimitive()) {
                return new ConversionJsonProperty(null);
            }
            ResourceLocation convertedGun = ResourceLocation.tryParse(conversionObj.get("converted_gun").getAsString());
            if (convertedGun == null) return new ConversionJsonProperty(null);

            return new ConversionJsonProperty(new Spec(convertedGun));
        } catch (Exception e) {
            net.tkg.RenaissanceLib.RenaissanceLibMod.LOGGER.error(
                    "[RenaissanceLib] Failed to parse attachment 'conversion' block", e);
            return new ConversionJsonProperty(null);
        }
    }

    @Override
    public CacheValue<Boolean> initCache(ItemStack gunItem, GunData gunData) {
        return new CacheValue<>(Boolean.FALSE);
    }

    @Override
    public void eval(java.util.List<Spec> modifiedValues, CacheValue<Boolean> cache) {
        // Carries data only; the identity redirect is done by ConversionKit, not the property pipeline.
    }

    public static class ConversionJsonProperty extends JsonProperty<Spec> {
        public ConversionJsonProperty(@Nullable Spec value) {
            super(value);
        }

        @Override
        public void initComponents() {
        }
    }

    /** The parsed {@code conversion} block: the gun id this kit converts the host weapon into. */
    public static final class Spec {
        private final ResourceLocation convertedGun;

        public Spec(ResourceLocation convertedGun) {
            this.convertedGun = convertedGun;
        }

        /** The id of the gun the host weapon becomes while this kit is installed. */
        public ResourceLocation getConvertedGun() {
            return convertedGun;
        }
    }
}
