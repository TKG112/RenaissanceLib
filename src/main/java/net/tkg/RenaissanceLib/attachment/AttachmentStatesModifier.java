package net.tkg.RenaissanceLib.attachment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AttachmentStatesModifier implements IAttachmentModifier<AttachmentStatesModifier.States, Boolean> {
    public static final String ID = "states";

    public static final String ANIMATION_KEY = "animation";

    public static final String ZOOM_INDEX_KEY = "zoom_index";

    public static final String ANIMATION_FILE_KEY = "animation_file";

    public static final String ZOOM_KEY_TOGGLE_KEY = "zoom_key_toggle";

    public static final String REQUIRE_KEY = "require";

    public static final String COOLDOWN_KEY = "cooldown";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<States> readJson(String json) {

        try {
            JsonElement rootElement = com.google.gson.JsonParser.parseString(json);
            if (!rootElement.isJsonObject()) return new StatesJsonProperty(null);
            JsonObject root = rootElement.getAsJsonObject();
            if (!root.has(ID) || !root.get(ID).isJsonObject()) return new StatesJsonProperty(null);

            JsonObject statesObj = root.getAsJsonObject(ID);
            States states = new States();
            states.setRaw(statesObj);

            if (statesObj.has(ANIMATION_FILE_KEY) && statesObj.get(ANIMATION_FILE_KEY).isJsonPrimitive()) {
                states.setAnimationFile(statesObj.get(ANIMATION_FILE_KEY).getAsString());
            }

            if (statesObj.has(ZOOM_KEY_TOGGLE_KEY) && statesObj.get(ZOOM_KEY_TOGGLE_KEY).isJsonPrimitive()) {
                states.setZoomKeyToggle(statesObj.get(ZOOM_KEY_TOGGLE_KEY).getAsBoolean());
            }

            if (statesObj.has(COOLDOWN_KEY) && statesObj.get(COOLDOWN_KEY).isJsonPrimitive()) {
                try {
                    states.setCooldownSeconds(statesObj.get(COOLDOWN_KEY).getAsFloat());
                } catch (Exception ignored) {
                }
            }

            if (statesObj.has("cycle") && statesObj.get("cycle").isJsonArray()) {
                List<String> cycle = new ArrayList<>();
                statesObj.getAsJsonArray("cycle").forEach(e -> {
                    if (e.isJsonPrimitive()) cycle.add(e.getAsString());
                });
                states.setCycle(cycle);
            }
            states.bake();
            return new StatesJsonProperty(states);
        } catch (Exception e) {
            net.tkg.RenaissanceLib.RenaissanceLibMod.LOGGER.error(
                    "[RenaissanceLib] Failed to parse attachment 'states' block", e);
            return new StatesJsonProperty(null);
        }
    }

    @Override
    public CacheValue<Boolean> initCache(ItemStack gunItem, GunData gunData) {

        return new CacheValue<>(Boolean.FALSE);
    }

    @Override
    public void eval(List<States> modifiedValues, CacheValue<Boolean> cache) {

    }

    public static class StatesJsonProperty extends JsonProperty<States> {
        public StatesJsonProperty(@Nullable States value) {
            super(value);
        }

        @Override
        public void initComponents() {
            States value = getValue();
            if (value != null && value.getCycle().size() > 1) {
                components.add(Component.translatable("tooltip.renaissance_lib.attachment.toggleable")
                        .withStyle(ChatFormatting.AQUA));
            }
        }
    }

    public static class States {
        @Nullable
        private List<String> cycle = null;

        private final Map<String, JsonObject> bodies = new LinkedHashMap<>();
        private List<String> bakedCycle = new ArrayList<>();
        private JsonObject raw;
        @Nullable
        private String animationFile = null;
        private boolean zoomKeyToggle = true;
        @Nullable
        private Float cooldownSeconds = null;

        void setRaw(JsonObject raw) {
            this.raw = raw;
        }

        void setAnimationFile(@Nullable String animationFile) {
            this.animationFile = animationFile;
        }

        @Nullable
        public String getAnimationFile() {
            return animationFile;
        }

        void setZoomKeyToggle(boolean zoomKeyToggle) {
            this.zoomKeyToggle = zoomKeyToggle;
        }

        public boolean isZoomKeyToggle() {
            return zoomKeyToggle;
        }

        void setCooldownSeconds(float cooldownSeconds) {
            this.cooldownSeconds = cooldownSeconds;
        }

        /**
         * Cooldown, in ticks, before the attachment may be toggled again after entering {@code
         * stateName}. Reads that state's {@code cooldown} (seconds), else the top-level {@code
         * cooldown}, else 0 (no cooldown). Lets an author require the toggle animation to finish.
         */
        public int getCooldownTicks(String stateName) {
            Float seconds = null;
            JsonObject body = bodies.get(stateName);
            if (body != null) {
                JsonElement cd = body.get(COOLDOWN_KEY);
                if (cd != null && cd.isJsonPrimitive()) {
                    try {
                        seconds = cd.getAsFloat();
                    } catch (Exception ignored) {
                    }
                }
            }
            if (seconds == null) seconds = cooldownSeconds;
            if (seconds == null || seconds <= 0f) return 0;
            return Math.round(seconds * 20f);
        }

        void setCycle(@Nullable List<String> cycle) {
            this.cycle = cycle;
        }

        void bake() {
            bodies.clear();
            bakedCycle = new ArrayList<>();
            if (raw != null) {
                for (Map.Entry<String, JsonElement> entry : raw.entrySet()) {
                    if ("cycle".equals(entry.getKey())) continue;
                    if (entry.getValue().isJsonObject()) {
                        bodies.put(entry.getKey(), entry.getValue().getAsJsonObject());
                    }
                }
            }
            if (cycle != null && !cycle.isEmpty()) {
                for (String name : cycle) {
                    if (bodies.containsKey(name)) bakedCycle.add(name);
                }
            }

            if (bakedCycle.isEmpty()) bakedCycle.addAll(bodies.keySet());
        }

        public List<String> getCycle() {
            return bakedCycle;
        }

        public String getDefaultState() {
            return bakedCycle.isEmpty() ? "" : bakedCycle.get(0);
        }

        @Nullable
        public JsonObject getStateBody(String name) {
            return bodies.get(name);
        }

        @Nullable
        public Integer getZoomIndex(String stateName) {
            JsonObject body = bodies.get(stateName);
            if (body == null) return null;
            JsonElement zoom = body.get(ZOOM_INDEX_KEY);
            if (zoom == null || !zoom.isJsonPrimitive()) return null;
            try {
                return zoom.getAsInt();
            } catch (Exception e) {
                return null;
            }
        }

        @Nullable
        public String getAnimation(String stateName) {
            JsonObject body = bodies.get(stateName);
            if (body == null) return null;
            JsonElement anim = body.get(ANIMATION_KEY);
            return anim != null && anim.isJsonPrimitive() ? anim.getAsString() : null;
        }

        public List<String> getRequire(String stateName) {
            JsonObject body = bodies.get(stateName);
            if (body == null) return java.util.Collections.emptyList();
            JsonElement req = body.get(REQUIRE_KEY);
            if (req == null) return java.util.Collections.emptyList();
            List<String> result = new ArrayList<>();
            if (req.isJsonPrimitive()) {
                result.add(req.getAsString());
            } else if (req.isJsonArray()) {
                req.getAsJsonArray().forEach(e -> {
                    if (e.isJsonPrimitive()) result.add(e.getAsString());
                });
            }
            return result;
        }

        public String next(String current) {
            if (bakedCycle.isEmpty()) return current;
            int index = bakedCycle.indexOf(current);
            return bakedCycle.get((index + 1) % bakedCycle.size());
        }

        @Nullable
        public String stateForZoomIndex(int zoomIndex) {
            for (String name : bakedCycle) {
                Integer zi = getZoomIndex(name);
                if (zi != null && zi == zoomIndex) return name;
            }
            return null;
        }
    }
}
