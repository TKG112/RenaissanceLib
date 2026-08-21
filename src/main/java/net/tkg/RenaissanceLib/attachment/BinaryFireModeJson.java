package net.tkg.RenaissanceLib.attachment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Lets pack authors write {@code "binary"} directly in a gun-data {@code fire_mode} array — the same way
 * a normal gun declares binary.
 *
 * <p>TaC:Z parses {@code fire_mode} straight into the fixed {@code FireMode} enum, which has no binary value,
 * so a raw {@code "binary"} entry can't load. This strips {@code "binary"} out of the array and records its
 * position (1-based, so it stays truthy) into {@code script_param.binary_fire_mode} — the flag every binary
 * consumer reads ({@link AttachmentOverrides#isBinaryCapable} for the host gun,
 * {@link UnderbarrelFireMode#binaryAvailable} for an underbarrel).
 *
 * <p>Shared because the two gun-data parse paths differ: a normal gun goes through {@code JsonDataManager}
 * (see {@code JsonDataManagerMixin}); an underbarrel's embedded {@code underbarrel_data} is parsed directly by
 * {@link UnderbarrelDataModifier} and would otherwise miss this translation.
 */
public final class BinaryFireModeJson {
    private BinaryFireModeJson() {}

    /** Translate a gun-data JSON object in place: {@code "binary"} in {@code fire_mode} → {@code script_param.binary_fire_mode}. */
    public static void translate(JsonObject gunDataObj) {
        if (gunDataObj == null) return;
        JsonElement fireModeElement = gunDataObj.get("fire_mode");
        if (fireModeElement == null || !fireModeElement.isJsonArray()) return;

        JsonArray original = fireModeElement.getAsJsonArray();
        JsonArray cleaned = new JsonArray();
        int binaryIndex = -1;
        for (int i = 0; i < original.size(); i++) {
            JsonElement entry = original.get(i);
            if (entry.isJsonPrimitive() && "binary".equalsIgnoreCase(entry.getAsString())) {
                if (binaryIndex < 0) binaryIndex = i;
            } else {
                cleaned.add(entry);
            }
        }
        if (binaryIndex < 0) return;

        gunDataObj.add("fire_mode", cleaned);
        JsonObject scriptParam;
        if (gunDataObj.get("script_param") != null && gunDataObj.get("script_param").isJsonObject()) {
            scriptParam = gunDataObj.getAsJsonObject("script_param");
        } else {
            scriptParam = new JsonObject();
            gunDataObj.add("script_param", scriptParam);
        }
        scriptParam.addProperty("binary_fire_mode", binaryIndex + 1);
    }
}
