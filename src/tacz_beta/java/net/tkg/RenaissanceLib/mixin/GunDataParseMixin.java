package net.tkg.RenaissanceLib.mixin;

import com.google.gson.JsonElement;
import com.tacz.guns.resource.manager.GunDataManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.tkg.RenaissanceLib.attachment.SemiVariantJson;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The {@code "binary"} / {@code "manual"} fire-mode translation for gun data — <b>BETA variant</b>. The beta
 * parses gun data in its own {@code GunDataManager.parseJson}, which overrides {@code JsonDataManager.parseJson}
 * without calling it, so {@link JsonDataManagerMixin} never sees a gun: an untranslated {@code "manual"} then
 * reaches Gson, becomes a null {@code FireMode} and breaks the gun. Same translation, at this parse point. The
 * server builds its client-sync JSON from the same (now translated) elements after parsing, so clients get it
 * too. The stable variant has no {@code GunDataManager} (its placeholder is empty).
 */
@Mixin(value = GunDataManager.class, remap = false)
public abstract class GunDataParseMixin {

    @Inject(method = "parseJson(Lcom/google/gson/JsonElement;)Lcom/tacz/guns/resource/pojo/data/gun/GunData;",
            at = @At("HEAD"), remap = false)
    private void renaissance$translateSemiVariants(JsonElement element, CallbackInfoReturnable<GunData> cir) {
        if (element != null && element.isJsonObject()) SemiVariantJson.translate(element.getAsJsonObject());
    }
}
