package net.tkg.RenaissanceLib.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.ShaderSpec;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ScopeShaderRegistry extends SimplePreparableReloadListener<Map<ResourceLocation, ShaderSpec>> {
    private static final Gson GSON = new Gson();
    private static final String SEARCH_PATH = "display/attachments";
    private static final String DISPLAY_SUFFIX = "_display";

    @Override
    protected @NotNull Map<ResourceLocation, ShaderSpec> prepare(ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        Map<ResourceLocation, ShaderSpec> map = new HashMap<>();

        var resources = resourceManager.listResources(SEARCH_PATH, location -> location.getPath().endsWith(".json"));

        for (var entry : resources.entrySet()) {
            ResourceLocation fileLoc = entry.getKey();

            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                if (json == null || !json.has("shader")) {
                    continue;
                }

                ShaderSpec spec = ShaderSpec.parse(json.get("shader"));
                if (spec == null) {
                    continue;
                }

                String path = fileLoc.getPath();
                String cleanPath = path.substring(SEARCH_PATH.length() + 1, path.length() - 5);
                if (cleanPath.endsWith(DISPLAY_SUFFIX)) {
                    cleanPath = cleanPath.substring(0, cleanPath.length() - DISPLAY_SUFFIX.length());
                }

                ResourceLocation scopeId = ResourceLocation.fromNamespaceAndPath(fileLoc.getNamespace(), cleanPath);
                map.put(scopeId, spec);
            } catch (Exception ignored) {
            }
        }

        return map;
    }

    @Override
    protected void apply(Map<ResourceLocation, ShaderSpec> object, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        ScopeShaderStorage.clear();
        object.forEach(ScopeShaderStorage::setShader);
    }
}
