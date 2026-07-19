package net.tkg.RenaissanceLib.client;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class ScopeShaderStorage {
    private static final Map<ResourceLocation, ResourceLocation> SCOPE_SHADERS = new HashMap<>();

    public static void setShader(ResourceLocation scopeId, ResourceLocation shader) {
        SCOPE_SHADERS.put(scopeId, shader);
    }

    public static ResourceLocation getShader(ResourceLocation scopeId) {
        return SCOPE_SHADERS.get(scopeId);
    }

    public static void clear() {
        SCOPE_SHADERS.clear();
    }
}
