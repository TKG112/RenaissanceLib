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
 * Parses an attachment's {@code fire_animation} block: an animation clip the attachment plays on its
 * <em>own</em> model each time the host gun fires — a reciprocating charging handle, an ejection-port
 * cover, suppressor baffles, a comp venting, and so on.
 *
 * <p>A mounted attachment already recoils rigidly with the gun (its mount node moves); this animates the
 * attachment's <em>own bones</em>, which the gun's Lua animation can't reach (a mounted attachment is a
 * separate model). The clip is authored exactly like a toggleable attachment's animation, and it plays
 * through the same {@link net.tkg.RenaissanceLib.client.AttachmentAnimationManager} controller, driven by
 * TaC:Z's per-shot {@code GunFireEvent} rather than a key press.
 *
 * <pre>
 * "fire_animation": {
 *   "animation_file": "mypack:my_attachment",
 *   "animation": "shoot"
 * }
 * </pre>
 *
 * <p>Registration mirrors the other RenaissanceLib modifiers (see {@link AttachmentOverrides#register()});
 * the parsed value is read back off an installed attachment through {@link FireAnimation}.
 */
public class FireAnimationModifier implements IAttachmentModifier<FireAnimationModifier.Spec, Boolean> {
    public static final String ID = "fire_animation";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<Spec> readJson(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return new FireAnimationJsonProperty(null);
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has(ID) || !obj.get(ID).isJsonObject()) return new FireAnimationJsonProperty(null);

            JsonObject block = obj.getAsJsonObject(ID);
            String file = block.has("animation_file") && block.get("animation_file").isJsonPrimitive()
                    ? block.get("animation_file").getAsString() : null;
            String clip = block.has("animation") && block.get("animation").isJsonPrimitive()
                    ? block.get("animation").getAsString() : null;
            if (file == null || file.isEmpty() || clip == null || clip.isEmpty()) {
                return new FireAnimationJsonProperty(null);
            }
            return new FireAnimationJsonProperty(new Spec(file, clip));
        } catch (Exception e) {
            net.tkg.RenaissanceLib.RenaissanceLibMod.LOGGER.error(
                    "[RenaissanceLib] Failed to parse attachment 'fire_animation' block", e);
            return new FireAnimationJsonProperty(null);
        }
    }

    @Override
    public CacheValue<Boolean> initCache(ItemStack gunItem, GunData gunData) {
        return new CacheValue<>(Boolean.FALSE);
    }

    @Override
    public void eval(java.util.List<Spec> modifiedValues, CacheValue<Boolean> cache) {
        // Carries data only; the clip is triggered by the fire event on the client, not the property pipeline.
    }

    public static class FireAnimationJsonProperty extends JsonProperty<Spec> {
        public FireAnimationJsonProperty(@Nullable Spec value) {
            super(value);
        }

        @Override
        public void initComponents() {
        }
    }

    /** The parsed {@code fire_animation} block: the attachment's animation file and the clip to play per shot. */
    public static final class Spec {
        private final String animationFile;
        private final String clip;

        public Spec(String animationFile, String clip) {
            this.animationFile = animationFile;
            this.clip = clip;
        }

        /** The attachment's bedrock animation file id (e.g. {@code mypack:my_attachment}). */
        public String getAnimationFile() {
            return animationFile;
        }

        /** The clip within that file to play each time the host gun fires (e.g. {@code shoot}). */
        public String getClip() {
            return clip;
        }
    }
}
