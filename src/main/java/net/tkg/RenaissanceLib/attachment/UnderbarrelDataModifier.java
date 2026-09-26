package net.tkg.RenaissanceLib.attachment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Parses an underbarrel attachment's {@code underbarrel_data} block: the complete sub-gun definition
 * (ammo, bullet + explosion, reload, fire modes, recoil, inaccuracy, its own allowed attachments, …)
 * for a grenade launcher / shotgun mounted under the host gun's barrel.
 *
 * <p>The block is authored exactly like a TaC:Z gun-data file, so we parse it straight into a {@link
 * GunData} POJO with TaC:Z's own {@link CommonAssetsManager#GSON} (which carries the custom adapters for
 * fire modes, recoil curves, bolt type, etc.). The surrounding attachment fields ({@code weight}, the
 * passive {@code recoil} modifier applied to the <em>host</em> gun) are handled natively by TaC:Z; this
 * modifier only lifts out the embedded sub-gun so the underbarrel fire-control layer can drive it.
 *
 * <pre>
 * "underbarrel_data": {
 *   "ammo": "tacz:40mm",
 *   "rpm": 150,
 *   "ammo_amount": 1,
 *   "bullet": { … "explosion": { … } },
 *   "reload": { … },
 *   "fire_mode": ["semi"],
 *   "allow_attachment_types": ["muzzle", "extended_mag"],
 *   "allow_attachments": ["#tacz:muzzle", "#tacz:ammo_mod"]
 * }
 * </pre>
 *
 * <p>Registration mirrors the other RenaissanceLib modifiers (see {@link
 * net.tkg.RenaissanceLib.attachment.AttachmentOverrides#register()}); the parsed value is read back
 * off an installed attachment through {@link Underbarrel}.
 */
public class UnderbarrelDataModifier implements IAttachmentModifier<GunData, Boolean> {
    public static final String ID = "underbarrel_data";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<GunData> readJson(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return new UnderbarrelJsonProperty(null);
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has(ID) || !obj.get(ID).isJsonObject()) return new UnderbarrelJsonProperty(null);

            JsonObject underbarrelData = obj.getAsJsonObject(ID);
            // Same "binary"/"manual" fire_mode translation a normal gun gets (JsonDataManagerMixin) — the underbarrel
            // parses on this separate path, so apply it here so authors can write them in its fire_mode too.
            SemiVariantJson.translate(underbarrelData);

            GunData gunData = CommonAssetsManager.GSON.fromJson(underbarrelData, GunData.class);
            return new UnderbarrelJsonProperty(gunData);
        } catch (Exception e) {
            net.tkg.RenaissanceLib.RenaissanceLibMod.LOGGER.error(
                    "[RenaissanceLib] Failed to parse attachment 'underbarrel_data' block", e);
            return new UnderbarrelJsonProperty(null);
        }
    }

    @Override
    public CacheValue<Boolean> initCache(ItemStack gunItem, GunData gunData) {
        return new CacheValue<>(Boolean.FALSE);
    }

    @Override
    public void eval(java.util.List<GunData> modifiedValues, CacheValue<Boolean> cache) {
        // Carries data only; the underbarrel doesn't modify the host gun's live properties here.
    }

    public static class UnderbarrelJsonProperty extends JsonProperty<GunData> {
        public UnderbarrelJsonProperty(@Nullable GunData value) {
            super(value);
        }

        @Override
        public void initComponents() {
        }
    }
}
