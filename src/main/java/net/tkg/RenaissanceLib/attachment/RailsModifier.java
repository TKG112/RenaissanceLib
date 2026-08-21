package net.tkg.RenaissanceLib.attachment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses a scope attachment's {@code rails} block: the extra sight mounts (canted irons, a
 * piggyback red-dot, etc.) the player can populate through slots that appear under the scope slot.
 *
 * <p>Each rail slot names a mount {@code node} in the scope model, a {@code type} (informational, drives
 * the slot label, e.g. {@code canted}) and an optional {@code allow} category restricting what may be
 * mounted: {@code "scope"} (scope-type optics only), {@code "sight"} (pure sight-type optics only) or
 * {@code "any"} (default). This modifier only carries the config; the slots, storage, rendering and
 * aim/zoom routing are layered on top by the rail system.
 *
 * <pre>
 * "rails": {
 *   "aim_self": false,
 *   "slots": [
 *     { "node": "main",     "type": "main",   "allow": "scope" },
 *     { "node": "canted_1", "type": "canted", "allow": "sight" }
 *   ]
 * }
 * </pre>
 *
 * <p>{@code aim_self} (default {@code false}): whether the rail-bearing attachment's own optic view
 * takes part in the zoom cycle. A pure mount (a bare canted rail) leaves this {@code false} so cycling
 * walks only the mounted sights; a real scope that also carries a rail sets it {@code true} so its own
 * view cycles alongside the mounted sights.
 */
public class RailsModifier implements IAttachmentModifier<RailsModifier.Spec, Boolean> {
    public static final String ID = "rails";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonProperty<Spec> readJson(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return new RailsJsonProperty(null);
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has(ID) || !obj.get(ID).isJsonObject()) return new RailsJsonProperty(null);

            JsonObject railsObj = obj.getAsJsonObject(ID);
            if (!railsObj.has("slots") || !railsObj.get("slots").isJsonArray()) {
                return new RailsJsonProperty(null);
            }

            boolean aimSelf = railsObj.has("aim_self") && railsObj.get("aim_self").isJsonPrimitive()
                    && railsObj.get("aim_self").getAsBoolean();

            JsonArray slotsArray = railsObj.getAsJsonArray("slots");
            List<RailSlot> slots = new ArrayList<>();
            for (JsonElement element : slotsArray) {
                if (!element.isJsonObject()) continue;
                JsonObject slotObj = element.getAsJsonObject();
                if (!slotObj.has("node") || !slotObj.get("node").isJsonPrimitive()) continue;
                String node = slotObj.get("node").getAsString();
                String type = slotObj.has("type") && slotObj.get("type").isJsonPrimitive()
                        ? slotObj.get("type").getAsString()
                        : "canted";
                slots.add(new RailSlot(node, type, parseAllow(slotObj.get("allow"))));
            }
            return new RailsJsonProperty(slots.isEmpty() ? null : new Spec(slots, aimSelf));
        } catch (Exception e) {
            net.tkg.RenaissanceLib.RenaissanceLibMod.LOGGER.error(
                    "[RenaissanceLib] Failed to parse attachment 'rails' block", e);
            return new RailsJsonProperty(null);
        }
    }

    /**
     * Parses a slot's {@code allow}: a single category string, or an array of category strings, or absent
     * (defaults to {@link RailSlot#ALLOW_ANY}). Categories are lower-cased.
     */
    private static List<String> parseAllow(@Nullable JsonElement allowElement) {
        List<String> allow = new ArrayList<>();
        if (allowElement != null && allowElement.isJsonArray()) {
            for (JsonElement e : allowElement.getAsJsonArray()) {
                if (e.isJsonPrimitive()) allow.add(e.getAsString().toLowerCase());
            }
        } else if (allowElement != null && allowElement.isJsonPrimitive()) {
            allow.add(allowElement.getAsString().toLowerCase());
        }
        if (allow.isEmpty()) allow.add(RailSlot.ALLOW_ANY);
        return allow;
    }

    @Override
    public CacheValue<Boolean> initCache(ItemStack gunItem, GunData gunData) {
        return new CacheValue<>(Boolean.FALSE);
    }

    @Override
    public void eval(List<Spec> modifiedValues, CacheValue<Boolean> cache) {
    }

    public static class RailsJsonProperty extends JsonProperty<Spec> {
        public RailsJsonProperty(@Nullable Spec value) {
            super(value);
        }

        @Override
        public void initComponents() {
        }
    }

    public static class Spec {
        private final List<RailSlot> slots;
        private final boolean aimSelf;

        public Spec(List<RailSlot> slots, boolean aimSelf) {
            this.slots = slots;
            this.aimSelf = aimSelf;
        }

        public List<RailSlot> getSlots() {
            return Collections.unmodifiableList(slots);
        }

        /** Whether the rail-bearing attachment's own optic view is part of the zoom cycle. */
        public boolean isAimSelf() {
            return aimSelf;
        }
    }

    /**
     * One rail mount point: a node name in the host model, an informational {@code type} (drives the slot
     * label), and an {@code allow} list of categories restricting what may be mounted. A slot accepts an
     * item if it matches <em>any</em> listed category.
     */
    public static final class RailSlot {
        /** Accepts anything (default). */
        public static final String ALLOW_ANY = "any";
        /** Accepts scope-type optics (TaC:Z {@code isScope}, including scope/sight combinations). */
        public static final String ALLOW_SCOPE = "scope";
        /** Accepts pure sight-type optics (TaC:Z {@code isSight} and not {@code isScope}). */
        public static final String ALLOW_SIGHT = "sight";
        /** Accepts laser-type attachments. */
        public static final String ALLOW_LASER = "laser";

        private final String node;
        private final String type;
        private final List<String> allow;

        public RailSlot(String node, String type, List<String> allow) {
            this.node = node;
            this.type = type;
            this.allow = (allow == null || allow.isEmpty()) ? List.of(ALLOW_ANY) : List.copyOf(allow);
        }

        public String getNode() {
            return node;
        }

        public String getType() {
            return type;
        }

        /** The categories this slot accepts (a mount matches if it matches any of them). */
        public List<String> getAllow() {
            return allow;
        }
    }
}
