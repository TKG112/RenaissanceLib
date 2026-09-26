package net.tkg.RenaissanceLib.attachment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Lets pack authors write the {@link SemiVariant} tokens ({@code "binary"}, {@code "manual"}) directly in a gun-data
 * {@code fire_mode} array, next to the real modes.
 *
 * <p>TaC:Z parses {@code fire_mode} straight into the fixed {@code FireMode} enum, which has no such values, so a raw
 * token can't load. This strips each variant token out of the array and records its position (1-based, so it stays
 * truthy) into {@code script_param.<token>_fire_mode} — the flag every consumer reads
 * ({@link AttachmentOverrides#availableVariants} for the host gun, {@link UnderbarrelFireMode} for an underbarrel).
 *
 * <p>Shared because the two gun-data parse paths differ: a normal gun goes through {@code JsonDataManager}
 * (see {@code JsonDataManagerMixin}); an underbarrel's embedded {@code underbarrel_data} is parsed directly by
 * {@link UnderbarrelDataModifier} and would otherwise miss this translation.
 */
public final class SemiVariantJson {
    private SemiVariantJson() {}

    /** Translate a gun-data JSON object in place: variant tokens in {@code fire_mode} → {@code script_param}. */
    public static void translate(JsonObject gunDataObj) {
        if (gunDataObj == null) return;
        JsonElement fireModeElement = gunDataObj.get("fire_mode");
        if (fireModeElement == null || !fireModeElement.isJsonArray()) return;

        JsonArray original = fireModeElement.getAsJsonArray();
        JsonArray cleaned = new JsonArray();
        JsonObject found = new JsonObject();
        for (int i = 0; i < original.size(); i++) {
            JsonElement entry = original.get(i);
            SemiVariant variant = entry.isJsonPrimitive() ? SemiVariant.byToken(entry.getAsString()) : null;
            if (variant == null) {
                cleaned.add(entry);
            } else if (!found.has(variant.scriptParam())) {
                found.addProperty(variant.scriptParam(), i + 1);
            }
        }
        if (found.size() == 0) return;

        gunDataObj.add("fire_mode", cleaned);
        JsonObject scriptParam;
        if (gunDataObj.get("script_param") != null && gunDataObj.get("script_param").isJsonObject()) {
            scriptParam = gunDataObj.getAsJsonObject("script_param");
        } else {
            scriptParam = new JsonObject();
            gunDataObj.add("script_param", scriptParam);
        }
        found.entrySet().forEach(e -> scriptParam.add(e.getKey(), e.getValue()));
    }
}
