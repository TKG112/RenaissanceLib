package net.tkg.RenaissanceLib.attachment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Parses an attachment's {@code aim_animation} block: an animation the attachment plays on its <em>own</em> model
 * when the player aims down sights — a flip-up sight rising, a magnifier swinging in, a lens cover opening. Two
 * styles, picked per attachment:
 *
 * <pre>
 * "aim_animation": {                       "aim_animation": {
 *   "animation_file": "mypack:my_sight",     "animation_file": "mypack:my_sight",
 *   "follow": "flip_up"                      "aim_in": "flip_up",
 * }                                          "aim_out": "flip_down"     (optional)
 *                                          }
 * </pre>
 *
 * <ul>
 *   <li>{@code follow} — the clip is tied to how far the gun has aimed in: half-aimed plays it halfway, and letting
 *       go of ADS plays it back from wherever it got to. Always in step with the gun's ADS speed.</li>
 *   <li>{@code aim_in} / {@code aim_out} — clips that play when ADS starts / ends, at their own speed. Without
 *       {@code aim_out}, {@code aim_in} plays in reverse.</li>
 * </ul>
 * {@code follow} wins if both are given. It plays through the same per-attachment controller as toggle states and
 * {@code fire_animation} (same {@code animation_file} if combined), on its own track, so it layers with them when
 * they animate different bones.
 *
 * <p>Registration mirrors the other RenaissanceLib modifiers (see {@link AttachmentOverrides#register()}); the
 * parsed value is read back off an attachment through {@link AimAnimation}.
 */
public class AimAnimationModifier implements IAttachmentModifier<AimAnimationModifier.Spec, Boolean> {
    public static final String ID = "aim_animation";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<Spec> readJson(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return new AimAnimationJsonProperty(null);
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has(ID) || !obj.get(ID).isJsonObject()) return new AimAnimationJsonProperty(null);

            JsonObject block = obj.getAsJsonObject(ID);
            String file = string(block, "animation_file");
            String follow = string(block, "follow");
            String aimIn = string(block, "aim_in");
            String aimOut = string(block, "aim_out");
            if (file == null || (follow == null && aimIn == null)) {
                net.tkg.RenaissanceLib.RenaissanceLibMod.LOGGER.warn(
                        "[RenaissanceLib] 'aim_animation' needs an animation_file and either 'follow' or 'aim_in'");
                return new AimAnimationJsonProperty(null);
            }
            return new AimAnimationJsonProperty(new Spec(file, follow, aimIn, aimOut));
        } catch (Exception e) {
            net.tkg.RenaissanceLib.RenaissanceLibMod.LOGGER.error(
                    "[RenaissanceLib] Failed to parse attachment 'aim_animation' block", e);
            return new AimAnimationJsonProperty(null);
        }
    }

    @Nullable
    private static String string(JsonObject block, String key) {
        if (!block.has(key) || !block.get(key).isJsonPrimitive()) return null;
        String value = block.get(key).getAsString();
        return value.isEmpty() ? null : value;
    }

    @Override
    public CacheValue<Boolean> initCache(ItemStack gunItem, GunData gunData) {
        return new CacheValue<>(Boolean.FALSE);
    }

    @Override
    public void eval(java.util.List<Spec> modifiedValues, CacheValue<Boolean> cache) {
        // Carries data only; the clip is driven by the aim state on the client, not the property pipeline.
    }

    public static class AimAnimationJsonProperty extends JsonProperty<Spec> {
        public AimAnimationJsonProperty(@Nullable Spec value) {
            super(value);
        }

        @Override
        public void initComponents() {
        }
    }

    /** The parsed {@code aim_animation} block. */
    public static final class Spec {
        private final String animationFile;
        @Nullable
        private final String follow;
        @Nullable
        private final String aimIn;
        @Nullable
        private final String aimOut;

        public Spec(String animationFile, @Nullable String follow, @Nullable String aimIn, @Nullable String aimOut) {
            this.animationFile = animationFile;
            this.follow = follow;
            this.aimIn = aimIn;
            this.aimOut = aimOut;
        }

        /** The attachment's bedrock animation file id (e.g. {@code mypack:my_sight}). */
        public String getAnimationFile() {
            return animationFile;
        }

        /** The clip scrubbed by aim progress, or {@code null} for the aim-in/out style. */
        @Nullable
        public String getFollow() {
            return follow;
        }

        /** The clip played when ADS starts (aim-in/out style). */
        @Nullable
        public String getAimIn() {
            return aimIn;
        }

        /** The clip played when ADS ends; {@code null} plays {@link #getAimIn()} in reverse. */
        @Nullable
        public String getAimOut() {
            return aimOut;
        }
    }
}
