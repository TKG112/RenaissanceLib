package net.tkg.RenaissanceLib.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.resource.manager.JsonDataManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.tkg.RenaissanceLib.attachment.BinaryFireModeJson;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets pack authors write {@code "binary"} directly in a gun's {@code fire_mode} array.
 *
 * <p>TaC:Z parses {@code fire_mode} straight into the fixed {@code FireMode} enum, which has no
 * binary value — so an unmodified {@code "binary"} entry would fail to load. Before TaC:Z parses a
 * gun-data file, we strip {@code "binary"} out of the array and record it (with its position, as a
 * 1-based index so it stays truthy) into {@code script_param.binary_fire_mode} — the same flag
 * {@link net.tkg.RenaissanceLib.attachment.AttachmentOverrides#isBinaryCapable} already reads. The
 * gun then loads cleanly and the binary pseudo-mode is woven back into the cycle at that position.
 *
 * <p>Runs at the shared parse point, so the translated JSON is what gets synced to clients too.
 */
@Mixin(value = JsonDataManager.class, remap = false)
public abstract class JsonDataManagerMixin {

    @Shadow
    public abstract Class<?> getDataClass();

    @Inject(method = "parseJson", at = @At("HEAD"), remap = false)
    private void renaissance$translateBinaryFireMode(JsonElement element, CallbackInfoReturnable<Object> cir) {
        if (getDataClass() != GunData.class) return;
        if (element == null || !element.isJsonObject()) return;
        BinaryFireModeJson.translate(element.getAsJsonObject());
    }
}
