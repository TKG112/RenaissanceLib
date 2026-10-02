package net.tkg.RenaissanceLib.attachment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * An attachment's {@code recoil_speed}: a multiplier on how fast the gun's camera recoil (the {@code recoil}
 * pitch/yaw keyframes in its data file) plays out. {@code 0.5} plays it at half speed, over twice as long;
 * {@code 2} plays it twice as fast. The curve's shape and strength are untouched (TaC:Z's {@code recoil} modifier
 * still scales strength). Several attachments multiply together.
 *
 * <pre>
 * "recoil_speed": 0.75
 * </pre>
 *
 * <p>Applied client-side by {@code CameraRecoilSpeedMixin}, which scales the time TaC:Z samples the recoil curve
 * at; the multiplier is taken when the shot fires ({@link net.tkg.RenaissanceLib.client.RecoilSpeed}).
 */
public class RecoilSpeedModifier implements IAttachmentModifier<Float, Float> {
    public static final String ID = "recoil_speed";
    /** Clamp so a typo can't freeze the recoil or make it instantaneous. */
    private static final float MIN = 0.05f, MAX = 20f;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<Float> readJson(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return new RecoilSpeedJsonProperty(null);
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has(ID) || !obj.get(ID).isJsonPrimitive() || !obj.get(ID).getAsJsonPrimitive().isNumber()) {
                return new RecoilSpeedJsonProperty(null);
            }
            float value = obj.get(ID).getAsFloat();
            if (!Float.isFinite(value) || value <= 0f) {
                net.tkg.RenaissanceLib.RenaissanceLibMod.LOGGER.warn(
                        "[RenaissanceLib] 'recoil_speed' must be a positive number (got {}); ignored", value);
                return new RecoilSpeedJsonProperty(null);
            }
            return new RecoilSpeedJsonProperty(value);
        } catch (Exception e) {
            net.tkg.RenaissanceLib.RenaissanceLibMod.LOGGER.error(
                    "[RenaissanceLib] Failed to parse attachment 'recoil_speed'", e);
            return new RecoilSpeedJsonProperty(null);
        }
    }

    @Override
    public CacheValue<Float> initCache(ItemStack gunItem, GunData gunData) {
        return new CacheValue<>(1f);
    }

    @Override
    public void eval(List<Float> modifiedValues, CacheValue<Float> cache) {
        float speed = 1f;
        for (Float value : modifiedValues) {
            if (value != null) speed *= value;
        }
        cache.setValue(Math.max(MIN, Math.min(MAX, speed)));
    }

    public static class RecoilSpeedJsonProperty extends JsonProperty<Float> {
        public RecoilSpeedJsonProperty(@Nullable Float value) {
            super(value);
        }

        @Override
        public void initComponents() {
            Float value = getValue();
            if (value == null || value == 1f) return;
            components.add(Component.translatable("tooltip.renaissance_lib.attachment.recoil_speed",
                            String.format(Locale.ROOT, "%.2f", value))
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
