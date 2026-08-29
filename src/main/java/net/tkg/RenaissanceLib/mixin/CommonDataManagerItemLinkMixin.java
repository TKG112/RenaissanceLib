package net.tkg.RenaissanceLib.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.manager.CommonDataManager;
import com.tacz.guns.resource.manager.JsonDataManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.tkg.RenaissanceLib.attachment.ItemLink;
import net.tkg.RenaissanceLib.attachment.ItemLinkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Parses our custom {@code item_link} field off attachment index jsons as they load (server / integrated
 * server), into {@link ItemLinkRegistry}. TaC:Z's {@code AttachmentIndexPOJO} has no such field, so it would
 * otherwise be silently dropped. The dedicated-server client half is handled by
 * {@code CommonNetworkCacheItemLinkMixin} (which parses the synced raw jsons).
 */
@Mixin(value = CommonDataManager.class, remap = false)
public abstract class CommonDataManagerItemLinkMixin {

    @Inject(
            method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("TAIL"))
    private void renaissance$parseItemLinks(Map<ResourceLocation, JsonElement> map, ResourceManager rm,
                                            ProfilerFiller profiler, CallbackInfo ci) {
        if (((JsonDataManager<?>) (Object) this).getDataClass() != CommonAttachmentIndex.class) return;
        ItemLinkRegistry.clear();
        for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
            JsonElement element = entry.getValue();
            if (element == null || !element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            if (!obj.has("item_link")) continue;
            ItemLink link = ItemLink.parse(obj.get("item_link").getAsString());
            if (link != null) ItemLinkRegistry.put(entry.getKey(), link);
        }
    }
}
