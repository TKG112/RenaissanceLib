package net.tkg.RenaissanceLib.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.resource.manager.JsonDataManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.tkg.RenaissanceLib.attachment.SemiVariantJson;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets pack authors write the semi-variant tokens ({@code "binary"}, {@code "manual"}) directly in a gun's
 * {@code fire_mode} array.
 *
 * <p>TaC:Z parses {@code fire_mode} straight into the fixed {@code FireMode} enum, which has no such values — so
 * an unmodified token would fail to load. Before TaC:Z parses a gun-data file, {@link SemiVariantJson} strips them
 * out of the array and records each (with its position) into {@code script_param.<token>_fire_mode}. The gun then
 * loads cleanly and the variant is woven back into the fire-select cycle at that position.
 *
 * <p>Runs at the shared parse point, so the translated JSON is what gets synced to clients too.
 */
@Mixin(value = JsonDataManager.class, remap = false)
public abstract class JsonDataManagerMixin {

    @Shadow
    public abstract Class<?> getDataClass();

    @Inject(method = "parseJson", at = @At("HEAD"), remap = false)
    private void renaissance$translateSemiVariants(JsonElement element, CallbackInfoReturnable<Object> cir) {
        if (getDataClass() != GunData.class) return;
        if (element == null || !element.isJsonObject()) return;
        SemiVariantJson.translate(element.getAsJsonObject());
    }
}
