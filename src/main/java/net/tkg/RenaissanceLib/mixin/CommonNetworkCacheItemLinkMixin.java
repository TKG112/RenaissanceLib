package net.tkg.RenaissanceLib.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.resource.network.DataType;
import net.minecraft.resources.ResourceLocation;
import net.tkg.RenaissanceLib.attachment.ItemLink;
import net.tkg.RenaissanceLib.attachment.ItemLinkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * The client half of {@code item_link} parsing (dedicated servers): when a server syncs its gun pack, the raw
 * attachment index jsons arrive here as strings — TaC:Z parses them into a POJO that drops our {@code item_link},
 * so we read it from the raw json first and rebuild {@link ItemLinkRegistry}. The server / integrated-server
 * half is {@code CommonDataManagerItemLinkMixin}.
 */
@Mixin(value = CommonNetworkCache.class, remap = false)
public abstract class CommonNetworkCacheItemLinkMixin {

    @Inject(method = "fromNetwork(Ljava/util/Map;)V", at = @At("HEAD"))
    private void renaissance$parseSyncedItemLinks(Map<DataType, Map<ResourceLocation, String>> cache, CallbackInfo ci) {
        ItemLinkRegistry.clear();
        Map<ResourceLocation, String> attachmentIndex = cache.get(DataType.ATTACHMENT_INDEX);
        if (attachmentIndex == null) return;
        for (Map.Entry<ResourceLocation, String> entry : attachmentIndex.entrySet()) {
            try {
                JsonElement element = JsonParser.parseString(entry.getValue());
                if (!element.isJsonObject()) continue;
                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("item_link")) continue;
                ItemLink link = ItemLink.parse(obj.get("item_link").getAsString());
                if (link != null) ItemLinkRegistry.put(entry.getKey(), link);
            } catch (Exception ignored) {
                // A single malformed entry shouldn't abort the whole sync.
            }
        }
    }
}
