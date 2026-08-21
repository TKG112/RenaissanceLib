package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import net.minecraft.resources.ResourceLocation;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.ScopeShaderModifier;
import net.tkg.RenaissanceLib.attachment.ShaderSpec;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ScopeShaderStorage {
    private static final Map<ResourceLocation, ShaderSpec> DISPLAY_SHADERS = new HashMap<>();
    private static final Set<ResourceLocation> WARNED = new HashSet<>();

    public static void setShader(ResourceLocation scopeId, ShaderSpec spec) {
        DISPLAY_SHADERS.put(scopeId, spec);
    }

    @Nullable
    public static ResourceLocation getShader(ResourceLocation scopeId, int viewIndex) {
        ShaderSpec spec = getSpec(scopeId);
        return spec == null ? null : spec.forView(viewIndex);
    }

    public static boolean hasAnyShader(ResourceLocation scopeId) {
        return getSpec(scopeId) != null;
    }

    @Nullable
    private static ShaderSpec getSpec(ResourceLocation scopeId) {
        ShaderSpec display = DISPLAY_SHADERS.get(scopeId);
        ShaderSpec authoritative = getDataSpec(scopeId);

        if (authoritative == null) return display;

        if (display != null && !display.equals(authoritative) && WARNED.add(scopeId)) {
            RenaissanceLibMod.LOGGER.warn(
                    "[RenaissanceLib] Scope {} declares shader '{}' in its display file but '{}' in its "
                            + "data file. Using the data file. If this is not a typo in the pack, the "
                            + "display file has been modified locally.",
                    scopeId, display, authoritative);
        }
        return authoritative;
    }

    @Nullable
    private static ShaderSpec getDataSpec(ResourceLocation scopeId) {
        try {
            AttachmentData data = TimelessAPI.getCommonAttachmentIndex(scopeId)
                    .map(index -> index.getData())
                    .orElse(null);
            if (data == null) return null;
            JsonProperty<?> property = data.getModifier().get(ScopeShaderModifier.ID);
            if (property == null) return null;
            Object value = property.getValue();
            return value instanceof ShaderSpec spec ? spec : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void clear() {
        DISPLAY_SHADERS.clear();
        WARNED.clear();
    }
}
