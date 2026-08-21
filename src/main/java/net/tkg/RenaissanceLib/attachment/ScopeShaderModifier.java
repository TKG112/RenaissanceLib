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
import net.tkg.RenaissanceLib.RenaissanceLibMod;

import javax.annotation.Nullable;
import java.util.List;

public class ScopeShaderModifier implements IAttachmentModifier<ShaderSpec, Boolean> {
    public static final String ID = "shader";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<ShaderSpec> readJson(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return new ShaderJsonProperty(null);
            JsonObject obj = root.getAsJsonObject();
            return new ShaderJsonProperty(ShaderSpec.parse(obj.get(ID)));
        } catch (Exception e) {
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Failed to parse attachment 'shader' key", e);
            return new ShaderJsonProperty(null);
        }
    }

    @Override
    public CacheValue<Boolean> initCache(ItemStack gunItem, GunData gunData) {
        return new CacheValue<>(Boolean.FALSE);
    }

    @Override
    public void eval(List<ShaderSpec> modifiedValues, CacheValue<Boolean> cache) {
    }

    public static class ShaderJsonProperty extends JsonProperty<ShaderSpec> {
        public ShaderJsonProperty(@Nullable ShaderSpec value) {
            super(value);
        }

        @Override
        public void initComponents() {
        }
    }
}
