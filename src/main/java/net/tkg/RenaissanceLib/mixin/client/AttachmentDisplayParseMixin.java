package net.tkg.RenaissanceLib.mixin.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.client.resource.manager.DisplayManager;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.client.underbarrel.IUnderbarrelDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lifts the underbarrel extras out of an attachment <em>display</em> file: the sub-gun
 * {@code underbarrel_display} ({@link GunDisplay}), {@code hide_tactical_handguard}, and {@code mount_offset}.
 * TaC:Z's {@code AttachmentDisplay} POJO silently drops these unknown fields.
 *
 * <p>Crucially, TaC:Z's {@code DisplayManager.apply} parses displays with a <em>direct</em>
 * {@code gson.fromJson} call (not the shared {@code parseJson}), so we redirect that call: parse as normal,
 * then re-read the extras from the same raw JSON and stash them on the display via {@link IUnderbarrelDisplay}
 * (woven on by {@link AttachmentDisplayMixin}). Only {@link AttachmentDisplay} parses are touched.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = DisplayManager.class, remap = false)
public abstract class AttachmentDisplayParseMixin {

    @Redirect(
            method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;"
                    + "Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/google/gson/Gson;fromJson(Lcom/google/gson/JsonElement;Ljava/lang/Class;)"
                            + "Ljava/lang/Object;"),
            remap = false)
    private Object renaissance$parseDisplay(Gson gson, JsonElement element, Class<?> clazz) {
        // Our custom `mount_offset` is an object ({position,rotation}); the TaC:Z beta added a NATIVE
        // `mount_offset` field typed as a Vector3f (expects an array [x,y,z]). Handing our object form to
        // TaC:Z's Gson makes its Vector3f adapter throw and abort the whole display parse (→ attachment falls
        // back to its 2D item model). So for an AttachmentDisplay, parse a copy with our object-form
        // `mount_offset` removed; we still read our own copy from the original element in applyExtras below.
        // (No-op on the stable release, which has no such field and just ignored the extra key.)
        JsonElement toParse = element;
        if (clazz == AttachmentDisplay.class && element != null && element.isJsonObject()
                && element.getAsJsonObject().has("mount_offset")
                && element.getAsJsonObject().get("mount_offset").isJsonObject()) {
            JsonObject copy = element.getAsJsonObject().deepCopy();
            copy.remove("mount_offset");
            toParse = copy;
        }
        Object result = gson.fromJson(toParse, clazz);
        if (clazz == AttachmentDisplay.class && result instanceof IUnderbarrelDisplay holder
                && element != null && element.isJsonObject()) {
            renaissance$applyExtras(holder, element.getAsJsonObject(), gson);
        }
        return result;
    }

    private static void renaissance$applyExtras(IUnderbarrelDisplay holder, JsonObject obj, Gson gson) {
        JsonElement hideElement = obj.get("hide_tactical_handguard");
        if (hideElement != null && hideElement.isJsonPrimitive()) {
            boolean hide;
            try {
                hide = hideElement.getAsBoolean();
            } catch (Exception e) {
                hide = Boolean.parseBoolean(hideElement.getAsString());
            }
            holder.renaissance$setHideTacticalHandguard(hide);
        }

        if (obj.has("underbarrel_display") && obj.get("underbarrel_display").isJsonObject()) {
            try {
                GunDisplay display = gson.fromJson(obj.getAsJsonObject("underbarrel_display"), GunDisplay.class);
                holder.renaissance$setUnderbarrelDisplay(display);
            } catch (Exception e) {
                RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Failed to parse 'underbarrel_display' block", e);
            }
        }

        if (obj.has("mount_offset") && obj.get("mount_offset").isJsonObject()) {
            try {
                JsonObject mo = obj.getAsJsonObject("mount_offset");
                float[] offset = new float[6];
                readVec3(mo.get("position"), offset, 0);
                readVec3(mo.get("rotation"), offset, 3);
                holder.renaissance$setMountOffset(offset);
            } catch (Exception e) {
                RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Failed to parse 'mount_offset' block", e);
            }
        }
    }

    /** Reads a 3-number JSON array into {@code out[base..base+2]}; missing/short entries stay 0. */
    private static void readVec3(JsonElement element, float[] out, int base) {
        if (element == null || !element.isJsonArray()) return;
        JsonArray arr = element.getAsJsonArray();
        for (int i = 0; i < 3 && i < arr.size(); i++) {
            if (arr.get(i).isJsonPrimitive()) out[base + i] = arr.get(i).getAsFloat();
        }
    }
}
